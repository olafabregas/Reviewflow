package com.reviewflow.auth.controller;

import com.reviewflow.auth.dto.response.SessionListResponse;
import com.reviewflow.auth.service.AuthCookieIssuer;
import com.reviewflow.auth.service.SessionService;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.shared.exception.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/sessions")
@RequiredArgsConstructor
public class SessionController {

  private final SessionService sessionService;
  private final AuthCookieIssuer authCookieIssuer;

  @Operation(summary = "List active login sessions for the current user")
  @GetMapping
  public ResponseEntity<ApiResponse<SessionListResponse>> list(
      @AuthenticationPrincipal ReviewFlowUserDetails user, HttpServletRequest request) {
    String refresh = getCookieValue(request, authCookieIssuer.getRefreshCookieName());
    return ResponseEntity.ok(
        ApiResponse.ok(sessionService.listSessions(user.getUserId(), refresh)));
  }

  @Operation(summary = "Revoke a single session (refresh token family)")
  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Map<String, String>>> revoke(
      @AuthenticationPrincipal ReviewFlowUserDetails user,
      @PathVariable("id") String sessionId,
      HttpServletRequest request,
      HttpServletResponse response) {
    String refresh = getCookieValue(request, authCookieIssuer.getRefreshCookieName());
    boolean current = sessionService.isCurrentSession(user.getUserId(), refresh, sessionId);
    sessionService.revokeSession(user.getUserId(), sessionId, current);
    if (current) {
      authCookieIssuer.clearAllAuthCookies(response);
    }
    return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Session revoked")));
  }

  @Operation(summary = "Logout all sessions for the current user")
  @PostMapping("/logout-all")
  public ResponseEntity<ApiResponse<Map<String, String>>> logoutAll(
      @AuthenticationPrincipal ReviewFlowUserDetails user, HttpServletResponse response) {
    sessionService.logoutAll(user.getUserId());
    authCookieIssuer.clearAllAuthCookies(response);
    return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "All sessions logged out")));
  }

  private String getCookieValue(HttpServletRequest request, String name) {
    if (request.getCookies() == null) {
      return null;
    }
    for (jakarta.servlet.http.Cookie c : request.getCookies()) {
      if (name.equals(c.getName())) {
        return c.getValue();
      }
    }
    return null;
  }
}
