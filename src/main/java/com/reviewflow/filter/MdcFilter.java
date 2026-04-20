package com.reviewflow.filter;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * PRD-08: MDC Filter for request correlation Injects traceId and requestId into
 * every log line automatically via Mapped Diagnostic Context Also populates
 * endpoint, IP address, and user context (populated later by
 * JwtAuthenticationFilter)
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";
    private static final String REQUEST_ID = "requestId";
    private static final String ENDPOINT = "endpoint";
    private static final String IP_ADDRESS = "ipAddress";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Generate unique trace ID for this request (allows correlating all logs for this request)
        String traceId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();

        // Extract endpoint (path without query parameters)
        String endpoint = request.getRequestURI();

        // Extract client IP address
        String ipAddress = extractIpAddress(request);

        // Put into MDC (will be included in every log line by logback-logstash-encoder)
        MDC.put(TRACE_ID, traceId);
        MDC.put(REQUEST_ID, requestId);
        MDC.put(ENDPOINT, endpoint);
        MDC.put(IP_ADDRESS, ipAddress);

        try {
            // userId and role will be populated by JwtAuthenticationFilter after successful token validation
            // They start as null for anonymous requests
            filterChain.doFilter(request, response);
        } finally {
            // Clean up MDC after request completes (important for thread pool reuse)
            MDC.clear();
        }
    }

    /**
     * Extract client IP address from request headers or socket Handles
     * X-Forwarded-For (behind load balancer), X-Real-IP, and direct connection
     */
    private String extractIpAddress(HttpServletRequest request) {
        // Check X-Forwarded-For header (behind load balancer/proxy)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain comma-separated list; take the first (original client)
            return xForwardedFor.split(",")[0].trim();
        }

        // Check X-Real-IP header
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // Fall back to direct connection IP
        return request.getRemoteAddr();
    }
}
