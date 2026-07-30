package com.example.messenger.service;

import com.example.messenger.dto.ChatMessagePayload;
import com.example.messenger.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Messages are relayed live over WebSocket only — nothing about an individual message is written
 * to the database (no content, sender, or recipient). This service just fills in server-trusted
 * fields (timestamp, sender display name) before the payload is broadcast, and bumps an
 * aggregate day-bucketed counter (see AnalyticsService) purely for the admin dashboard — there is
 * still no per-message persistence or history to query.
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
