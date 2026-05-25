package com.reviewflow.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
import com.reviewflow.infrastructure.ratelimit.RateLimitService;
import com.reviewflow.infrastructure.ratelimit.RateLimitTestFixtures;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_JWT_FAILURE;
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
  @Mock private RateLimitService rateLimitService;
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
    org.springframework.test.util.ReflectionTestUtils.setField(filter, "allowBearerHeader", true);
    org.springframework.test.util.ReflectionTestUtils.setField(
        filter, "allowLegacyTokensWithoutKid", true);
    when(ipAddressExtractor.extract(request)).thenReturn("127.0.0.1");
    when(rateLimitService.probe("127.0.0.1", AUTH_JWT_FAILURE, null))
        .thenReturn(RateLimitTestFixtures.allowed(AUTH_JWT_FAILURE));
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

    verify(rateLimitService).consumeOnFailure("127.0.0.1", AUTH_JWT_FAILURE, null);
    verify(httpErrorJsonWriter)
        .writeError(
            eq(response),
            eq(401),
            eq("TOKEN_REVOKED"),
            eq("Your session has been invalidated. Please log in again."));
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void doFilterInternal_WhenTokenRateLimited_WritesJson429() throws Exception {
    when(rateLimitService.probe("127.0.0.1", AUTH_JWT_FAILURE, null))
        .thenReturn(RateLimitTestFixtures.denied(AUTH_JWT_FAILURE));

    filter.doFilter(request, response, filterChain);

    verify(securityMetrics).recordTokenRateLimited();
    verify(httpErrorJsonWriter)
        .writeTooManyRequests(
            eq(response),
            anyLong(),
            eq("Too many token validation attempts. Try again later."),
            anyLong(),
            anyLong());
    verify(filterChain, never()).doFilter(request, response);
  }
}
