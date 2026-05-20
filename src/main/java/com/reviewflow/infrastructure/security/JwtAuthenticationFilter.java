package com.reviewflow.infrastructure.security;

import com.reviewflow.auth.exception.TokenVersionMismatchException;
import com.reviewflow.auth.service.SessionPolicyResolver;
import com.reviewflow.auth.service.TokenVersionService;
import com.reviewflow.auth.service.UserDetailsCacheService;
import com.reviewflow.infrastructure.monitoring.ReviewFlowMetrics;
import com.reviewflow.infrastructure.ratelimit.RateLimitResult;
import com.reviewflow.infrastructure.ratelimit.RateLimitService;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_JWT_FAILURE;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.shared.util.IpAddressExtractor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final UserDetailsCacheService userDetailsCacheService;
  private final RateLimitService rateLimitService;
  private final IpAddressExtractor ipAddressExtractor;
  private final ReviewFlowMetrics metrics;
  private final HashidService hashidService;
  private final TokenVersionService tokenVersionService;
  private final SessionPolicyResolver sessionPolicyResolver;
  private final HttpErrorJsonWriter httpErrorJsonWriter;

  @Value("${jwt.cookie-name:reviewflow_access}")
  private String accessCookieName;

  @Value("${security.token.fingerprinting-enabled:false}")
  private boolean tokenFingerprintingEnabled;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String ip = ipAddressExtractor.extract(request);

    RateLimitResult jwtProbe = rateLimitService.probe(ip, AUTH_JWT_FAILURE, null);
    if (!jwtProbe.allowed()) {
      log.warn("Token validation rate limited for IP: {}", ip);
      metrics.recordTokenRateLimited();
      httpErrorJsonWriter.writeTooManyRequests(
          response,
          jwtProbe.retryAfterSeconds(),
          "Too many token validation attempts. Try again later.",
          jwtProbe.limitCapacity(),
          jwtProbe.resetEpochSeconds());
      return;
    }

    TokenExtraction extraction = extractToken(request);

    if (extraction.token().isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = extraction.token().get();
    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      String email = jwtService.extractEmail(token);
      if (email != null) {
        var userDetails = userDetailsCacheService.loadUserByUsername(email);

        if (jwtService.isTokenValid(token, userDetails)) {
          Integer tokenVer = jwtService.extractTokenVersion(token);
          Long userId = jwtService.extractUserId(token);
          if (userId != null && tokenVer != null) {
            int currentVer = tokenVersionService.getCurrentVersion(userId);
            if (tokenVer != currentVer) {
              throw new TokenVersionMismatchException(userId);
            }
          }

          boolean fingerprintRequired = tokenFingerprintingEnabled;
          if (userDetails instanceof ReviewFlowUserDetails rfDetails) {
            fingerprintRequired = fingerprintRequired || sessionPolicyResolver.resolveFor(rfDetails.getRole()).requireFingerprint();
          }

          if (fingerprintRequired) {
            String tokenUserAgent = jwtService.extractClaim(token, "userAgent");
            String requestUserAgent = request.getHeader("User-Agent");

            if (tokenUserAgent != null && !tokenUserAgent.equals(requestUserAgent)) {
              log.warn(
                  "Token fingerprint mismatch for user={} ip={} tokenUA={} requestUA={}",
                  email,
                  ip,
                  tokenUserAgent,
                  requestUserAgent);
              metrics.recordTokenFingerprintMismatch();
              rateLimitService.consumeOnFailure(ip, AUTH_JWT_FAILURE, null);
              filterChain.doFilter(request, response);
              return;
            }
          }

          var auth =
              new UsernamePasswordAuthenticationToken(
                  userDetails, null, userDetails.getAuthorities());
          auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(auth);

          if (extraction.fromBearer()) {
            metrics.recordAuthTokenFromBearer();
          } else {
            metrics.recordAuthTokenFromCookie();
          }

          if (userDetails instanceof ReviewFlowUserDetails rfDetails) {
            MDC.put("userId", hashidService.encode(rfDetails.getUserId()));
            MDC.put("role", rfDetails.getRole().name());
          }

          log.debug(
              "Authenticated user={} from ip={} via {}",
              email,
              ip,
              extraction.fromBearer() ? "Bearer" : "Cookie");
        }
      }
    } catch (TokenVersionMismatchException ex) {
      log.warn("Token version mismatch for request {}: {}", request.getRequestURI(), ex.getMessage());
      rateLimitService.consumeOnFailure(ip, AUTH_JWT_FAILURE, null);
      metrics.recordTokenValidation("invalid");
      httpErrorJsonWriter.writeError(
          response,
          HttpStatus.UNAUTHORIZED.value(),
          "TOKEN_REVOKED",
          "Your session has been invalidated. Please log in again.");
      return;
    } catch (io.jsonwebtoken.JwtException
        | org.springframework.security.core.userdetails.UsernameNotFoundException e) {
      log.debug("Token validation failed for ip={}: {}", ip, e.getMessage());
      rateLimitService.consumeOnFailure(ip, AUTH_JWT_FAILURE, null);
      metrics.recordTokenValidation("invalid");
    }

    filterChain.doFilter(request, response);
  }

  private TokenExtraction extractToken(HttpServletRequest request) {
    Optional<String> cookieToken = getAccessTokenFromCookie(request);
    if (cookieToken.isPresent()) {
      return new TokenExtraction(cookieToken, false);
    }
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String t = authHeader.substring(7);
      if (!t.isBlank()) {
        return new TokenExtraction(Optional.of(t), true);
      }
    }
    return new TokenExtraction(Optional.empty(), false);
  }

  private Optional<String> getAccessTokenFromCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    for (Cookie cookie : cookies) {
      if (accessCookieName.equals(cookie.getName())) {
        String value = cookie.getValue();
        return value != null && !value.isBlank() ? Optional.of(value) : Optional.empty();
      }
    }
    return Optional.empty();
  }

  private record TokenExtraction(Optional<String> token, boolean fromBearer) {}
}
