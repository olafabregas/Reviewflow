package com.reviewflow.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SecurityMetrics {

    private final Counter loginSuccessCounter;
    private final Counter loginFailedCounter;
    private final Counter loginRateLimitedCounter;
    
    private final Counter tokenRateLimitedCounter;
    private final Counter tokenFingerprintMismatchCounter;
    
    private final Counter fileBlockedCounter;
    private final Counter fileExecutableCounter;
    private final Counter fileMimeMismatchCounter;
    private final Counter uploadBlockRateLimitedCounter;
    
    private final Counter clamavCleanCounter;
    private final Counter clamavInfectedCounter;
    private final Counter clamavErrorCounter;

    public SecurityMetrics(MeterRegistry meterRegistry) {
        // Login metrics
        this.loginSuccessCounter = Counter.builder("reviewflow.security.login")
            .tag("result", "success")
            .description("Successful login attempts")
            .register(meterRegistry);
            
        this.loginFailedCounter = Counter.builder("reviewflow.security.login")
            .tag("result", "failed")
            .description("Failed login attempts")
            .register(meterRegistry);
            
        this.loginRateLimitedCounter = Counter.builder("reviewflow.security.login")
            .tag("result", "rate_limited")
            .description("Rate limited login attempts")
            .register(meterRegistry);
        
        // Token metrics
        this.tokenRateLimitedCounter = Counter.builder("reviewflow.security.token")
            .tag("result", "rate_limited")
            .description("Rate limited token validation attempts")
            .register(meterRegistry);
            
        this.tokenFingerprintMismatchCounter = Counter.builder("reviewflow.security.token")
            .tag("result", "fingerprint_mismatch")
            .description("Token fingerprint mismatch")
            .register(meterRegistry);
        
        // File upload metrics
        this.fileBlockedCounter = Counter.builder("reviewflow.security.file_upload")
            .tag("result", "blocked_extension")
            .description("File uploads blocked by extension")
            .register(meterRegistry);
            
        this.fileExecutableCounter = Counter.builder("reviewflow.security.file_upload")
            .tag("result", "blocked_executable")
            .description("Executable files blocked")
            .register(meterRegistry);
            
        this.fileMimeMismatchCounter = Counter.builder("reviewflow.security.file_upload")
            .tag("result", "mime_mismatch")
            .description("File MIME type mismatches")
            .register(meterRegistry);
            
        this.uploadBlockRateLimitedCounter = Counter.builder("reviewflow.security.file_upload")
            .tag("result", "rate_limited")
            .description("Upload blocked by rate limiting")
            .register(meterRegistry);
        
        // ClamAV metrics
        this.clamavCleanCounter = Counter.builder("reviewflow.security.clamav_scan")
            .tag("result", "clean")
            .description("Clean files from ClamAV scan")
            .register(meterRegistry);
            
        this.clamavInfectedCounter = Counter.builder("reviewflow.security.clamav_scan")
            .tag("result", "infected")
            .description("Infected files detected by ClamAV")
            .register(meterRegistry);
            
        this.clamavErrorCounter = Counter.builder("reviewflow.security.clamav_scan")
            .tag("result", "error")
            .description("ClamAV scan errors")
            .register(meterRegistry);
    }

    // Login
    public void recordLoginSuccess() {
        loginSuccessCounter.increment();
    }

    public void recordLoginFailed() {
        loginFailedCounter.increment();
    }

    public void recordLoginRateLimited() {
        loginRateLimitedCounter.increment();
    }

    // Token
    public void recordTokenRateLimited() {
        tokenRateLimitedCounter.increment();
    }

    public void recordTokenFingerprintMismatch() {
        tokenFingerprintMismatchCounter.increment();
    }

    // File upload
    public void recordFileBlocked() {
        fileBlockedCounter.increment();
    }

    public void recordFileExecutable() {
        fileExecutableCounter.increment();
    }

    public void recordFileMimeMismatch() {
        fileMimeMismatchCounter.increment();
    }

    public void recordUploadBlockRateLimited() {
        uploadBlockRateLimitedCounter.increment();
    }

    // ClamAV
    public void recordClamavClean() {
        clamavCleanCounter.increment();
    }

    public void recordClamavInfected() {
        clamavInfectedCounter.increment();
    }

    public void recordClamavError() {
        clamavErrorCounter.increment();
    }
}
