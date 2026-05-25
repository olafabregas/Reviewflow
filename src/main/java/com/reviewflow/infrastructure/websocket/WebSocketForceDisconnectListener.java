package com.reviewflow.infrastructure.websocket;

import com.reviewflow.shared.event.ForceLogoutEvent;
import com.reviewflow.shared.util.HashidService;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketForceDisconnectListener {

  private final WebSocketUserEventService webSocketUserEventService;
  private final WebSocketSessionRegistry webSocketSessionRegistry;
  private final HashidService hashidService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onForceLogout(ForceLogoutEvent event) {
    Long userId = event.getTargetUserId();
    String hashedUserId = hashidService.encode(userId);

    log.info("Force disconnect initiated userId={}", hashedUserId);

    // Step 1: token revoked before this listener runs (AFTER_COMMIT on forceLogout TX)

    // Step 2: notify well-behaved clients
    webSocketUserEventService.notifySessionsRevoked(userId);

    // Step 3: hard close transport sessions
    Set<String> sessionIds = webSocketSessionRegistry.getSessionIds(userId);
    if (sessionIds.isEmpty()) {
      log.debug("No active WS sessions for userId={}", hashedUserId);
    } else {
      log.info("Closing {} WS sessions for userId={}", sessionIds.size(), hashedUserId);
      sessionIds.forEach(sessionId -> closeSession(sessionId, hashedUserId));
    }

    // Step 4: reconnect hits invalid token on CONNECT (WebSocketAuthInterceptor)

    webSocketSessionRegistry.clearUser(userId);
  }

  private void closeSession(String sessionId, String hashedUserId) {
    try {
      WebSocketSession rawSession = webSocketSessionRegistry.getTransportSession(sessionId);
      if (rawSession != null && rawSession.isOpen()) {
        rawSession.close(CloseStatus.POLICY_VIOLATION);
        log.debug("WS session closed sessionId={} userId={}", sessionId, hashedUserId);
      }
    } catch (IOException e) {
      log.warn("Error closing WS session sessionId={}: {}", sessionId, e.getMessage());
    }
  }
}
