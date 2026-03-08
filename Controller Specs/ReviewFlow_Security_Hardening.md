# ReviewFlow — Security Hardening & Configuration Guide
> Package: `com.reviewflow` | Based on direct code audit of your actual files
> Every issue listed here was found in your existing code. Nothing is generic.
> Follow sections in order — later sections depend on earlier ones.

---

## Audit Summary

Issues found across your four files:

| Area | Issues Found |
|---|---|
| `RateLimiterService` | Hardcoded constants, no stale-entry cleanup, not wired to JWT filter, no upload-block limiting |
| `FileSecurityValidator` | No ClamAV, no security event metrics, mime-detection timeout not enforced |
| `JwtAuthenticationFilter` | Cookie-only (no Bearer), no method logging, no token fingerprinting, no brute-force protection |
| `application.properties` | 15 hardcoded values, production properties file nearly empty |
| `SecurityConfig` | Zero security headers (X-Content-Type-Options, X-Frame-Options, CSP, HSTS) |
| Missing entirely | IP extraction utility, ClamAV async integration, Micrometer security metrics |

---

## Implementation Order

```
Step 1  → Fix application.properties — move all hardcoded values to properties
Step 2  → Create .env template
Step 3  → Add security headers to SecurityConfig
Step 4  → Create IpAddressExtractor utility
Step 5  → Upgrade RateLimiterService — externalize config, add stale cleanup, add upload blocking
Step 6  → Wire rate limiting into JwtAuthenticationFilter
Step 7  → Upgrade JwtAuthenticationFilter — Bearer support, logging, token fingerprinting
Step 8  → Add security event metrics (Micrometer)
Step 9  → Add ClamAV async integration
Step 10 → Wire ClamAV into FileSecurityValidator
Step 11 → Fix production application-prod.properties
Step 12 → Verification checklist
```

---

## Step 1 — Fix application.properties

Your current file has 15 hardcoded values. Replace it entirely:

```properties
# ═══════════════════════════════════════════════════════
# APPLICATION
# ═══════════════════════════════════════════════════════
spring.application.name=reviewflow-backend
spring.profiles.active=${SPRING_PROFILES_ACTIVE:local}

# ═══════════════════════════════════════════════════════
# DATABASE
# ═══════════════════════════════════════════════════════
spring.datasource.url=${SPRING_DATASOURCE_URL}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# ═══════════════════════════════════════════════════════
# JPA / HIBERNATE
# ═══════════════════════════════════════════════════════
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=${JPA_SHOW_SQL:false}
spring.jpa.properties.hibernate.format_sql=${JPA_FORMAT_SQL:false}

# ═══════════════════════════════════════════════════════
# FLYWAY
# ═══════════════════════════════════════════════════════
spring.flyway.enabled=true

# ═══════════════════════════════════════════════════════
# SERVER
# ═══════════════════════════════════════════════════════
server.port=${SERVER_PORT:8081}

# ═══════════════════════════════════════════════════════
# JWT
# ═══════════════════════════════════════════════════════
jwt.secret=${JWT_SECRET}
jwt.access-expiration-ms=${JWT_ACCESS_EXPIRATION_MS:900000}
jwt.refresh-expiration-ms=${JWT_REFRESH_EXPIRATION_MS:604800000}
jwt.cookie-name=${JWT_COOKIE_NAME:reviewflow_access}
jwt.refresh-cookie-name=${JWT_REFRESH_COOKIE_NAME:reviewflow_refresh}

# ═══════════════════════════════════════════════════════
# CORS
# ═══════════════════════════════════════════════════════
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS}

# ═══════════════════════════════════════════════════════
# COOKIE
# ═══════════════════════════════════════════════════════
app.cookie.secure=${COOKIE_SECURE:false}
app.cookie.same-site=${COOKIE_SAME_SITE:Lax}

# ═══════════════════════════════════════════════════════
# FILE UPLOAD
# ═══════════════════════════════════════════════════════
spring.servlet.multipart.max-file-size=${MAX_FILE_SIZE:50MB}
spring.servlet.multipart.max-request-size=${MAX_REQUEST_SIZE:55MB}

# ═══════════════════════════════════════════════════════
# FILE SECURITY
# ═══════════════════════════════════════════════════════
security.file.max-archive-entries=${MAX_ARCHIVE_ENTRIES:1000}
security.file.max-archive-uncompressed-size=${MAX_ARCHIVE_UNCOMPRESSED_SIZE:524288000}
security.file.max-upload-size=${MAX_UPLOAD_SIZE:52428800}
security.file.mime-detection-timeout-ms=${MIME_DETECTION_TIMEOUT_MS:2000}

# ═══════════════════════════════════════════════════════
# RATE LIMITING
# ═══════════════════════════════════════════════════════
rate-limit.login.max-attempts=${RATE_LIMIT_LOGIN_MAX_ATTEMPTS:5}
rate-limit.login.window-seconds=${RATE_LIMIT_LOGIN_WINDOW_SECONDS:900}
rate-limit.token.max-attempts=${RATE_LIMIT_TOKEN_MAX_ATTEMPTS:20}
rate-limit.token.window-seconds=${RATE_LIMIT_TOKEN_WINDOW_SECONDS:60}
rate-limit.upload-block.max-attempts=${RATE_LIMIT_UPLOAD_BLOCK_MAX_ATTEMPTS:10}
rate-limit.upload-block.window-seconds=${RATE_LIMIT_UPLOAD_BLOCK_WINDOW_SECONDS:3600}

# ═══════════════════════════════════════════════════════
# CLAMAV
# ═══════════════════════════════════════════════════════
clamav.host=${CLAMAV_HOST:localhost}
clamav.port=${CLAMAV_PORT:3310}
clamav.timeout-ms=${CLAMAV_TIMEOUT_MS:5000}
clamav.enabled=${CLAMAV_ENABLED:false}

# ═══════════════════════════════════════════════════════
# TOKEN FINGERPRINTING
# ═══════════════════════════════════════════════════════
security.token.fingerprinting-enabled=${TOKEN_FINGERPRINTING_ENABLED:false}

# ═══════════════════════════════════════════════════════
# ACTUATOR
# ═══════════════════════════════════════════════════════
management.endpoints.web.exposure.include=health,info,metrics,caches
management.endpoint.health.show-details=when-authorized
management.endpoint.caches.enabled=true
```

> **Key changes from your old file:**
> - `app.cors.allowed-origins` no longer has localhost as a hardcoded fallback —
>   it must be explicitly set. This prevents accidentally running production
>   with localhost CORS open.
> - `spring.jpa.show-sql` defaults to `false` — was `true` in your file, leaking
>   SQL to production logs.
> - Cookie name, refresh cookie name are now configurable — were hardcoded strings
>   in `JwtAuthenticationFilter`.
> - Added rate limit, ClamAV, and token fingerprinting properties.
> - `MAX_UPLOAD_SIZE` is now 50MB (52428800 bytes) — your old file had 100MB
>   (104857600) which contradicts the 50MB limit in the API checklist.
> - Multipart `max-request-size` is 55MB (slightly above file limit to allow
>   for form field overhead).

---

## Step 2 — .env Template

Create `.env.example` in the project root (committed to git as a template).
Create `.env` alongside it (gitignored — never committed):

```bash
# .env.example — copy to .env and fill in values — NEVER commit .env

# ── Database ──────────────────────────────────────────────────────
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/reviewflow_dev
SPRING_DATASOURCE_USERNAME=reviewflow_user
SPRING_DATASOURCE_PASSWORD=change_me

# ── Server ────────────────────────────────────────────────────────
SERVER_PORT=8081
SPRING_PROFILES_ACTIVE=local

# ── JWT ───────────────────────────────────────────────────────────
JWT_SECRET=your_256_bit_base64_encoded_secret_here
JWT_ACCESS_EXPIRATION_MS=900000
JWT_REFRESH_EXPIRATION_MS=604800000
JWT_COOKIE_NAME=reviewflow_access
JWT_REFRESH_COOKIE_NAME=reviewflow_refresh

# ── CORS ──────────────────────────────────────────────────────────
CORS_ALLOWED_ORIGINS=http://localhost:5173

# ── Cookie ────────────────────────────────────────────────────────
COOKIE_SECURE=false
COOKIE_SAME_SITE=Lax

# ── File Upload ───────────────────────────────────────────────────
MAX_FILE_SIZE=50MB
MAX_REQUEST_SIZE=55MB
MAX_UPLOAD_SIZE=52428800
MAX_ARCHIVE_ENTRIES=1000
MAX_ARCHIVE_UNCOMPRESSED_SIZE=524288000
MIME_DETECTION_TIMEOUT_MS=2000

# ── Rate Limiting ─────────────────────────────────────────────────
RATE_LIMIT_LOGIN_MAX_ATTEMPTS=5
RATE_LIMIT_LOGIN_WINDOW_SECONDS=900
RATE_LIMIT_TOKEN_MAX_ATTEMPTS=20
RATE_LIMIT_TOKEN_WINDOW_SECONDS=60
RATE_LIMIT_UPLOAD_BLOCK_MAX_ATTEMPTS=10
RATE_LIMIT_UPLOAD_BLOCK_WINDOW_SECONDS=3600

# ── ClamAV ────────────────────────────────────────────────────────
CLAMAV_HOST=localhost
CLAMAV_PORT=3310
CLAMAV_TIMEOUT_MS=5000
CLAMAV_ENABLED=false

# ── Token Fingerprinting ──────────────────────────────────────────
TOKEN_FINGERPRINTING_ENABLED=false

# ── Logging ───────────────────────────────────────────────────────
JPA_SHOW_SQL=true
JPA_FORMAT_SQL=true
```

Add to `.gitignore` if not already present:
```
.env
*.env
!.env.example
```

---

## Step 3 — Security Headers (SecurityConfig)

You currently have zero security headers. Add them to your existing `SecurityConfig.java`.
These are the five headers that matter for a university web application:

```java
// In SecurityConfig.java — update filterChain() method:
// Add .headers() configuration inside the http builder:

http
    .csrf(csrf -> csrf.disable())
    .sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .cors(cors -> cors.configurationSource(corsConfigurationSource()))

    // ADD THIS BLOCK ──────────────────────────────────────────────
    .headers(headers -> headers
        // Prevents browsers from MIME-sniffing a response away from declared content-type
        .contentTypeOptions(Customizer.withDefaults())

        // Prevents clickjacking — page cannot be embedded in an iframe
        .frameOptions(frame -> frame.deny())

        // Forces HTTPS for 1 year — only enable in production
        .httpStrictTransportSecurity(hsts -> hsts
            .includeSubDomains(true)
            .maxAgeInSeconds(31536000)
            .preload(true)
        )

        // Content Security Policy — restricts what resources the browser can load
        .contentSecurityPolicy(csp -> csp
            .policyDirectives(
                "default-src 'self'; " +
                "script-src 'self'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data:; " +
                "font-src 'self'; " +
                "connect-src 'self' ws: wss:; " +  // ws/wss needed for WebSocket
                "frame-ancestors 'none'"
            )
        )

        // Prevents cross-site scripting reflection in older browsers
        .xssProtection(xss -> xss
            .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
        )

        // Referrer policy — don't leak the full URL to external sites
        .referrerPolicy(referrer -> referrer
            .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
        )
    )
    // END HEADERS BLOCK ───────────────────────────────────────────

    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/v1/auth/**").permitAll()
        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
        .requestMatchers("/actuator/health").permitAll()
        .requestMatchers("/ws/**").permitAll()
        .anyRequest().authenticated())
    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

Add imports:
```java
import org.springframework.security.config.Customizer;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
```

> **Note on HSTS:** This tells browsers to only connect via HTTPS for one year.
> If you are still developing on HTTP locally, HSTS will break your local
> browser after first hitting a production URL. Conditionally disable it
> based on profile:

```java
// Inject active profile to conditionally apply HSTS:
@Value("${spring.profiles.active:local}")
private String activeProfile;

// Then in headers config:
.httpStrictTransportSecurity(hsts -> {
    if ("prod".equals(activeProfile)) {
        hsts.includeSubDomains(true)
            .maxAgeInSeconds(31536000)
            .preload(true);
    } else {
        hsts.maxAgeInSeconds(0); // disable HSTS in local/dev
    }
})
```

---

## Step 4 — IP Address Extractor

Your `FileSecurityValidator` already uses `ipAddress` as a parameter but there
is no utility that correctly extracts it. Naive `request.getRemoteAddr()` returns
the load balancer IP, not the real client IP. This utility checks the proxy
headers in the correct priority order:

```java
// src/main/java/com/reviewflow/util/IpAddressExtractor.java

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
        "X-Real-IP",            // Nginx common header
        "X-Original-Forwarded-For",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_X_FORWARDED_FOR",
        "HTTP_X_FORWARDED",
        "HTTP_FORWARDED_FOR",
        "HTTP_FORWARDED"
    };

    private static final String UNKNOWN = "unknown";

    /**
     * Extract the real client IP address from the request.
     * Handles proxy chains — takes the first (leftmost) IP from X-Forwarded-For.
     * Falls back to request.getRemoteAddr() if no proxy headers present.
     */
    public String extract(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isBlank() && !UNKNOWN.equalsIgnoreCase(ip)) {
                // X-Forwarded-For can be a comma-separated chain — take the first (client) IP
                String clientIp = ip.split(",")[0].trim();
                if (isValidIp(clientIp)) {
                    log.debug("IP extracted from header {}: {}", header, clientIp);
                    return clientIp;
                }
            }
        }

        String remoteAddr = request.getRemoteAddr();
        log.debug("IP extracted from remoteAddr: {}", remoteAddr);
        return remoteAddr;
    }

    /**
     * Basic validation — rejects obviously invalid values.
     * Not a full IP validator — just guards against injected garbage in headers.
     */
    private boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank() || ip.length() > 45) return false;
        // Allow IPv4 and IPv6 characters only
        return ip.matches("[0-9a-fA-F:.]+");
    }
}
```

---

## Step 5 — Upgrade RateLimiterService

Your current service has three problems:
1. `MAX_ATTEMPTS` and `WINDOW_SECONDS` are hardcoded constants
2. No stale-entry cleanup — the `ConcurrentHashMap` grows forever
3. No rate limiting for upload blocks or token brute-force

Replace your `RateLimiterService` entirely:

```java
// src/main/java/com/reviewflow/service/RateLimiterService.java

package com.reviewflow.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RateLimiterService {

    // ── Login rate limiting ────────────────────────────────────────
    @Value("${rate-limit.login.max-attempts:5}")
    private int loginMaxAttempts;

    @Value("${rate-limit.login.window-seconds:900}")
    private long loginWindowSeconds;

    // ── Token brute-force rate limiting ───────────────────────────
    @Value("${rate-limit.token.max-attempts:20}")
    private int tokenMaxAttempts;

    @Value("${rate-limit.token.window-seconds:60}")
    private long tokenWindowSeconds;

    // ── Upload block rate limiting ─────────────────────────────────
    @Value("${rate-limit.upload-block.max-attempts:10}")
    private int uploadBlockMaxAttempts;

    @Value("${rate-limit.upload-block.window-seconds:3600}")
    private long uploadBlockWindowSeconds;

    // ── Stores ────────────────────────────────────────────────────
    private final Map<String, AttemptRecord> loginAttempts      = new ConcurrentHashMap<>();
    private final Map<String, AttemptRecord> tokenAttempts      = new ConcurrentHashMap<>();
    private final Map<String, AttemptRecord> uploadBlockAttempts = new ConcurrentHashMap<>();

    // ════════════════════════════════════════════════════════════════
    // LOGIN
    // ════════════════════════════════════════════════════════════════

    public void recordFailedLogin(String ip) {
        record(ip, loginAttempts, loginWindowSeconds);
    }

    public boolean isLoginRateLimited(String ip) {
        return isLimited(ip, loginAttempts, loginWindowSeconds, loginMaxAttempts);
    }

    public long getLoginRetryAfterSeconds(String ip) {
        return getRetryAfter(ip, loginAttempts, loginWindowSeconds);
    }

    public void clearFailedLogins(String ip) {
        if (ip != null) loginAttempts.remove(ip);
    }

    // ════════════════════════════════════════════════════════════════
    // TOKEN BRUTE-FORCE (JWT filter)
    // ════════════════════════════════════════════════════════════════

    public void recordFailedTokenValidation(String ip) {
        record(ip, tokenAttempts, tokenWindowSeconds);
    }

    public boolean isTokenRateLimited(String ip) {
        return isLimited(ip, tokenAttempts, tokenWindowSeconds, tokenMaxAttempts);
    }

    public long getTokenRetryAfterSeconds(String ip) {
        return getRetryAfter(ip, tokenAttempts, tokenWindowSeconds);
    }

    // ════════════════════════════════════════════════════════════════
    // UPLOAD BLOCK (FileSecurityValidator blocked attempts)
    // ════════════════════════════════════════════════════════════════

    public void recordBlockedUpload(String ip) {
        record(ip, uploadBlockAttempts, uploadBlockWindowSeconds);
        int count = getCount(ip, uploadBlockAttempts);
        if (count >= uploadBlockMaxAttempts) {
            log.warn("UPLOAD_BLOCK_RATE_LIMIT_EXCEEDED ip={} attempts={}", ip, count);
        }
    }

    public boolean isUploadBlockRateLimited(String ip) {
        return isLimited(ip, uploadBlockAttempts, uploadBlockWindowSeconds, uploadBlockMaxAttempts);
    }

    // ════════════════════════════════════════════════════════════════
    // STALE ENTRY CLEANUP — runs every 30 minutes
    // Prevents unbounded ConcurrentHashMap growth on long-running server
    // ════════════════════════════════════════════════════════════════

    @Scheduled(fixedDelayString = "${rate-limit.cleanup-interval-ms:1800000}")
    public void cleanupStaleEntries() {
        int loginRemoved  = cleanup(loginAttempts,       loginWindowSeconds);
        int tokenRemoved  = cleanup(tokenAttempts,       tokenWindowSeconds);
        int uploadRemoved = cleanup(uploadBlockAttempts, uploadBlockWindowSeconds);

        if (loginRemoved + tokenRemoved + uploadRemoved > 0) {
            log.debug("Rate limiter cleanup: removed {} login, {} token, {} upload entries",
                    loginRemoved, tokenRemoved, uploadRemoved);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════

    private void record(String ip, Map<String, AttemptRecord> store, long windowSeconds) {
        if (ip == null) return;
        store.compute(ip, (key, record) -> {
            Instant now = Instant.now();
            if (record == null || isWindowExpired(record, windowSeconds)) {
                return new AttemptRecord(now, 1);
            }
            return new AttemptRecord(record.windowStart(), record.count() + 1);
        });
    }

    private boolean isLimited(String ip, Map<String, AttemptRecord> store,
                               long windowSeconds, int maxAttempts) {
        if (ip == null) return false;
        AttemptRecord record = store.get(ip);
        if (record == null) return false;
        if (isWindowExpired(record, windowSeconds)) {
            store.remove(ip);
            return false;
        }
        return record.count() >= maxAttempts;
    }

    private long getRetryAfter(String ip, Map<String, AttemptRecord> store, long windowSeconds) {
        if (ip == null) return 0;
        AttemptRecord record = store.get(ip);
        if (record == null) return 0;
        long secondsUntilReset = record.windowStart().plusSeconds(windowSeconds)
                .getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, secondsUntilReset);
    }

    private int getCount(String ip, Map<String, AttemptRecord> store) {
        AttemptRecord record = store.get(ip);
        return record == null ? 0 : record.count();
    }

    private boolean isWindowExpired(AttemptRecord record, long windowSeconds) {
        return record.windowStart().plusSeconds(windowSeconds).isBefore(Instant.now());
    }

    private int cleanup(Map<String, AttemptRecord> store, long windowSeconds) {
        int removed = 0;
        for (var entry : store.entrySet()) {
            if (isWindowExpired(entry.getValue(), windowSeconds)) {
                store.remove(entry.getKey());
                removed++;
            }
        }
        return removed;
    }

    private record AttemptRecord(Instant windowStart, int count) {}
}
```

Add to `application.properties`:
```properties
rate-limit.cleanup-interval-ms=${RATE_LIMIT_CLEANUP_INTERVAL_MS:1800000}
```

Add to `.env.example`:
```bash
RATE_LIMIT_CLEANUP_INTERVAL_MS=1800000
```

---

## Step 6 — Wire Rate Limiting Into JwtAuthenticationFilter

Your current filter does not check rate limits at all.
This is the updated filter with all improvements applied:

```java
// src/main/java/com/reviewflow/security/JwtAuthenticationFilter.java

package com.reviewflow.security;

import com.reviewflow.service.RateLimiterService;
import com.reviewflow.util.IpAddressExtractor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final JwtService           jwtService;
    private final UserDetailsService   userDetailsService;
    private final RateLimiterService   rateLimiterService;
    private final IpAddressExtractor   ipExtractor;

    @Value("${jwt.cookie-name:reviewflow_access}")
    private String accessCookieName;

    @Value("${security.token.fingerprinting-enabled:false}")
    private boolean fingerprintingEnabled;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String ip = ipExtractor.extract(request);

        // ── Rate limit check — block token brute-force attempts ──────
        if (rateLimiterService.isTokenRateLimited(ip)) {
            long retryAfter = rateLimiterService.getTokenRetryAfterSeconds(ip);
            log.warn("TOKEN_RATE_LIMIT_EXCEEDED ip={} retryAfter={}s", ip, retryAfter);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"success\":false,\"error\":{\"code\":\"RATE_LIMITED\"," +
                "\"message\":\"Too many requests. Retry after " + retryAfter + " seconds.\"}}"
            );
            return; // Do not continue filter chain
        }

        // ── Try cookie first, then Bearer header ─────────────────────
        Optional<String> tokenOpt = getTokenFromCookie(request);
        String authMethod = "cookie";

        if (tokenOpt.isEmpty()) {
            tokenOpt = getTokenFromBearerHeader(request);
            authMethod = "bearer";
        }

        if (tokenOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = tokenOpt.get();

        try {
            String email = jwtService.extractEmail(token);
            if (email != null) {
                var userDetails = userDetailsService.loadUserByUsername(email);

                // ── Token fingerprint validation ──────────────────────
                if (fingerprintingEnabled && !validateFingerprint(token, request)) {
                    log.warn("TOKEN_FINGERPRINT_MISMATCH ip={} email={} method={}",
                            ip, email, authMethod);
                    rateLimiterService.recordFailedTokenValidation(ip);
                    filterChain.doFilter(request, response);
                    return;
                }

                if (jwtService.isTokenValid(token, userDetails)) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("AUTH_SUCCESS email={} method={} ip={}", email, authMethod, ip);
                } else {
                    log.debug("TOKEN_INVALID email={} method={} ip={}", email, authMethod, ip);
                    rateLimiterService.recordFailedTokenValidation(ip);
                }
            }
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.debug("TOKEN_EXPIRED method={} ip={}", authMethod, ip);
            // Do not record as brute-force — expired tokens are normal
        } catch (io.jsonwebtoken.JwtException e) {
            log.debug("TOKEN_MALFORMED method={} ip={} error={}", authMethod, ip, e.getMessage());
            rateLimiterService.recordFailedTokenValidation(ip);
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            log.debug("USER_NOT_FOUND method={} ip={}", authMethod, ip);
            rateLimiterService.recordFailedTokenValidation(ip);
        }

        filterChain.doFilter(request, response);
    }

    // ── Token extraction ──────────────────────────────────────────

    private Optional<String> getTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie cookie : cookies) {
            if (accessCookieName.equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value != null && !value.isBlank())
                        ? Optional.of(value)
                        : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Optional<String> getTokenFromBearerHeader(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            return token.isBlank() ? Optional.empty() : Optional.of(token);
        }
        return Optional.empty();
    }

    // ── Token fingerprinting ──────────────────────────────────────
    // Binds token to the User-Agent it was issued to.
    // Stored as a claim in the JWT during login.
    // Prevents token theft across different clients.

    private boolean validateFingerprint(String token, HttpServletRequest request) {
        try {
            String storedFingerprint = jwtService.extractClaim(token, "fp");
            if (storedFingerprint == null) return true; // token has no fingerprint claim — skip
            String currentFingerprint = buildFingerprint(request);
            return storedFingerprint.equals(currentFingerprint);
        } catch (Exception e) {
            log.debug("Fingerprint validation error: {}", e.getMessage());
            return true; // fail open — don't block on fingerprint errors
        }
    }

    private String buildFingerprint(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) userAgent = "unknown";
        // Hash User-Agent to avoid storing full string in token
        return String.valueOf(userAgent.hashCode());
    }
}
```

### What you need to add to JwtService

For token fingerprinting to work, `JwtService` needs two additions:

```java
// Add to JwtService.java:

// 1. Generic claim extractor
public String extractClaim(String token, String claimKey) {
    return Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .getBody()
            .get(claimKey, String.class);
}

// 2. Add fingerprint claim when generating access token (in generateAccessToken()):
// Pass userAgent from the login request:
public String generateAccessToken(UserDetails userDetails, String userAgent) {
    Map<String, Object> claims = new HashMap<>();
    if (userAgent != null && fingerprintingEnabled) {
        claims.put("fp", String.valueOf(userAgent.hashCode()));
    }
    return buildToken(claims, userDetails, accessExpiration);
}
```

> **Token fingerprinting is disabled by default** (`TOKEN_FINGERPRINTING_ENABLED=false`).
> Enable it in production only after confirming your users don't switch
> User-Agents mid-session (mobile apps rotating User-Agents can cause false positives).

---

## Step 7 — Security Event Metrics (Micrometer)

Micrometer is already in your project via Spring Boot Actuator.
Create a dedicated metrics component that other services use to record security events:

```java
// src/main/java/com/reviewflow/monitoring/SecurityMetrics.java

package com.reviewflow.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SecurityMetrics {

    // ── Counters ──────────────────────────────────────────────────
    private final Counter loginSuccess;
    private final Counter loginFailed;
    private final Counter loginRateLimited;
    private final Counter tokenRateLimited;
    private final Counter fileBlocked;
    private final Counter fileMimeMismatch;
    private final Counter fileExecutable;
    private final Counter uploadBlockRateLimited;
    private final Counter clamavClean;
    private final Counter clamavInfected;
    private final Counter clamavUnavailable;
    private final Counter tokenFingerprintMismatch;

    public SecurityMetrics(MeterRegistry registry) {
        this.loginSuccess             = counter(registry, "login",        "result", "success");
        this.loginFailed              = counter(registry, "login",        "result", "failed");
        this.loginRateLimited         = counter(registry, "login",        "result", "rate_limited");
        this.tokenRateLimited         = counter(registry, "token",        "result", "rate_limited");
        this.fileBlocked              = counter(registry, "file_upload",  "result", "blocked_extension");
        this.fileMimeMismatch         = counter(registry, "file_upload",  "result", "mime_mismatch");
        this.fileExecutable           = counter(registry, "file_upload",  "result", "executable_detected");
        this.uploadBlockRateLimited   = counter(registry, "file_upload",  "result", "rate_limited");
        this.clamavClean              = counter(registry, "clamav_scan",  "result", "clean");
        this.clamavInfected           = counter(registry, "clamav_scan",  "result", "infected");
        this.clamavUnavailable        = counter(registry, "clamav_scan",  "result", "unavailable");
        this.tokenFingerprintMismatch = counter(registry, "token",        "result", "fingerprint_mismatch");
    }

    // ── Public API ────────────────────────────────────────────────

    public void recordLoginSuccess()               { loginSuccess.increment(); }
    public void recordLoginFailed()                { loginFailed.increment(); }
    public void recordLoginRateLimited()           { loginRateLimited.increment(); }
    public void recordTokenRateLimited()           { tokenRateLimited.increment(); }
    public void recordFileBlocked()                { fileBlocked.increment(); }
    public void recordFileMimeMismatch()           { fileMimeMismatch.increment(); }
    public void recordFileExecutable()             { fileExecutable.increment(); }
    public void recordUploadBlockRateLimited()     { uploadBlockRateLimited.increment(); }
    public void recordClamavClean()                { clamavClean.increment(); }
    public void recordClamavInfected()             { clamavInfected.increment(); }
    public void recordClamavUnavailable()          { clamavUnavailable.increment(); }
    public void recordTokenFingerprintMismatch()   { tokenFingerprintMismatch.increment(); }

    // ── Helper ────────────────────────────────────────────────────

    private Counter counter(MeterRegistry registry, String name,
                             String tagKey, String tagValue) {
        return Counter.builder("reviewflow.security." + name)
                .tag(tagKey, tagValue)
                .description("ReviewFlow security event counter")
                .register(registry);
    }
}
```

### Wire SecurityMetrics into existing services

**AuthService** — add to login method:
```java
private final SecurityMetrics securityMetrics;

// On successful login:
securityMetrics.recordLoginSuccess();

// On failed login (wrong password):
securityMetrics.recordLoginFailed();

// On rate limited login:
securityMetrics.recordLoginRateLimited();
```

**FileSecurityValidator** — add field and update blocked cases:
```java
private final SecurityMetrics securityMetrics;

// In validate() — after BLOCKED_EXTENSIONS check:
securityMetrics.recordFileBlocked();

// After isExecutableMime() check:
securityMetrics.recordFileExecutable();

// After isMimeAcceptable() fails:
securityMetrics.recordFileMimeMismatch();
```

**JwtAuthenticationFilter** — add field and record rate limited:
```java
private final SecurityMetrics securityMetrics;

// When isTokenRateLimited() triggers:
securityMetrics.recordTokenRateLimited();

// When fingerprint mismatch:
securityMetrics.recordTokenFingerprintMismatch();
```

### View metrics via Actuator:
```
GET http://localhost:8081/actuator/metrics/reviewflow.security.login?tag=result:failed
GET http://localhost:8081/actuator/metrics/reviewflow.security.file_upload?tag=result:blocked_extension
GET http://localhost:8081/actuator/metrics/reviewflow.security.clamav_scan?tag=result:infected
```

---

## Step 8 — ClamAV Async Integration

ClamAV scans for malware at the content level — the only check your current
`FileSecurityValidator` cannot do. It runs after MIME and structural validation,
on a separate thread so it never blocks the HTTP response.

### Option A — Docker (recommended for local dev)

Add to `docker-compose.yml` (create if you don't have one):

```yaml
version: '3.8'
services:
  clamav:
    image: clamav/clamav:stable
    ports:
      - "3310:3310"
    volumes:
      - clamav_data:/var/lib/clamav
    environment:
      - CLAMAV_NO_FRESHCLAMD=false
    restart: unless-stopped

volumes:
  clamav_data:
```

Start: `docker compose up -d clamav`
First start takes 2-5 minutes to download virus definitions.

### Option B — Existing ClamAV server

Set in `.env`:
```bash
CLAMAV_HOST=your-clamav-server-hostname
CLAMAV_PORT=3310
CLAMAV_ENABLED=true
```

### ClamAV Dependency

Add to `pom.xml`:
```xml
<!-- ClamAV Java client -->
<dependency>
    <groupId>fi.solita.clamav</groupId>
    <artifactId>clamav-client</artifactId>
    <version>1.0.1</version>
</dependency>
```

### ClamAvScanResult

```java
// src/main/java/com/reviewflow/model/enums/ClamAvScanResult.java

package com.reviewflow.model.enums;

public enum ClamAvScanResult {
    CLEAN,         // No threat found
    INFECTED,      // Malware detected — file must be rejected
    UNAVAILABLE,   // ClamAV is down — scan skipped (fail open with warning)
    DISABLED       // clamav.enabled=false
}
```

### ClamAvScanService

```java
// src/main/java/com/reviewflow/service/ClamAvScanService.java

package com.reviewflow.service;

import com.reviewflow.model.enums.ClamAvScanResult;
import com.reviewflow.monitoring.SecurityMetrics;
import fi.solita.clamav.ClamAVClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClamAvScanService {

    private final SecurityMetrics securityMetrics;

    @Value("${clamav.host:localhost}")
    private String clamavHost;

    @Value("${clamav.port:3310}")
    private int clamavPort;

    @Value("${clamav.timeout-ms:5000}")
    private int clamavTimeoutMs;

    @Value("${clamav.enabled:false}")
    private boolean clamavEnabled;

    /**
     * Async ClamAV scan. Returns a CompletableFuture so the caller can
     * choose to await it or fire-and-forget depending on the use case.
     *
     * For submissions: await the result before saving to storage.
     * If UNAVAILABLE: log a warning and allow the upload (fail open).
     * If INFECTED: reject the upload with 400 MALWARE_DETECTED.
     */
    @Async("notificationExecutor") // reuse existing thread pool
    public CompletableFuture<ClamAvScanResult> scanAsync(Path filePath, String filename) {
        if (!clamavEnabled) {
            log.debug("ClamAV disabled — skipping scan for {}", filename);
            return CompletableFuture.completedFuture(ClamAvScanResult.DISABLED);
        }

        try {
            ClamAVClient client = new ClamAVClient(clamavHost, clamavPort, clamavTimeoutMs);

            try (InputStream is = Files.newInputStream(filePath)) {
                byte[] reply = client.scan(is);
                boolean isClean = ClamAVClient.isCleanReply(reply);

                if (isClean) {
                    log.debug("CLAMAV_CLEAN file={}", filename);
                    securityMetrics.recordClamavClean();
                    return CompletableFuture.completedFuture(ClamAvScanResult.CLEAN);
                } else {
                    String replyStr = new String(reply).trim();
                    log.warn("CLAMAV_INFECTED file={} reply={}", filename, replyStr);
                    securityMetrics.recordClamavInfected();
                    return CompletableFuture.completedFuture(ClamAvScanResult.INFECTED);
                }
            }

        } catch (IOException e) {
            log.warn("CLAMAV_UNAVAILABLE file={} error={} — scan skipped (fail open)",
                    filename, e.getMessage());
            securityMetrics.recordClamavUnavailable();
            // Fail open — do not block uploads when ClamAV is temporarily unavailable
            return CompletableFuture.completedFuture(ClamAvScanResult.UNAVAILABLE);
        }
    }
}
```

### Add MALWARE_DETECTED exception

```java
// src/main/java/com/reviewflow/exception/MalwareDetectedException.java

package com.reviewflow.exception;

public class MalwareDetectedException extends RuntimeException {
    public MalwareDetectedException() {
        super("File failed malware scan and cannot be uploaded");
    }
}
```

### Wire ClamAV into SubmissionService

ClamAV runs after `FileSecurityValidator` passes, before saving to storage:

```java
// In SubmissionService.upload() — after fileSecurityValidator.validate():

private final ClamAvScanService clamAvScanService;

// After fileSecurityValidator.validate(file, userId, ip):
// The temp file written during validation is already deleted at this point,
// so we write a new temp file for ClamAV:

Path scanFile = Files.createTempFile("clamav-", "-" + file.getOriginalFilename());
try {
    file.transferTo(scanFile.toFile());

    ClamAvScanResult scanResult = clamAvScanService
            .scanAsync(scanFile, file.getOriginalFilename())
            .get(10, TimeUnit.SECONDS); // wait up to 10s

    if (scanResult == ClamAvScanResult.INFECTED) {
        log.warn("MALWARE_DETECTED user={} file={} ip={}", userId, file.getOriginalFilename(), ip);
        throw new MalwareDetectedException();
    }

    if (scanResult == ClamAvScanResult.UNAVAILABLE) {
        log.warn("CLAMAV_SKIPPED user={} file={} — proceeding with upload", userId, file.getOriginalFilename());
        // Fail open — upload proceeds but is flagged in audit log
    }

} catch (MalwareDetectedException e) {
    throw e;
} catch (Exception e) {
    log.warn("ClamAV scan timed out or failed for file={} — proceeding", file.getOriginalFilename());
} finally {
    Files.deleteIfExists(scanFile);
}
```

Add imports:
```java
import com.reviewflow.model.enums.ClamAvScanResult;
import com.reviewflow.service.ClamAvScanService;
import java.util.concurrent.TimeUnit;
```

---

## Step 9 — Wire Upload Block Rate Limiting

In your `SubmissionService.upload()` — check before processing,
record after a block:

```java
private final RateLimiterService rateLimiterService;

// Before fileSecurityValidator.validate():
if (rateLimiterService.isUploadBlockRateLimited(ip)) {
    securityMetrics.recordUploadBlockRateLimited();
    throw new RateLimitException("Too many blocked upload attempts. Try again later.");
}

// In catch block for BlockedFileTypeException, InvalidMimeTypeException, InvalidFileTypeException:
} catch (BlockedFileTypeException | InvalidMimeTypeException | InvalidFileTypeException e) {
    rateLimiterService.recordBlockedUpload(ip);
    throw e;
}
```

Add `RateLimitException` if you don't have one:
```java
// src/main/java/com/reviewflow/exception/RateLimitException.java

package com.reviewflow.exception;

public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}
```

---

## Step 10 — Production Properties File

Your `application-prod.properties` currently has 3 lines. It should be a
complete override of all defaults with all sensitive values from environment:

```properties
# ── Application ───────────────────────────────────────────────────
spring.application.name=reviewflow-backend
spring.profiles.active=prod

# ── Database ──────────────────────────────────────────────────────
spring.datasource.url=${SPRING_DATASOURCE_URL}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000

# ── JPA ───────────────────────────────────────────────────────────
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=false

# ── Flyway ────────────────────────────────────────────────────────
spring.flyway.enabled=true

# ── Server ────────────────────────────────────────────────────────
server.port=${SERVER_PORT:8080}

# ── JWT ───────────────────────────────────────────────────────────
jwt.secret=${JWT_SECRET}
jwt.access-expiration-ms=${JWT_ACCESS_EXPIRATION_MS:900000}
jwt.refresh-expiration-ms=${JWT_REFRESH_EXPIRATION_MS:604800000}
jwt.cookie-name=${JWT_COOKIE_NAME:reviewflow_access}
jwt.refresh-cookie-name=${JWT_REFRESH_COOKIE_NAME:reviewflow_refresh}

# ── CORS — no localhost fallback in production ─────────────────────
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS}

# ── Cookie — must be Secure in production ─────────────────────────
app.cookie.secure=true
app.cookie.same-site=Strict

# ── File Upload ───────────────────────────────────────────────────
spring.servlet.multipart.max-file-size=${MAX_FILE_SIZE:50MB}
spring.servlet.multipart.max-request-size=${MAX_REQUEST_SIZE:55MB}

# ── File Security ─────────────────────────────────────────────────
security.file.max-archive-entries=${MAX_ARCHIVE_ENTRIES:1000}
security.file.max-archive-uncompressed-size=${MAX_ARCHIVE_UNCOMPRESSED_SIZE:524288000}
security.file.max-upload-size=${MAX_UPLOAD_SIZE:52428800}
security.file.mime-detection-timeout-ms=${MIME_DETECTION_TIMEOUT_MS:2000}

# ── Rate Limiting ─────────────────────────────────────────────────
rate-limit.login.max-attempts=${RATE_LIMIT_LOGIN_MAX_ATTEMPTS:5}
rate-limit.login.window-seconds=${RATE_LIMIT_LOGIN_WINDOW_SECONDS:900}
rate-limit.token.max-attempts=${RATE_LIMIT_TOKEN_MAX_ATTEMPTS:20}
rate-limit.token.window-seconds=${RATE_LIMIT_TOKEN_WINDOW_SECONDS:60}
rate-limit.upload-block.max-attempts=${RATE_LIMIT_UPLOAD_BLOCK_MAX_ATTEMPTS:10}
rate-limit.upload-block.window-seconds=${RATE_LIMIT_UPLOAD_BLOCK_WINDOW_SECONDS:3600}
rate-limit.cleanup-interval-ms=${RATE_LIMIT_CLEANUP_INTERVAL_MS:1800000}

# ── ClamAV ────────────────────────────────────────────────────────
clamav.host=${CLAMAV_HOST}
clamav.port=${CLAMAV_PORT:3310}
clamav.timeout-ms=${CLAMAV_TIMEOUT_MS:5000}
clamav.enabled=${CLAMAV_ENABLED:true}

# ── Token Fingerprinting ──────────────────────────────────────────
security.token.fingerprinting-enabled=${TOKEN_FINGERPRINTING_ENABLED:true}

# ── Actuator — restrict in production ─────────────────────────────
management.endpoints.web.exposure.include=health,metrics
management.endpoint.health.show-details=when-authorized
management.endpoints.web.base-path=/internal/actuator

# ── Logging ───────────────────────────────────────────────────────
logging.level.com.reviewflow=INFO
logging.level.org.springframework.security=WARN
```

> **Key production differences from local:**
> - `COOKIE_SECURE=true` and `COOKIE_SAME_SITE=Strict` — enforced, not configurable
> - `clamav.enabled` defaults to `true` — must be explicitly disabled
> - `TOKEN_FINGERPRINTING_ENABLED` defaults to `true`
> - Actuator moved to `/internal/actuator` — not exposed on the public port
> - `CORS_ALLOWED_ORIGINS` has no fallback — server refuses to start without it
> - HikariCP pool configured for production load

---

## Step 11 — Verification Checklist

### Security Headers
```
GET http://localhost:8081/api/v1/auth/login (any request)
→ Response headers must include:
  X-Content-Type-Options: nosniff
  X-Frame-Options: DENY
  X-XSS-Protection: 1; mode=block
  Referrer-Policy: strict-origin-when-cross-origin
  Content-Security-Policy: default-src 'self'; ...
```

### IP Extraction
```java
// Add a temporary debug endpoint or log the IP in AuthController:
log.info("Login attempt from IP: {}", ipExtractor.extract(request));
// Verify it shows the real client IP, not 127.0.0.1
```

### Rate Limiting — Login
```
POST /api/v1/auth/login  (wrong password × 5)
→ 6th attempt: 429 with Retry-After header

GET /actuator/metrics/reviewflow.security.login?tag=result:rate_limited
→ count > 0
```

### Rate Limiting — Token brute-force
```
Send 21 requests with invalid JWT tokens from same IP within 60 seconds
→ 429 with Retry-After header
```

### Rate Limiting — Upload blocks
```
Upload .exe file × 10 from same IP
→ 11th attempt: 429 before validation even runs
```

### ClamAV
```bash
# Confirm ClamAV is running (Docker):
docker compose ps clamav
→ Status: running

# Test the EICAR test file (safe malware test string):
# Upload a file containing exactly this string:
X5O!P%@AP[4\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*
→ Expected: 400 with { code: "MALWARE_DETECTED" }

# Test with clean file:
→ Expected: 201 upload success
```

### Metrics
```
GET http://localhost:8081/actuator/metrics
→ reviewflow.security.login appears in names list
→ reviewflow.security.file_upload appears
→ reviewflow.security.clamav_scan appears

GET http://localhost:8081/actuator/metrics/reviewflow.security.login?tag=result:success
→ Returns counter with count value
```

### Properties — Confirm no hardcoded values remain
- [ ] `RateLimiterService` has no hardcoded constants — all via `@Value`
- [ ] `JwtAuthenticationFilter` reads cookie name from `${jwt.cookie-name}`
- [ ] `ClamAvScanService` reads host/port/timeout from properties
- [ ] `application-prod.properties` has no localhost references
- [ ] `.env` is in `.gitignore`
- [ ] `.env.example` is committed and up to date

---

## Files Created / Modified Summary

| Action | File |
|---|---|
| Modified | `application.properties` |
| Created | `.env.example` |
| Modified | `SecurityConfig.java` — security headers |
| Created | `IpAddressExtractor.java` |
| Modified | `RateLimiterService.java` — full replacement |
| Modified | `JwtAuthenticationFilter.java` — full replacement |
| Created | `SecurityMetrics.java` |
| Created | `ClamAvScanService.java` |
| Created | `ClamAvScanResult.java` (enum) |
| Created | `MalwareDetectedException.java` |
| Created | `RateLimitException.java` |
| Modified | `SubmissionService.java` — ClamAV + upload block rate limiting |
| Modified | `AuthService.java` — SecurityMetrics wiring |
| Modified | `FileSecurityValidator.java` — SecurityMetrics wiring |
| Created | `application-prod.properties` — full replacement |
| Modified | `docker-compose.yml` — ClamAV service |
