# Observability & Alerting Audit Report

**Date:** 2026-05-18  
**Branch:** `audit/observability` (worktree scan)  
**Tier:** 3 — production operability  
**Flyway:** V34  
**Rule set:** OBS01–OBS10 (`observability-alerting-audit.mdc`)

---

## Executive Summary

| Severity | Count |
|----------|------:|
| CRITICAL | 0 |
| HIGH | 5 |
| MEDIUM | 12 |
| INFO | 5 |
| **Total** | **22** |

ReviewFlow has a solid **Micrometer foundation** (`ReviewFlowMetrics`, `reviewflow.*` naming, rate-limit fail-open metric, ClamAV infected WARN + counter). The largest gaps are **stub SYSTEM_ADMIN WebSocket metrics presented as live**, **no Redis (or ClamAV) actuator health**, **async job failure not metered**, **SSE job terminal failures not pushed to clients**, and **duplicate `SecurityMetrics` / `ReviewFlowMetrics` beans** registering the same meter IDs. Several **log.error** call sites drop stack traces; **email/job MDC** and **unused metric hooks** reduce correlation and dashboard value.

`orchestration/MASTER_PROJECT_SUMMARY.md` §7 was **not present** in this repository; OBS01 was validated directly against `SystemService.collectAndPushMetrics()`.

---

## Findings by Severity

### HIGH

#### [RULE-OBS01 | HIGH] `SystemService.java:422–437`

**Issue:** DB pool, cache hit rates, and `recentSecurityEvents` are hardcoded to `0` / `0.0` in the WebSocket payload with no `stub` flag or API disclaimer.

**Context:** SYSTEM_ADMIN real-time dashboard is the primary push path for infra metrics; operators may treat zeros as healthy pool/cache during incidents.

**Snippet:**
```java
.db(SystemMetricsDto.DbMetrics.builder()
    .activeConnections(0)
    .idleConnections(0)
    ...
.cache(...adminStatsHitRate(0.0)...)
.recentSecurityEvents(0)
```

**Fix:** Wire HikariCP MXBean + Caffeine stats (or Micrometer gauges); add `@JsonProperty("stub")` sections or omit sections until wired. Document in OpenAPI.

**Alert:** Do not alert on WebSocket `db.activeConnections` until live.

---

#### [RULE-OBS08 | HIGH] Redis dependency — no health indicator

**Issue:** Redis backs rate limits, async job state, and import locks; no `HealthIndicator` or `management.health.redis.enabled` configuration found.

**Context:** Load balancer health stays `UP` when Redis is down → fail-open rate limits and broken job UX.

**Looked at:** `config/RedisConfig.java` (templates only), `application.properties` / `application-prod.properties` (`management.endpoints.web.exposure.include=health,...` only).

**Fix:** Add `RedisHealthIndicator` (or enable Spring Boot Redis health) and optionally a composite readiness group including Redis for prod.

**Alert:** `reviewflow.ratelimit.check_failed` rate > N/min + Redis health DOWN → page platform.

---

#### [RULE-OBS06 | HIGH] Async jobs — no `reviewflow.job.failed` counter

**Issue:** `JobStatus.FAILED` transitions (CSV validate/commit, PDF listener) do not increment a shared job-failure counter with `jobType` tag.

**Context:** No Prometheus/CloudWatch signal for import/PDF regression; instructors rely on UI only.

**Call sites:** `CsvImportService.failJob` / `runCommit`, `PdfGenerationListener` (`recordPdfGenerationFailed` exists for PDF only — not generic jobs).

**Fix:** Add `reviewflow.job.failed` with tags `jobType`, `status`; increment in `failJob`, commit catch, and PDF catch.

**Alert:** `rate(reviewflow.job.failed[5m]) > 0` → notify grading/on-call.

---

#### [RULE-OBS06 | HIGH] `CsvImportService` — FAILED without terminal SSE

**Issue:** `failJob()` and `runCommit()` set `JobStatus.FAILED` in Redis but do not `sseEmitterRegistry.push()` a terminal/error event; `JobProgressEvent` has only `processed`/`total`/`percent` (no status or error).

**Context:** Clients subscribed via SSE see progress stop or timeout; must poll REST — easy to miss failures within 30s emitter window.

**Snippet:** `failJob` updates state only; `sseEmitterRegistry.complete(jobId)` on some paths without failure payload.

**Fix:** Extend `JobProgressEvent` with `status`, `errorMessage`; push on FAILED/VALIDATION_FAILED; call `complete()` after terminal event.

---

#### [RULE-OBS03 | HIGH] Unknown-email login not counted

**Issue:** `AuthService.login` when `userOpt.isEmpty()` does not call `metrics.recordFailedLogin()` (only dummy bcrypt + audit).

**Context:** Credential-stuffing against non-existent emails is invisible on `reviewflow.security.login{result=failed}`.

**Fix:** Increment failed login (or dedicated `result=unknown_user`) on empty user path after rate-limit consume.

**Alert:** Spike in failed login without matching lockout metric → review auth logs by `traceId`.

---

### MEDIUM

#### [RULE-OBS03 | MEDIUM] `JwtAuthenticationFilter.java:149–154`

**Issue:** Invalid JWT / token version mismatch / user-not-found only `log.debug` + rate-limit consume; no `reviewflow.security.token` counter for invalid token (only `rate_limited`, `fingerprint_mismatch`).

**Fix:** Add e.g. `result=invalid` on catch path (distinct from fingerprint).

---

#### [RULE-OBS03 | MEDIUM] `AuthService.refresh` — no failure metrics

**Issue:** Bad/missing refresh token throws `BadCredentialsException` without security metric.

**Fix:** `metrics.recordFailedLogin()` or `reviewflow.security.refresh{result=failed}`.

---

#### [RULE-OBS04 | MEDIUM] `LoginLockoutService.java:48–57`

**Issue:** Account lockout sets `locked_until` and audit `ACCOUNT_LOCKED` but no Micrometer counter (`reviewflow.security.lockout`) and no WARN log with `userId`/`ipAddress`.

**Fix:** Increment lockout counter; `log.warn` on threshold with MDC fields.

**Alert:** `reviewflow.security.lockout` rate > baseline → security notification.

---

#### [RULE-OBS07 | MEDIUM] `S3Service.java` (multiple lines ~51–172)

**Issue:** `log.error("...: {}", key, e.getMessage())` without passing `e` as last argument — stack traces lost in centralized logging.

**Fix:** `log.error("S3 upload failed for key {}", key, e);`

---

#### [RULE-OBS07 | MEDIUM] `EmailService.java:37`, `EmailEventListener.java:332–336`

**Issue:** Email failures log message only; `ReviewFlowMetrics.recordEmailFailed` exists but is **never called** anywhere in the codebase.

**Fix:** Pass `e` to `log.error`; call `metrics.recordEmailFailed(eventType)` in catch.

---

#### [RULE-OBS07 | MEDIUM] `PdfGenerationListener.java:65`

**Issue:** `log.error("...: {}", hashedEvalId, e.getMessage())` without throwable (metric `recordPdfGenerationFailed` is called — good).

**Fix:** Add `, e` as final argument.

---

#### [RULE-OBS07 | MEDIUM] `ClamAvScanService.java:71,90`

**Issue:** Scan errors log `e.getMessage()` only on IOException path; line 90 wraps but line 71 omits stack.

**Fix:** Consistent `log.error(..., e)`.

---

#### [RULE-OBS02 | MEDIUM] MDC — no `courseId` / `assignmentId`

**Issue:** `MdcFilter` sets `traceId`, `requestId`, `endpoint`, `ipAddress`; `JwtAuthenticationFilter` adds `userId`, `role`. No standard `courseId`/`assignmentId` on course-scoped routes.

**Fix:** Optional filter or interceptor after auth for `/api/v1/courses/{id}/...` paths.

---

#### [RULE-OBS09 | MEDIUM] Async workers — no `jobId` in MDC

**Issue:** `AsyncJobService` has no logging; CSV/PDF workers log `jobId` in message text but do not `MDC.put("jobId", jobId)` in worker `try/finally`.

**Fix:** Set/clear MDC at start of `runValidation` / `runCommit` / PDF handler.

---

#### [RULE-OBS02 | MEDIUM] `MdcFilter` — no `X-Trace-Id` response header

**Issue:** PRD-08 references `X-Trace-Id` for gateway correlation; filter sets MDC but does not echo `traceId` on the HTTP response.

**Fix:** `response.setHeader("X-Trace-Id", traceId)` before `filterChain.doFilter`.

---

#### [RULE-OBS10 | MEDIUM] Duplicate metric beans

**Issue:** `SecurityMetrics` and `ReviewFlowMetrics` both register identical meters (e.g. `reviewflow.security.login`, `reviewflow.security.clamav_scan`). Both are `@Component` and actively injected (`JwtAuthenticationFilter` → `SecurityMetrics`, `AuthService` → `ReviewFlowMetrics`).

**Context:** Micrometer may merge by ID, but dual beans confuse ownership and risk divergent call sites.

**Fix:** Deprecate `SecurityMetrics`; migrate `ClamAvScanService`, `JwtAuthenticationFilter`, `SubmissionService` to `ReviewFlowMetrics` only.

---

### INFO

#### [RULE-OBS10 | INFO] Dead metric APIs

`recordEmailSent`, `recordEmailFailed`, `recordNotificationSent`, `recordSubmissionUploaded` — defined in `ReviewFlowMetrics` but **no call sites** in `src/main/java`.

---

#### [RULE-OBS10 | INFO] `recordNotificationSent(String type)` — type tag unused

Counter builder has no `.tag("type", type)` — notification breakdown not possible.

---

#### [RULE-OBS08 | INFO] ClamAV not in health aggregate

When `clamav.enabled=true`, no health contributor reflects scanner reachability (optional custom indicator).

---

#### [RULE-OBS01 | INFO] `getCacheStats()` REST endpoint

`SystemService.getCacheStats()` uses Caffeine native stats (real `estimatedSize`, hit/miss where available) — **distinct** from WebSocket stub hit rates. Document which API is authoritative.

---

#### [RULE-OBS05 | INFO] ClamAV positive path — **clean**

`ClamAvScanService` logs WARN on infected, calls `securityMetrics.recordClamavInfected()` — satisfies OBS05.

---

## Stub Metrics Inventory (OBS01)

| Field / section | Value in `collectAndPushMetrics()` | Live source available? | Risk |
|-----------------|-----------------------------------|-------------------------|------|
| `db.activeConnections` | `0` | HikariCP MXBean | HIGH |
| `db.idleConnections` | `0` | HikariCP MXBean | HIGH |
| `db.maxConnections` | `10` (constant) | HikariCP | MEDIUM |
| `cache.*HitRate` | `0.0` | Caffeine / Micrometer | HIGH |
| `recentSecurityEvents` | `0` | Audit query / counter | MEDIUM |
| `jvm.*` | Runtime MXBean | Live | OK |
| `uptimeSeconds` | Runtime MXBean | Live | OK |

---

## MDC Coverage Matrix

| MDC key | Set by | When | Gap |
|---------|--------|------|-----|
| `traceId` | `MdcFilter` | Request start | Not returned as `X-Trace-Id` |
| `requestId` | `MdcFilter` | Request start | — |
| `endpoint` | `MdcFilter` | Request URI | — |
| `ipAddress` | `MdcFilter` | Request start | — |
| `userId` | `JwtAuthenticationFilter` | After valid JWT | Hashid encoded |
| `role` | `JwtAuthenticationFilter` | After valid JWT | — |
| `courseId` | — | — | Not implemented |
| `assignmentId` | — | — | Not implemented |
| `jobId` | — | — | Log text only in CSV worker |

**Async MDC propagation:** `@Async` listeners (email, PDF, notification) do not copy MDC from publisher thread — expected gap for OBS02.

---

## Metric Catalog (registered + call sites)

| Metric name | Type | Tags | Call sites |
|-------------|------|------|------------|
| `reviewflow.security.login` | Counter | `result=success\|failed\|rate_limited` | `AuthService` (success, failed, rate_limited) |
| `reviewflow.security.token` | Counter | `result=rate_limited\|fingerprint_mismatch` | `JwtAuthenticationFilter` |
| `reviewflow.security.auth_token_source` | Counter | `source=cookie\|bearer` | `JwtAuthenticationFilter` |
| `reviewflow.security.file_upload` | Counter | `result=*` | `ReviewFlowMetrics` / `FileSecurityValidator` |
| `reviewflow.security.clamav_scan` | Counter | `result=clean\|infected\|error` | `SecurityMetrics` ← `ClamAvScanService` |
| `reviewflow.s3.upload_duration` | Timer | — | `S3Service` (if wired) |
| `reviewflow.s3.upload_failures` | Counter | — | `S3Service` |
| `reviewflow.email.sent` / `failed` | Counter | — | **No call sites** |
| `reviewflow.notifications.sent` | Counter | — | **No call sites** |
| `reviewflow.pdf.generation.failed` | Counter | — | `PdfGenerationListener` |
| `reviewflow.ratelimit.hit` | Counter | `strategy`, `role` | `DefaultRateLimitService` |
| `reviewflow.ratelimit.check_failed` | Counter | `strategy` | `DefaultRateLimitService` (Redis fail-open) |
| `reviewflow.websocket.push.failed` | Counter | `type` | `MessagingService` (if used) |
| `reviewflow.assignment_groups.*` | Counter | — | Assignment group service |
| `reviewflow.job.failed` | — | — | **Not implemented** |

---

## Health Indicator Status

| Dependency | Used in prod paths | Actuator health | Notes |
|------------|---------------------|-----------------|-------|
| MySQL / JPA | Yes | Default datasource | Standard |
| Redis | Rate limit, jobs, locks | **Missing** | OBS08 HIGH |
| ClamAV | File upload (prod enabled) | **Missing** | OBS08 INFO |
| S3 | Uploads | **Missing** | Optional; often checked via synthetic job |
| Email SMTP | Async | **Missing** | Typical optional indicator |

---

## Alerting Recommendations

| Signal | Suggested threshold | Action |
|--------|---------------------|--------|
| `reviewflow.ratelimit.check_failed` | > 10/min per instance | Check Redis; expect fail-open abuse window |
| `reviewflow.security.login{result=failed}` | 3× baseline 15m | Review lockout + WAF; correlate `traceId` |
| `reviewflow.security.clamav_scan{result=infected}` | ≥ 1 | Page security; quarantine S3 object; trace uploader |
| `reviewflow.pdf.generation.failed` | > 0 sustained 5m | Check `pdfExecutor` pool + evaluation logs |
| `reviewflow.job.failed` (after implement) | > 0 | Inspect `jobId` in logs / Redis job key |
| Redis health DOWN | 1 | Drain instance or fail readiness |
| WebSocket `db.activeConnections == 0` | **Do not alert** until OBS01 fixed | — |

---

## Clean Targets (no OBS violations)

| # | Target | Notes |
|---|--------|-------|
| 1 | `ReviewFlowMetrics.java` | Naming convention; broad catalog (some unused APIs) |
| 2 | `MdcFilter.java` | Core correlation fields; clear in `finally` |
| 3 | `DefaultRateLimitService.java` | Fail-open WARN + `check_failed` metric |
| 4 | `ClamAvScanService.java` | OBS05 satisfied (WARN + infected counter) |
| 5 | `SystemService.collectAndPushMetrics` catch | `log.error(..., e)` with stack |
| 6 | `CsvImportService` validation catch | Logs with `jobId` + throwable |
| 7 | `AuthService` bad-password path | `recordFailedLogin()` |
| 8 | `PasswordResetService` | Rate limits; no OBS07 in scanned paths |
| 9 | `AsyncJobConfig` / `SseEmitterRegistry` | Structure OK; gaps are OBS06/09 usage |
| 10 | `application.properties` `management.*` | Actuator exposure configured; Redis health not enabled |

---

## Scan Progress (17 targets)

```
✓ ReviewFlowMetrics.java — 2 findings (INFO dead APIs)
✓ SecurityMetrics.java — 1 finding (duplicate meters)
✓ MdcFilter.java — 2 findings (OBS02, X-Trace-Id)
✓ JwtAuthenticationFilter.java — 2 findings (OBS03, OBS02 partial)
✓ SystemService.java — 2 findings (OBS01, getCacheStats INFO)
✓ system/controller/ — 0 findings (delegates to service)
✓ AuthService + LoginLockoutService — 3 findings
✓ PasswordResetService — 0 findings
✓ infrastructure/jobs/ — 3 findings (OBS06, OBS09)
✓ CsvImportService + ImportJobService — 2 findings (OBS06)
✓ PdfGenerationListener + PDF — 2 findings (OBS07, PDF metric OK)
✓ EmailEventListener — 2 findings (OBS07, email metrics)
✓ NotificationEventListener — 0 findings (no OBS07 in grep)
✓ infrastructure/storage/ — 4 findings (OBS07 S3/ClamAV; OBS05 clean)
✓ DefaultRateLimitService — 0 findings
✓ application.properties management.* — 1 finding (OBS08)
✓ MASTER_PROJECT_SUMMARY §7 — skipped (file not in repo)
```

**Files scanned:** ~28 primary + grep cross-checks  
**Report path:** `docs/observability-alerting-audit-report.md`

---

## Recommended Action Plan (ordered)

1. **OBS01** — Mark stub fields or wire Hikari/Caffeine before any dashboard alerting.
2. **OBS08** — Redis health in readiness probe.
3. **OBS06** — `reviewflow.job.failed` + SSE terminal failure events for CSV jobs.
4. **OBS03/04** — Auth failure + lockout metrics; JWT invalid token counter.
5. **OBS10** — Consolidate on `ReviewFlowMetrics`; wire email/notification counters.
6. **OBS07** — Pass throwables to `log.error` in S3/email/PDF/ClamAV.
7. **OBS09/02** — `jobId` MDC in workers; optional `courseId` on scoped routes.
