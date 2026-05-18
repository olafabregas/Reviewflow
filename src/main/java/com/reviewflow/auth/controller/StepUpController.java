package com.reviewflow.auth.controller;

import com.reviewflow.auth.dto.request.StepUpRequest;
import com.reviewflow.auth.dto.response.StepUpResponse;
import com.reviewflow.auth.service.AuthCookieIssuer;
import com.reviewflow.auth.service.SessionPolicyResolver;
import com.reviewflow.auth.service.StepUpService;
import com.reviewflow.infrastructure.ratelimit.RateLimitResult;
import com.reviewflow.infrastructure.ratelimit.RateLimitService;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_STEP_UP;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.shared.exception.ApiResponse;
import com.reviewflow.shared.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Instant;
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
  private final RateLimitService rateLimitService;

  @PostMapping("/step-up")
  public ResponseEntity<ApiResponse<StepUpResponse>> stepUp(
      @AuthenticationPrincipal ReviewFlowUserDetails user,
      @Valid @RequestBody StepUpRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (user == null) {
      return ResponseEntity.status(401)
          .body(ApiResponse.error("UNAUTHORIZED", "Not authenticated"));
    }

    String userIdKey = String.valueOf(user.getUserId());
    RateLimitResult stepUpProbe = rateLimitService.probe(userIdKey, AUTH_STEP_UP, user.getRole());
    if (!stepUpProbe.allowed()) {
      throw new TooManyRequestsException(
          "Too many step-up attempts. Please try again later.", stepUpProbe.retryAfterSeconds());
    }

    String ip = clientIp(request);
    String userAgent = request.getHeader("User-Agent");
    try {
      String accessToken =
          stepUpService.completeStepUp(user.getUserId(), body.getPassword(), ip, userAgent);
      long accessTtlMs = sessionPolicyResolver.resolveFor(user.getRole()).accessTtlMs();
      authCookieIssuer.writeAccess(response, accessToken, accessTtlMs / 1000);
      rateLimitService.reset(userIdKey, AUTH_STEP_UP);
      long stepUpValidUntil = Instant.now().getEpochSecond() + 300;
      return ResponseEntity.ok(
          ApiResponse.ok(StepUpResponse.builder().stepUpValidUntil(stepUpValidUntil).build()));
    } catch (BadCredentialsException e) {
      rateLimitService.consumeOnFailure(userIdKey, AUTH_STEP_UP, user.getRole());
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
