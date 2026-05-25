package com.reviewflow.auth.interceptor;

import com.reviewflow.auth.annotation.RequiresStepUp;
import com.reviewflow.auth.exception.StepUpRequiredException;
import com.reviewflow.infrastructure.security.AuthAccessTokenResolver;
import com.reviewflow.infrastructure.security.JwtService;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Order(100)
@RequiredArgsConstructor
public class StepUpInterceptor implements HandlerInterceptor {

  private final JwtService jwtService;
  private final AuthAccessTokenResolver authAccessTokenResolver;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    if (!(handler instanceof HandlerMethod hm)) {
      return true;
    }
    RequiresStepUp ann = hm.getMethodAnnotation(RequiresStepUp.class);
    if (ann == null) {
      return true;
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof ReviewFlowUserDetails)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return false;
    }

    String token =
        authAccessTokenResolver
            .resolveRawAccessToken(request)
            .orElseThrow(
                () ->
                    new StepUpRequiredException(
                        "Step-up required",
                        Map.of("stepUpEndpoint", "/api/v1/auth/step-up")));

    Long stepUpAt = jwtService.extractStepUpAt(token);
    int maxAge = ann.maxAgeSeconds();
    long now = Instant.now().getEpochSecond();
    if (stepUpAt == null || now - stepUpAt > maxAge) {
      throw new StepUpRequiredException(
          "Step-up required",
          Map.of("stepUpEndpoint", "/api/v1/auth/step-up", "maxAgeSeconds", maxAge));
    }
    return true;
  }
}
