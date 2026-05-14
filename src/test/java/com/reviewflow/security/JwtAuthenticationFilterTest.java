package com.reviewflow.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reviewflow.auth.exception.TokenVersionMismatchException;
import com.reviewflow.auth.service.SessionPolicyResolver;
import com.reviewflow.auth.service.TokenVersionService;
import com.reviewflow.auth.service.UserDetailsCacheService;
import com.reviewflow.infrastructure.monitoring.SecurityMetrics;
import com.reviewflow.infrastructure.security.HttpErrorJsonWriter;
import com.reviewflow.infrastructure.security.JwtAuthenticationFilter;
import com.reviewflow.infrastructure.security.JwtService;
import com.reviewflow.infrastructure.security.RateLimiterService;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.shared.util.IpAddressExtractor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock private JwtService jwtService;
  @Mock private UserDetailsCacheService userDetailsCacheService;
  @Mock private RateLimiterService rateLimiterService;
  @Mock private IpAddressExtractor ipAddressExtractor;
  @Mock private SecurityMetrics securityMetrics;
  @Mock private HashidService hashidService;
  @Mock private TokenVersionService tokenVersionService;
  @Mock private SessionPolicyResolver sessionPolicyResolver;
  @Mock private HttpErrorJsonWriter httpErrorJsonWriter;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;
  @Mock private ReviewFlowUserDetails userDetails;

  @InjectMocks private JwtAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    when(ipAddressExtractor.extract(request)).thenReturn("127.0.0.1");
    when(rateLimiterService.isTokenRateLimited("127.0.0.1")).thenReturn(false);
    lenient().when(userDetails.getRole()).thenReturn(UserRole.STUDENT);
    lenient().when(userDetails.getUserId()).thenReturn(1L);
    lenient().when(hashidService.encode(1L)).thenReturn("U1");
    lenient()
        .when(sessionPolicyResolver.resolveFor(any(UserRole.class)))
        .thenReturn(new SessionPolicyResolver.SessionPolicy(1, 24, 900_000L, 3_600_000L, false));
  }

  @Test
  void doFilterInternal_ShouldContinue_WhenTokenVersionMatches()
      throws ServletException, IOException {
    String token = "valid-token";
    Long userId = 1L;
    int version = 1;

    when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
    when(jwtService.extractEmail(token)).thenReturn("user@test.com");
    when(userDetailsCacheService.loadUserByUsername("user@test.com")).thenReturn(userDetails);
    when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);
    when(jwtService.extractTokenVersion(token)).thenReturn(version);
    when(jwtService.extractUserId(token)).thenReturn(userId);
    when(tokenVersionService.getCurrentVersion(userId)).thenReturn(version);

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(securityMetrics).recordAuthTokenFromBearer();
  }

  @Test
  void doFilterInternal_ShouldNotAuthenticate_WhenTokenVersionMismatches()
      throws ServletException, IOException {
    String token = "old-token";
    Long userId = 1L;
    int oldVersion = 1;
    int currentVersion = 2;

    when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
    when(jwtService.extractEmail(token)).thenReturn("user@test.com");
    when(userDetailsCacheService.loadUserByUsername("user@test.com")).thenReturn(userDetails);
    when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);
    when(jwtService.extractTokenVersion(token)).thenReturn(oldVersion);
    when(jwtService.extractUserId(token)).thenReturn(userId);
    when(tokenVersionService.getCurrentVersion(userId)).thenReturn(currentVersion);

    filter.doFilter(request, response, filterChain);

    verify(rateLimiterService).recordFailedTokenValidation("127.0.0.1");
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doFilterInternal_WhenTokenRateLimited_WritesJson429() throws Exception {
    when(rateLimiterService.isTokenRateLimited("127.0.0.1")).thenReturn(true);
    when(rateLimiterService.getTokenRetryAfterSeconds("127.0.0.1")).thenReturn(42L);

    filter.doFilter(request, response, filterChain);

    verify(securityMetrics).recordTokenRateLimited();
    verify(httpErrorJsonWriter)
        .writeTooManyRequests(
            response, 42L, "Too many token validation attempts. Try again later.");
  }
}
