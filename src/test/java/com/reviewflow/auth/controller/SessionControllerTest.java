package com.reviewflow.auth.controller;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reviewflow.auth.dto.response.SessionEntryResponse;
import com.reviewflow.auth.dto.response.SessionListResponse;
import com.reviewflow.auth.service.AuthCookieIssuer;
import com.reviewflow.auth.service.SessionService;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.exception.ApiResponse;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

  @Mock private SessionService sessionService;
  @Mock private AuthCookieIssuer authCookieIssuer;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  @InjectMocks private SessionController sessionController;

  @Test
  void listSessionsDelegatesAndReturnsResponse() {
    User user =
        User.builder()
            .id(42L)
            .email("user@example.com")
            .passwordHash("hash")
            .firstName("Test")
            .lastName("User")
            .role(UserRole.STUDENT)
            .isActive(true)
            .build();
    ReviewFlowUserDetails principal = new ReviewFlowUserDetails(user);
    when(authCookieIssuer.getRefreshCookieName()).thenReturn("reviewflow_refresh");
    when(request.getCookies()).thenReturn(new Cookie[] {
        createCookie("reviewflow_refresh", "refresh-token-123")
    });
    SessionListResponse sessionList =
        SessionListResponse.builder()
            .sessions(List.of(
                SessionEntryResponse.builder()
                    .id("session-1")
                    .deviceId("device-1")
                    .userAgent("Chrome/124")
                    .ipCreated("203.0.113.1")
                    .ipLastSeen("203.0.113.1")
                    .current(true)
                    .build()))
            .build();
    when(sessionService.listSessions(42L, "refresh-token-123")).thenReturn(sessionList);

    var result = sessionController.list(principal, request);

    assertThat(result.getBody()).isNotNull();
    ApiResponse<SessionListResponse> payload = result.getBody();
    assertThat(payload.isSuccess()).isTrue();
    assertThat(payload.getData().getSessions()).hasSize(1);
    assertThat(payload.getData().getSessions().get(0).getId()).isEqualTo("session-1");
  }

  @Test
  void revokeSessionClearsAllCookiesWhenCurrent() {
    User user =
        User.builder()
            .id(42L)
            .email("user@example.com")
            .passwordHash("hash")
            .firstName("Test")
            .lastName("User")
            .role(UserRole.STUDENT)
            .isActive(true)
            .build();
    ReviewFlowUserDetails principal = new ReviewFlowUserDetails(user);
    when(authCookieIssuer.getRefreshCookieName()).thenReturn("reviewflow_refresh");
    when(request.getCookies()).thenReturn(new Cookie[] {
        createCookie("reviewflow_refresh", "refresh-token-123")
    });
    when(sessionService.isCurrentSession(42L, "refresh-token-123", "session-1")).thenReturn(true);

    var result = sessionController.revoke(principal, "session-1", request, response);

    assertThat(result.getBody()).isNotNull();
    ApiResponse<Map<String, String>> payload = result.getBody();
    assertThat(payload.isSuccess()).isTrue();
    assertThat(payload.getData()).containsEntry("message", "Session revoked");

    verify(sessionService).revokeSession(42L, "session-1", true);
    verify(authCookieIssuer).clearAllAuthCookies(response);
  }

  @Test
  void logoutAllRevokesAllSessionsAndClears() {
    User user =
        User.builder()
            .id(42L)
            .email("user@example.com")
            .passwordHash("hash")
            .firstName("Test")
            .lastName("User")
            .role(UserRole.STUDENT)
            .isActive(true)
            .build();
    ReviewFlowUserDetails principal = new ReviewFlowUserDetails(user);

    var result = sessionController.logoutAll(principal, response);

    assertThat(result.getBody()).isNotNull();
    ApiResponse<Map<String, String>> payload = result.getBody();
    assertThat(payload.isSuccess()).isTrue();
    assertThat(payload.getData()).containsEntry("message", "All sessions logged out");

    verify(sessionService).logoutAll(42L);
    verify(authCookieIssuer).clearAllAuthCookies(response);
  }

  private Cookie createCookie(String name, String value) {
    Cookie c = new Cookie(name, value);
    return c;
  }
}
