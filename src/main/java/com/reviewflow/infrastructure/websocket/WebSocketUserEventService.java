package com.reviewflow.infrastructure.websocket;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketUserEventService {

  private final SimpMessagingTemplate messagingTemplate;

  public void notifySessionsRevoked(Long userId) {
    try {
      messagingTemplate.convertAndSendToUser(
          String.valueOf(userId),
          "/queue/session-revoked",
          Map.of("reason", "transport_invalidated"));
    } catch (Exception e) {
      log.debug("WS session-revoked notify skipped for user {}: {}", userId, e.getMessage());
    }
  }
}
