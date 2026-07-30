package com.example.messenger.service;

import com.example.messenger.dto.AuditLogEntryDto;
import com.example.messenger.dto.PageResponse;
import com.example.messenger.model.AdminAuditLog;
import com.example.messenger.model.User;
import com.example.messenger.repository.AdminAuditLogRepository;
import com.example.messenger.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Records admin actions for the "Журнал" panel. Never blocks or fails the action it's logging.
 * Depends on UserRepository directly (not UserService) so it doesn't form a constructor cycle
 * with UserService, which also needs to call into this service to log ban/verify/delete actions.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AdminAuditLogRepository repository;
    private final UserRepository userRepository;

    public AuditLogService(AdminAuditLogRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    public void record(String actorUsername, String action, String target, String details) {
        try {
            repository.save(new AdminAuditLog(actorUsername, action, target, details));
        } catch (Exception e) {
            log.warn("Failed to record admin audit log entry", e);
        }
    }

    /** Server-side paged audit log for the "Журнал" panel. */
    public PageResponse<AuditLogEntryDto> recent(String requester, int page, int size) {
        boolean isAdmin = userRepository.findByUsername(requester).map(User::isAdmin).orElse(false);
        if (!isAdmin) {
            throw new SecurityException("Admin only");
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        Page<AdminAuditLog> result = repository.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage, safeSize));
        List<AuditLogEntryDto> content = result.getContent().stream()
                .map(a -> new AuditLogEntryDto(a.getActorUsername(), a.getAction(), a.getTarget(), a.getDetails(),
                        DateTimeFormatter.ISO_INSTANT.format(a.getCreatedAt().atZone(ZoneOffset.UTC))))
                .collect(Collectors.toList());
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
