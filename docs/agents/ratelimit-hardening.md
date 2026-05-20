# Agent — Rate Limit & Abuse Hardening

**Status:** Final  
**Author:** Roqeeb Olamide Ayorinde  
**Source:** Rate Limit & Abuse Audit Report 2026-05-18 (9 findings)  
**Tier:** 3 — production abuse prevention  
**Migration:** None  
**Depends on:** PRD-20 (Bucket4j + Redis rate limiting must be implemented and verified before starting)

---

## How to Invoke This Agent

```
@docs/agents/ratelimit-hardening.md fix all
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

You are a senior Spring Boot engineer executing a structured rate limiting hardening pass on ReviewFlow, a **Spring Boot 4.0.x / Java 21** academic submission and grading platform.

**Rules:**
- PRD-20 (Bucket4j + Redis rate limiting) must be fully implemented before starting. Verify `RateLimitStrategy`, `RateLimitConfigurationProvider`, and `RateLimitFilter` all exist and are operational.
- **Read `RateLimitStrategy`, `RateLimitConfigurationProvider`, and `RateLimitFilter` fully before touching anything.** Every new strategy must follow the exact same pattern as existing strategies.
- HIGH-3 and MEDIUM-3 are resolved by the same `isExportPath()` fix — implement once, verify both.
- All changes are additive — do not modify existing strategy behaviour.

**Files touched:**

| File | Path |
|---|---|
| RateLimitStrategy | `infrastructure/ratelimit/RateLimitStrategy.java` |
| RateLimitConfigurationProvider | `infrastructure/ratelimit/RateLimitConfigurationProvider.java` |
| RateLimitFilter | `infrastructure/ratelimit/RateLimitFilter.java` |
| WsTicketService | `auth/service/WsTicketService.java` |
| SubmissionService | `submission/service/SubmissionService.java` |
| MessagingClientException | `shared/exception/MessagingClientException.java` |
| RateLimitException | `shared/exception/RateLimitException.java` |
| GlobalExceptionHandler | `shared/exception/GlobalExceptionHandler.java` |
| ReviewFlowMetrics | `infrastructure/monitoring/ReviewFlowMetrics.java` |
| application.properties | `src/main/resources/application.properties` |
| application-prod.properties | `src/main/resources/application-prod.properties` |
| RUNBOOK.md | `orchestration/RUNBOOK.md` |

---

## Locked Decisions

| Decision | Choice |
|---|---|
| UPLOAD_BLOCK tiers | Role-based: STUDENT 10/hr, INSTRUCTOR 30/hr, ADMIN+ 60/hr |
| Avatar uploads | Stay on `API_WRITE` — not included in `UPLOAD_BLOCK` |
| GET export/PDF | Same `API_EXPORT` tier (5/min) — generated on-the-fly, same cost |
| WS ticket strategy | `AUTH_WS_TICKET` per-user sliding window, 30 tickets/15 min |
| Property migration | Remove `rate-limiter.upload-block.*` — replace with `rate-limit.upload.*` |

---

## Execution Protocol for `fix all`

```
STEP 1 — Pre-flight
  Verify PRD-20 is implemented: RateLimitStrategy, RateLimitConfigurationProvider,
  RateLimitFilter all exist and operational
  Read all three files fully before writing any code

STEP 2 — Create fix branch
  git checkout -b fix/ratelimit-hardening

STEP 3 — HIGH tier (HIGH-1 through HIGH-3 in order)
  HIGH-3 isExportPath() fix also resolves MEDIUM-3 — implement once
  Verify HIGH checklist before advancing

STEP 4 — MEDIUM tier (MEDIUM-1 through MEDIUM-4 in order)
  MEDIUM-3 is already resolved by HIGH-3 — verify only, no code change
  Verify MEDIUM checklist before advancing

STEP 5 — Run full verification checklist
  Report pass/fail per item
```

---

## Final State — RateLimitStrategy Enum

After this PRD the enum must contain exactly these values. Use this as the target state when reading the current enum:

```java
public enum RateLimitStrategy {
    // Sliding window — auth (from PRD-20)
    AUTH_LOGIN,
    AUTH_REFRESH_IP,
    AUTH_REFRESH_USER,
    AUTH_PASSWORD_RESET_REQUEST_IP,
    AUTH_PASSWORD_RESET_EMAIL,
    AUTH_PASSWORD_RESET_CONFIRM_IP,
    AUTH_STEP_UP,
    AUTH_JWT_FAILURE,
    AUTH_WS_TICKET,          // ← NEW (HIGH-2)

    // Sliding window — messaging (from PRD-20)
    MSG_SEND,
    MSG_CREATE,

    // Sliding window — upload (from this PRD)
    UPLOAD_BLOCK,            // ← NEW (HIGH-1, role-resolved capacity)

    // Token bucket — general API (from PRD-20)
    API_PUBLIC,
    API_READ,
    API_WRITE,
    API_EXPORT
}
```

---

## HIGH — Fix Before Production

### HIGH-1 · UPLOAD_BLOCK Strategy — Submission Upload Rate Limiting

**Files:** `RateLimitStrategy.java`, `RateLimitConfigurationProvider.java`, `SubmissionService.java`, `ReviewFlowMetrics.java`, `application.properties`, `application-prod.properties`  
**Issue:** Legacy `rate-limiter.upload-block.*` prod properties have no Java consumer since `RateLimiterService` was deleted. Submission uploads share the global `API_WRITE` bucket (60/min) with all mutations. ClamAV + S3 load during deadline windows needs a dedicated per-user throttle.

#### Step 1 — Add `UPLOAD_BLOCK` to `RateLimitStrategy`

Single strategy, role-resolved capacity in the provider:
```java
UPLOAD_BLOCK  // role resolved in RateLimitConfigurationProvider
```

#### Step 2 — Add `UPLOAD_BLOCK` case to `RateLimitConfigurationProvider`

Read the file first — match the exact pattern used for existing cases.

```java
case UPLOAD_BLOCK -> {
    long capacity = uploadBlockCapacity(role);
    yield BucketConfiguration.builder()
        .addLimit(Bandwidth.sliding(
            capacity,
            Duration.ofHours(uploadBlockWindowHours)))
        .build();
}

private long uploadBlockCapacity(UserRole role) {
    if (role == null) return uploadBlockStudentLimit;
    return switch (role) {
        case SYSTEM_ADMIN, ADMIN -> uploadBlockAdminLimit;
        case INSTRUCTOR           -> uploadBlockInstructorLimit;
        default                   -> uploadBlockStudentLimit; // STUDENT
    };
}
```

Inject the new properties:
```java
@Value("${rate-limit.upload.student.limit:10}")
private long uploadBlockStudentLimit;

@Value("${rate-limit.upload.instructor.limit:30}")
private long uploadBlockInstructorLimit;

@Value("${rate-limit.upload.admin.limit:60}")
private long uploadBlockAdminLimit;

@Value("${rate-limit.upload.window-hours:1}")
private long uploadBlockWindowHours;
```

Redis key pattern: `reviewflow:ratelimit:upload:{userId}` — per user, sliding window, role-resolved capacity.

#### Step 3 — Wire in `SubmissionService.upload()`

Add at service entry before any DB or S3 work:

```java
RateLimitResult uploadLimit = rateLimitService.tryConsume(
    String.valueOf(userId),
    RateLimitStrategy.UPLOAD_BLOCK,
    currentUser.getRole());

if (!uploadLimit.allowed()) {
    metrics.recordUploadBlockRateLimited();
    throw new TooManyRequestsException(
        "Upload limit reached. Please try again in " +
        uploadLimit.retryAfterSeconds() + " seconds.",
        uploadLimit.retryAfterSeconds());
}
```

#### Step 4 — Add metric to `ReviewFlowMetrics`

```java
public void recordUploadBlockRateLimited() {
    registry.counter("reviewflow.upload.block.rate_limited").increment();
}
```

#### Step 5 — Add properties to `application.properties`

```properties
# ─── Upload rate limiting ─────────────────────────────────────────────────────
rate-limit.upload.student.limit=${RATE_LIMIT_UPLOAD_STUDENT_LIMIT:10}
rate-limit.upload.instructor.limit=${RATE_LIMIT_UPLOAD_INSTRUCTOR_LIMIT:30}
rate-limit.upload.admin.limit=${RATE_LIMIT_UPLOAD_ADMIN_LIMIT:60}
rate-limit.upload.window-hours=${RATE_LIMIT_UPLOAD_WINDOW_HOURS:1}
```

#### Step 6 — Remove dead prod properties from `application-prod.properties`

```properties
# REMOVE these lines entirely (no Java consumer — never had any effect):
# rate-limiter.upload-block.max-attempts=10         ← DELETE
# rate-limiter.upload-block.window-minutes=60       ← DELETE
```

**Avatar uploads:** Do NOT add `UPLOAD_BLOCK` to the avatar upload path. Avatars stay on `API_WRITE`. Different usage pattern — one-time profile update, not deadline-driven bulk upload.

**Verify:**
```
STUDENT: 11th submission upload in 1hr → 429 TOO_MANY_REQUESTS with Retry-After
INSTRUCTOR: 31st upload in 1hr → 429
ADMIN: 61st upload in 1hr → 429
Avatar upload (any role) → governed by API_WRITE bucket, not UPLOAD_BLOCK
reviewflow.upload.block.rate_limited incremented on each rejection
Redis key reviewflow:ratelimit:upload:{userId} exists after first upload
rate-limiter.upload-block.* absent from application-prod.properties
```

---

### HIGH-2 · WsTicketService — Rate Limit Ticket Issuance

**Files:** `RateLimitStrategy.java`, `RateLimitConfigurationProvider.java`, `WsTicketService.java`, `application.properties`  
**Issue:** `GET /api/v1/auth/ws-ticket` lives under `/api/v1/auth/` which `RateLimitFilter` intentionally skips. An authenticated attacker can mint unlimited tickets until the Caffeine cache hits `maximumSize(50,000)`.

**Why service-layer not filter:** Auth endpoints have service-layer limits by convention — consistent with `AUTH_LOGIN`, `AUTH_REFRESH_*`, `AUTH_JWT_FAILURE` all wired at the service layer.

#### Step 1 — Add `AUTH_WS_TICKET` to `RateLimitStrategy`

```java
AUTH_WS_TICKET  // per-user sliding window
```

#### Step 2 — Add `AUTH_WS_TICKET` case to `RateLimitConfigurationProvider`

```java
case AUTH_WS_TICKET -> BucketConfiguration.builder()
    .addLimit(Bandwidth.sliding(
        wsTicketLimit,
        Duration.ofMinutes(wsTicketWindowMinutes)))
    .build();
```

Inject:
```java
@Value("${rate-limit.auth.ws-ticket.limit:30}")
private long wsTicketLimit;

@Value("${rate-limit.auth.ws-ticket.window-minutes:15}")
private long wsTicketWindowMinutes;
```

Redis key: `reviewflow:ratelimit:auth:wsticket:{userId}`

#### Step 3 — Add properties to `application.properties`

```properties
# ─── WebSocket ticket issuance ────────────────────────────────────────────────
rate-limit.auth.ws-ticket.limit=${RATE_LIMIT_WS_TICKET_LIMIT:30}
rate-limit.auth.ws-ticket.window-minutes=${RATE_LIMIT_WS_TICKET_WINDOW_MIN:15}
```

30 tickets/15 min: generous for legitimate reconnect scenarios, tight enough to prevent automated grinding.

#### Step 4 — Wire in `WsTicketService.issueTicket()`

Add at the start of the method:

```java
RateLimitResult result = rateLimitService.tryConsume(
    String.valueOf(userId),
    RateLimitStrategy.AUTH_WS_TICKET,
    currentUser.getRole());

if (!result.allowed()) {
    log.warn("WS ticket rate limit exceeded for userId={}", hashedUserId);
    throw new TooManyRequestsException(
        "Too many WebSocket ticket requests. Please try again in " +
        result.retryAfterSeconds() + " seconds.",
        result.retryAfterSeconds());
}
```

**Verify:**
```
31st ws-ticket request in 15 min (same user) → 429 TOO_MANY_REQUESTS
30th ws-ticket request → 200 OK (succeeds)
Redis key reviewflow:ratelimit:auth:wsticket:{userId} exists after first call
Reconnect storm (legitimate): 30 reconnects in 15 min = within limit, no 429
```

---

### HIGH-3 · Job Commit — Force API_EXPORT Tier (also resolves MEDIUM-3)

**File:** `infrastructure/ratelimit/RateLimitFilter.java`  
**Issue:** `POST /api/v1/jobs/{jobId}/commit` does not match `/import`, `/export`, or `/pdf` in `isExportPath()` — falls through to `API_WRITE` (60/min) instead of `API_EXPORT` (5/min). Import start correctly maps to `API_EXPORT`; commit does not.

**Additionally:** GET export and PDF routes currently only hit `API_EXPORT` on non-GET methods. GET `/grade-export` and GET `/evaluations/{id}/pdf` are generated on-the-fly and equally expensive — they must also map to `API_EXPORT`.

**Fix — full final `isExportPath()` implementation (replaces existing):**

```java
private boolean isExportPath(String path, String method) {
    if ("GET".equals(method)) {
        // GET export and PDF downloads — generated on-the-fly, same cost as write
        return path.contains("/export")
            || (path.contains("/pdf") && !path.contains("/preview"));
    }

    // Non-GET: import start, export trigger, PDF generation, job commit
    return path.contains("/import")
        || path.contains("/export")
        || (path.contains("/pdf") && !path.contains("/preview"))
        || (path.contains("/jobs/") && path.endsWith("/commit")); // ← NEW
}
```

This single fix resolves both HIGH-3 (job commit) and MEDIUM-3 (GET export/PDF).

**Verify:**
```
POST /api/v1/jobs/{jobId}/commit      → API_EXPORT bucket consumed (not API_WRITE)
6th commit attempt in 1 min           → 429 (not 61st)
GET /api/v1/grade-export/...          → API_EXPORT bucket consumed (not API_READ)
GET /api/v1/evaluations/{id}/pdf      → API_EXPORT bucket consumed
GET /api/v1/evaluations/{id}/pdf/preview → API_READ (excluded — preview is lightweight)
POST /api/v1/assignments/{id}/instructor-scores/import → still API_EXPORT (regression)
```

---

## MEDIUM — Fix in This PR

### MEDIUM-1 · MessagingClientException — Add Retry-After Header

**Files:** `shared/exception/MessagingClientException.java`, `GlobalExceptionHandler.java`  
**Issue:** Messaging 429 responses have no `Retry-After` or `X-RateLimit-*` headers. `RateLimitResult.retryAfterSeconds()` is available but not plumbed through.

**Update `MessagingClientException` — add `retryAfterSeconds` field:**

```java
public class MessagingClientException extends RuntimeException {
    private final long retryAfterSeconds;

    public static MessagingClientException tooManyRequests(
            String message, long retryAfterSeconds) {
        return new MessagingClientException(message, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
```

**Update call sites in `MessagingService` — pass `retryAfterSeconds` from `RateLimitResult`:**

```java
// BEFORE:
throw MessagingClientException.tooManyRequests("Too many messages");

// AFTER:
RateLimitResult result = rateLimitService.tryConsume(...);
if (!result.allowed()) {
    throw MessagingClientException.tooManyRequests(
        "Too many messages. Please wait " + result.retryAfterSeconds() + " seconds.",
        result.retryAfterSeconds());
}
```

**Update `GlobalExceptionHandler` handler:**

```java
@ExceptionHandler(MessagingClientException.class)
public ResponseEntity<ApiResponse<Void>> handleMessagingClientException(
        MessagingClientException ex) {

    ResponseEntity.BodyBuilder builder =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);

    if (ex.getRetryAfterSeconds() > 0) {
        builder.header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        builder.header("X-RateLimit-Remaining", "0");
    }

    return builder.body(ApiResponse.error("TOO_MANY_REQUESTS", ex.getMessage()));
}
```

**Verify:**
```
31st message in 1 min → 429 TOO_MANY_REQUESTS
Response header: Retry-After: {n}
Response header: X-RateLimit-Remaining: 0
```

---

### MEDIUM-2 · RateLimitException — Add Retry-After Header

**Files:** `shared/exception/RateLimitException.java`, `GlobalExceptionHandler.java`  
**Issue:** Generic `RateLimitException` handler returns 429 body only — no `Retry-After` header. Inconsistent with the `HttpErrorJsonWriter` 429 path which does set the header.

**Update `RateLimitException`:**

```java
public class RateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public RateLimitException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
```

**Update `GlobalExceptionHandler` handler:**

```java
@ExceptionHandler(RateLimitException.class)
public ResponseEntity<ApiResponse<Void>> handleRateLimitException(
        RateLimitException ex) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
        .header("X-RateLimit-Remaining", "0")
        .body(ApiResponse.error("TOO_MANY_REQUESTS", ex.getMessage()));
}
```

**Update all `RateLimitException` throw sites** to pass `retryAfterSeconds` from the `RateLimitResult`. If `retryAfterSeconds` is unavailable at a throw site, default to `60`.

**Verify:**
```
Any RateLimitException thrown → 429 with Retry-After header set
Consistent with HttpErrorJsonWriter 429 output (both set Retry-After)
```

---

### MEDIUM-3 · GET Export/PDF — API_EXPORT Tier

**Already resolved by HIGH-3.** The updated `isExportPath()` handles both GET and non-GET cases. No additional code change needed.

**Verify only:**
```
GET /api/v1/grade-export/...       → API_EXPORT (not API_READ)
GET /api/v1/evaluations/{id}/pdf   → API_EXPORT (not API_READ)
GET /api/v1/evaluations/{id}/pdf/preview → API_READ (not API_EXPORT)
```

---

### MEDIUM-4 · Remove Orphan Legacy `rate-limiter.*` Properties

**File:** `application-prod.properties`  
**Issue:** Prod still contains `rate-limiter.login`, `rate-limiter.token`, and `rate-limiter.upload-block` keys. No `@Value` binding reads `rate-limiter.*`. Operators tuning these env vars see no effect.

**Remove these sections entirely from `application-prod.properties`:**

```properties
# DELETE these lines:
# rate-limiter.login.max-attempts=...
# rate-limiter.login.window-seconds=...
# rate-limiter.token.max-attempts=...
# rate-limiter.token.window-seconds=...
# rate-limiter.upload-block.max-attempts=...
# rate-limiter.upload-block.window-minutes=...
```

**Replace with a canonical key documentation comment:**

```properties
# ─── Rate Limiting (canonical keys — override via env vars) ──────────────────
# Auth:     RATE_LIMIT_AUTH_LOGIN_LIMIT, RATE_LIMIT_AUTH_LOGIN_WINDOW_MIN
# Upload:   RATE_LIMIT_UPLOAD_STUDENT_LIMIT, RATE_LIMIT_UPLOAD_INSTRUCTOR_LIMIT,
#           RATE_LIMIT_UPLOAD_ADMIN_LIMIT
# WS:       RATE_LIMIT_WS_TICKET_LIMIT, RATE_LIMIT_WS_TICKET_WINDOW_MIN
# Messaging: RATE_LIMIT_MSG_SEND_LIMIT, RATE_LIMIT_MSG_CREATE_LIMIT
# See application.properties for full list and defaults
```

**Add to `orchestration/RUNBOOK.md`:**

```
## Rate Limit Tuning

All rate limits use rate-limit.* properties (NOT rate-limiter.*).
Legacy rate-limiter.* properties were removed in the RL hardening pass — they had no effect.
Canonical env vars are documented in application.properties comments.
```

**Verify:**
```
grep "rate-limiter\." application-prod.properties → zero results
RUNBOOK.md documents canonical rate-limit.* env var names
```

---

## New Properties Summary

```properties
# ─── Upload rate limiting ─────────────────────────────────────────────────────
rate-limit.upload.student.limit=${RATE_LIMIT_UPLOAD_STUDENT_LIMIT:10}
rate-limit.upload.instructor.limit=${RATE_LIMIT_UPLOAD_INSTRUCTOR_LIMIT:30}
rate-limit.upload.admin.limit=${RATE_LIMIT_UPLOAD_ADMIN_LIMIT:60}
rate-limit.upload.window-hours=${RATE_LIMIT_UPLOAD_WINDOW_HOURS:1}

# ─── WebSocket ticket issuance ────────────────────────────────────────────────
rate-limit.auth.ws-ticket.limit=${RATE_LIMIT_WS_TICKET_LIMIT:30}
rate-limit.auth.ws-ticket.window-minutes=${RATE_LIMIT_WS_TICKET_WINDOW_MIN:15}
```

---

## Verification Checklist

Run in full before opening the PR.

### HIGH
```
[ ] STUDENT: 11th submission upload in 1hr → 429 TOO_MANY_REQUESTS with Retry-After
[ ] INSTRUCTOR: 31st upload in 1hr → 429
[ ] ADMIN: 61st upload in 1hr → 429
[ ] Avatar upload (any role): governed by API_WRITE, not UPLOAD_BLOCK
[ ] reviewflow.upload.block.rate_limited incremented on each rejection
[ ] Upload 429: Retry-After header present
[ ] Redis key reviewflow:ratelimit:upload:{userId} exists after first upload
[ ] rate-limiter.upload-block.* absent from application-prod.properties

[ ] 31st ws-ticket request in 15 min (same user) → 429 TOO_MANY_REQUESTS
[ ] 30th ws-ticket request → 200 OK
[ ] Redis key reviewflow:ratelimit:auth:wsticket:{userId} exists after first call
[ ] WsTicketService does not bypass limit via direct internal call

[ ] POST /jobs/{jobId}/commit → API_EXPORT bucket consumed (not API_WRITE)
[ ] 6th commit attempt in 1 min → 429 (not 61st)
[ ] GET /grade-export/... → API_EXPORT (not API_READ)
[ ] GET /evaluations/{id}/pdf → API_EXPORT (not API_READ)
[ ] GET /evaluations/{id}/pdf/preview → API_READ (not API_EXPORT)
[ ] POST /instructor-scores/import → still API_EXPORT (regression check)
```

### MEDIUM
```
[ ] Messaging 429: Retry-After header present
[ ] Messaging 429: X-RateLimit-Remaining: 0 header present
[ ] RateLimitException 429: Retry-After header present
[ ] RateLimitException 429: X-RateLimit-Remaining: 0 header present
[ ] All RateLimitException throw sites pass retryAfterSeconds (not omitted)
[ ] GET /pdf (non-preview) → API_EXPORT confirmed (verified from HIGH-3 fix)
[ ] grep "rate-limiter\." application-prod.properties → zero results
[ ] RUNBOOK.md documents canonical rate-limit.* env var names
```

### Regression
```
[ ] Submission upload within limit → proceeds normally (UPLOAD_BLOCK not triggered)
[ ] Auth login rate limiting unchanged (PRD-20 behaviour preserved)
[ ] Messaging send rate limiting unchanged
[ ] Grade export rejects before CPU overload (API_EXPORT tier applied)
[ ] Postman collection: all rate-limit tests still pass
[ ] /actuator/health still UP after these changes
```
