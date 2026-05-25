package com.reviewflow.infrastructure.security;

import com.reviewflow.auth.service.TokenVersionService;
import com.reviewflow.auth.service.WsTicketService;
import com.reviewflow.auth.service.WsTicketService.WsTicketPayload;
import com.reviewflow.infrastructure.websocket.WebSocketSessionRegistry;
import com.reviewflow.infrastructure.websocket.WebSocketTransportTrackingDecorator;
import com.reviewflow.shared.domain.User;
import com.reviewflow.user.repository.UserRepository;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.socket.WebSocketSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
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

    StompCommand command = accessor.getCommand();
    if (StompCommand.CONNECT.equals(command)) {
      return handleConnect(accessor, message);
    }
    if (StompCommand.SUBSCRIBE.equals(command)) {
      validateSubscribeDestination(accessor);
    }

    return message;
  }

  private Message<?> handleConnect(StompHeaderAccessor accessor, Message<?> message) {
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

  private void validateSubscribeDestination(StompHeaderAccessor accessor) {
    Principal principal = accessor.getUser();
    if (principal == null) {
      throw new MessagingException("Not authenticated");
    }

    String destination = accessor.getDestination();
    if (destination == null) {
      return;
    }

    if (destination.startsWith("/user/")) {
      String destinationUserId = extractUserIdFromDestination(destination);
      String principalName = principal.getName();
      if (destinationUserId != null && !principalName.equals(destinationUserId)) {
        log.warn(
            "WebSocket SUBSCRIBE to foreign destination: principal={} destination={}",
            principalName,
            destination);
        throw new MessagingException("Cannot subscribe to another user's destination");
      }
    }
  }

  private static String extractUserIdFromDestination(String destination) {
    String[] parts = destination.split("/");
    if (parts.length >= 3) {
      return parts[2];
    }
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

      // PRD-18: STOMP user destination uses Principal#getName() — must be raw user id string.
      var userDetails = new WebSocketPrincipalDetails(user);
      var auth =
          new UsernamePasswordAuthenticationToken(
              userDetails, null, userDetails.getAuthorities());
      accessor.setUser(auth);
      String stompSessionId = accessor.getSessionId();
      webSocketSessionRegistry.register(user.getId(), stompSessionId);
      registerTransportSession(accessor, stompSessionId);
      log.debug("WebSocket CONNECT authenticated via ticket for userId={}", user.getId());
      return message;
    } catch (Exception e) {
      log.warn("WebSocket CONNECT rejected — {}", e.getMessage());
      return null;
    }
  }

  private void registerTransportSession(StompHeaderAccessor accessor, String stompSessionId) {
    Map<String, Object> attrs = accessor.getSessionAttributes();
    if (attrs == null) {
      return;
    }
    Object transport = attrs.get(WebSocketTransportTrackingDecorator.TRANSPORT_SESSION_KEY);
    if (transport instanceof WebSocketSession wsSession) {
      webSocketSessionRegistry.registerTransport(stompSessionId, wsSession);
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

  /** Same as {@link ReviewFlowUserDetails} but {@link #getUsername()} returns numeric user id for STOMP. */
  private static final class WebSocketPrincipalDetails extends ReviewFlowUserDetails {
    WebSocketPrincipalDetails(User user) {
      super(user);
    }

    @Override
    public String getUsername() {
      return String.valueOf(getUserId());
    }
  }
}
