package com.reviewflow.service;

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
    }

    public boolean isUploadBlockRateLimited(String ip) {
        return isLimited(ip, uploadBlockAttempts, uploadBlockWindowSeconds, uploadBlockMaxAttempts);
    }

    public long getUploadBlockRetryAfterSeconds(String ip) {
        return getRetryAfter(ip, uploadBlockAttempts, uploadBlockWindowSeconds);
    }

    // ════════════════════════════════════════════════════════════════
    // INTERNAL HELPERS
    // ════════════════════════════════════════════════════════════════

    private void record(String ip, Map<String, AttemptRecord> store, long windowSeconds) {
        if (ip == null) return;

        store.compute(ip, (key, record) -> {
            Instant now = Instant.now();
            if (record == null || record.windowStart.plusSeconds(windowSeconds).isBefore(now)) {
                // Start new window
                return new AttemptRecord(now, 1);
            } else {
                // Increment within same window
                return new AttemptRecord(record.windowStart, record.count + 1);
            }
        });
    }

    private boolean isLimited(String ip, Map<String, AttemptRecord> store, long windowSeconds, int maxAttempts) {
        if (ip == null) return false;

        AttemptRecord record = store.get(ip);
        if (record == null) return false;

        Instant now = Instant.now();
        // Check if window has expired
        if (record.windowStart.plusSeconds(windowSeconds).isBefore(now)) {
            store.remove(ip);
            return false;
        }

        return record.count >= maxAttempts;
    }

    private long getRetryAfter(String ip, Map<String, AttemptRecord> store, long windowSeconds) {
        if (ip == null) return 0;

        AttemptRecord record = store.get(ip);
        if (record == null) return 0;

        Instant windowEnd = record.windowStart.plusSeconds(windowSeconds);
        long secondsUntilReset = windowEnd.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, secondsUntilReset);
    }

    // ════════════════════════════════════════════════════════════════
    // STALE ENTRY CLEANUP — runs every 30 minutes
    // ════════════════════════════════════════════════════════════════

    @Scheduled(fixedDelayString = "${rate-limit.cleanup-interval-ms:1800000}")
    public void cleanupStaleEntries() {
        Instant now = Instant.now();
        int removed = 0;

        removed += cleanup(loginAttempts, loginWindowSeconds, now);
        removed += cleanup(tokenAttempts, tokenWindowSeconds, now);
        removed += cleanup(uploadBlockAttempts, uploadBlockWindowSeconds, now);

        if (removed > 0) {
            log.info("Rate limiter cleanup: removed {} stale entries", removed);
        }
    }

    private int cleanup(Map<String, AttemptRecord> store, long windowSeconds, Instant now) {
        return store.entrySet().removeIf(entry -> 
            entry.getValue().windowStart.plusSeconds(windowSeconds).isBefore(now)
        ) ? store.size() : 0;
    }

    private record AttemptRecord(Instant windowStart, int count) {}
}
