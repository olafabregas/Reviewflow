package com.reviewflow.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IpAddressExtractor {

    // Headers checked in priority order — first non-null, non-unknown wins
    private static final String[] IP_HEADERS = {
        "X-Forwarded-For",      // Standard proxy header — may contain chain e.g. "1.2.3.4, 5.6.7.8"
        "X-Real-IP",            // Nginx proxy
        "Proxy-Client-IP",      // Apache
        "WL-Proxy-Client-IP",   // WebLogic
        "HTTP_X_FORWARDED_FOR",
        "HTTP_X_FORWARDED",
        "HTTP_X_CLUSTER_CLIENT_IP",
        "HTTP_CLIENT_IP",
        "HTTP_FORWARDED_FOR",
        "HTTP_FORWARDED"
    };

    private static final String UNKNOWN = "unknown";

    /**
     * Extract the real client IP address from the request.
     * Checks proxy headers in order, falls back to remote address.
     * Returns the leftmost IP in X-Forwarded-For chain (the original client).
     */
    public String extract(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String ipList = request.getHeader(header);
            if (ipList != null && !ipList.isBlank() && !UNKNOWN.equalsIgnoreCase(ipList)) {
                // X-Forwarded-For can be: "client, proxy1, proxy2" — take the first (original client)
                String ip = ipList.split(",")[0].trim();
                if (isValidIp(ip)) {
                    log.debug("Extracted IP from header {}: {}", header, ip);
                    return ip;
                }
            }
        }

        // Fallback to remote address (may be load balancer IP)
        String remoteAddr = request.getRemoteAddr();
        log.debug("No proxy headers found — using remote address: {}", remoteAddr);
        return remoteAddr;
    }

    /**
     * Basic validation — rejects obviously invalid values.
     * Does not fully validate IPv4/IPv6 — just checks format plausibility.
     */
    private boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank() || ip.length() > 45) return false;
        if (UNKNOWN.equalsIgnoreCase(ip)) return false;
        // Allow IPv4 (dots) and IPv6 (colons)
        return ip.matches("[0-9a-fA-F:.]+");
    }
}
