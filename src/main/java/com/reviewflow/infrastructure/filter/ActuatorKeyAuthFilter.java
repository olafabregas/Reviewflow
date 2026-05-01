package com.reviewflow.infrastructure.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * PRD-08: Machine Client Actuator Authentication Filter Validates X-Actuator-Key header for
 * /actuator/** endpoints This allows machine clients (Prometheus, CI/CD, CloudWatch agents) to call
 * Actuator endpoints without requiring JWT authentication
 */
@Slf4j
@Component
public class ActuatorKeyAuthFilter extends OncePerRequestFilter {

  @Value("${actuator.internal-key:}")
  private String actuatorInternalKey;

  private static final String ACTUATOR_KEY_HEADER = "X-Actuator-Key";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // Only process /actuator/** requests
    if (!request.getRequestURI().startsWith("/actuator/")) {
      filterChain.doFilter(request, response);
      return;
    }

    // Public health endpoint - no authentication required
    if (request.getRequestURI().equals("/actuator/health")
        || request.getRequestURI().equals("/actuator/info")) {
      filterChain.doFilter(request, response);
      return;
    }

    // Validate X-Actuator-Key header for all other /actuator/* endpoints
    String providedKey = request.getHeader(ACTUATOR_KEY_HEADER);

    if (providedKey == null || providedKey.isBlank()) {
      log.warn(
          "Actuator access attempt without X-Actuator-Key header from ip={} endpoint={}",
          request.getRemoteAddr(),
          request.getRequestURI());
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.getWriter().write("{\"error\":\"Missing X-Actuator-Key header\"}");
      return;
    }

    if (!providedKey.equals(actuatorInternalKey)) {
      log.warn(
          "Actuator access attempt with invalid X-Actuator-Key from ip={} endpoint={}",
          request.getRemoteAddr(),
          request.getRequestURI());
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.getWriter().write("{\"error\":\"Invalid X-Actuator-Key\"}");
      return;
    }

    // Key is valid - set authentication in security context so Spring allows the request
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken("actuator-machine-client", null, null);
    SecurityContextHolder.getContext().setAuthentication(token);

    log.debug(
        "Actuator access granted via X-Actuator-Key from ip={} endpoint={}",
        request.getRemoteAddr(),
        request.getRequestURI());

    filterChain.doFilter(request, response);
  }
}
