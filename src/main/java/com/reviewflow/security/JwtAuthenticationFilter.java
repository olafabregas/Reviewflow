package com.reviewflow.security;

import com.reviewflow.monitoring.SecurityMetrics;
import com.reviewflow.service.RateLimiterService;
import com.reviewflow.util.HashidService;
import com.reviewflow.util.IpAddressExtractor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final RateLimiterService rateLimiterService;
    private final IpAddressExtractor ipAddressExtractor;
    private final SecurityMetrics securityMetrics;
    private final HashidService hashidService;

    @Value("${jwt.cookie-name:reviewflow_access}")
    private String accessCookieName;

    @Value("${security.token.fingerprinting-enabled:false}")
    private boolean tokenFingerprintingEnabled;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String ip = ipAddressExtractor.extract(request);

        // Check token brute-force rate limiting
        if (rateLimiterService.isTokenRateLimited(ip)) {
            log.warn("Token validation rate limited for IP: {}", ip);
            securityMetrics.recordTokenRateLimited();
            response.setStatus(429); // 429 Too Many Requests
            response.setHeader("Retry-After", String.valueOf(rateLimiterService.getTokenRetryAfterSeconds(ip)));
            response.getWriter().write("{\"error\":\"Too many token validation attempts. Try again later.\"}");
            return;
        }

        // Extract token from Cookie or Bearer header
        Optional<String> tokenOpt = extractToken(request);

        if (tokenOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = tokenOpt.get();
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String email = jwtService.extractEmail(token);
            if (email != null) {
                var userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtService.isTokenValid(token, userDetails)) {
                    // Token fingerprinting check
                    if (tokenFingerprintingEnabled) {
                        String tokenUserAgent = jwtService.extractClaim(token, "userAgent");
                        String requestUserAgent = request.getHeader("User-Agent");

                        if (tokenUserAgent != null && !tokenUserAgent.equals(requestUserAgent)) {
                            log.warn("Token fingerprint mismatch for user={} ip={} tokenUA={} requestUA={}",
                                    email, ip, tokenUserAgent, requestUserAgent);
                            securityMetrics.recordTokenFingerprintMismatch();
                            rateLimiterService.recordFailedTokenValidation(ip);
                            filterChain.doFilter(request, response);
                            return;
                        }
                    }

                    var auth = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    // PRD-08: Populate MDC for request correlation
                    if (userDetails instanceof ReviewFlowUserDetails rfDetails) {
                        MDC.put("userId", hashidService.encode(rfDetails.getUserId()));
                        MDC.put("role", rfDetails.getRole().name());
                    }

                    log.debug("Authenticated user={} from ip={} via {}",
                            email, ip, tokenOpt.get().startsWith("Bearer") ? "Bearer" : "Cookie");
                }
            }
        } catch (io.jsonwebtoken.JwtException | org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            log.debug("Token validation failed for ip={}: {}", ip, e.getMessage());
            rateLimiterService.recordFailedTokenValidation(ip);
            // Do not set authentication; chain continues and Spring Security returns 401 for protected routes
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from Cookie (priority) or Bearer Authorization header
     * (fallback).
     */
    private Optional<String> extractToken(HttpServletRequest request) {
        // Priority 1: Cookie
        Optional<String> cookieToken = getAccessTokenFromCookie(request);
        if (cookieToken.isPresent()) {
            return cookieToken;
        }

        // Priority 2: Bearer header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return token.isBlank() ? Optional.empty() : Optional.of(token);
        }

        return Optional.empty();
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
}
