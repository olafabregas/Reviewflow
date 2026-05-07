package com.reviewflow.infrastructure.security;

import com.reviewflow.auth.service.TokenVersionService;
import com.reviewflow.auth.service.WsTicketService;
import com.reviewflow.auth.service.WsTicketService.WsTicketPayload;
import com.reviewflow.infrastructure.websocket.WebSocketSessionRegistry;
import com.reviewflow.shared.domain.User;
import com.reviewflow.user.repository.UserRepository;
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
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

  private final TokenVersionService tokenVersionService;
  private final WsTicketService wsTicketService;
  private final UserRepository userRepository;
  private final WebSocketSessionRegistry webSocketSessionRegistry;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null) {
      return message;
    }

    if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
      return message;
    }

    String ticket = firstHeader(accessor, "X-Auth-Ticket", "auth-ticket");
    if (ticket != null && !ticket.isBlank()) {
      return handleTicket(ticket, accessor, message);
    }

    List<String> authHeaders = accessor.getNativeHeader("Authorization");
    if (authHeaders != null && !authHeaders.isEmpty()) {
      log.warn("WebSocket CONNECT rejected — JWT bridge disabled; use X-Auth-Ticket");
      return null;
    }

    log.warn("WebSocket CONNECT rejected — no X-Auth-Ticket header");
    return null;
  }

  private Message<?> handleTicket(String ticket, StompHeaderAccessor accessor, Message<?> message) {
    try {
      var payloadOpt = wsTicketService.consume(ticket);
      if (payloadOpt.isEmpty()) {
        log.warn("WebSocket CONNECT rejected — invalid or reused ticket");
        return null;
      }
      WsTicketPayload payload = payloadOpt.get();
      User user =
          userRepository
              .findById(payload.userId())
              .orElseThrow(() -> new IllegalStateException("User not found for ticket"));

      int currentVer = tokenVersionService.getCurrentVersion(payload.userId());
      if (payload.tokenVersion() != currentVer) {
        log.warn(
            "WebSocket CONNECT rejected — ticket token version mismatch userId={}", payload.userId());
        return null;
      }

      var userDetails = new ReviewFlowUserDetails(user);
      var auth =
          new UsernamePasswordAuthenticationToken(
              userDetails, null, userDetails.getAuthorities());
      accessor.setUser(auth);
      webSocketSessionRegistry.register(user.getId(), accessor.getSessionId());
      log.debug("WebSocket CONNECT authenticated via ticket for userId={}", user.getId());
      return message;
    } catch (Exception e) {
      log.warn("WebSocket CONNECT rejected — {}", e.getMessage());
      return null;
    }
  }

  private static String firstHeader(
      StompHeaderAccessor accessor, String primary, String alternate) {
    List<String> a = accessor.getNativeHeader(primary);
    if (a != null && !a.isEmpty() && a.get(0) != null && !a.get(0).isBlank()) {
      return a.get(0).trim();
    }
    List<String> b = accessor.getNativeHeader(alternate);
    if (b != null && !b.isEmpty() && b.get(0) != null && !b.get(0).isBlank()) {
      return b.get(0).trim();
    }
    return null;
  }
}
