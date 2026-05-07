package com.reviewflow.infrastructure.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class WebSocketPresenceListener {

  private final WebSocketSessionRegistry webSocketSessionRegistry;

  @EventListener
  public void onDisconnect(SessionDisconnectEvent event) {
    StompHeaderAccessor acc = StompHeaderAccessor.wrap(event.getMessage());
    if (acc != null && acc.getSessionId() != null) {
      webSocketSessionRegistry.removeSession(acc.getSessionId());
    }
  }
}
