package com.reviewflow.infrastructure.ratelimit;

import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.API_EXPORT;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.API_PUBLIC;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.API_READ;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.API_WRITE;

import com.reviewflow.infrastructure.security.HttpErrorJsonWriter;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.util.IpAddressExtractor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

  private final RateLimitService rateLimitService;
  private final IpAddressExtractor ipExtractor;
  private final HttpErrorJsonWriter errorWriter;

  @Value("${rate-limit.filter.skip-swagger-locally:true}")
  private boolean skipSwaggerLocally;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path.startsWith("/actuator/health")) {
      return true;
    }
    if (path.startsWith("/api/v1/auth/")) {
      return true;
    }
    if (path.startsWith("/ws/")) {
      return true;
    }
    if (skipSwaggerLocally) {
      if (path.startsWith("/swagger-ui")
          || path.startsWith("/v3/api-docs")
          || path.startsWith("/docs")) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    boolean authenticated =
        auth != null
            && auth.isAuthenticated()
            && !(auth instanceof AnonymousAuthenticationToken)
            && auth.getPrincipal() instanceof ReviewFlowUserDetails;

    RateLimitResult result;
    if (!authenticated) {
      String ip = ipExtractor.extract(request);
      result = rateLimitService.tryConsume(ip, API_PUBLIC, null);
    } else {
      ReviewFlowUserDetails principal = (ReviewFlowUserDetails) auth.getPrincipal();
      String userId = String.valueOf(principal.getUserId());
      UserRole role = principal.getRole();
      RateLimitStrategy strategy = strategyFor(request);
      result = rateLimitService.tryConsume(userId, strategy, role);
    }

    addRateLimitHeaders(response, result);

    if (!result.allowed()) {
      errorWriter.writeTooManyRequests(
          response,
          result.retryAfterSeconds(),
          "Too many requests. Please try again in " + result.retryAfterSeconds() + " seconds.",
          result.limitCapacity(),
          result.resetEpochSeconds());
      return;
    }

    chain.doFilter(request, response);
  }

  private RateLimitStrategy strategyFor(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    if (isExportPath(path, method)) {
      return API_EXPORT;
    }
    if ("GET".equals(method) || "HEAD".equals(method)) {
      return API_READ;
    }
    return API_WRITE;
  }

  private boolean isExportPath(String path, String method) {
    if ("GET".equals(method) || "HEAD".equals(method)) {
      return path.contains("/export")
          || (path.contains("/pdf") && !path.contains("/preview"));
    }
    return path.contains("/import")
        || path.contains("/export")
        || (path.contains("/pdf") && !path.contains("/preview"))
        || (path.contains("/jobs/") && path.endsWith("/commit"));
  }

  private void addRateLimitHeaders(HttpServletResponse response, RateLimitResult result) {
    response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));
    if (result.limitCapacity() > 0) {
      response.setHeader("X-RateLimit-Limit", String.valueOf(result.limitCapacity()));
    }
    if (result.resetEpochSeconds() > 0) {
      response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetEpochSeconds()));
    }
  }
}
