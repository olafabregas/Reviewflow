# Agent — Async Job & Executor Hardening

**Status:** Final  
**Author:** Roqeeb Olamide Ayorinde  
**Source:** Async Job & Executor Audit Report 2026-05-18 (17 findings)  
**Depends on:** PRD-21 (async job pipeline must be implemented first)  
**Migration:** None

---

## How to Invoke This Agent

```
@docs/agents/async-hardening.md fix all
```

Other commands:

| Command | What it does |
|---|---|
| `fix all` | Execute HIGH → MEDIUM in order, verify each tier before advancing |
| `fix high` | HIGH tier only |
| `fix medium` | MEDIUM tier only |
| `verify` | Run full verification checklist, no code changes |
| `status` | Show which tiers are complete and which remain |

---

## Agent Instructions — Read Before Starting

You are a senior Spring Boot engineer executing a structured async hardening pass on ReviewFlow, a **Spring Boot 4.0.x / Java 21** academic submission and grading platform.

**Rules:**
- Implement in strict priority order: HIGH → MEDIUM. Do not skip ahead.
- **Read every file before touching it.** Do not assume method signatures, existing logic, or bean names.
- Do not touch INFO items — they are documentation-only.
- Never commit a tier until every item in that tier is verified.
- PRD-21 async job pipeline must exist before this pass begins — verify it first.

**Files likely touched:**

| File | Path |
|---|---|
| AsyncJobConfig | `infrastructure/config/AsyncJobConfig.java` |
| ClamAvScanService | `infrastructure/storage/ClamAvScanService.java` |
| SubmissionService | `submission/service/SubmissionService.java` |
| MessagingService | `messaging/service/MessagingService.java` |
| CsvImportService | `grading/service/CsvImportService.java` |
| ImportJobService | `grading/service/ImportJobService.java` |
| EmailEventListener | `infrastructure/email/EmailEventListener.java` |
| GradeAggregateUpdateListener | `grading/event/GradeAggregateUpdateListener.java` |
| PdfGenerationListener | `evaluation/listener/PdfGenerationListener.java` |
| NotificationEventListener | `notification/event/NotificationEventListener.java` |
| DeadlineWarningScheduler | `infrastructure/scheduling/DeadlineWarningScheduler.java` |
| ReviewFlowMetrics | `infrastructure/monitoring/ReviewFlowMetrics.java` |

---

## Locked Decisions

| Decision | Choice |
|---|---|
| `gradeAggregateExecutor` rejection | Keep `DiscardOldestPolicy` + bounded queue + rejection metric + WARN log + coalescing |
| `EmailEventListener` exception | Metric + `log.error(..., e)` + optional retry. **Never rethrow.** |
| `ClamAvScanService` executor | Dedicated `scanExecutor` — own bounded pool, own rejection policy, own metrics |
| `CommitJobService` rejection | Revert to `VALIDATION_PASSED` on rejection (not `FAILED`) — commit can be retried |
| `CsvImportService` SSE terminal event | Push named `failed` SSE event before `complete()` |

---

## Execution Protocol for `fix all`

```
STEP 1 — Verify PRD-21 async job pipeline exists
  If missing — STOP and notify user

STEP 2 — Create fix branch
  git checkout -b fix/async-hardening

STEP 3 — HIGH tier (HIGH-1 through HIGH-4 in order)
  Read each affected file → apply fix → verify → commit

STEP 4 — MEDIUM tier (MEDIUM-1 through MEDIUM-10 in order)
  Read each affected file → apply fix → verify → commit

STEP 5 — Add all new metrics to ReviewFlowMetrics
  Verify each counter exists exactly once

STEP 6 — Add new properties to application.properties / application.yml

STEP 7 — Run full verification checklist
  Report pass/fail per item

STEP 8 — Final summary
```

---

## HIGH — Fix Before Production

### HIGH-1 · gradeAggregateExecutor — Rejection Metric + Coalescing Guard

**Files:** `AsyncJobConfig.java`, `GradeAggregateUpdateListener.java`, `ReviewFlowMetrics.java`  
**Rule:** RULE-ASYNC01  
**Issue:** `gradeAggregateExecutor` uses bare `DiscardOldestPolicy` with no rejection metric. Under grade-publish bursts, stale Redis grade overviews persist with no observable signal.

**Step 1 — Replace bare `DiscardOldestPolicy` with metric-aware rejection handler in `AsyncJobConfig`:**

```java
@Bean(name = "gradeAggregateExecutor")
public Executor gradeAggregateExecutor(
        @Value("${async.grade.core-pool-size:2}") int core,
        @Value("${async.grade.max-pool-size:5}") int max,
        @Value("${async.grade.queue-capacity:100}") int queue,
        ReviewFlowMetrics metrics) {

    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(core);
    exec.setMaxPoolSize(max);
    exec.setQueueCapacity(queue);
    exec.setThreadNamePrefix("grade-agg-");
    exec.setWaitForTasksToCompleteOnShutdown(true);
    exec.setAwaitTerminationSeconds(30);

    exec.setRejectedExecutionHandler((runnable, executor) -> {
        if (!executor.getQueue().isEmpty()) {
            executor.getQueue().poll(); // discard oldest — superseded by newer events
        }
        try {
            executor.execute(runnable);
        } catch (RejectedExecutionException e) {
            // Pool still full after discard — drop this task too
            log.warn("gradeAggregateExecutor: task dropped after discard-oldest. " +
                     "Queue depth: {}", executor.getQueue().size());
        }
        metrics.recordAsyncRejected("gradeAggregateExecutor");
    });

    exec.initialize();
    return exec;
}
```

**Step 2 — Add coalescing guard in `GradeAggregateUpdateListener`:**

```java
private final Set<String> pendingRecomputes = ConcurrentHashMap.newKeySet();

@Async("gradeAggregateExecutor")
@EventListener
public void handleGradePublished(GradePublishedEvent event) {
    String key = event.getCourseId() + ":" + event.getStudentId();

    if (!pendingRecomputes.add(key)) {
        log.debug("Grade aggregate recompute already queued for key={}", key);
        return; // Deduplicate — newer event will supersede
    }

    try {
        aggregateService.evictStudent(event.getCourseId(), event.getStudentId());
        GradeOverviewDto fresh = calculationService.calculate(
            event.getCourseId(), event.getStudentId());
        aggregateService.storeInRedis(event.getCourseId(), event.getStudentId(), fresh);
    } catch (Exception e) {
        metrics.recordGradeAggregateFailed();
        log.error("Grade aggregate update failed courseId={} studentId={}: {}",
            event.getCourseId(), event.getStudentId(), e.getMessage(), e);
        // Non-fatal: next read will trigger full recalculation from DB
    } finally {
        pendingRecomputes.remove(key); // always clear on completion or failure
    }
}
```

**New metrics to add to `ReviewFlowMetrics`:**
```java
public void recordAsyncRejected(String executorName) {
    registry.counter("reviewflow.async.rejected",
        "executor", executorName).increment();
}

public void recordGradeAggregateFailed() {
    registry.counter("reviewflow.grade.aggregate.failed").increment();
}
```

**Verify:**
```
Burst: grade aggregate rejection → WARN log + reviewflow.async.rejected{executor=gradeAggregateExecutor} incremented
Duplicate GradePublishedEvent for same student → second submission skipped (coalesced)
Grade still eventually correct after burst (read path recalculates from DB on cache miss)
```

---

### HIGH-2 · SubmissionService — Map uploadExecutor Rejection to 503

**File:** `submission/service/SubmissionService.java` (around line 549–573)  
**Rule:** RULE-ASYNC02  
**Issue:** `CompletableFuture.supplyAsync(..., uploadExecutor)` throws `RejectedExecutionException` when pool queue is full, propagating as a generic 500.

**Fix — wrap `supplyAsync` and map rejection. Also propagate MDC trace context into the lambda:**

```java
CompletableFuture<String> uploadFuture;
try {
    uploadFuture = CompletableFuture.supplyAsync(
        () -> {
            MDC.setContextMap(MDC.getCopyOfContextMap()); // propagate trace context
            return s3Service.putObject(key, file.getInputStream(), file.getSize());
        },
        uploadExecutor);
} catch (RejectedExecutionException e) {
    metrics.recordAsyncRejected("uploadExecutor");
    log.warn("uploadExecutor queue full — submission upload rejected for " +
             "assignmentId={} userId={}", hashedAssignmentId, hashedUserId);
    throw new ServiceUnavailableException(
        "Upload service is temporarily at capacity. Please try again shortly.");
}

try {
    uploadFuture.get(uploadTimeoutSeconds, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    uploadFuture.cancel(true);
    s3Service.deleteObjectSilently(key);
    throw new FileUploadTimeoutException(uploadTimeoutSeconds);
} catch (ExecutionException e) {
    throw new StorageException("Upload failed", e.getCause());
}
```

**New exception + handler (shared with HIGH-3 — add once, reuse):**

```java
// New exception class:
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) { super(message); }
}

// Add to GlobalExceptionHandler:
@ExceptionHandler(ServiceUnavailableException.class)
public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(
        ServiceUnavailableException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header("Retry-After", "30")
        .body(ApiResponse.error("SERVICE_UNAVAILABLE", ex.getMessage()));
}
```

**Verify:**
```
uploadExecutor queue saturated → 503 SERVICE_UNAVAILABLE with Retry-After: 30
Not → 500 INTERNAL_ERROR
reviewflow.async.rejected{executor=uploadExecutor} incremented
```

---

### HIGH-3 · MessagingService — Same uploadExecutor Rejection Fix

**File:** `messaging/service/MessagingService.java` (around line 736–741)  
**Rule:** RULE-ASYNC02  
**Issue:** Identical to HIGH-2. Attachment uploads via `uploadExecutor` fail opaquely under pool saturation.

**Fix:** Apply the identical pattern from HIGH-2 — wrap `runAsync`/`supplyAsync` in try/catch for `RejectedExecutionException`, throw `ServiceUnavailableException`, increment `reviewflow.async.rejected{executor=uploadExecutor}`. Also propagate MDC context into the lambda.

`ServiceUnavailableException` and the `GlobalExceptionHandler` entry were added in HIGH-2 — **do not duplicate, reuse.**

**Verify:**
```
Message with attachment rejected by uploadExecutor → 503 SERVICE_UNAVAILABLE
Not → opaque 500
reviewflow.async.rejected{executor=uploadExecutor} incremented
```

---

### HIGH-4 · CsvImportService — runAsync Rejection Releases Lock Immediately

**File:** `grading/service/CsvImportService.java` (around line 133–179)  
**Rule:** RULE-ASYNC08  
**Issue:** If `csvWorkerExecutor` rejects `runAsync`, the job stays at `UPLOADED` forever and `reviewflow:import:lock:{courseId}` is held for the full 2hr TTL. Instructors cannot start a new import.

**Step 1 — Wrap runAsync in `startImport()`:**

```java
asyncJobService.saveJob(initialJobState); // save UPLOADED state first

try {
    CompletableFuture.runAsync(
        () -> runValidation(jobId, assignmentId, hashedCourseId),
        csvWorkerExecutor);
} catch (RejectedExecutionException e) {
    metrics.recordAsyncRejected("csvWorkerExecutor");
    metrics.recordJobFailed("csv_import");
    log.warn("csvWorkerExecutor rejected validation for jobId={}: {}",
             jobId, e.getMessage());

    asyncJobService.failJob(jobId, hashedCourseId,
        "Import service is temporarily at capacity. Please try again.");

    // Push terminal SSE event so clients do not poll forever
    sseEmitterRegistry.pushFailed(jobId,
        "Import rejected — service at capacity. Please retry.");
    sseEmitterRegistry.complete(jobId);

    throw new ServiceUnavailableException(
        "Import service is temporarily at capacity. Please try again shortly.");
}
```

**Step 2 — Add `failJob()` to `AsyncJobService` (if not already present):**
```java
public void failJob(String jobId, String hashedCourseId, String reason) {
    updateJobStatus(jobId, JobStatus.FAILED);
    updateJobError(jobId, reason);
    releaseImportLock(hashedCourseId);
}
```

**Step 3 — Add `pushFailed()` to `SseEmitterRegistry`:**
```java
public void pushFailed(String jobId, String errorMessage) {
    SseEmitter emitter = emitters.get(jobId);
    if (emitter == null) return;
    try {
        emitter.send(SseEmitter.event()
            .name("failed")
            .data(JsonUtils.toJson(Map.of(
                "status", "FAILED",
                "errorMessage", errorMessage
            ))));
    } catch (IOException e) {
        emitters.remove(jobId);
    }
}
```

**New metric:**
```java
public void recordJobFailed(String jobType) {
    registry.counter("reviewflow.job.failed",
        "jobType", jobType).increment();
}
```

**Verify:**
```
csvWorkerExecutor saturated → job immediately FAILED, lock released, SSE sends 'failed' event
Instructor can start new import after failure (lock not held)
GET /jobs/{jobId}/status → FAILED with errorMessage
reviewflow.async.rejected{executor=csvWorkerExecutor} incremented
reviewflow.job.failed{jobType=csv_import} incremented
```

---

## MEDIUM — Fix in This PR

### MEDIUM-1 · CsvImportService Source CSV Upload — Map Rejection to 503

**File:** `grading/service/CsvImportService.java` (around line 140–157)  
**Rule:** RULE-ASYNC02  
**Issue:** Source CSV upload uses `uploadExecutor` with `.join()`. `RejectedExecutionException` propagates as `StorageException` before the job record exists — no lock release, no client signal.

```java
try {
    uploadFuture.join();
} catch (CompletionException e) {
    if (e.getCause() instanceof RejectedExecutionException) {
        metrics.recordAsyncRejected("uploadExecutor");
        asyncJobService.releaseImportLock(hashedCourseId);
        throw new ServiceUnavailableException(
            "Upload service is temporarily at capacity. Please try again.");
    }
    throw new StorageException("CSV upload failed", e.getCause());
}
```

**Verify:**
```
uploadExecutor full during CSV source upload → 503 SERVICE_UNAVAILABLE, lock released
No orphaned import lock
```

---

### MEDIUM-2 · ImportJobService Commit Path — Rejection Reverts to VALIDATION_PASSED

**File:** `grading/service/ImportJobService.java` (around line 75–76)  
**Rule:** RULE-ASYNC02  
**Issue:** Commit path sets status to `COMMITTING` then submits without rejection handling. Rejection leaves `COMMITTING` permanently. Lock held. Instructor cannot retry.

**Fix — revert to `VALIDATION_PASSED` on rejection (not `FAILED`, data is intact in S3):**

```java
asyncJobService.updateStatus(jobId, JobStatus.COMMITTING);

try {
    CompletableFuture.runAsync(
        () -> runCommit(jobId, hashedCourseId),
        csvWorkerExecutor);
} catch (RejectedExecutionException e) {
    metrics.recordAsyncRejected("csvWorkerExecutor");
    log.warn("csvWorkerExecutor rejected commit for jobId={}", jobId);

    // Revert — validated rows are still in S3, instructor can retry commit
    asyncJobService.updateStatus(jobId, JobStatus.VALIDATION_PASSED);
    asyncJobService.updateJobError(jobId,
        "Commit temporarily rejected — please retry.");

    throw new ServiceUnavailableException(
        "Commit service is temporarily at capacity. Please try again.");
}
```

**Why `VALIDATION_PASSED` not `FAILED`:** Validated rows are still in S3 (`imports/{jobId}/validated-rows.json`). Setting `FAILED` forces re-upload and re-validation. `VALIDATION_PASSED` allows immediate commit retry.

**Verify:**
```
csvWorkerExecutor full during commit → status VALIDATION_PASSED (not COMMITTING, not FAILED)
GET /jobs/{jobId}/status → VALIDATION_PASSED with retry error message
POST /jobs/{jobId}/commit can be retried immediately
```

---

### MEDIUM-3 · EmailEventListener — Structured Exception Handling

**File:** `infrastructure/email/EmailEventListener.java` (around line 330–337)  
**Rule:** RULE-ASYNC03  
**Issue:** `catch (Exception)` logs at ERROR without passing `e` (no full stack). No metric increment. SMTP outages are invisible to alerting.

**Fix — split catch blocks, pass `e` as final log arg, increment metric. Never rethrow:**

```java
} catch (MailException | MessagingException e) {
    metrics.recordEmailFailed(eventType);
    log.error("Email delivery failed type={} recipient={}: {}",
        eventType, recipient, e.getMessage(), e);
    // Fire-and-forget preserved — no rethrow
    // Optional @Retryable is a separate PR

} catch (Exception e) {
    metrics.recordEmailFailed(eventType);
    log.error("Unexpected email handler failure type={}: {}",
        eventType, e.getMessage(), e);
}
```

**New metric:**
```java
public void recordEmailFailed(String eventType) {
    registry.counter("reviewflow.email.failed",
        "type", eventType).increment();
}
```

**Verify:**
```
SMTP failure → reviewflow.email.failed{type=...} incremented
Log line includes full stack trace (e passed as final arg)
Request that triggered email is NOT affected — fire-and-forget preserved
```

---

### MEDIUM-4 · GradeAggregateUpdateListener — Error Counter + Full Stack Log

**File:** `grading/event/GradeAggregateUpdateListener.java` (around line 31–63)  
**Rule:** RULE-ASYNC03  
**Issue:** `catch (Exception)` uses `log.warn` only, no metric. **Note: HIGH-1 already restructures this method — integrate MEDIUM-4 into that same fix. Change `log.warn` to `log.error` and pass `e` as final arg.**

```java
} catch (Exception e) {
    metrics.recordGradeAggregateFailed(); // added in HIGH-1
    log.error("Grade aggregate update failed courseId={} studentId={}: {}",
        event.getCourseId(), event.getStudentId(), e.getMessage(), e); // was log.warn
}
```

**Verify:**
```
Grade aggregate failure → log.error with full stack (not log.warn)
reviewflow.grade.aggregate.failed incremented (verified in HIGH-1)
```

---

### MEDIUM-5 · PdfGenerationListener — Full Stack Log + Idempotency Guard

**File:** `evaluation/listener/PdfGenerationListener.java` (around line 38–67)  
**Rule:** RULE-ASYNC03 + RULE-ASYNC12

**Fix A — Full stack log:**
```java
} catch (Exception e) {
    metrics.recordPdfGenerationFailed(); // already exists
    log.error("PDF generation failed evaluationId={}: {}",
        event.getHashedEvaluationId(), e.getMessage(), e); // add e — full stack
    // Non-fatal — student still has grade, PDF is supplementary
}
```

**Fix B — Idempotency guard (skip replayed events):**
```java
@Async("pdfExecutor")
@EventListener
public void handleEvaluationPublished(EvaluationPublishedEvent event) {
    Evaluation evaluation = evaluationRepository
        .findById(event.getEvaluationId())
        .orElse(null);

    if (evaluation == null) {
        log.warn("PDF generation skipped — evaluation not found id={}",
            event.getEvaluationId());
        return;
    }

    if (evaluation.getPdfPath() != null) {
        log.debug("PDF generation skipped — pdfPath already set evaluationId={}",
            event.getEvaluationId());
        return; // Replayed event — already generated
    }

    // ... existing PDF generation logic ...
}
```

**Verify:**
```
Exception in PDF generation → log.error with full stack
EvaluationPublishedEvent replayed for evaluation with existing pdfPath → skipped (no duplicate S3 write)
```

---

### MEDIUM-6 · ClamAvScanService — Dedicated scanExecutor

**Files:** `AsyncJobConfig.java`, `ClamAvScanService.java`  
**Rule:** RULE-ASYNC05  
**Issue:** `@Async` without qualifier binds to Spring's default executor. ClamAV scans compete with all other async work with no backpressure.

**Step 1 — Add `scanExecutor` bean to `AsyncJobConfig`:**

```java
@Bean(name = "scanExecutor")
public Executor scanExecutor(
        @Value("${async.scan.core-pool-size:1}") int core,
        @Value("${async.scan.max-pool-size:2}") int max,
        @Value("${async.scan.queue-capacity:10}") int queue,
        ReviewFlowMetrics metrics) {

    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(core);
    exec.setMaxPoolSize(max);
    exec.setQueueCapacity(queue);
    exec.setThreadNamePrefix("clamav-scan-");
    exec.setWaitForTasksToCompleteOnShutdown(true);
    exec.setAwaitTerminationSeconds(60); // ClamAV scans can be slow

    exec.setRejectedExecutionHandler((runnable, executor) -> {
        metrics.recordAsyncRejected("scanExecutor");
        log.warn("scanExecutor queue full — ClamAV scan rejected. " +
                 "Queue depth: {}", executor.getQueue().size());
        // Fail-closed: if we cannot scan, treat as suspicious
        throw new RejectedExecutionException("ClamAV scan queue full");
    });

    exec.initialize();
    return exec;
}
```

**New properties:**
```properties
async.scan.core-pool-size=${ASYNC_SCAN_CORE:1}
async.scan.max-pool-size=${ASYNC_SCAN_MAX:2}
async.scan.queue-capacity=${ASYNC_SCAN_QUEUE:10}
```

**Step 2 — Add qualifier to `ClamAvScanService`:**
```java
@Async("scanExecutor")  // was: @Async (bare — used default executor)
public CompletableFuture<ScanResult> scanFile(InputStream fileStream, String filename) {
    // ... existing scan logic unchanged ...
}
```

**New metric:**
```java
public void recordScanRejected() {
    registry.counter("reviewflow.clamav.scan.rejected").increment();
}
```

**Verify:**
```
ClamAV scan submits to scanExecutor thread (prefix: clamav-scan-)
Not to default Spring async executor
scanExecutor saturation → RejectedExecutionException + metric (fail-closed)
local profile (clamav.enabled=false) → scanExecutor never called
```

---

### MEDIUM-7 · NotificationEventListener — Route DB Writes Through @Transactional Service

**File:** `notification/event/NotificationEventListener.java` (around line 491–501)  
**Rule:** RULE-ASYNC07  
**Issue:** `notificationRepository.save` called directly in listener without `@Transactional`. Multi-step notification + cache evict + WebSocket push may partial-commit under failure.

**Fix — delegate to `NotificationService.create()` which is already `@Transactional`:**

```java
// BEFORE:
private void saveAndPush(Notification notification) {
    notificationRepository.save(notification); // direct repo — no TX boundary
    cacheService.evictUnreadCount(notification.getUserId());
    webSocketService.push(notification);
}

// AFTER:
private void saveAndPush(CreateNotificationDto dto) {
    Notification saved = notificationService.create(dto); // @Transactional — save + cache evict atomic
    webSocketService.push(saved); // push after TX commits
}
```

Also remove any cross-feature repository imports from the listener:
- `UserRepository` → replace with `UserService.findById()` or equivalent
- `CourseEnrollmentRepository` → replace with `CourseService.getEnrolledStudents()` or equivalent

**Verify:**
```
Notification save failure → full rollback (no orphaned cache eviction)
Cross-feature repository imports removed from NotificationEventListener
WebSocket push happens after transaction commits
```

---

### MEDIUM-8 · PdfGenerationListener — @Transactional Boundary for evaluationRepository.save

**File:** `evaluation/listener/PdfGenerationListener.java` (around line 50–51)  
**Rule:** RULE-ASYNC07  
**Issue:** `evaluationRepository.save(evaluation)` inside async listener without explicit transaction boundary.

**Step 1 — Add `updatePdfPath()` to `EvaluationService`:**
```java
@Transactional
public void updatePdfPath(Long evaluationId, String pdfPath) {
    Evaluation evaluation = evaluationRepository.findByIdOrThrow(evaluationId);
    evaluation.setPdfPath(pdfPath);
    evaluationRepository.save(evaluation);
}
```

**Step 2 — Use service method in listener:**
```java
// BEFORE:
evaluation.setPdfPath(key);
evaluationRepository.save(evaluation); // no TX boundary

// AFTER:
evaluationService.updatePdfPath(event.getEvaluationId(), key); // @Transactional
```

**Verify:**
```
PDF path update is transactional
evaluationRepository not injected directly into PdfGenerationListener
```

---

### MEDIUM-9 · DeadlineWarningScheduler — Dedup for Assignment Notifications

**File:** `infrastructure/scheduling/DeadlineWarningScheduler.java`  
**Rule:** RULE-ASYNC12  
**Issue:** Assignment deadline reminder notifications have no dedup key. Scheduler re-run or clock skew produces duplicates. The `notifications` table already has `date_bucket` and `dedup_key` columns from V34.

**Fix — set `date_bucket` on assignment deadline notifications in `NotificationEventListener`:**

```java
// NotificationEventListener — handleDeadlineWarning():

Notification notification = Notification.builder()
    .userId(studentId)
    .type(NotificationType.ASSIGNMENT_DUE_SOON)
    .relatedEntityId(assignmentId)
    .dateBucket(LocalDate.now()) // triggers dedup key — one per student per assignment per day
    .title("Assignment due soon: " + assignmentTitle)
    .message("Your assignment is due in " + hoursUntilDue + " hours")
    .build();

try {
    notificationRepository.save(notification);
    emailEventPublisher.publishEvent(
        new DeadlineReminderEmailEvent(studentId, assignmentId, dueAt));
} catch (DataIntegrityViolationException ignored) {
    // Duplicate for today — silent skip
    log.debug("Deadline reminder already sent today userId={} assignmentId={}",
        studentId, assignmentId);
}
```

**Verify:**
```
Scheduler runs twice in same day → second run: notifications silently skipped (dedup key violation)
Scheduler runs on day 1, then day 2 → both send (date_bucket differs)
```

---

### MEDIUM-10 · CsvImportService — SSE Terminal Event on Failure

**File:** `grading/service/CsvImportService.java` (around line 229–237, 358–378)  
**Rule:** RULE-ASYNC08  
**Issue:** `failJob` updates Redis to `FAILED` and calls `sseEmitterRegistry.complete(jobId)` but never pushes a terminal progress event. SSE subscribers only see connection close.

**Fix — push named `failed` SSE event before `complete()` everywhere `failJob` is called. `pushFailed()` was added to `SseEmitterRegistry` in HIGH-4 — reuse it.**

Apply consistently to all three failure paths in `CsvImportService`:

```java
// Pattern to apply at every failure path:
asyncJobService.failJob(jobId, hashedCourseId, errorMessage);
sseEmitterRegistry.pushFailed(jobId, errorMessage); // push BEFORE complete
sseEmitterRegistry.complete(jobId);
```

The three failure paths are:
- Validation exception catch block in `runValidation`
- `VALIDATION_FAILED` path (after writing error CSV)
- System error during commit in `runCommit` catch block

**Also extend `JobProgressEvent` to include terminal state:**
```java
public record JobProgressEvent(
    int processed,
    int total,
    int percent,
    String status,       // VALIDATING | VALIDATION_FAILED | COMPLETED | FAILED | null
    String errorMessage  // null unless FAILED or VALIDATION_FAILED
) {}
```

**Verify:**
```
Validation fails → SSE receives named 'failed' event with errorMessage before connection closes
Commit fails → SSE receives named 'failed' event
Client knows failure reason from SSE without needing to poll REST
```

---

## INFO — Documentation Only, No Code Change

### INFO-1 · MDC Propagation
Already handled by HIGH-2 (SubmissionService) and HIGH-3 (MessagingService). No separate action needed.

### INFO-2 · pdfExecutor Sizing Comment
Add to `AsyncJobConfig.java` near the `pdfExecutor` bean:
```java
// pdfExecutor: intentionally small (max 3). PDF generation is CPU-bound
// via OpenPDF; larger pool on t3.micro would cause GC pressure under load.
```

### INFO-3 · CSV Pool Separation Runbook Note
Add to `orchestration/RUNBOOK.md` or equivalent:
```
CSV import validation and commit run on csvWorkerExecutor (CPU+DB heavy).
Source CSV upload runs on uploadExecutor (I/O bound, S3).
These are intentionally separate pools — do not consolidate.
```

---

## New Metrics Summary

All counters to add to `ReviewFlowMetrics`. Verify each exists exactly once — do not duplicate.

```java
// Verify this already exists from PRD-21 (add if missing):
public void recordPdfGenerationFailed() { ... }

// New in this PRD:
public void recordAsyncRejected(String executorName) {
    registry.counter("reviewflow.async.rejected",
        "executor", executorName).increment();
}

public void recordGradeAggregateFailed() {
    registry.counter("reviewflow.grade.aggregate.failed").increment();
}

public void recordJobFailed(String jobType) {
    registry.counter("reviewflow.job.failed",
        "jobType", jobType).increment();
}

public void recordEmailFailed(String eventType) {
    registry.counter("reviewflow.email.failed",
        "type", eventType).increment();
}

public void recordScanRejected() {
    registry.counter("reviewflow.clamav.scan.rejected").increment();
}
```

---

## New Properties

Add to `application.properties` / environment config:

```properties
async.scan.core-pool-size=${ASYNC_SCAN_CORE:1}
async.scan.max-pool-size=${ASYNC_SCAN_MAX:2}
async.scan.queue-capacity=${ASYNC_SCAN_QUEUE:10}
```

---

## Verification Checklist

Run after completing all tiers. Every item must pass before opening PR.

### HIGH
```
[ ] gradeAggregateExecutor: rejection handler increments reviewflow.async.rejected{executor=gradeAggregateExecutor}
[ ] gradeAggregateExecutor: WARN log on rejection with queue depth
[ ] Grade coalescing: duplicate GradePublishedEvent for same student does not queue twice
[ ] SubmissionService: RejectedExecutionException → 503 SERVICE_UNAVAILABLE with Retry-After: 30
[ ] SubmissionService: reviewflow.async.rejected{executor=uploadExecutor} incremented on rejection
[ ] MessagingService: same 503 pattern on attachment rejection
[ ] CsvImportService startImport: rejection → job immediately FAILED, lock released
[ ] CsvImportService startImport: SSE receives 'failed' event on rejection
[ ] CsvImportService startImport: reviewflow.async.rejected{executor=csvWorkerExecutor} incremented
[ ] CsvImportService startImport: reviewflow.job.failed{jobType=csv_import} incremented
[ ] Instructor can immediately start new import after rejection (lock not held)
```

### MEDIUM
```
[ ] CsvImportService CSV upload rejection → 503, lock released, not StorageException
[ ] ImportJobService commit rejection → status VALIDATION_PASSED (not COMMITTING, not FAILED)
[ ] ImportJobService commit rejection → instructor can retry commit
[ ] EmailEventListener: log.error(..., e) with full stack trace
[ ] EmailEventListener: reviewflow.email.failed{type=...} incremented on failure
[ ] EmailEventListener: request that triggered email is NOT affected
[ ] GradeAggregateUpdateListener: log.error (not log.warn) with full stack
[ ] PdfGenerationListener: log.error with full stack
[ ] PdfGenerationListener: replayed event with existing pdfPath → skipped (no duplicate S3 write)
[ ] ClamAvScanService: @Async("scanExecutor") qualifier applied
[ ] scanExecutor: dedicated bean in AsyncJobConfig with bounded queue
[ ] scanExecutor: rejection → fail-closed (RejectedExecutionException propagated to caller)
[ ] NotificationEventListener: notificationRepository.save removed
[ ] NotificationEventListener: delegates to notificationService.create()
[ ] NotificationEventListener: cross-feature repository imports removed
[ ] PdfGenerationListener: evaluationRepository.save replaced by evaluationService.updatePdfPath()
[ ] DeadlineWarningScheduler: assignment notifications use date_bucket dedup
[ ] Duplicate deadline reminder in same day → silent skip (DataIntegrityViolationException)
[ ] CsvImportService all failure paths: pushFailed() before complete()
[ ] JobProgressEvent: status and errorMessage fields added
```

### New Metrics
```
[ ] reviewflow.async.rejected (tagged by executor)
[ ] reviewflow.grade.aggregate.failed
[ ] reviewflow.job.failed (tagged by jobType)
[ ] reviewflow.email.failed (tagged by type)
[ ] reviewflow.clamav.scan.rejected
```

### Regression
```
[ ] Email delivery still works (fire-and-forget preserved, no rethrow)
[ ] Grade overview still correct after burst (L1/L2 cache miss falls through to DB)
[ ] PDF generation still works on EvaluationPublishedEvent
[ ] Notification dedup does not block legitimate cross-day reminders
[ ] ClamAV local profile (clamav.enabled=false) not affected by scanExecutor addition
[ ] All existing PRD-21 executor tests still pass
```
