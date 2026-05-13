package com.reviewflow.auth.controller;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reviewflow.auth.dto.request.StepUpRequest;
import com.reviewflow.auth.dto.response.StepUpResponse;
import com.reviewflow.auth.service.AuthCookieIssuer;
import com.reviewflow.auth.service.SessionPolicyResolver;
import com.reviewflow.auth.service.StepUpService;
import com.reviewflow.infrastructure.security.RateLimiterService;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.exception.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class StepUpControllerTest {

  @Mock private StepUpService stepUpService;
  @Mock private AuthCookieIssuer authCookieIssuer;
  @Mock private SessionPolicyResolver sessionPolicyResolver;
  @Mock private RateLimiterService rateLimiterService;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  @InjectMocks private StepUpController stepUpController;

  @Test
  void shouldReturnStepUpValidUntilAndWriteAccessCookie() {
    User user =
        User.builder()
            .id(42L)
            .email("admin@example.com")
            .passwordHash("hash")
            .firstName("Ada")
            .lastName("Admin")
            .role(UserRole.ADMIN)
            .isActive(true)
            .build();
    ReviewFlowUserDetails principal = new ReviewFlowUserDetails(user);
    StepUpRequest body = new StepUpRequest("secret");

    when(rateLimiterService.isStepUpRateLimited(42L)).thenReturn(false);
    when(stepUpService.completeStepUp(anyLong(), anyString(), anyString(), anyString()))
        .thenReturn("new-access-token");
    when(sessionPolicyResolver.resolveFor(UserRole.ADMIN))
        .thenReturn(new SessionPolicyResolver.SessionPolicy(2, 12, 900000L, 604800000L, false));
    when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("203.0.113.5");

    long before = Instant.now().getEpochSecond();
    var result = stepUpController.stepUp(principal, body, request, response);
    long after = Instant.now().getEpochSecond();

    assertThat(result.getBody()).isNotNull();
    ApiResponse<StepUpResponse> payload = result.getBody();
    assertThat(payload.isSuccess()).isTrue();
    assertThat(payload.getData()).isNotNull();
    assertThat(payload.getData().getStepUpValidUntil()).isBetween(before + 300, after + 300);

    verify(authCookieIssuer).writeAccess(response, "new-access-token", 900);
    verify(rateLimiterService).clearStepUpAttempts(42L);
  }
}
