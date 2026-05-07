package com.reviewflow.infrastructure.security;

import com.reviewflow.auth.exception.TokenVersionMismatchException;
import com.reviewflow.auth.service.TokenVersionService;
import com.reviewflow.auth.service.UserDetailsCacheService;
import com.reviewflow.infrastructure.monitoring.SecurityMetrics;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.shared.util.IpAddressExtractor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
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
  private final RateLimiterService rateLimiterService;
  private final IpAddressExtractor ipAddressExtractor;
  private final SecurityMetrics securityMetrics;
  private final HashidService hashidService;
  private final TokenVersionService tokenVersionService;
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

    if (rateLimiterService.isTokenRateLimited(ip)) {
      log.warn("Token validation rate limited for IP: {}", ip);
      securityMetrics.recordTokenRateLimited();
      httpErrorJsonWriter.writeTooManyRequests(
          response,
          rateLimiterService.getTokenRetryAfterSeconds(ip),
          "Too many token validation attempts. Try again later.");
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

          if (tokenFingerprintingEnabled) {
            String tokenUserAgent = jwtService.extractClaim(token, "userAgent");
            String requestUserAgent = request.getHeader("User-Agent");

            if (tokenUserAgent != null && !tokenUserAgent.equals(requestUserAgent)) {
              log.warn(
                  "Token fingerprint mismatch for user={} ip={} tokenUA={} requestUA={}",
                  email,
                  ip,
                  tokenUserAgent,
                  requestUserAgent);
              securityMetrics.recordTokenFingerprintMismatch();
              rateLimiterService.recordFailedTokenValidation(ip);
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
            securityMetrics.recordAuthTokenFromBearer();
          } else {
            securityMetrics.recordAuthTokenFromCookie();
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
    } catch (io.jsonwebtoken.JwtException
        | org.springframework.security.core.userdetails.UsernameNotFoundException
        | TokenVersionMismatchException e) {
      log.debug("Token validation failed for ip={}: {}", ip, e.getMessage());
      rateLimiterService.recordFailedTokenValidation(ip);
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
