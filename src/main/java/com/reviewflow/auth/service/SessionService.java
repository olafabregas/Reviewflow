package com.reviewflow.auth.service;

import com.reviewflow.admin.service.AuditService;
import com.reviewflow.auth.dto.response.SessionEntryResponse;
import com.reviewflow.auth.dto.response.SessionListResponse;
import com.reviewflow.auth.repository.RefreshTokenRepository;
import com.reviewflow.infrastructure.security.TokenVersionInvalidatedEvent;
import com.reviewflow.infrastructure.websocket.WebSocketUserEventService;
import com.reviewflow.shared.domain.RefreshToken;
import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionService {

  private final RefreshTokenRepository refreshTokenRepository;
  private final HashidService hashidService;
  private final AuditService auditService;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final WebSocketUserEventService webSocketUserEventService;

  public SessionListResponse listSessions(Long userId, String refreshCookieValue) {
    Instant now = Instant.now();
    List<RefreshToken> active =
        refreshTokenRepository.findByUser_IdAndRevokedFalseAndExpiresAtAfter(userId, now);

    Long currentSessionGroupId =
        refreshCookieValue == null || refreshCookieValue.isBlank()
            ? null
            : refreshTokenRepository
                .findByTokenHash(RefreshTokenService.hashRefreshToken(refreshCookieValue))
                .filter(t -> t.getUser().getId().equals(userId))
                .map(RefreshToken::getSessionGroupId)
                .orElse(null);

    Map<Long, RefreshToken> best = new HashMap<>();
    for (RefreshToken rt : active) {
      if (rt.getSessionGroupId() == null) {
        continue;
      }
      best.merge(
          rt.getSessionGroupId(),
          rt,
          (a, b) -> a.getId() > b.getId() ? a : b);
    }

    List<SessionEntryResponse> rows = new ArrayList<>();
    for (RefreshToken rt : best.values()) {
      rows.add(
          SessionEntryResponse.builder()
              .id(hashidService.encode(rt.getSessionGroupId()))
              .deviceId(rt.getDeviceId())
              .userAgent(
                  Optional.ofNullable(rt.getUserAgentLastSeen())
                      .orElse(rt.getUserAgentCreated()))
              .ipCreated(rt.getIpCreated())
              .ipLastSeen(Optional.ofNullable(rt.getIpLastSeen()).orElse(rt.getIpCreated()))
              .createdAt(rt.getCreatedAt())
              .lastUsedAt(rt.getLastUsedAt())
              .expiresAt(rt.getExpiresAt())
              .current(
                  currentSessionGroupId != null
                      && currentSessionGroupId.equals(rt.getSessionGroupId()))
              .build());
    }
    rows.sort(Comparator.comparing(SessionEntryResponse::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
    return SessionListResponse.builder().sessions(rows).build();
  }

  @Transactional
  public void revokeSession(Long userId, String sessionHashId, boolean wasCurrentSession) {
    Long sessionGroupId = hashidService.decodeOrThrow(sessionHashId);
    RefreshToken any =
        refreshTokenRepository
            .findByUser_IdAndRevokedFalseAndExpiresAtAfter(userId, Instant.now())
            .stream()
            .filter(rt -> sessionGroupId.equals(rt.getSessionGroupId()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Session", sessionHashId));

    String familyId = any.getFamilyId();
    refreshTokenRepository.revokeActiveTokensInFamily(familyId);

    auditService.log(
        userId,
        "SESSION_REVOKED",
        "User",
        userId,
        "Session revoked: " + sessionHashId,
        null);

    if (wasCurrentSession) {
      webSocketUserEventService.notifySessionsRevoked(userId);
    }
  }

  @Transactional
  public void logoutAll(Long userId) {
    refreshTokenRepository.revokeAllForUser(userId);
    userRepository.incrementTokenVersion(userId);
    eventPublisher.publishEvent(new TokenVersionInvalidatedEvent(userId));
    auditService.log(
        userId, "USER_LOGOUT_ALL", "User", userId, "All sessions revoked by user", null);
    webSocketUserEventService.notifySessionsRevoked(userId);
  }

  public boolean isCurrentSession(Long userId, String refreshCookieValue, String sessionHashId) {
    Long sessionGroupId = hashidService.decode(sessionHashId);
    if (sessionGroupId == null) {
      return false;
    }
    return matchesCurrentSession(userId, refreshCookieValue, sessionGroupId);
  }

  private boolean matchesCurrentSession(
      Long userId, String refreshCookieValue, Long sessionGroupId) {
    if (refreshCookieValue == null || refreshCookieValue.isBlank()) {
      return false;
    }
    return refreshTokenRepository
        .findByTokenHash(RefreshTokenService.hashRefreshToken(refreshCookieValue))
        .filter(t -> t.getUser().getId().equals(userId))
        .map(RefreshToken::getSessionGroupId)
        .map(sg -> sg.equals(sessionGroupId))
        .orElse(false);
  }
}
