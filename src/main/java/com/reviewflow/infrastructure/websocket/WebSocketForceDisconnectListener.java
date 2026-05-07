package com.reviewflow.infrastructure.websocket;

import com.reviewflow.shared.event.ForceLogoutEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketForceDisconnectListener {

  private final WebSocketUserEventService webSocketUserEventService;
  private final WebSocketSessionRegistry webSocketSessionRegistry;

  @Order(10)
  @EventListener
  public void onForceLogout(ForceLogoutEvent event) {
    Long userId = event.getTargetUserId();
    webSocketSessionRegistry.clearUser(userId);
    webSocketUserEventService.notifySessionsRevoked(userId);
    log.debug("WS transport invalidated after force logout for user {}", userId);
  }
}
