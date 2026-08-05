package com.example.messenger.service;

import com.example.messenger.dto.ChatMessagePayload;
import com.example.messenger.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Fills in server-trusted fields (timestamp, sender display name/avatar) before a chat payload is
 * broadcast, and bumps an aggregate day-bucketed counter (see AnalyticsService) for the admin
 * dashboard. Persistence of the enriched payload itself happens separately in MessageService,
 * called from ChatWebSocketController after this.
 */
@Service
public class ChatService {

    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;

    public ChatService(UserRepository userRepository, AnalyticsService analyticsService) {
        this.userRepository = userRepository;
        this.analyticsService = analyticsService;
    }

    public ChatMessagePayload enrich(ChatMessagePayload payload) {
        payload.setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now().atZone(ZoneOffset.UTC)));
        userRepository.findByUsername(payload.getSenderUsername())
                .ifPresent(u -> {
                    payload.setSenderDisplayName(u.getDisplayName());
                    payload.setSenderAvatarUrl(u.getAvatarUrl());
                });
        analyticsService.incrementMessageCount(payload.getGroupId());
        return payload;
    }
}
