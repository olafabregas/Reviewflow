# Async Job & Executor Audit Report

**Date:** 2026-05-18  
**Scope:** Full `scan all` (17 targets)  
**Branch:** working tree (read-only audit; report not committed)  
**Tier:** 2 — resolve CRITICAL/HIGH before production

---

## Executive Summary

| Severity | Count |
|----------|------:|
| CRITICAL | 0 |
| HIGH | 4 |
| MEDIUM | 10 |
| INFO | 3 |
| **Total** | **17** |

**Top risks**

1. **`uploadExecutor` + `AbortPolicy`** — pool saturation surfaces as opaque 500s on submission/message uploads; CSV import can leave jobs stuck with an import lock held.
2. **`gradeAggregateExecutor` + `DiscardOldestPolicy`** — aggregate recalculations can be dropped under burst load with no rejection metric.
3. **CSV job SSE** — validation failures update Redis to `FAILED` but do not push a terminal progress event; clients must poll REST (acceptable only if documented).
4. **`ClamAvScanService` bare `@Async`** — uses Spring’s default executor (unbounded / not the named upload pool).

**Clean areas:** `AsyncConfig` global uncaught handler, executor shutdown flags in `AsyncJobConfig` / `EmailConfig`, intentional `@Async("notificationExecutor")` / `@Async("emailTaskExecutor")` on listeners, WebSocket presence/disconnect listeners (lightweight sync), no auth-path `CallerRunsPolicy` misuse.

---

## Findings

### HIGH

#### [RULE-ASYNC01 | HIGH] AsyncJobConfig.java:43-44

**Issue:** `gradeAggregateExecutor` uses `DiscardOldestPolicy` with no `reviewflow.async.rejected` (or equivalent) Micrometer counter per executor bean.

**Context:** Under grade-publish storms, oldest pending aggregate recomputes are silently dropped. Stale Redis grade overviews may persist until the next write.

**Snippet:**
```java
return buildExecutor(
    core, max, queue, "grade-agg-", new ThreadPoolExecutor.DiscardOldestPolicy());
```

**Fix:** Register a rejected-task handler that increments `reviewflow.async.rejected` with tag `executor=gradeAggregateExecutor` and logs at WARN. Document that superseded recalcs are safe only if the next `GradePublishedEvent` always fires.

---

#### [RULE-ASYNC02 | HIGH] SubmissionService.java:549-573

**Issue:** `CompletableFuture.supplyAsync(..., uploadExecutor)` does not handle `RejectedExecutionException` when the pool queue is full (`AbortPolicy`).

**Context:** Deadline upload spikes can reject tasks synchronously at submit time; callers see generic `StorageException` or uncaught 500, not a retryable capacity error.

**Snippet:**
```java
CompletableFuture<String> uploadFuture =
    CompletableFuture.supplyAsync(..., uploadExecutor);
return uploadFuture.get(uploadTimeoutSeconds, TimeUnit.SECONDS);
```

**Fix:** Catch `RejectedExecutionException` and map to a domain exception (e.g. `503` / `SERVICE_UNAVAILABLE` with stable code). Optionally surface queue depth via metric.

---

#### [RULE-ASYNC02 | HIGH] MessagingService.java:736-741

**Issue:** Same `uploadExecutor` / `AbortPolicy` gap for attachment `runAsync` + blocking `get`.

**Context:** Message sends with attachments fail opaquely under pool saturation.

**Fix:** Same as SubmissionService — catch rejection, return structured error to client, increment `reviewflow.async.rejected`.

---

#### [RULE-ASYNC08 | HIGH] CsvImportService.java:179, 133-136

**Issue:** If `CompletableFuture.runAsync(..., csvWorkerExecutor)` rejects (or never runs), job remains `UPLOADED` and `reviewflow:import:lock:{courseId}` stays until TTL (2h default).

**Context:** Instructors cannot start a new import; UI may poll indefinitely if it only watches SSE.

**Snippet:**
```java
if (!asyncJobService.acquireImportLock(hashedCourseId, jobId)) { ... }
asyncJobService.saveJob(initial);
CompletableFuture.runAsync(() -> runValidation(jobId), csvWorkerExecutor);
```

**Fix:** Wrap `runAsync` in try/catch for `RejectedExecutionException`; call `failJob(jobId, hashedCourseId, "...")` and release lock immediately. Consider metric `reviewflow.job.failed` with `jobType=csv_import`.

---

### MEDIUM

#### [RULE-ASYNC02 | MEDIUM] CsvImportService.java:140-157

**Issue:** Source CSV upload uses `uploadExecutor` with `.join()`; rejection releases lock but throws `StorageException` before job record exists — acceptable, but no distinct “system busy” code.

**Fix:** Map `RejectedExecutionException` to a dedicated error code for clients to retry.

---

#### [RULE-ASYNC02 | MEDIUM] ImportJobService.java:75-76

**Issue:** Commit path sets `COMMITTING` then `runAsync` on `csvWorkerExecutor` without rejection handling.

**Context:** Rejection leaves status `COMMITTING` until manual intervention; lock released only if `runCommit` runs.

**Fix:** Try/catch around `runAsync`; on rejection revert to `VALIDATION_PASSED` or set `FAILED` with message.

---

#### [RULE-ASYNC03 | MEDIUM] EmailEventListener.java:330-337

**Issue:** `sendEmail` catches `Exception`, logs at ERROR, does not rethrow — bypasses `AsyncUncaughtExceptionHandler`.

**Context:** Email failures are invisible to ops alerting beyond log scraping; intentional “never break request flows” applies to async side effects but hides systemic SMTP outages.

**Snippet:**
```java
} catch (Exception e) {
  log.error("Email handler failed: ...", e.getMessage());
}
```

**Fix:** Rethrow after log, or call `metrics.recordEmailFailed(eventType)` (verify wired) and use ERROR with full throwable: `log.error(..., e)`.

---

#### [RULE-ASYNC03 | MEDIUM] GradeAggregateUpdateListener.java:31-37, 54-63

**Issue:** Broad `catch (Exception)` with WARN-only logging and no rethrow.

**Context:** Redis grade cache can stay stale with no failure counter.

**Fix:** Increment `reviewflow.grade.aggregate.failed` (new counter); rethrow or schedule compensating recalc on next read.

---

#### [RULE-ASYNC03 | MEDIUM] PdfGenerationListener.java:64-67

**Issue:** Exceptions caught and swallowed after `recordPdfGenerationFailed()` — no rethrow.

**Context:** Mitigated partially by metric; still bypasses global async handler. Acceptable if metric is alerted (cross-ref OBS06).

**Fix:** Keep metric; add `log.error(..., e)` with stack; optional evaluation row `pdfGenerationStatus=FAILED`.

---

#### [RULE-ASYNC05 | MEDIUM] ClamAvScanService.java:50-51

**Issue:** `@Async` without executor qualifier — binds to Spring default async executor (not `uploadExecutor`).

**Context:** Unbounded or mis-sized default pool can starve other `@Async` work or grow without backpressure; FILE audit cross-ref ASYNC05.

**Fix:** Use `@Async("uploadExecutor")` or a dedicated `scanExecutor` with bounded queue and documented rejection policy.

---

#### [RULE-ASYNC07 | MEDIUM] NotificationEventListener.java:491-501

**Issue:** `notificationRepository.save` in listener helpers without `@Transactional` on listener or service delegate for `saveAndPush`.

**Context:** Multi-step notification + cache evict + WebSocket push may partial-commit under failure (cross-ref transaction-boundary audit RULE-02).

**Fix:** Move `saveAndPush` persistence into `NotificationService.create(...)` (already `@Transactional`) instead of direct repository access in listener.

**Law 2 note:** Listener imports `CourseEnrollmentRepository`, `UserRepository` across features — coupling risk.

---

#### [RULE-ASYNC07 | MEDIUM] PdfGenerationListener.java:50-51

**Issue:** `evaluationRepository.save(evaluation)` inside async listener without explicit transaction boundary on listener method.

**Context:** `pdfPath` update may not participate in a single TX with related rows if expanded later.

**Fix:** Delegate to `@Transactional` method on `EvaluationService` (e.g. `updatePdfPath(evaluationId, path)`).

---

#### [RULE-ASYNC08 | MEDIUM] CsvImportService.java:358-378, 229-237

**Issue:** `failJob` updates Redis to `FAILED` and calls `sseEmitterRegistry.complete(jobId)` but never `push`es a terminal `JobProgressEvent` (event has no status field).

**Context:** SSE subscribers only see connection close; must fall back to `GET /api/v1/jobs/{id}/status` (implemented on `JobController` — good REST fallback).

**Fix:** Extend `JobProgressEvent` with `status` / `errorMessage`, or push a named SSE event `failed` before `complete()`.

---

#### [RULE-ASYNC12 | MEDIUM] DeadlineWarningScheduler.java + NotificationEventListener.java:367-400

**Issue:** Scheduled `DeadlineWarningEvent` + async listener `saveAndPushMany` has no dedup key (unlike discussion reminders with `uk_notification_dedup` / `tryCreateDedupedDiscussionReminder`).

**Context:** Scheduler re-run or clock skew could duplicate 24h/48h notifications for the same assignment window.

**Fix:** Add date-bucket dedup similar to PRD-17 discussion reminders, or idempotency key on `(userId, assignmentId, notificationType, hoursUntilDue)`.

---

#### [RULE-ASYNC12 | MEDIUM] PdfGenerationListener.java:38-51

**Issue:** No guard skipping PDF generation when `evaluation.getPdfPath()` is already set.

**Context:** Replayed `EvaluationPublishedEvent` can regenerate PDFs and overwrite S3 objects (duplicate work, race on path).

**Fix:** Early return if `pdfPath != null` unless `forceRegenerate` flag; or compare evaluation version/status.

---

### INFO

#### [RULE-ASYNC04 | INFO] SubmissionService.java / MessagingService.java

**Issue:** CompletableFuture chains use blocking `get()` with partial exception mapping; no MDC propagation to worker threads (`jobId` / `submissionId`).

**Context:** Logs from async upload threads harder to correlate (cross-ref OBS09).

**Fix:** `MDC.setContextMap(...)` in async lambda; ensure `RejectedExecutionException` logged with correlation IDs.

---

#### [RULE-ASYNC10 | INFO] AsyncJobConfig.java:30-35

**Issue:** `pdfExecutor` max pool 3 — sizing rationale lives only in properties defaults (`async.pdf.*`), not in-code comment.

**Context:** Acceptable if ops documents CPU cost; INFO only.

**Fix:** One-line class-level comment: PDF generation is CPU-bound; pool intentionally small.

---

#### [RULE-ASYNC10 | INFO] CsvImportService.java:68-69

**Issue:** CSV parse runs on `csvWorkerExecutor`; S3 staging upload uses `uploadExecutor` — good separation. Document that validation is CPU + DB heavy on worker pool, not upload pool.

**Fix:** No code change required; add to runbook.

---

#### [Law 2 | INFO] EmailEventListener.java:38

**Issue:** Cross-feature `UserRepository` import in email listener.

**Context:** Architecture coupling; not an executor bug.

**Fix:** Prefer `UserService` / preference API in `user` feature.

---

## Clean Targets

| Target | Notes |
|--------|--------|
| `infrastructure/config/AsyncConfig.java` | Global `AsyncUncaughtExceptionHandler`; `notificationExecutor` shutdown waits |
| `infrastructure/jobs/AsyncJobService.java` | Redis job state + import lock API |
| `infrastructure/jobs/SseEmitterRegistry.java` | 30s emitter timeout; push/complete lifecycle |
| `infrastructure/jobs/JobProgressEvent.java` | DTO only — no violations |
| `infrastructure/email/EmailConfig.java` | `CallerRunsPolicy`, shutdown flags |
| `grading/controller/JobController.java` | REST status + SSE subscribe endpoints |
| `infrastructure/scheduling/PasswordResetCleanupScheduler.java` | Not async-heavy |
| `infrastructure/scheduling/NotificationCleanupScheduler.java` | Not async-heavy |
| `infrastructure/websocket/WebSocketPresenceListener.java` | Lightweight sync disconnect |
| `infrastructure/websocket/WebSocketForceDisconnectListener.java` | Lightweight sync registry clear |
| `@Async` qualified listeners | `notificationExecutor`, `emailTaskExecutor`, `pdfExecutor`, `gradeAggregateExecutor` |

---

## Rule Coverage Matrix

| Rule | Status | Hit count |
|------|--------|----------:|
| ASYNC01 | FAIL | 1 |
| ASYNC02 | FAIL | 4 |
| ASYNC03 | FAIL | 3 |
| ASYNC04 | PARTIAL | 1 (INFO) |
| ASYNC05 | FAIL | 1 |
| ASYNC06 | PASS | 0 |
| ASYNC07 | FAIL | 2 |
| ASYNC08 | FAIL | 2 |
| ASYNC09 | PASS | 0 |
| ASYNC10 | INFO | 2 |
| ASYNC11 | PASS | 0 |
| ASYNC12 | FAIL | 2 |

---

## Recommended Action Plan

1. **Observability (before prod):** Add `reviewflow.async.rejected` tagged by executor bean; add `reviewflow.job.failed` for CSV import terminal failures (align with OBS06).
2. **User-facing rejection:** Map `RejectedExecutionException` on `uploadExecutor` to 503 + stable error code in submission and messaging paths.
3. **CSV import hardening:** Wrap `runAsync` submit paths; on rejection call `failJob` + `releaseImportLock`; push terminal SSE or document REST-only failure UX.
4. **Grade aggregates:** Either switch to `CallerRunsPolicy` with bounded impact, or keep `DiscardOldest` with rejection metric + alert.
5. **ClamAV:** Bind `@Async("uploadExecutor")` or dedicated `scanExecutor`.
6. **Idempotency:** PDF skip-if-exists; deadline notification dedup.
7. **Transactions:** Route listener DB writes through `@Transactional` services.

---

## Scan Metadata

| Metric | Value |
|--------|------:|
| Targets scanned | 17 |
| Java files read | 22 |
| Scan errors | 0 |

### Progress log

```
✓ infrastructure/config/AsyncConfig.java — 0 findings
✓ infrastructure/jobs/ — 0 findings
✓ infrastructure/email/EmailConfig.java — 0 findings
✓ infrastructure/email/EmailEventListener.java — 1 finding
✓ notification/event/NotificationEventListener.java — 2 findings
✓ evaluation/listener/PdfGenerationListener.java — 3 findings
✓ grading/event/GradeAggregateUpdateListener.java — 2 findings
✓ infrastructure/storage/ClamAvScanService.java — 1 finding
✓ submission/service/SubmissionService.java — 1 finding
✓ messaging/service/MessagingService.java — 1 finding
✓ grading/service/CsvImportService.java — 4 findings
✓ grading/service/ImportJobService.java — 1 finding
✓ grading/controller/JobController.java — 0 findings
✓ infrastructure/scheduling/ — 1 finding
✓ infrastructure/websocket/WebSocketPresenceListener.java — 0 findings
✓ infrastructure/websocket/WebSocketForceDisconnectListener.java — 0 findings
✓ Grep @Async without qualifier — 1 finding (ClamAvScanService)
```
