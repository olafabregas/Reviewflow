# Agent — Observability & Alerting Hardening

**Status:** Final  
**Author:** Roqeeb Olamide Ayorinde  
**Source:** Observability & Alerting Audit Report 2026-05-18 (22 findings)  
**Tier:** 3 — production operability  
**Migration:** None

---

## How to Invoke This Agent

```
@docs/agents/observability-hardening.md fix all
```

Other commands:

| Command | What it does |
|---|---|
| `fix all` | Execute HIGH → MEDIUM → INFO in order |
| `fix high` | HIGH tier only |
| `fix medium` | MEDIUM tier only |
| `verify` | Run full verification checklist, no code changes |
| `status` | Show which tiers are complete and which remain |

---

## Already Covered — Do Not Re-Implement

These findings are handled in other hardening PRDs. **Verify they exist in the codebase before starting this pass. Do not duplicate.**

| Finding | Covered in |
|---|---|
| `reviewflow.job.failed` counter | PRD-Async-Hardening HIGH-4 |
| SSE terminal failure `pushFailed()` | PRD-Async-Hardening HIGH-4 |
| `JobProgressEvent` status + errorMessage | PRD-Async-Hardening MEDIUM-10 |
| `PdfGenerationListener` `log.error(..., e)` | PRD-Async-Hardening MEDIUM-5 |
| `EmailEventListener` `recordEmailFailed()` + full stack | PRD-Async-Hardening MEDIUM-3 |
| `reviewflow.async.rejected` metrics | PRD-Async-Hardening HIGH-1/2/3/4 |
| `reviewflow.grade.aggregate.failed` | PRD-Async-Hardening MEDIUM-4 |
| `ClamAvScanService` `log.error(..., e)` | PRD-File-Hardening CRITICAL-1 |
| `reviewflow.clamav.scan.error/clean/detected` | PRD-File-Hardening CRITICAL-1 |

---

## Agent Instructions — Read Before Starting

You are a senior Spring Boot engineer executing a structured observability hardening pass on ReviewFlow, a **Spring Boot 4.0.x / Java 21** academic submission and grading platform.

**Rules:**
- Work in priority order: HIGH → MEDIUM → INFO. Do not skip ahead.
- **Read every file before touching it.**
- Verify all "Already Covered" items exist in the codebase before implementing anything — do not duplicate.
- `SecurityMetrics.java` must be deleted as part of MEDIUM-5. The project must compile with zero references to it after deletion.
- INFO items are optional for Phase 1 — complete HIGH + MEDIUM first.

**Files likely touched:**

| File | Path |
|---|---|
| SystemService | `system/service/SystemService.java` |
| CacheConfig | `infrastructure/config/CacheConfig.java` |
| RedisHealthConfig | `infrastructure/config/RedisHealthConfig.java` (new) |
| ReviewFlowMetrics | `infrastructure/monitoring/ReviewFlowMetrics.java` |
| SecurityMetrics | `infrastructure/monitoring/SecurityMetrics.java` ← **DELETE** |
| AuthService | `auth/service/AuthService.java` |
| LoginLockoutService | `auth/service/LoginLockoutService.java` |
| JwtAuthenticationFilter | `infrastructure/security/JwtAuthenticationFilter.java` |
| MdcFilter | `infrastructure/security/MdcFilter.java` |
| S3Service | `infrastructure/storage/S3Service.java` |
| CsvImportService | `grading/service/CsvImportService.java` |
| PdfGenerationListener | `evaluation/listener/PdfGenerationListener.java` |
| EmailEventListener | `infrastructure/email/EmailEventListener.java` |
| NotificationEventListener | `notification/event/NotificationEventListener.java` |
| SubmissionService | `submission/service/SubmissionService.java` |
| CourseContextInterceptor | `infrastructure/web/CourseContextInterceptor.java` (new) |
| WebMvcConfig | `infrastructure/config/WebMvcConfig.java` (or equivalent) |
| SystemController | `system/controller/SystemController.java` |
| application.properties | `src/main/resources/application.properties` |
| RUNBOOK.md | `orchestration/RUNBOOK.md` |

---

## Locked Decisions

| Decision | Choice |
|---|---|
| Stub metrics | Wire real HikariCP + Caffeine data now |
| Redis health | Separate indicator — does NOT affect `/actuator/health` UP/DOWN |
| SecurityMetrics | Delete immediately — migrate all call sites to `ReviewFlowMetrics` |
| MDC courseId/assignmentId | Add via interceptor on course-scoped routes |
| X-Trace-Id | Add `response.setHeader("X-Trace-Id", traceId)` in `MdcFilter` |

---

## Execution Protocol for `fix all`

```
STEP 1 — Pre-flight
  Verify all "Already Covered" items exist in codebase
  Note which are missing — do not implement here, flag for async/file hardening PRDs

STEP 2 — Create fix branch
  git checkout -b fix/observability-hardening

STEP 3 — HIGH tier (HIGH-1 through HIGH-3)
  Verify CRITICAL checklist before advancing to MEDIUM

STEP 4 — MEDIUM tier (MEDIUM-1 through MEDIUM-10 in order)
  MEDIUM-5 (SecurityMetrics deletion) must leave project compiling
  Verify MEDIUM checklist before advancing

STEP 5 — INFO tier (optional Phase 1)

STEP 6 — Update RUNBOOK.md with alerting thresholds

STEP 7 — Run full verification checklist
```

---

## HIGH — Fix Before Production

### HIGH-1 · SystemService — Wire Real HikariCP + Caffeine Metrics

**File:** `system/service/SystemService.java` (around line 422–437)  
**Issue:** `collectAndPushMetrics()` pushes hardcoded zeros for DB pool connections, cache hit rates, and recent security events. The SYSTEM_ADMIN dashboard shows false health indicators.

**Stubs to replace:**

| Stub field | Live source |
|---|---|
| `db.activeConnections` | HikariCP MXBean `ActiveConnections` |
| `db.idleConnections` | HikariCP MXBean `IdleConnections` |
| `db.maxConnections` | HikariCP MXBean `TotalConnections` |
| `cache.*HitRate` | Caffeine `CacheStats.hitRate()` per named cache |
| `recentSecurityEvents` | Micrometer counter snapshot |

**Fix — wire HikariCP via MXBean:**

```java
@Autowired
private DataSource dataSource;

private HikariPoolMXBean getHikariMXBean() {
    if (dataSource instanceof HikariDataSource hikari) {
        return hikari.getHikariPoolMXBean();
    }
    return null;
}

// In collectAndPushMetrics() — replace hardcoded zeros:
HikariPoolMXBean pool = getHikariMXBean();
int active = pool != null ? pool.getActiveConnections() : -1;
int idle   = pool != null ? pool.getIdleConnections()   : -1;
int max    = pool != null ? pool.getTotalConnections()   : -1;
// -1 signals "unavailable" — client renders as "N/A" not "0"
```

**Fix — wire Caffeine stats per cache:**

```java
@Autowired
private CacheManager cacheManager;

private double getCacheHitRate(String cacheName) {
    org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
    if (cache == null) return -1.0;
    Object nativeCache = cache.getNativeCache();
    if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache<?,?> caffeine) {
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = caffeine.stats();
        return stats.hitRate(); // NaN if no requests yet — see safeRate() below
    }
    return -1.0;
}

// NaN guard (new cache, no requests yet):
private double safeRate(double rate) {
    return Double.isNaN(rate) || Double.isInfinite(rate) ? 0.0 : rate;
}

// In collectAndPushMetrics():
double adminStatsHit  = safeRate(getCacheHitRate("adminStats"));
double userCoursesHit = safeRate(getCacheHitRate("userCourses"));
// etc. per cache used in dashboard — read CacheConfig for all named caches
```

**Prerequisite — read `CacheConfig` before modifying `SystemService`:** Every Caffeine cache builder must have `.recordStats()` — without it, `stats()` returns all zeros. Add `.recordStats()` to any builder that does not have it.

**Fix — wire recent security events from Micrometer:**

```java
@Autowired
private MeterRegistry meterRegistry;

private long getRecentSecurityEvents() {
    try {
        Counter failedLogins = meterRegistry.find("reviewflow.security.login")
            .tag("result", "failed")
            .counter();
        Counter lockouts = meterRegistry.find("reviewflow.security.lockout")
            .counter();
        double total = (failedLogins != null ? failedLogins.count() : 0)
                     + (lockouts    != null ? lockouts.count()    : 0);
        return Math.round(total);
    } catch (Exception e) {
        return -1L; // unavailable
    }
}
```

**Verify:**
```
WebSocket push to SYSTEM_ADMIN dashboard:
  db.activeConnections → real HikariCP value (not 0)
  db.idleConnections   → real HikariCP value (not 0)
  cache.adminStatsHitRate → real Caffeine hitRate (NaN → 0.0 for new cache)
  recentSecurityEvents → real counter sum (not 0)
  jvm.* → unchanged (already live)
All CacheConfig builders have .recordStats()
```

---

### HIGH-2 · Redis Health Indicator — Separate, Non-Blocking

**Issue:** No health indicator for Redis. Load balancer stays `UP` when Redis is down — fail-open rate limits and broken job UX with no alertable signal.

**Decision:** Redis health is visible at `/actuator/health/redis` (and inside `components` of the top-level response) but does **not** affect the top-level `UP/DOWN`. On a single t3.micro, marking readiness DOWN on a Redis failure equals a full service outage — incorrect for a degraded-but-running state.

**Step 1 — Check if auto-configured first.** Spring Boot auto-configures `RedisHealthIndicator` when `spring-boot-starter-data-redis` is present. Check `application.properties` for `management.health.redis.enabled`. If already registered, skip Step 2.

**Step 2 — Add `RedisHealthConfig.java` if not already present:**

```java
// infrastructure/config/RedisHealthConfig.java
@Configuration
public class RedisHealthConfig {

    @Bean
    public HealthIndicator redisHealthIndicator(
            RedisConnectionFactory connectionFactory) {
        return new RedisHealthIndicator(connectionFactory);
    }
}
```

**Step 3 — Add to `application.properties`:**

```properties
management.health.redis.enabled=true
management.endpoint.health.show-details=when-authorized
```

**Add to `application-prod.properties`:**
```properties
management.health.redis.enabled=true
```

**Expected response shape:**
```json
GET /actuator/health
{
  "status": "UP",
  "components": {
    "db":        { "status": "UP" },
    "redis":     { "status": "DOWN", "details": { "error": "Connection refused" } },
    "diskSpace": { "status": "UP" }
  }
}
```

**Alerting note:** Once CloudWatch Phase 2 is active, alarm on `redis` component health. Until then, `reviewflow.ratelimit.check_failed` rate is the proxy signal for Redis unavailability.

**Verify:**
```
Redis running  → GET /actuator/health/redis → { "status": "UP" }
Redis stopped  → GET /actuator/health/redis → { "status": "DOWN" }
Redis stopped  → GET /actuator/health → { "status": "UP" } (top-level unaffected)
```

---

### HIGH-3 · Unknown-Email Login — Count in Security Metrics

**File:** `auth/service/AuthService.java`  
**Issue:** When `userOpt.isEmpty()` (login for non-existent email), dummy bcrypt runs but `recordFailedLogin()` is not called. Credential stuffing against non-existent emails is invisible in metrics.

**Fix — add `recordLoginResult` with granular tags to `ReviewFlowMetrics`:**

```java
// ReviewFlowMetrics:
public void recordLoginResult(String result) {
    // result values: "success", "failed", "unknown_user", "refresh_failed"
    registry.counter("reviewflow.security.login",
        "result", result).increment();
}
```

**Fix — three call sites in `AuthService`:**

```java
// Success path:
metrics.recordLoginResult("success");

// Wrong password path:
metrics.recordLoginResult("failed");

// Unknown email path (userOpt.isEmpty()):
if (userOpt.isEmpty()) {
    passwordEncoder.matches(request.getPassword(), DUMMY_HASH); // timing-safe — keep
    metrics.recordLoginResult("unknown_user"); // NEW
    rateLimitService.consumeOnFailure(ip, AUTH_LOGIN, null);
    throw new BadCredentialsException("Invalid credentials");
}
```

This allows dashboards to distinguish credential stuffing (high `unknown_user`) from targeted brute force (high `failed`).

**Verify:**
```
Login with non-existent email → reviewflow.security.login{result=unknown_user} incremented
Login with wrong password     → reviewflow.security.login{result=failed} incremented
Login success                 → reviewflow.security.login{result=success} incremented
No user enumeration timing difference between unknown_user and failed paths
```

---

## MEDIUM — Fix in This PR

### MEDIUM-1 · JwtAuthenticationFilter — Invalid Token Counter

**File:** `infrastructure/security/JwtAuthenticationFilter.java`  
**Issue:** Invalid JWT / token version mismatch paths log at DEBUG and consume rate limit, but no `reviewflow.security.token{result=invalid}` counter.

**Add to `ReviewFlowMetrics`:**
```java
public void recordTokenValidation(String result) {
    // result: "valid", "invalid", "fingerprint_mismatch", "rate_limited"
    registry.counter("reviewflow.security.token",
        "result", result).increment();
}
```

**Fix in filter catch block:**
```java
} catch (JwtException | TokenVersionMismatchException e) {
    rateLimitService.consumeOnFailure(ip, AUTH_JWT_FAILURE, null);
    metrics.recordTokenValidation("invalid"); // NEW
    log.debug("JWT validation failed for {}: {}", ip, e.getMessage());
    httpErrorJsonWriter.writeError(response, 401, "TOKEN_REVOKED",
        "Your session has been invalidated. Please log in again.");
    return;
}
```

**Note:** Migrate existing `SecurityMetrics` call sites in this file as part of MEDIUM-5.

**Verify:**
```
Request with invalid JWT → reviewflow.security.token{result=invalid} incremented
Request with expired token → counter incremented
```

---

### MEDIUM-2 · AuthService.refresh — Failure Metric

**File:** `auth/service/AuthService.java`  
**Issue:** Bad/missing refresh token throws `BadCredentialsException` with no security metric.

```java
// AuthService.refresh() — exception path:
} catch (BadCredentialsException e) {
    metrics.recordLoginResult("refresh_failed"); // NEW
    throw e;
}
```

**Verify:**
```
Expired/invalid refresh token → reviewflow.security.login{result=refresh_failed} incremented
```

---

### MEDIUM-3 · LoginLockoutService — Lockout Counter + WARN Log

**File:** `auth/service/LoginLockoutService.java` (around line 48–57)  
**Issue:** Account lockout sets `locked_until` and creates audit `ACCOUNT_LOCKED` but no Micrometer counter and no structured WARN log.

**Add to `ReviewFlowMetrics`:**
```java
public void recordLockout() {
    registry.counter("reviewflow.security.lockout").increment();
}
```

**Fix — add counter + structured WARN at lockout trigger:**
```java
public void recordFailedAttempt(Long userId, String ipAddress) {
    // ... existing increment logic ...

    if (isNowLocked(userId)) {
        log.warn("Account locked userId={} ipAddress={} lockedUntil={}",
            hashidService.encode(userId), ipAddress, computeLockedUntil()); // NEW
        metrics.recordLockout(); // NEW
    }
}
```

**Verify:**
```
Account lockout triggered → reviewflow.security.lockout incremented
Lockout → log.warn with hashed userId and ipAddress in structured MDC
```

---

### MEDIUM-4 · S3Service — Full Throwable in log.error

**File:** `infrastructure/storage/S3Service.java` (lines ~51–172)  
**Issue:** Multiple `log.error("...: {}", key, e.getMessage())` calls — passing `e.getMessage()` as a format arg loses the stack trace in structured logging.

**Scan the file first:** grep for `log.error` in `S3Service.java`. Every occurrence passing `e.getMessage()` as a format argument must be changed.

```java
// BEFORE (loses stack trace):
log.error("S3 upload failed for key {}: {}", key, e.getMessage());

// AFTER (full stack trace in structured log):
log.error("S3 upload failed for key {}", key, e);
```

The format string drops `": {}"` at the end — `e` as the final positional arg is SLF4J's convention for attaching the full throwable.

**Verify:**
```
S3 upload failure → log output contains full exception stack trace
Not → just e.getMessage() string in the log line
```

---

### MEDIUM-5 · SecurityMetrics — Delete and Migrate All Call Sites

**File:** `infrastructure/monitoring/SecurityMetrics.java`  
**Issue:** `SecurityMetrics` and `ReviewFlowMetrics` both register identical meter IDs as `@Component` beans — dual registration, divergent call sites.

**Step 1 — Read `SecurityMetrics.java` fully.** Understand every method and its meter ID.

**Step 2 — Grep for all call sites:**
```bash
grep -r "SecurityMetrics" src/main/java --include="*.java" -l
```

Known call sites from audit:
- `ClamAvScanService` → `securityMetrics.recordClamavInfected()`
- `JwtAuthenticationFilter` → `securityMetrics.*`
- `SubmissionService` → `securityMetrics.*`

**Step 3 — Verify `ReviewFlowMetrics` has an equivalent for every `SecurityMetrics` method.** If missing, add it with the same meter ID and tags — do not create new counter names.

**Step 4 — Update every call site:**
```java
// BEFORE:
@Autowired
private SecurityMetrics securityMetrics;
// ...
securityMetrics.recordClamavInfected();

// AFTER:
@Autowired
private ReviewFlowMetrics metrics;
// ...
metrics.recordMalwareDetected(); // equivalent method in ReviewFlowMetrics
```

**Step 5 — Delete `SecurityMetrics.java`.**

**Step 6 — Verify project compiles with zero references:**
```bash
grep -r "SecurityMetrics" src/main/java → must return zero results
```

**Verify:**
```
grep -r "SecurityMetrics" src/main/java → zero results
Project compiles cleanly
All Micrometer meters previously in SecurityMetrics still appear in /actuator/metrics
No duplicate meter registration errors in startup logs
```

---

### MEDIUM-6 · Wire Dead Metric Call Sites

**Files:** `EmailEventListener.java`, `SubmissionService.java`, `NotificationEventListener.java`, `ReviewFlowMetrics.java`  
**Issue:** `recordEmailSent`, `recordNotificationSent`, `recordSubmissionUploaded` are defined in `ReviewFlowMetrics` but have zero call sites — dead metrics.

**Wire `recordEmailSent` in `EmailEventListener` after successful send:**
```java
// After the try/catch send block — only reached if no exception thrown:
metrics.recordEmailSent(eventType);
// (complement to recordEmailFailed wired in PRD-Async-Hardening MEDIUM-3)
```

**Wire `recordSubmissionUploaded` in `SubmissionService.upload()` after S3 success:**
```java
// After s3Service.putObject() succeeds:
metrics.recordSubmissionUploaded();
```

**Wire `recordNotificationSent` in `NotificationEventListener` after save:**
```java
metrics.recordNotificationSent(notification.getType().name());
```

**Fix `recordNotificationSent` type tag — currently unused (tag passed but not applied):**
```java
// BEFORE (type tag ignored — no breakdown possible):
public void recordNotificationSent(String type) {
    registry.counter("reviewflow.notifications.sent").increment(); // type ignored
}

// AFTER (tag applied):
public void recordNotificationSent(String type) {
    registry.counter("reviewflow.notifications.sent",
        "type", type).increment();
}
```

**Verify:**
```
Email sent successfully → reviewflow.email.sent{type=...} incremented
Submission uploaded → reviewflow.submissions.uploaded incremented
Notification saved → reviewflow.notifications.sent{type=...} incremented
/actuator/metrics lists all three meters
```

---

### MEDIUM-7 · MdcFilter — Add X-Trace-Id Response Header

**File:** `infrastructure/security/MdcFilter.java`  
**Issue:** `MdcFilter` puts `traceId` into MDC for logging but never writes it to the HTTP response. No `X-Trace-Id` header on any API response — impossible to correlate client errors with log lines.

**Fix — set header before `filterChain.doFilter` (set before chain so it survives exceptions):**

```java
// MdcFilter.doFilterInternal():

String traceId   = UUID.randomUUID().toString();
String requestId = UUID.randomUUID().toString();

// Honor incoming X-Trace-Id from upstream gateway if present:
String incomingTraceId = ((HttpServletRequest) request).getHeader("X-Trace-Id");
if (incomingTraceId != null && !incomingTraceId.isBlank()) {
    traceId = incomingTraceId; // propagate upstream trace
}

MDC.put(TRACE_ID, traceId);
MDC.put(REQUEST_ID, requestId);
MDC.put(ENDPOINT, endpoint);
MDC.put(IP_ADDRESS, ipAddress);

// NEW: write to response header so clients/gateways can correlate
((HttpServletResponse) response).setHeader("X-Trace-Id", traceId);

try {
    filterChain.doFilter(request, response);
} finally {
    MDC.clear();
}
```

**Verify:**
```
GET /api/v1/assignments        → response header X-Trace-Id: {uuid} present
POST /api/v1/submissions       → X-Trace-Id present
Request with X-Trace-Id: upstream-id → response echoes upstream-id (not new UUID)
Logs for the request contain matching traceId in MDC
```

---

### MEDIUM-8 · MDC — jobId in Async Workers

**Files:** `grading/service/CsvImportService.java`, `evaluation/listener/PdfGenerationListener.java`  
**Issue:** CSV/PDF workers log `jobId` in message text but do not `MDC.put("jobId", jobId)`. Cannot filter all logs for a single import job in CloudWatch Logs Insights.

**The `finally` block is mandatory** — async threads are reused from the pool. Without `MDC.remove()`, the next task on that thread inherits the previous task's `jobId`.

```java
// CsvImportService.runValidation():
private void runValidation(String jobId, ...) {
    MDC.put("jobId", jobId);
    try {
        // ... existing validation logic ...
    } finally {
        MDC.remove("jobId");
    }
}

// CsvImportService.runCommit():
private void runCommit(String jobId, ...) {
    MDC.put("jobId", jobId);
    try {
        // ... existing commit logic ...
    } finally {
        MDC.remove("jobId");
    }
}

// PdfGenerationListener.handleEvaluationPublished():
@Async("pdfExecutor")
@EventListener
public void handleEvaluationPublished(EvaluationPublishedEvent event) {
    MDC.put("evaluationId", event.getHashedEvaluationId());
    try {
        // ... existing PDF logic ...
    } finally {
        MDC.remove("evaluationId");
    }
}
```

**Verify:**
```
CSV import running → all log lines from csvWorkerExecutor threads contain jobId in MDC
PDF generation → log lines contain evaluationId in MDC
After job completes → MDC key removed, not present in subsequent thread logs
CloudWatch Logs Insights: filter by jobId to trace one full import end-to-end
```

---

### MEDIUM-9 · CourseContextInterceptor — courseId/assignmentId in MDC

**File:** New `infrastructure/web/CourseContextInterceptor.java`  
**Issue:** No `courseId` or `assignmentId` in MDC on course-scoped routes. Cannot filter logs by course in CloudWatch.

```java
// infrastructure/web/CourseContextInterceptor.java

@Component
@RequiredArgsConstructor
public class CourseContextInterceptor implements HandlerInterceptor {

    private final HashidService hashidService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String path = request.getRequestURI();

        extractPathSegment(path, "/courses/")
            .ifPresent(hashedCourseId ->
                MDC.put("courseId", hashedCourseId));

        extractPathSegment(path, "/assignments/")
            .ifPresent(hashedAssignmentId ->
                MDC.put("assignmentId", hashedAssignmentId));

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        MDC.remove("courseId");
        MDC.remove("assignmentId");
    }

    private Optional<String> extractPathSegment(String path, String prefix) {
        int idx = path.indexOf(prefix);
        if (idx == -1) return Optional.empty();

        String after = path.substring(idx + prefix.length());
        int slash = after.indexOf('/');
        String segment = slash == -1 ? after : after.substring(0, slash);

        // Keep as hashed ID — do not decode (MDC should log what clients see)
        if (!segment.isBlank()) return Optional.of(segment);
        return Optional.empty();
    }
}
```

**Register in `WebMvcConfig` (or equivalent config class):**
```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(courseContextInterceptor)
        .addPathPatterns("/api/v1/courses/**", "/api/v1/assignments/**");
}
```

**Verify:**
```
GET /api/v1/courses/abc123/assignments  → MDC contains courseId=abc123
GET /api/v1/assignments/xyz789/submissions → MDC contains assignmentId=xyz789
After response → MDC keys removed (afterCompletion fires)
Log lines for course-scoped requests show courseId in structured JSON
```

---

### MEDIUM-10 · Document getCacheStats Authoritative Source

**File:** `system/controller/SystemController.java`  
**Issue:** `SystemService.getCacheStats()` REST endpoint uses real Caffeine stats. `collectAndPushMetrics()` previously used stub zeros (now fixed in HIGH-1). Document which is authoritative.

```java
@Operation(
    summary = "Get cache statistics",
    description = "Returns real-time Caffeine cache statistics. " +
                  "This is the authoritative REST endpoint for cache metrics. " +
                  "The WebSocket system dashboard also exposes cache data via " +
                  "collectAndPushMetrics() — both use the same live Caffeine source " +
                  "after the OBS01 fix."
)
@GetMapping("/cache/stats")
public ResponseEntity<ApiResponse<List<CacheStatsDto>>> getCacheStats() { ... }
```

---

## INFO — Optional Phase 1

### INFO-1 · ClamAV Health Indicator

When `clamav.enabled=true`, add a custom `HealthIndicator` that pings the ClamAV daemon:

```java
@Component
@ConditionalOnProperty(name = "clamav.enabled", havingValue = "true")
public class ClamAvHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        try {
            boolean reachable = clamAvScanService.ping();
            return reachable ? Health.up().build()
                : Health.down().withDetail("error", "Scanner unreachable").build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
```

If `ClamAvScanService` does not have a `ping()` method, add one that opens a TCP socket to the ClamAV daemon host/port and checks for the `PONG` response.

---

## Alerting Runbook — Add to `orchestration/RUNBOOK.md`

```
## Observability Alerting Thresholds (ReviewFlow)

reviewflow.ratelimit.check_failed > 10/min
  → Check Redis connectivity (fail-open rate limits now in effect)

reviewflow.security.login{result=failed} 3× baseline over 15m
  → Review lockout config + WAF rules

reviewflow.security.login{result=unknown_user} spike
  → Credential stuffing investigation

reviewflow.security.lockout rate > baseline
  → Security notification

reviewflow.clamav.malware.detected ≥ 1
  → Page security; quarantine S3 object immediately

reviewflow.job.failed > 0 sustained 5m
  → Check csvWorkerExecutor pool size and job logs

reviewflow.pdf.generation.failed > 0 sustained 5m
  → Check pdfExecutor pool and CloudFront signed URL config

Redis health DOWN (GET /actuator/health/redis)
  → Ops alert; fail-open rate limits in effect; job creation degraded

## Notes

DO NOT ALERT on WebSocket db.activeConnections == 0 before
verifying HikariCP wiring (was stub zero — now wired via HIGH-1).
Establish a 30-day baseline after deploy before setting thresholds.
```

---

## Verification Checklist

Run in full before opening the PR.

### HIGH
```
[ ] db.activeConnections in WebSocket push = real HikariCP value (not 0)
[ ] db.idleConnections in WebSocket push = real HikariCP value (not 0)
[ ] cache.adminStatsHitRate = real Caffeine stat (not 0.0)
[ ] All CacheConfig builders have .recordStats()
[ ] GET /actuator/health/redis → { "status": "UP" } when Redis running
[ ] GET /actuator/health/redis → { "status": "DOWN" } when Redis stopped
[ ] GET /actuator/health → { "status": "UP" } regardless of Redis state
[ ] Unknown-email login → reviewflow.security.login{result=unknown_user} incremented
[ ] No user enumeration timing difference on unknown vs bad-password path
```

### MEDIUM
```
[ ] Invalid JWT → reviewflow.security.token{result=invalid} incremented
[ ] Bad refresh token → reviewflow.security.login{result=refresh_failed} incremented
[ ] Account lockout → reviewflow.security.lockout incremented
[ ] Account lockout → log.warn with hashed userId and ipAddress
[ ] S3Service: all log.error calls pass 'e' as final arg (not e.getMessage())
[ ] SecurityMetrics.java deleted
[ ] grep -r "SecurityMetrics" src/main/java → zero results
[ ] All former SecurityMetrics call sites use ReviewFlowMetrics
[ ] reviewflow.clamav.* meters still fire correctly (via ReviewFlowMetrics)
[ ] recordEmailSent wired in EmailEventListener after successful send
[ ] recordSubmissionUploaded wired in SubmissionService after S3 success
[ ] recordNotificationSent wired in NotificationEventListener with type tag applied
[ ] recordNotificationSent type tag actually applied (not discarded)
[ ] X-Trace-Id header present on every API response
[ ] X-Trace-Id echoes incoming header when provided by upstream
[ ] jobId in MDC for all log lines in runValidation and runCommit
[ ] evaluationId in MDC for PdfGenerationListener
[ ] MDC.remove() called in finally for all async workers
[ ] GET /api/v1/courses/{id}/... → MDC contains courseId
[ ] MDC keys removed after response (afterCompletion)
[ ] getCacheStats OpenAPI description updated
```

### Regression
```
[ ] Submission upload still works (SecurityMetrics removal did not break FileSecurityValidator)
[ ] JWT validation still works (SecurityMetrics removal did not break JwtAuthenticationFilter)
[ ] ClamAV scan still fires malware counter (now via ReviewFlowMetrics)
[ ] Email still fires-and-forgets (no rethrow added by recordEmailSent wiring)
[ ] Login still works end-to-end (new result=unknown_user counter does not break flow)
[ ] Postman: no new failures across auth + submission + system endpoints
```

---

## Summary of All Changed Files

| File | Change |
|---|---|
| `SystemService.java` | Wire HikariCP MXBean + Caffeine stats + security event counter |
| `CacheConfig.java` | Add `.recordStats()` to all Caffeine builders |
| `RedisHealthConfig.java` | New — `RedisHealthIndicator` bean |
| `application.properties` | `management.health.redis.enabled=true` |
| `ReviewFlowMetrics.java` | Add `recordLoginResult`, `recordLockout`, `recordTokenValidation`; fix `recordNotificationSent` type tag; wire dead metrics |
| `SecurityMetrics.java` | **DELETE** |
| `AuthService.java` | Add `result=unknown_user` on empty-user path; refresh failure metric |
| `LoginLockoutService.java` | Add lockout counter + structured WARN log |
| `JwtAuthenticationFilter.java` | Replace `SecurityMetrics` → `ReviewFlowMetrics`; add `result=invalid` token counter |
| `ClamAvScanService.java` | Replace `SecurityMetrics` → `ReviewFlowMetrics` |
| `SubmissionService.java` | Replace `SecurityMetrics` → `ReviewFlowMetrics`; wire `recordSubmissionUploaded` |
| `S3Service.java` | Fix all `log.error(..., e.getMessage())` → `log.error(..., e)` |
| `MdcFilter.java` | Add `response.setHeader("X-Trace-Id", traceId)`; honor incoming header |
| `CsvImportService.java` | `MDC.put("jobId", jobId)` + `MDC.remove` in finally |
| `PdfGenerationListener.java` | `MDC.put("evaluationId", ...)` + `MDC.remove` in finally |
| `EmailEventListener.java` | Wire `recordEmailSent` after successful send |
| `NotificationEventListener.java` | Wire `recordNotificationSent(type)` with tag |
| `CourseContextInterceptor.java` | New — extracts courseId/assignmentId from path → MDC |
| `WebMvcConfig.java` | Register `CourseContextInterceptor` for course/assignment paths |
| `SystemController.java` | Add OpenAPI description to `getCacheStats` |
| `orchestration/RUNBOOK.md` | Add alerting thresholds and recommendations |
