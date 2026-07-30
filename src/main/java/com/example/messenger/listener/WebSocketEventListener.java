package com.example.messenger.listener;

import com.example.messenger.repository.UserRepository;
import com.example.messenger.service.PresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;

@Component
public class WebSocketEventListener {

    private final PresenceService presenceService;
    private final UserRepository userRepository;

    public WebSocketEventListener(PresenceService presenceService, UserRepository userRepository) {
        this.presenceService = presenceService;
        this.userRepository = userRepository;
    }

    @EventListener
    public void handleConnected(SessionConnectedEvent event) {
        Principal principal = event.getUser();
        if (principal == null) return;
        String username = principal.getName();
        presenceService.userConnected(username);
        userRepository.findByUsername(username).ifPresent(u -> {
            u.setOnline(true);
            userRepository.save(u);
        });
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        if (principal == null) return;
        String username = principal.getName();
        boolean fullyOffline = presenceService.userDisconnected(username);
        if (fullyOffline) {
            userRepository.findByUsername(username).ifPresent(u -> {
                u.setOnline(false);
                u.setLastSeen(Instant.now());
                userRepository.save(u);
            });
        }
    }
}
