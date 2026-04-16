package com.reviewflow.monitoring;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * PRD-08: Centralised metric registration for ReviewFlow Replaces
 * SecurityMetrics with comprehensive coverage including: - Login security
 * (success, failure, rate limiting) - Token validation metrics - File upload
 * security - S3 operations (upload/download timers) - Email delivery
 * (success/failure) - Notifications (per type) - Cache reload failures -
 * Submission and evaluation operations
 */
@Slf4j
@Component
public class ReviewFlowMetrics {

    private final MeterRegistry meterRegistry;

    // ──── LOGIN METRICS ────────────────────────────────────────────
    private final Counter loginSuccessCounter;
    private final Counter loginFailedCounter;
    private final Counter loginRateLimitedCounter;

    // ──── TOKEN METRICS ────────────────────────────────────────────
    private final Counter tokenRateLimitedCounter;
    private final Counter tokenFingerprintMismatchCounter;

    // ──── FILE UPLOAD SECURITY ────────────────────────────────────
    private final Counter fileBlockedCounter;
    private final Counter fileExecutableCounter;
    private final Counter fileMimeMismatchCounter;
    private final Counter uploadBlockRateLimitedCounter;

    // ──── CLAMAV SCAN RESULTS ─────────────────────────────────────
    private final Counter clamavCleanCounter;
    private final Counter clamavInfectedCounter;
    private final Counter clamavErrorCounter;

    // ──── S3 OPERATIONS ───────────────────────────────────────────
    private final Timer s3UploadTimer;
    private final Timer s3DownloadTimer;
    private final Counter s3UploadFailureCounter;
    private final Counter s3DownloadFailureCounter;

    // ──── EMAIL DELIVERY ───────────────────────────────────────────
    private final Counter emailSentCounter;
    private final Counter emailFailedCounter;

    // ──── NOTIFICATIONS ───────────────────────────────────────────
    private final Counter notificationSentCounter;

    // ──── CACHE OPERATIONS ────────────────────────────────────────
    private final Counter cacheReloadFailureCounter;

    // ──── SUBMISSION & EVALUATION ─────────────────────────────────
    private final Counter submissionUploadedCounter;
    private final Counter evaluationPublishedCounter;

    // ──── ASSIGNMENT GROUPS ───────────────────────────────────────
    private final Counter assignmentGroupCreatedCounter;
    private final Counter assignmentGroupUpdatedCounter;
    private final Counter assignmentGroupDeletedCounter;
    private final Counter assignmentGroupMovedCounter;

    // ──── CONSTRUCTOR ────────────────────────────────────────────
    public ReviewFlowMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

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

        // S3 operations
        this.s3UploadTimer = Timer.builder("reviewflow.s3.upload_duration")
                .description("S3 upload operation duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.s3DownloadTimer = Timer.builder("reviewflow.s3.download_duration")
                .description("S3 download operation duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.s3UploadFailureCounter = Counter.builder("reviewflow.s3.upload_failures")
                .description("S3 upload failures")
                .register(meterRegistry);

        this.s3DownloadFailureCounter = Counter.builder("reviewflow.s3.download_failures")
                .description("S3 download failures")
                .register(meterRegistry);

        // Email delivery
        this.emailSentCounter = Counter.builder("reviewflow.email.sent")
                .description("Emails successfully sent")
                .register(meterRegistry);

        this.emailFailedCounter = Counter.builder("reviewflow.email.failed")
                .description("Email send failures")
                .register(meterRegistry);

        // Notifications
        this.notificationSentCounter = Counter.builder("reviewflow.notifications.sent")
                .description("Notifications sent")
                .register(meterRegistry);

        // Cache operations
        this.cacheReloadFailureCounter = Counter.builder("reviewflow.cache.reload_failures")
                .description("Cache reload failures (refreshAfterWrite)")
                .register(meterRegistry);

        // Submission & evaluation
        this.submissionUploadedCounter = Counter.builder("reviewflow.submissions.uploaded")
                .description("Submissions uploaded")
                .register(meterRegistry);

        this.evaluationPublishedCounter = Counter.builder("reviewflow.evaluations.published")
                .description("Evaluations published")
                .register(meterRegistry);

        this.assignmentGroupCreatedCounter = Counter.builder("reviewflow.assignment_groups.created")
                .description("Assignment groups created")
                .register(meterRegistry);

        this.assignmentGroupUpdatedCounter = Counter.builder("reviewflow.assignment_groups.updated")
                .description("Assignment groups updated")
                .register(meterRegistry);

        this.assignmentGroupDeletedCounter = Counter.builder("reviewflow.assignment_groups.deleted")
                .description("Assignment groups deleted")
                .register(meterRegistry);

        this.assignmentGroupMovedCounter = Counter.builder("reviewflow.assignment_groups.moved")
                .description("Assignments moved between groups")
                .register(meterRegistry);
    }

    // ──── LOGIN METRICS ────────────────────────────────────────────
    public void recordUserLogin() {
        loginSuccessCounter.increment();
    }

    public void recordFailedLogin() {
        loginFailedCounter.increment();
    }

    public void recordLoginRateLimited() {
        loginRateLimitedCounter.increment();
    }

    // ──── TOKEN METRICS ────────────────────────────────────────────
    public void recordTokenRateLimited() {
        tokenRateLimitedCounter.increment();
    }

    public void recordTokenFingerprintMismatch() {
        tokenFingerprintMismatchCounter.increment();
    }

    // ──── FILE UPLOAD SECURITY ────────────────────────────────────
    public void recordBlockedFileUpload(String reason) {
        switch (reason) {
            case "extension" ->
                fileBlockedCounter.increment();
            case "executable" ->
                fileExecutableCounter.increment();
            case "mime_mismatch" ->
                fileMimeMismatchCounter.increment();
            case "rate_limited" ->
                uploadBlockRateLimitedCounter.increment();
            default ->
                fileBlockedCounter.increment();
        }
    }

    // ──── CLAMAV SCAN RESULTS ─────────────────────────────────────
    public void recordClamAvScanResult(String result) {
        switch (result) {
            case "clean" ->
                clamavCleanCounter.increment();
            case "infected" ->
                clamavInfectedCounter.increment();
            case "error" ->
                clamavErrorCounter.increment();
            default -> {
            }
        }
    }

    // ──── S3 OPERATIONS ───────────────────────────────────────────
    public Timer.Sample startS3UploadTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordS3Upload(Timer.Sample sample) {
        sample.stop(s3UploadTimer);
    }

    public void recordS3UploadFailure() {
        s3UploadFailureCounter.increment();
    }

    public Timer.Sample startS3DownloadTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordS3Download(Timer.Sample sample) {
        sample.stop(s3DownloadTimer);
    }

    public void recordS3DownloadFailure() {
        s3DownloadFailureCounter.increment();
    }

    // ──── EMAIL DELIVERY ───────────────────────────────────────────
    public void recordEmailSent(String eventType) {
        emailSentCounter.increment();
    }

    public void recordEmailFailed(String eventType) {
        emailFailedCounter.increment();
    }

    // ──── NOTIFICATIONS ───────────────────────────────────────────
    public void recordNotificationSent(String type) {
        notificationSentCounter.increment();
    }

    // ──── CACHE OPERATIONS ────────────────────────────────────────
    public void recordCacheReloadFailure(String cacheName) {
        cacheReloadFailureCounter.increment();
    }

    // ──── SUBMISSION & EVALUATION ─────────────────────────────────
    public void recordSubmissionUploaded() {
        submissionUploadedCounter.increment();
    }

    public void recordEvaluationPublished() {
        evaluationPublishedCounter.increment();
    }

    // ──── ASSIGNMENT GROUPS ───────────────────────────────────────
    public void recordAssignmentGroupCreated() {
        assignmentGroupCreatedCounter.increment();
    }

    public void recordAssignmentGroupUpdated() {
        assignmentGroupUpdatedCounter.increment();
    }

    public void recordAssignmentGroupDeleted() {
        assignmentGroupDeletedCounter.increment();
    }

    public void recordAssignmentGroupMoved() {
        assignmentGroupMovedCounter.increment();
    }
}
