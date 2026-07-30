package com.example.messenger.service;

import com.example.messenger.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks which usernames currently have an open WebSocket session and broadcasts changes. */
@Service
public class PresenceService {

    private final Map<String, Integer> sessionCounts = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    public PresenceService(SimpMessagingTemplate messagingTemplate, UserRepository userRepository) {
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
    }

    public void userConnected(String username) {
        sessionCounts.merge(username, 1, Integer::sum);
        broadcast(username, true);
    }

    /** @return true if the user has no more open sessions (fully offline) */
    public boolean userDisconnected(String username) {
        Integer remaining = sessionCounts.computeIfPresent(username, (k, v) -> v > 1 ? v - 1 : null);
        boolean offline = remaining == null;
        if (offline) {
            broadcast(username, false);
        }
        return offline;
    }

    public boolean isOnline(String username) {
        return sessionCounts.containsKey(username);
    }

    /** Number of distinct users with at least one open WebSocket session right now. */
    public int onlineCount() {
        return sessionCounts.size();
    }

    private void broadcast(String username, boolean online) {
        // Respect the user's privacy toggle: if they've hidden their online status,
        // never broadcast a "true" transition to other clients.
        boolean visibleOnline = online && userRepository.findByUsername(username)
                .map(u -> u.isShowOnlineStatus())
                .orElse(true);
        messagingTemplate.convertAndSend("/topic/presence",
                Map.of("username", username, "online", visibleOnline));
    }
}
