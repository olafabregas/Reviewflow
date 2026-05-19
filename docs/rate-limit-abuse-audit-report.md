# Rate Limit & Abuse Audit Report

**Date:** 2026-05-18  
**Branch:** audit/rate-limit  
**Tier:** 3  
**Auditor:** Static scan (RULE-RL01–RL10)

## Executive Summary

ReviewFlow’s Bucket4j/Redis rate-limit stack is largely well-integrated: auth paths use dedicated `AUTH_*` strategies in services (correct, because `RateLimitFilter` skips `/api/v1/auth/`), the global filter applies `API_PUBLIC` for anonymous callers and per-user `API_READ`/`API_WRITE`/`API_EXPORT` for authenticated traffic, and Redis fail-open paths log at WARN and increment `reviewflow.ratelimit.check.failed`.

The highest-risk gaps are **removed upload-block limiting** (legacy `rate-limiter.upload-block.*` properties remain in prod but no code consumes them), **unlimited WebSocket ticket issuance** (ws-ticket lives under the skipped auth prefix with no service-layer bucket), and **CSV job commit** using the loose `API_WRITE` tier instead of `API_EXPORT`.

| Severity | Count |
|----------|-------|
| CRITICAL | 0 |
| HIGH | 3 |
| MEDIUM | 4 |
| INFO | 2 |
| **Total** | **9** |

## Findings

### HIGH

#### [RULE-RL02 | HIGH] Submission / avatar upload — dedicated upload-block limiter absent

**Location:** `submission/service/SubmissionService.java`, `user/controller/AvatarController.java`, `application-prod.properties:102-103`

**Issue:** The legacy per-user upload block limiter (`rate-limiter.upload-block.*`, 10 attempts / 60 minutes in prod) has no implementation. `RateLimiterService` was removed; nothing in `src/main/java` references `rate-limiter.upload-block` or calls `SecurityMetrics.recordUploadBlockRateLimited()`.

**Context:** Upload POST/PUT still hit the global filter’s shared `API_WRITE` bucket (default 60/min per user), which also covers unrelated mutations. Operators and Postman harnesses assume a stricter upload-specific cap. ClamAV/S3 load during deadline windows needs a dedicated throttle and `reviewflow.upload.block.rate_limited` metric on reject.

**Snippet:**

```properties
# application-prod.properties (unused by any @Value in Java)
rate-limiter.upload-block.max-attempts=${RATE_LIMITER_UPLOAD_BLOCK_MAX_ATTEMPTS:10}
rate-limiter.upload-block.window-minutes=${RATE_LIMITER_UPLOAD_BLOCK_WINDOW_MINUTES:60}
```

**Fix:** Add `UPLOAD_BLOCK` (or equivalent) to `RateLimitStrategy`, wire limits from `rate-limit.upload.*` properties in `RateLimitConfigurationProvider`, call `tryConsume(userId, …)` at the start of `SubmissionService.upload` and avatar upload service path, throw `TooManyRequestsException` with `retryAfterSeconds`, and call `securityMetrics.recordUploadBlockRateLimited()` on deny. Remove or migrate orphaned `rate-limiter.upload-block.*` keys.

---

#### [RULE-RL04 | HIGH] WebSocket ticket issuance and CONNECT path not rate limited

**Location:** `auth/controller/AuthController.java:190-197`, `auth/service/WsTicketService.java:40-49`, `infrastructure/ratelimit/RateLimitFilter.java:42-46`

**Issue:** `GET /api/v1/auth/ws-ticket` is under `/api/v1/auth/`, so `RateLimitFilter` never runs. `WsTicketService.issueTicket` has no `RateLimitService` call. `/ws/**` is also excluded from the filter; inbound STOMP has no message-rate interceptor.

**Context:** An authenticated attacker can mint tickets until the in-memory Caffeine cache hits `maximumSize(50_000)` and open unlimited SockJS sessions, bypassing HTTP API tiers. Ticket grinding complements WS05 in the websocket audit.

**Snippet:**

```java
// RateLimitFilter — entire auth prefix skipped
if (path.startsWith("/api/v1/auth/")) {
  return true;
}
```

**Fix:** Add `AUTH_WS_TICKET` strategy (per-user key) in `AuthController` or `WsTicketService.issueTicket`; optionally add IP bucket for CONNECT abuse via inbound channel interceptor. Do not rely on moving the route outside `/auth/` without also adding a bucket.

---

#### [RULE-RL10 | HIGH] CSV import commit uses API_WRITE instead of API_EXPORT

**Location:** `grading/controller/JobController.java:55-59`, `infrastructure/ratelimit/RateLimitFilter.java:109-114`

**Issue:** `POST /api/v1/jobs/{jobId}/commit` does not contain `/import`, `/export`, or `/pdf`, so `isExportPath()` returns false and the request is limited by `API_WRITE` (default capacity 60/min, refill 30/min) rather than `API_EXPORT` (5/min, refill 2/min).

**Context:** Commit triggers heavy DB writes via `csvWorkerExecutor`. The import **start** path (`…/instructor-scores/import`) correctly maps to `API_EXPORT`; commit does not, allowing ~12× more commit attempts than export-tier design intends.

**Snippet:**

```java
private boolean isExportPath(String path, String method) {
  return !("GET".equals(method))
      && (path.contains("/import")
          || path.contains("/export")
          || (path.contains("/pdf") && !path.contains("/preview")));
}
```

**Fix:** Extend `isExportPath()` to include `/jobs/` + `commit`, or add `POST` paths matching `/commit` suffix; alternatively enforce `API_EXPORT` in `ImportJobService.commit` via `RateLimitService`.

---

### MEDIUM

#### [RULE-RL09 | MEDIUM] Messaging rate-limit 429 omits Retry-After header

**Location:** `messaging/service/MessagingService.java:204-207, 249-252`, `shared/exception/GlobalExceptionHandler.java:510-521`

**Issue:** `MessagingClientException.tooManyRequests` returns 429 via the generic handler without `Retry-After` or `X-RateLimit-*` headers, even though `RateLimitResult` exposes `retryAfterSeconds()`.

**Fix:** Extend `MessagingClientException` with `retryAfterSeconds`, or map `MESSAGING_RATE_LIMIT_EXCEEDED` in a dedicated handler that sets `Retry-After` like `handleTooManyRequests`.

---

#### [RULE-RL09 | MEDIUM] RateLimitException handler omits Retry-After

**Location:** `shared/exception/GlobalExceptionHandler.java:722-733`, `shared/exception/RateLimitException.java`

**Issue:** Handler returns 429 with body only. `RateLimitException` has no retry field; metric path for upload block is unused.

**Fix:** Add `retryAfterSeconds` to `RateLimitException` and set header in handler; align with `HttpErrorJsonWriter` contract.

---

#### [RULE-RL10 | MEDIUM] GET export/PDF download uses API_READ tier

**Location:** `grading/controller/GradeExportController.java:58`, `evaluation/controller/EvaluationController.java:373`, `RateLimitFilter.java:109-114`

**Issue:** `isExportPath()` requires non-GET methods. `GET …/evaluations/export` and `GET …/pdf` use `API_READ` (default 200 capacity / 100 refill per minute per user), not `API_EXPORT`.

**Context:** Large CSV/PDF generation on read paths can be abused for CPU/IO at read-tier limits.

**Fix:** Add GET export detection (paths containing `/export` or `/pdf` excluding `/preview`) mapping to `API_EXPORT`, or a dedicated `API_EXPORT_READ` strategy with the same tight limits.

---

#### [RULE-RL06 | MEDIUM] Orphan legacy rate-limiter.* properties in prod profile

**Location:** `application-prod.properties:98-103` vs `application.properties:116-149`

**Issue:** Prod still documents `rate-limiter.login`, `rate-limiter.token`, and `rate-limiter.upload-block` keys. Runtime auth limits use `rate-limit.auth.*` via `RateLimitConfigurationProvider`. No Java `@Value` binds `rate-limiter.*`.

**Context:** Operators tuning prod env vars for `RATE_LIMITER_*` will see no effect; creates false confidence especially for upload-block.

**Fix:** Remove legacy block or add deprecation comments; document canonical `rate-limit.*` keys in README/ops runbook.

---

### INFO

#### [RULE-RL05 | INFO] Redis fail-open observable but no alert runbook

**Location:** `infrastructure/ratelimit/DefaultRateLimitService.java:80-87`, `ReviewFlowMetrics.java:360-361`

**Issue:** Fail-open correctly logs WARN and increments `reviewflow.ratelimit.check.failed`. No in-repo Grafana/alert rule reference for sustained failures.

**Fix:** Add ops alert on rate of `reviewflow.ratelimit.check.failed`; consider fail-closed for `AUTH_*` strategies only.

---

#### [RULE-RL02 | INFO] Upload paths share API_WRITE bucket with all mutations

**Location:** `submission/controller/SubmissionController.java:73`, `RateLimitFilter` authenticated branch

**Issue:** Submission multipart POST is throttled only by the global write bucket, not an upload-specific strategy. Acceptable as partial mitigation until dedicated bucket ships.

---

## Clean Targets

| Target | Notes |
|--------|-------|
| `infrastructure/ratelimit/` (all 9 classes) | Filter, service, provider, strategies — RL03, RL07, RL09 (filter path) pass |
| `infrastructure/ratelimit/RateLimitConfig.java` | ProxyManager bean (note: audit doc cites `RedisConfig`; bean lives here) |
| `auth/service/AuthService.java` | RL01, RL08 — login IP probe, refresh IP+user |
| `auth/service/PasswordResetService.java` | RL01, RL08 — IP + email buckets |
| `auth/controller/StepUpController.java` | RL01 — AUTH_STEP_UP probe/consume |
| `infrastructure/security/JwtAuthenticationFilter.java` | RL01 — AUTH_JWT_FAILURE probe; RL09 Retry-After on deny |
| `messaging/service/MessagingService.java` | RL08 — MSG_SEND/MSG_CREATE per user (RL09 header gap only) |
| `grading/controller/InstructorScoreController.java` | Import start → `/import` → API_EXPORT |
| `evaluation/controller/EvaluationController.java` | POST `/{id}/pdf` → API_EXPORT |
| `system/controller/`, `admin/controller/` | RL07 — no admin bypass; elevated tier only |
| Remaining controllers (spot-check) | Covered by global filter; no extra exclusions found |

## Configuration Snapshot

| Property | Default (application.properties) | Used by |
|----------|----------------------------------|---------|
| `rate-limit.auth.login.limit` | 10 | AUTH_LOGIN |
| `rate-limit.auth.login.window-minutes` | 15 | AUTH_LOGIN |
| `rate-limit.auth.refresh.ip.limit` | 30 | AUTH_REFRESH_IP |
| `rate-limit.auth.refresh.user.limit` | 60 | AUTH_REFRESH_USER |
| `rate-limit.auth.refresh.window-seconds` | 60 | AUTH_REFRESH_* |
| `rate-limit.auth.password-reset.request.ip.limit` | 5 | AUTH_PASSWORD_RESET_REQUEST_IP |
| `rate-limit.auth.password-reset.email.limit` | 3 | AUTH_PASSWORD_RESET_EMAIL |
| `rate-limit.auth.password-reset.confirm.ip.limit` | 10 | AUTH_PASSWORD_RESET_CONFIRM_IP |
| `rate-limit.auth.step-up.limit` | 5 | AUTH_STEP_UP |
| `rate-limit.auth.jwt-failure.limit` | 20 | AUTH_JWT_FAILURE |
| `rate-limit.messaging.send.limit` | 30 | MSG_SEND |
| `rate-limit.messaging.create.limit` | 10 | MSG_CREATE |
| `rate-limit.api.public.capacity` | 20 | API_PUBLIC (anonymous IP) |
| `rate-limit.api.public.refill-per-minute` | 10 | API_PUBLIC |
| `rate-limit.api.read.default.capacity` | 200 | API_READ |
| `rate-limit.api.write.default.capacity` | 60 | API_WRITE |
| `rate-limit.api.export.default.capacity` | 5 | API_EXPORT |
| `rate-limit.filter.skip-swagger-locally` | true | Swagger/docs skip (local) |
| `rate-limiter.upload-block.*` (prod only) | 10 / 60 min | **Unwired — no consumer** |

## Rule Coverage

| Rule | Status | Notes |
|------|--------|-------|
| RL01 | **PASS** | Auth login, refresh, password-reset, step-up, JWT failure use dedicated buckets in services |
| RL02 | **FAIL** | Dedicated upload-block removed; metric never incremented |
| RL03 | **PASS** | Anonymous branch uses API_PUBLIC; filter after JWT |
| RL04 | **FAIL** | ws-ticket + `/ws/**` unlimited at HTTP layer |
| RL05 | **PASS** (INFO) | Fail-open has metric + WARN log; alerting gap |
| RL06 | **FAIL** | Orphan `rate-limiter.*` in prod profile |
| RL07 | **PASS** | ADMIN/SYSTEM_ADMIN → ELEVATED tier only |
| RL08 | **PASS** | Login IP; refresh IP+user; reset IP+email; API uses userId |
| RL09 | **PARTIAL** | Filter + TooManyRequests paths OK; messaging + RateLimitException gaps |
| RL10 | **FAIL** | Job commit + GET export/pdf on READ tier |

## Recommended Action Plan

1. **Restore upload-block limiting** — new `RateLimitStrategy`, service-layer checks on submission + avatar, wire metrics, delete dead `rate-limiter.upload-block.*` or map to new keys.
2. **Rate-limit ws-ticket** — `AUTH_WS_TICKET` per user (and optional IP) in `WsTicketService` or controller.
3. **Tighten job commit** — extend `isExportPath()` or service-level `API_EXPORT` on `POST …/commit`.
4. **Retry-After on all 429 paths** — messaging + `RateLimitException` handlers.
5. **GET heavy downloads** — map export/pdf GET to export tier or lower read caps for those paths.
6. **Ops** — alert on `reviewflow.ratelimit.check.failed`; clean prod property drift (RL06).

## Scan Metadata

| Metric | Value |
|--------|-------|
| Targets scanned | 15 |
| Java files reviewed | ~38 |
| Scan errors | None |
