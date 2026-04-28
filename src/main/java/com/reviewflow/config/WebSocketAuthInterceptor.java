package com.reviewflow.config;

import com.reviewflow.security.JwtService;
import com.reviewflow.service.TokenVersionService;
import java.util.List;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

  private final JwtService jwtService;
  private final UserDetailsService userDetailsService;
  private final TokenVersionService tokenVersionService;

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
        log.warn("WebSocket CONNECT rejected — could not extract email");
        return null;
      }

      var userDetails = userDetailsService.loadUserByUsername(email);

      if (!jwtService.isTokenValid(token, userDetails)) {
        log.warn("WebSocket CONNECT rejected — token invalid for {}", email);
        return null;
      }

      // Token version check — closes the stateless revocation gap for WebSocket connections
      Integer tokenVer = jwtService.extractTokenVersion(token);
      Long userId = jwtService.extractUserId(token);
      if (userId != null && tokenVer != null) {
        int currentVer = tokenVersionService.getCurrentVersion(userId);
        if (tokenVer != currentVer) {
          log.warn("WebSocket CONNECT rejected — token version mismatch for userId={}", userId);
          return null;
        }
      }

      // Set authenticated principal on the STOMP session
      var auth =
          new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
      accessor.setUser(auth);
      log.debug("WebSocket CONNECT authenticated: {}", email);

    } catch (Exception e) {
      log.warn("WebSocket CONNECT rejected — error: {}", e.getMessage());
      return null;
    }

    return message;
  }
}
