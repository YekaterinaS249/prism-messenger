package com.example.messenger.config;

import com.example.messenger.security.JwtUtil;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Reads the JWT sent by the client as a STOMP CONNECT header ("Authorization: Bearer ...")
 * and turns it into the Principal used for @MessageMapping/@SendToUser routing.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    public StompAuthChannelInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    String username = jwtUtil.extractUsername(token);
                    if (username != null && jwtUtil.isTokenValid(token, username)) {
                        Principal principal = new UsernamePasswordAuthenticationToken(username, null, java.util.Collections.emptyList());
                        accessor.setUser(principal);
                    }
                } catch (Exception ignored) {
                    // invalid token -> connection stays anonymous, will be rejected downstream if needed
                }
            }
        }
        return message;
    }
}
