package com.reviewflow.infrastructure.websocket;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WebSocketSessionRegistry {

  private final ConcurrentHashMap<String, Long> sessionToUser = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Set<String>> userToSessions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, WebSocketSession> stompIdToTransport =
      new ConcurrentHashMap<>();

  public void register(Long userId, String stompSessionId) {
    if (userId == null || stompSessionId == null) {
      return;
    }
    sessionToUser.put(stompSessionId, userId);
    userToSessions
        .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
        .add(stompSessionId);
  }

  public void registerTransport(String stompSessionId, WebSocketSession transportSession) {
    if (stompSessionId != null && transportSession != null) {
      stompIdToTransport.put(stompSessionId, transportSession);
    }
  }

  public WebSocketSession getTransportSession(String stompSessionId) {
    return stompIdToTransport.get(stompSessionId);
  }

  public void removeSession(String stompSessionId) {
    stompIdToTransport.remove(stompSessionId);
    Long userId = sessionToUser.remove(stompSessionId);
    if (userId != null) {
      Set<String> sessions = userToSessions.get(userId);
      if (sessions != null) {
        sessions.remove(stompSessionId);
        if (sessions.isEmpty()) {
          userToSessions.remove(userId);
        }
      }
    }
  }

  public Set<String> getSessionIds(Long userId) {
    Set<String> s = userToSessions.get(userId);
    return s == null ? Set.of() : Collections.unmodifiableSet(s);
  }

  public void clearUser(Long userId) {
    Set<String> sessions = userToSessions.remove(userId);
    if (sessions != null) {
      for (String sid : sessions) {
        sessionToUser.remove(sid);
        stompIdToTransport.remove(sid);
      }
    }
  }
}
