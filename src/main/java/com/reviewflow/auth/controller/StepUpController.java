package com.reviewflow.auth.controller;

import com.reviewflow.auth.dto.request.StepUpRequest;
import com.reviewflow.auth.service.AuthCookieIssuer;
import com.reviewflow.auth.service.SessionPolicyResolver;
import com.reviewflow.auth.service.StepUpService;
import com.reviewflow.infrastructure.security.RateLimiterService;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.shared.exception.ApiResponse;
import com.reviewflow.shared.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class StepUpController {

  private final StepUpService stepUpService;
  private final AuthCookieIssuer authCookieIssuer;
  private final SessionPolicyResolver sessionPolicyResolver;
  private final RateLimiterService rateLimiterService;

  @PostMapping("/step-up")
  public ResponseEntity<ApiResponse<Map<String, Long>>> stepUp(
      @AuthenticationPrincipal ReviewFlowUserDetails user,
      @Valid @RequestBody StepUpRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (user == null) {
      return ResponseEntity.status(401)
          .body(ApiResponse.error("UNAUTHORIZED", "Not authenticated"));
    }

    if (rateLimiterService.isStepUpRateLimited(user.getUserId())) {
      throw new TooManyRequestsException(
          "Too many step-up attempts. Please try again later.",
          rateLimiterService.getStepUpRetryAfterSeconds(user.getUserId()));
    }

    String ip = clientIp(request);
    String userAgent = request.getHeader("User-Agent");
    try {
      String accessToken =
          stepUpService.completeStepUp(user.getUserId(), body.getPassword(), ip, userAgent);
      long accessTtlMs = sessionPolicyResolver.resolveFor(user.getRole()).accessTtlMs();
      authCookieIssuer.writeAccess(response, accessToken, accessTtlMs / 1000);
      rateLimiterService.clearStepUpAttempts(user.getUserId());
      long stepUpAt = java.time.Instant.now().getEpochSecond();
      return ResponseEntity.ok(ApiResponse.ok(Map.of("stepUpAt", stepUpAt)));
    } catch (BadCredentialsException e) {
      rateLimiterService.recordStepUpAttempt(user.getUserId());
      throw e;
    }
  }

  private static String clientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isEmpty()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
