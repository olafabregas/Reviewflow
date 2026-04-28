package com.reviewflow.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.reviewflow.exception.TokenVersionMismatchException;
import com.reviewflow.monitoring.SecurityMetrics;
import com.reviewflow.service.RateLimiterService;
import com.reviewflow.service.TokenVersionService;
import com.reviewflow.util.HashidService;
import com.reviewflow.util.IpAddressExtractor;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock private JwtService jwtService;
  @Mock private UserDetailsService userDetailsService;
  @Mock private RateLimiterService rateLimiterService;
  @Mock private IpAddressExtractor ipAddressExtractor;
  @Mock private SecurityMetrics securityMetrics;
  @Mock private HashidService hashidService;
  @Mock private TokenVersionService tokenVersionService;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;
  @Mock private UserDetails userDetails;

  @InjectMocks private JwtAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    when(ipAddressExtractor.extract(request)).thenReturn("127.0.0.1");
    when(rateLimiterService.isTokenRateLimited("127.0.0.1")).thenReturn(false);
  }

  @Test
  void doFilterInternal_ShouldContinue_WhenTokenVersionMatches() throws ServletException, IOException {
    String token = "valid-token";
    Long userId = 1L;
    int version = 1;

    when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
    when(jwtService.extractEmail(token)).thenReturn("user@test.com");
    when(userDetailsService.loadUserByUsername("user@test.com")).thenReturn(userDetails);
    when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);
    when(jwtService.extractTokenVersion(token)).thenReturn(version);
    when(jwtService.extractUserId(token)).thenReturn(userId);
    when(tokenVersionService.getCurrentVersion(userId)).thenReturn(version);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doFilterInternal_ShouldNotAuthenticate_WhenTokenVersionMismatches() throws ServletException, IOException {
    String token = "old-token";
    Long userId = 1L;
    int oldVersion = 1;
    int currentVersion = 2;

    when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
    when(jwtService.extractEmail(token)).thenReturn("user@test.com");
    when(userDetailsService.loadUserByUsername("user@test.com")).thenReturn(userDetails);
    when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);
    when(jwtService.extractTokenVersion(token)).thenReturn(oldVersion);
    when(jwtService.extractUserId(token)).thenReturn(userId);
    when(tokenVersionService.getCurrentVersion(userId)).thenReturn(currentVersion);

    filter.doFilterInternal(request, response, filterChain);

    // Verify authentication was NOT set (or rather, the chain continued without authentication)
    // The filter catches the exception and calls filterChain.doFilter
    verify(rateLimiterService).recordFailedTokenValidation("127.0.0.1");
    verify(filterChain).doFilter(request, response);
  }
}
