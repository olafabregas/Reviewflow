package com.reviewflow.config;

import com.reviewflow.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService         jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        // Only authenticate on the initial CONNECT command
        if (!StompCommand.CONNECT.equals(accessor.getCommand())) return message;

        List<String> authHeaders = accessor.getNativeHeader("Authorization");

        if (authHeaders == null || authHeaders.isEmpty()) {
            log.warn("WebSocket CONNECT rejected — no Authorization header");
            return null; // Returning null rejects the connection
        }

        String token = authHeaders.get(0);

        try {
            String email = jwtService.extractEmail(token);
            if (email == null) {
                log.warn("WebSocket CONNECT rejected — could not extract email from token");
                return null;
            }

            var userDetails = userDetailsService.loadUserByUsername(email);

            if (!jwtService.isTokenValid(token, userDetails)) {
                log.warn("WebSocket CONNECT rejected — token invalid for {}", email);
                return null;
            }

            // Set authenticated principal on the STOMP session
            // This is what makes convertAndSendToUser() route to the right person
            var auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
            );
            accessor.setUser(auth);
            log.debug("WebSocket CONNECT authenticated: {}", email);

        } catch (Exception e) {
            log.warn("WebSocket CONNECT rejected — {}", e.getMessage());
            return null;
        }

        return message;
    }
}
