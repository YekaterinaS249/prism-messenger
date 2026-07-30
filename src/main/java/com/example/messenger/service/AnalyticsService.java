package com.example.messenger.service;

import com.example.messenger.dto.AdminDashboardDto;
import com.example.messenger.dto.DailyStatDto;
import com.example.messenger.dto.GroupActivityDto;
import com.example.messenger.model.ChatGroup;
import com.example.messenger.model.DailyGroupMessageStat;
import com.example.messenger.model.DailyMessageStat;
import com.example.messenger.repository.ChatGroupRepository;
import com.example.messenger.repository.DailyGroupMessageStatRepository;
import com.example.messenger.repository.DailyMessageStatRepository;
import com.example.messenger.repository.GroupMemberRepository;
import com.example.messenger.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin-only dashboard/analytics. The only thing persisted here is aggregate day-bucketed
 * message counts (total, and per-group) — never message content, sender, or recipient. Chat
 * itself stays exactly as ephemeral as before; this is purely a counter for the admin panel.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final UserRepository userRepository;
    private final ChatGroupRepository chatGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PresenceService presenceService;
    private final UserService userService;
    private final DailyMessageStatRepository dailyMessageStatRepository;
    private final DailyGroupMessageStatRepository dailyGroupMessageStatRepository;

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    public AnalyticsService(UserRepository userRepository, ChatGroupRepository chatGroupRepository,
                             GroupMemberRepository groupMemberRepository, PresenceService presenceService,
                             UserService userService, DailyMessageStatRepository dailyMessageStatRepository,
                             DailyGroupMessageStatRepository dailyGroupMessageStatRepository) {
        this.userRepository = userRepository;
        this.chatGroupRepository = chatGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.presenceService = presenceService;
        this.userService = userService;
        this.dailyMessageStatRepository = dailyMessageStatRepository;
        this.dailyGroupMessageStatRepository = dailyGroupMessageStatRepository;
    }

    /**
     * Bumps today's message counters. Called for every direct + group message sent. Deliberately
     * swallows any failure — analytics must never be able to break live message delivery.
     */
    public void incrementMessageCount(Long groupId) {
        try {
            LocalDate today = LocalDate.now(ZONE);
            if (dailyMessageStatRepository.incrementIfExists(today) == 0) {
                try {
                    dailyMessageStatRepository.save(new DailyMessageStat(today, 1));
                } catch (DataIntegrityViolationException race) {
                    dailyMessageStatRepository.incrementIfExists(today);
                }
            }
            if (groupId != null) {
                if (dailyGroupMessageStatRepository.incrementIfExists(today, groupId) == 0) {
                    try {
                        dailyGroupMessageStatRepository.save(new DailyGroupMessageStat(today, groupId, 1));
                    } catch (DataIntegrityViolationException race) {
                        dailyGroupMessageStatRepository.incrementIfExists(today, groupId);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to record message analytics counter", e);
        }
    }

    public AdminDashboardDto dashboard(String requester) {
        requireAdmin(requester);
        LocalDate todayStart = LocalDate.now(ZONE);
        Instant todayStartInstant = todayStart.atStartOfDay(ZONE).toInstant();
        long totalUsers = userRepository.count();
        int onlineNow = presenceService.onlineCount();
        long totalGroups = chatGroupRepository.count();
        long newUsersToday = userRepository.countByCreatedAtGreaterThanEqual(todayStartInstant);
        long storageBytes = storageUsageBytes();
        return new AdminDashboardDto(totalUsers, onlineNow, totalGroups, newUsersToday, storageBytes);
    }

    public List<DailyStatDto> trend(String requester, int days) {
        requireAdmin(requester);
        int windowDays = Math.max(1, Math.min(days, 90));
        LocalDate from = LocalDate.now(ZONE).minusDays(windowDays - 1L);

        Map<LocalDate, Long> messagesByDay = dailyMessageStatRepository
                .findByStatDateGreaterThanEqualOrderByStatDateAsc(from).stream()
                .collect(Collectors.toMap(DailyMessageStat::getStatDate, DailyMessageStat::getMessageCount));

        Instant fromInstant = from.atStartOfDay(ZONE).toInstant();
        Map<LocalDate, Long> newUsersByDay = userRepository.findAll().stream()
                .filter(u -> u.getCreatedAt() != null && !u.getCreatedAt().isBefore(fromInstant))
                .collect(Collectors.groupingBy(u -> u.getCreatedAt().atZone(ZONE).toLocalDate(), Collectors.counting()));

        List<DailyStatDto> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(LocalDate.now(ZONE)); d = d.plusDays(1)) {
            out.add(new DailyStatDto(d.toString(), messagesByDay.getOrDefault(d, 0L), newUsersByDay.getOrDefault(d, 0L)));
        }
        return out;
    }

    public List<GroupActivityDto> topGroups(String requester, int days, int limit) {
        requireAdmin(requester);
        int windowDays = Math.max(1, Math.min(days, 90));
        int cap = Math.max(1, Math.min(limit, 50));
        LocalDate from = LocalDate.now(ZONE).minusDays(windowDays - 1L);

        Map<Long, Long> totalsByGroup = dailyGroupMessageStatRepository.findByStatDateGreaterThanEqual(from).stream()
                .collect(Collectors.groupingBy(DailyGroupMessageStat::getGroupId,
                        Collectors.summingLong(DailyGroupMessageStat::getMessageCount)));

        List<GroupActivityDto> out = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : totalsByGroup.entrySet()) {
            ChatGroup group = chatGroupRepository.findById(entry.getKey()).orElse(null);
            if (group == null) continue; // group was deleted since
            long memberCount = groupMemberRepository.countByGroupId(entry.getKey());
            out.add(new GroupActivityDto(entry.getKey(), group.getName(), entry.getValue(), memberCount));
        }
        out.sort(Comparator.comparingLong(GroupActivityDto::getMessageCount).reversed());
        return out.size() > cap ? out.subList(0, cap) : out;
    }

    private long storageUsageBytes() {
        try {
            Path dir = Paths.get(uploadsDir);
            if (!Files.isDirectory(dir)) return 0L;
            try (var stream = Files.walk(dir)) {
                return stream.filter(Files::isRegularFile).mapToLong(p -> {
                    try { return Files.size(p); } catch (IOException e) { return 0L; }
                }).sum();
            }
        } catch (IOException e) {
            log.warn("Failed to compute uploads storage usage", e);
            return 0L;
        }
    }

    private void requireAdmin(String requester) {
        if (!userService.isAdmin(requester)) {
            throw new SecurityException("Admin only");
        }
    }
}
