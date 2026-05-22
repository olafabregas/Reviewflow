markdown

# Agent — Security & Auth Hardening

**Status:** Final  
**Author:** Roqeeb Olamide Ayorinde  
**Source:** Security & Auth Hardening Audit Report 2026-05-18 (18 findings)  
**Depends on:** PRD-20 (Bucket4j+Redis), PRD-RateLimit-Hardening, PRD-API-Hardening, PRD-File-Hardening

---

## How to Invoke This Agent

```
@docs/agents/security-hardening.md fix all
```

Other commands:

| Command | What it does |
|---|---|
| `fix all` | Execute CRITICAL → HIGH → MEDIUM → INFO in order |
| `fix critical` | CRITICAL tier only |
| `fix high` | HIGH tier only |
| `fix medium` | MEDIUM tier only |
| `fix info` | INFO tier only |
| `verify` | Run full verification checklist, no code changes |
| `status` | Show which tiers are complete and which remain |

---

## Agent Instructions — Read Before Starting

You are a senior Spring Boot security engineer executing a structured hardening pass on ReviewFlow, a **Spring Boot 4.0.x / Java 21** academic submission and grading platform.

**Rules:**
- Implement in strict priority order: CRITICAL → HIGH → MEDIUM → INFO. Do not skip ahead.
- **Read every file completely before touching it.** Do not guess `@Value` property names, method signatures, or FK column names — verify from the actual code first.
- Do not re-implement findings already covered by other PRDs (see table below).
- Never commit a tier until every item in that tier is verified.
- Postman collection must be updated as part of this pass, not after.

**Already covered — do not re-implement:**

| Finding | Covered in |
|---|---|
| In-memory rate limiting not distributed | PRD-20 (Bucket4j+Redis) |
| No WebSocket CONNECT rate limit | PRD-RateLimit-Hardening HIGH-2 |
| SubmissionController no @PreAuthorize | PRD-API-Hardening P0-3 |
| ClamAV fail-closed on error/timeout | PRD-File-Hardening CRITICAL-1 |

**Files likely touched:**

| File | Change area |
|---|---|
| `application-prod.properties` | Property fixes, feature flags |
| `application.properties` | Base defaults |
| `infrastructure/security/SecurityConfig.java` | RoleHierarchy beans |
| `infrastructure/security/WebSocketAuthInterceptor.java` | SUBSCRIBE validation |
| `submission/service/SubmissionService.java` | IDOR fix |
| `infrastructure/security/JwtAuthenticationFilter.java` | Token claim + bearer guard |
| `infrastructure/security/ActuatorKeyAuthFilter.java` | Constant-time comparison |
| `auth/service/RefreshTokenService.java` | SessionContext cleanup |
| `submission/controller/SubmissionController.java` | @Validated + @Size |
| `auth/service/JwtKeyRegistry.java` | Secret length check |
| `infrastructure/security/StepUpInterceptor.java` | Null principal guard |
| New: `infrastructure/config/SecurityStartupValidator.java` | Fail-fast prod validator |

---

## Locked Decisions

| Decision | Choice |
|---|---|
| SYSTEM_ADMIN role hierarchy | Register `RoleHierarchy` + `DefaultWebSecurityExpressionHandler` beans |
| Swagger in prod | `springdoc.api-docs.enabled=false` + `springdoc.swagger-ui.enabled=false` in prod |
| Legacy JWT tokens | `jwt.allow-legacy-tokens-without-kid=false` in prod |
| Bearer fallback | Disabled in prod via property — cookies only |
| Property alignment | Fix all three mismatches: cookie.secure, cors, fingerprinting |

---

## Execution Protocol for `fix all`

```
STEP 1 — Create fix branch
  git checkout -b fix/security-hardening

STEP 2 — CRITICAL tier
  Read each affected file → apply fix → verify → commit

STEP 3 — HIGH tier (HIGH-1 through HIGH-5)
  Read each affected file → apply fix → verify → commit

STEP 4 — MEDIUM tier (MEDIUM-1 through MEDIUM-6)
  Read each affected file → apply fix → verify → commit

STEP 5 — INFO tier (INFO-1, INFO-2)
  Read each affected file → apply fix → verify → commit

STEP 6 — Run full verification checklist
  Report pass/fail per item

STEP 7 — Final summary
  Total files changed, findings resolved, tiers completed
```

---

## CRITICAL — Fix First

### CRITICAL-1 · application-prod.properties — Three Property Name Mismatches

**Files:** `application-prod.properties`, `AuthCookieIssuer.java`, `SecurityConfig.java`, `WebSocketConfig.java`, `TokenFingerprintingService.java` or `JwtAuthenticationFilter.java`  
**Rules:** RULE-S02, RULE-S08, RULE-S13  
**Issue:** Three security properties in `application-prod.properties` use wrong key names. Java code reads different property names with insecure defaults — result: cookies not Secure, CORS falls back to localhost, fingerprinting disabled, all in production.

**Step 1 — Read the Java `@Value` bindings first. Do not touch properties until you know the exact key names the code reads.**

**Step 2 — Fix `application-prod.properties`:**

```properties
# REMOVE these broken keys:
# cookie.secure=true
# cors.allowed-origins=${CORS_ALLOWED_ORIGINS}
# token.fingerprinting-enabled=true

# ADD the correct keys (verify names against @Value before committing):
app.cookie.secure=true
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS}
security.token.fingerprinting-enabled=true
```

**Step 3 — Scan all other prod property overrides for additional mismatches.**

**Verify:**
```
POST /auth/login → Set-Cookie must contain: Secure; HttpOnly; SameSite=Strict
GET from different origin → CORS allows only CORS_ALLOWED_ORIGINS, not localhost
Request with fingerprint mismatch (different User-Agent) → rejected
```

---

### CRITICAL-2 · Swagger/OpenAPI — Disabled in Production

**File:** `application-prod.properties`  
**Rule:** RULE-S02  
**Issue:** Swagger endpoints are `permitAll()` in `SecurityConfig` with no profile guard. Full API surface visible in production.

```properties
# application-prod.properties:
springdoc.api-docs.enabled=false
springdoc.swagger-ui.enabled=false
```

The `permitAll()` rules in `SecurityConfig` remain unchanged — harmless when endpoints return 404. No `SecurityConfig` change needed.

**Verify:**
```
Prod profile: GET /swagger-ui/index.html → 404 (not 403, not 200)
Prod profile: GET /v3/api-docs           → 404
Local profile: GET /swagger-ui/index.html → 200 (unchanged)
```

---

### CRITICAL-3 · SecurityStartupValidator — Fail-Fast on Prod Misconfiguration

**File:** New `infrastructure/config/SecurityStartupValidator.java`  
**Rules:** RULE-S08, RULE-S02, RULE-S13  
**Issue:** Property fixes are one-line changes; an env var override can silently undo them. Without a startup validator, a misconfigured deployment ships with open CORS or non-Secure cookies and no alert.

```java
@Component
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class SecurityStartupValidator {

    @Value("${app.cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${security.token.fingerprinting-enabled:false}")
    private boolean fingerprintingEnabled;

    @PostConstruct
    public void validate() {
        List violations = new ArrayList<>();

        if (corsAllowedOrigins.isBlank()) {
            violations.add(
                "app.cors.allowed-origins must be set in production " +
                "(CORS_ALLOWED_ORIGINS env var is empty)");
        }
        Arrays.stream(corsAllowedOrigins.split(","))
            .map(String::trim)
            .filter(o -> o.equals("*") || o.equals("*://*") || o.contains("localhost"))
            .findAny()
            .ifPresent(bad -> violations.add(
                "app.cors.allowed-origins must not contain '*' or 'localhost' " +
                "in production. Found: " + bad));

        if (!cookieSecure) {
            violations.add(
                "app.cookie.secure must be true in production");
        }

        if (!fingerprintingEnabled) {
            violations.add(
                "security.token.fingerprinting-enabled must be true in production");
        }

        if (!violations.isEmpty()) {
            String message = "PRODUCTION SECURITY MISCONFIGURATION — " +
                "Application startup aborted:\n" +
                violations.stream()
                    .map(v -> "  ✗ " + v)
                    .collect(Collectors.joining("\n"));
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.info("Security startup validation passed (prod profile) ✓");
    }
}
```

**Verify:**
```
Prod + CORS_ALLOWED_ORIGINS=*           → startup fails: "must not contain '*'"
Prod + CORS_ALLOWED_ORIGINS=(empty)     → startup fails: "must be set"
Prod + CORS_ALLOWED_ORIGINS=localhost:* → startup fails: "must not contain 'localhost'"
Prod + app.cookie.secure=false          → startup fails: "must be true"
Prod + fingerprinting disabled          → startup fails: "must be true"
Prod + all correct settings             → logs "Security startup validation passed ✓"
Local/test profile                      → validator not executed, no startup impact
```

---

## HIGH — Fix Before Production

### HIGH-1 · SubmissionService — Instructor IDOR on getSubmission

**File:** `submission/service/SubmissionService.java` line 399–402  
**Rule:** RULE-S16  
**Issue:** Instructor role returns any submission by ID without verifying the instructor teaches the assignment's course. Any instructor can read arbitrary students' submissions across all courses.

**Read `GradeCalculationService.calculateStudentOverview` first — mirror that course-instructor check exactly.**

```java
if (role == UserRole.INSTRUCTOR) {
    Long courseId = sub.getAssignment().getCourse().getId();
    boolean teaches = courseInstructorRepository
        .existsByCourseIdAndUserId(courseId, userId);
    if (!teaches) {
        throw new ForbiddenException(
            "You do not have access to this submission");
    }
    return sub;
}
```

**Verify:**
```
Instructor A teaches CS401, requests CS402 submission → 403 FORBIDDEN
Instructor A teaches CS401, requests CS401 submission → 200 OK
ADMIN role                                            → no course check required
STUDENT role                                          → existing check unchanged
```

---

### HIGH-2 · SubmissionService — Instructor IDOR on getVersionHistory

**File:** `submission/service/SubmissionService.java` line 438–441  
**Rule:** RULE-S16  
**Issue:** Same missing course-instructor check for `getVersionHistory`. Identical vulnerability.

Apply the same `courseInstructorRepository.existsByCourseIdAndUserId` check as HIGH-1 to the `role == INSTRUCTOR` branch in `getVersionHistory`.

**Verify:**
```
Instructor A requests CS402 version history → 403 FORBIDDEN
Instructor A requests CS401 version history → 200 OK
```

---

### HIGH-3 · SecurityConfig — RoleHierarchy + ExpressionHandler Beans

**File:** `infrastructure/security/SecurityConfig.java`  
**Rule:** RULE-S09  
**Issue:** No `RoleHierarchy` bean registered. `hasRole('ADMIN')` in `@PreAuthorize` blocks `SYSTEM_ADMIN` because Spring Security treats roles as flat without explicit hierarchy.

**Two beans are required.** Without `DefaultWebSecurityExpressionHandler`, the `RoleHierarchy` bean exists but `@PreAuthorize` SpEL expressions do not use it — only URL-based `HttpSecurity` rules do.

```java
@Bean
public RoleHierarchy roleHierarchy() {
    RoleHierarchyImpl hierarchy = new RoleHierarchyImpl();
    hierarchy.setHierarchy("""
        ROLE_SYSTEM_ADMIN > ROLE_ADMIN
        ROLE_ADMIN > ROLE_INSTRUCTOR
        ROLE_INSTRUCTOR > ROLE_STUDENT
        """);
    return hierarchy;
}

@Bean
public DefaultWebSecurityExpressionHandler expressionHandler(
        RoleHierarchy roleHierarchy) {
    DefaultWebSecurityExpressionHandler handler =
        new DefaultWebSecurityExpressionHandler();
    handler.setRoleHierarchy(roleHierarchy);
    return handler;
}
```

No `@PreAuthorize` annotations need changing. If `@PreAuthorize` expressions still do not see the hierarchy after adding these beans, verify `@EnableMethodSecurity` is present and that the `expressionHandler` bean is wired to the method security configuration.

**Verify:**
```
SYSTEM_ADMIN calls GET /admin/users           → 200 (not 403)
SYSTEM_ADMIN calls POST /courses              → 200 (not 403)
ADMIN calls SYSTEM_ADMIN-only endpoint        → 403 (hierarchy is downward only)
STUDENT calls INSTRUCTOR endpoint             → 403 (unchanged)
All existing @PreAuthorize checks still pass
```

---

### HIGH-4 · WebSocketAuthInterceptor — Validate SUBSCRIBE Destinations

**File:** `infrastructure/security/WebSocketAuthInterceptor.java` line 40–41  
**Rule:** RULE-S12  
**Issue:** Only `CONNECT` frames are authenticated. After a valid CONNECT, a client can `SUBSCRIBE` to arbitrary destinations including other users' queues.

**Read `JwtHandshakeInterceptor` or `authenticateConnect` first — confirm what `principal.getName()` returns (raw userId string, e.g. `"42"`).**

```java
@Override
public Message preSend(Message message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null) return message;

    StompCommand command = accessor.getCommand();

    if (StompCommand.CONNECT.equals(command)) {
        authenticateConnect(accessor); // existing — unchanged

    } else if (StompCommand.SUBSCRIBE.equals(command)) {
        validateSubscribeDestination(accessor);
    }

    return message;
}

private void validateSubscribeDestination(StompHeaderAccessor accessor) {
    Principal principal = accessor.getUser();
    if (principal == null) {
        throw new MessagingException("Not authenticated");
    }

    String destination = accessor.getDestination();
    if (destination == null) return;

    if (destination.startsWith("/user/")) {
        String destinationUserId = extractUserIdFromDestination(destination);
        String principalName = principal.getName();

        if (!principalName.equals(destinationUserId)) {
            log.warn("WebSocket SUBSCRIBE to foreign destination: " +
                     "principal={} destination={}", principalName, destination);
            throw new MessagingException(
                "Cannot subscribe to another user's destination");
        }
    }
    // /topic/** destinations are public — no user restriction
}

private String extractUserIdFromDestination(String destination) {
    // /user/{userId}/queue/messages → parts[2] = userId
    String[] parts = destination.split("/");
    if (parts.length >= 3) return parts[2];
    return null;
}
```

**Verify:**
```
userId=42, SUBSCRIBE to /user/42/queue/messages → allowed
userId=42, SUBSCRIBE to /user/99/queue/messages → rejected (MessagingException)
userId=42, SUBSCRIBE to /topic/announcements    → allowed (public topic)
CONNECT without SUBSCRIBE                        → unchanged
```

---

### HIGH-5 · Token Fingerprinting — Property

Resolved by CRITICAL-1. `security.token.fingerprinting-enabled=true` added to `application-prod.properties` as part of the three-property fix. No additional code change needed.

**Verify:** Covered in CRITICAL-1 verification block.

---

## MEDIUM — Fix in This PR

### MEDIUM-1 · JwtAuthenticationFilter — Reject Tokens Without ver Claim

**File:** `infrastructure/security/JwtAuthenticationFilter.java` line 87–94  
**Rule:** RULE-S03  
**Issue:** Token version validation is skipped when `ver` claim is absent. Tokens without `ver` bypass force-logout and password-change invalidation until natural expiry.

```java
String verClaim = claims.get("ver", String.class);
Long userId = claims.get("userId", Long.class);

if (userId == null || verClaim == null) {
    if (!allowLegacyTokensWithoutKid) {
        log.warn("Rejecting token with missing ver or userId claim from ip={}",
            ipExtractor.extract(request));
        httpErrorJsonWriter.writeError(response, 401, "INVALID_TOKEN",
            "Token is missing required claims");
        return;
    }
    log.debug("Legacy token without ver claim — allowing (legacy mode)");
}
```

**`@Value` binding:**
```java
@Value("${jwt.allow-legacy-tokens-without-kid:true}")
private boolean allowLegacyTokensWithoutKid;
```

**`application-prod.properties` addition:**
```properties
jwt.allow-legacy-tokens-without-kid=false
```

**Verify:**
```
Prod: token without ver claim → 401 INVALID_TOKEN
Prod: token with ver claim    → processed normally
Local: token without ver claim → allowed with debug log
```

---

### MEDIUM-2 · ActuatorKeyAuthFilter — Constant-Time Key Comparison

**File:** `infrastructure/security/ActuatorKeyAuthFilter.java` line 60  
**Rule:** RULE-S11  
**Issue:** `providedKey.equals(actuatorInternalKey)` exits early on first differing character — timing analysis can reveal key length and prefix.

```java
// BEFORE:
if (providedKey.equals(actuatorInternalKey)) {

// AFTER:
byte[] provided = providedKey.getBytes(StandardCharsets.UTF_8);
byte[] expected = actuatorInternalKey.getBytes(StandardCharsets.UTF_8);
if (MessageDigest.isEqual(provided, expected)) {
```

**Verify:**
```
Valid actuator key   → 200 OK (unchanged)
Invalid actuator key → 401 (unchanged)
```

---

### MEDIUM-3 · RefreshTokenService — Clear SessionContext on Family Revocation

**File:** `auth/service/RefreshTokenService.java` line 98–108  
**Rule:** RULE-S04  
**Issue:** On refresh token reuse detection, `revokeActiveTokensInFamily` revokes all tokens but `SessionContext` rows are not deleted. The attacker's device remains visible in the user's active session list.

**Read `SessionContext` entity and `SessionContextRepository` first — verify the FK column name for `familyId` before writing this fix.**

```java
@Transactional
public void handleReuseDetection(String familyId) {
    refreshTokenRepository.revokeAllByFamilyId(familyId);          // existing
    sessionContextRepository.deleteByFamilyId(familyId);           // NEW
    eventPublisher.publishEvent(
        new RefreshTokenReuseDetectedEvent(familyId, Instant.now()));
}
```

Both operations must be in the same `@Transactional` boundary — if token revocation succeeds but session deletion fails, the attacker's device still appears in the session list.

**Verify:**
```
Reuse detected → all tokens in family revoked (existing)
Reuse detected → session_context rows for family deleted in same TX
After reuse, user's active session list does not show attacker's device
Family token revocation failure → session context not partially deleted (rollback)
```

---

### MEDIUM-4 · SubmissionController — @Valid on changeNote

**File:** `submission/controller/SubmissionController.java` line 73–77  
**Rule:** RULE-S14  
**Issue:** `@RequestParam String changeNote` on multipart upload has no Bean Validation. A 10,000-character `changeNote` is accepted without constraint.

**Add `@Validated` at class level (required for parameter-level validation):**
```java
@RestController
@Validated
public class SubmissionController {
```

**Add constraints on `changeNote`:**
```java
// AFTER:
public ResponseEntity<ApiResponse> upload(
        @PathVariable String assignmentId,
        @RequestParam @Size(max = 500, message = "Change note must not exceed 500 characters")
            @NotBlank(message = "Change note is required") String changeNote,
        @RequestParam MultipartFile file) {
```

**Verify:**
```
changeNote of 501 chars → 400 VALIDATION_ERROR
changeNote blank        → 400 VALIDATION_ERROR
changeNote valid        → proceeds normally
```

---

### MEDIUM-5 · JwtKeyRegistry — Minimum Secret Length on Startup

**File:** `auth/service/JwtKeyRegistry.java` line 25  
**Rule:** RULE-S01  
**Issue:** Only checks non-blank. `Keys.hmacShaKeyFor` accepts any length — a 1-byte secret is technically valid but cryptographically useless for HS256 (requires 256-bit / 32-byte minimum).

```java
@PostConstruct
public void validateSecret() {
    if (jwtSecret == null || jwtSecret.isBlank()) {
        throw new IllegalStateException(
            "JWT_SECRET must be set (REVIEWFLOW_JWT_SECRET env var)");
    }

    byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
    if (secretBytes.length < 32) {
        throw new IllegalStateException(
            "JWT_SECRET must be at least 32 bytes (256 bits) for HS256. " +
            "Current length: " + secretBytes.length + " bytes. " +
            "Generate a secure secret with: openssl rand -base64 32");
    }
}
```

**`application.properties` — add documentation comment:**
```properties
# JWT_SECRET must be at least 32 bytes (256 bits) for HS256
# Generate: openssl rand -base64 32
```

**Verify:**
```
JWT_SECRET of 31 bytes → startup fails with clear message
JWT_SECRET of 32+ bytes → starts normally
Empty JWT_SECRET → existing check triggers (no regression)
```

---

### MEDIUM-6 · JwtAuthenticationFilter — Disable Bearer Fallback in Prod

**File:** `infrastructure/security/JwtAuthenticationFilter.java`  
**Rule:** RULE-S01  
**Issue:** Access token accepted from `Authorization: Bearer` header in addition to cookies. In prod, cookies-only is correct — Bearer header increases XSS/log leakage surface.

```java
@Value("${security.jwt.allow-bearer-header:true}")
private boolean allowBearerHeader;

// In token extraction logic:
if (allowBearerHeader) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        return authHeader.substring(7);
    }
}
// Fall through to cookie extraction
```

**`application-prod.properties`:**
```properties
security.jwt.allow-bearer-header=false
```

**`application.properties` (base default):**
```properties
security.jwt.allow-bearer-header=${SECURITY_JWT_ALLOW_BEARER_HEADER:true}
```

Verify no Postman prod environment request relies on `Authorization: Bearer` — all prod tests must use cookie-based auth.

**Verify:**
```
Prod: request with Authorization: Bearer {valid_token} → 401 (bearer ignored)
Prod: request with cookie                              → 200 OK (unchanged)
Local: both bearer and cookie work (allowBearerHeader=true default)
```

---

## INFO — Fix in This PR

### INFO-1 · Legacy kid-less Tokens — Disabled in Prod

Handled by MEDIUM-1: `jwt.allow-legacy-tokens-without-kid=false` added to `application-prod.properties`. No additional change.

---

### INFO-2 · StepUpInterceptor — 401 When Unauthenticated

**File:** `infrastructure/security/StepUpInterceptor.java` line 40–42  
**Rule:** RULE-S05  
**Issue:** If `@RequiresStepUp` were hit without prior auth (misconfiguration), the interceptor passes through without blocking.

```java
// StepUpInterceptor.preHandle() — add at top:
Principal principal = request.getUserPrincipal();
if (principal == null) {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    return false;
}
// ... existing step-up validation logic unchanged
```

**Verify:**
```
Unauthenticated request to @RequiresStepUp endpoint → 401 (not passthrough)
Authenticated + step-up valid                       → 200 (unchanged)
Authenticated + step-up expired                     → 403 STEP_UP_REQUIRED (unchanged)
```

---

## Verification Checklist

Run this after completing all tiers. Every item must pass before the PR is opened.

### CRITICAL — Property Mismatches + Swagger + Startup Validator
```
[ ] Prod cookies have Secure flag (check Set-Cookie response header)
[ ] Prod cookies have HttpOnly flag
[ ] Prod cookies have SameSite=Strict
[ ] CORS allows only CORS_ALLOWED_ORIGINS value (not localhost)
[ ] Token fingerprinting rejects request with wrong User-Agent in prod
[ ] Swagger: GET /swagger-ui/index.html in prod → 404
[ ] Swagger: GET /v3/api-docs in prod → 404
[ ] Swagger: accessible locally (unchanged)
[ ] No other property key mismatches between prod properties and Java @Value bindings

SecurityStartupValidator:
[ ] Prod + CORS_ALLOWED_ORIGINS=* → startup fails
[ ] Prod + empty CORS_ALLOWED_ORIGINS → startup fails
[ ] Prod + localhost in CORS origins → startup fails
[ ] Prod + app.cookie.secure=false → startup fails
[ ] Prod + fingerprinting disabled → startup fails
[ ] Prod + all correct → logs "Security startup validation passed ✓"
[ ] Local/test profile → validator does not execute
[ ] SecurityStartupValidator annotated @Profile("prod")
```

### HIGH — IDOR and Role Hierarchy
```
[ ] Instructor A requests CS402 submission → 403 FORBIDDEN
[ ] Instructor A requests CS401 submission → 200 OK
[ ] Instructor A requests CS402 version history → 403 FORBIDDEN
[ ] ADMIN accesses all instructor-level endpoints (RoleHierarchy)
[ ] SYSTEM_ADMIN accesses all admin-level endpoints (RoleHierarchy)
[ ] ADMIN cannot access SYSTEM_ADMIN-only endpoints (hierarchy is downward only)
[ ] Existing @PreAuthorize annotations unchanged and still pass
[ ] DefaultWebSecurityExpressionHandler bean wired to RoleHierarchy
[ ] SUBSCRIBE to own /user/{id}/... destination → allowed
[ ] SUBSCRIBE to other user's /user/{id}/... destination → rejected
[ ] SUBSCRIBE to /topic/** → allowed
```

### MEDIUM
```
[ ] Prod: token without ver claim → 401 INVALID_TOKEN
[ ] Local: token without ver claim → allowed with debug log
[ ] ActuatorKeyAuthFilter: valid key → 200; invalid key → 401
[ ] ActuatorKeyAuthFilter: uses MessageDigest.isEqual (not String.equals)
[ ] Family reuse → session_context rows deleted in same TX as token revocation
[ ] Attacker device absent from active session list after reuse detection
[ ] changeNote > 500 chars → 400 VALIDATION_ERROR
[ ] changeNote blank → 400 VALIDATION_ERROR
[ ] JWT_SECRET < 32 bytes → startup failure with clear message
[ ] JWT_SECRET = 32 bytes → starts normally
[ ] Prod: Bearer header ignored (cookies only)
[ ] Local: Bearer header still works
```

### INFO
```
[ ] StepUpInterceptor: unauthenticated request → 401 (not passthrough)
```

### Regression
```
[ ] Login still works (cookie issued correctly with prod flags)
[ ] Refresh token rotation still works
[ ] Force logout still works
[ ] Step-up on grade import still works
[ ] WebSocket CONNECT still works for legitimate clients
[ ] All Postman tests pass with prod-profile equivalent settings
[ ] No new 500 errors across the test suite
```

---

## Summary of All Changes

| File | Change |
|---|---|
| `application-prod.properties` | Fix 3 property mismatches; add `springdoc.*.enabled=false`; add `jwt.allow-legacy-tokens-without-kid=false`; add `security.jwt.allow-bearer-header=false`; remove orphan keys |
| `application.properties` | Add `security.jwt.allow-bearer-header=${...:true}`; add JWT secret length comment |
| `SecurityConfig.java` | Add `RoleHierarchy` bean + `DefaultWebSecurityExpressionHandler` bean |
| `WebSocketAuthInterceptor.java` | Add SUBSCRIBE destination validation |
| `SubmissionService.java` | Add `courseInstructorRepository` check on `getSubmission` and `getVersionHistory` for INSTRUCTOR role |
| `JwtAuthenticationFilter.java` | Reject tokens without `ver` claim when legacy mode disabled; add bearer header property guard |
| `ActuatorKeyAuthFilter.java` | Replace `String.equals` with `MessageDigest.isEqual` |
| `RefreshTokenService.java` | Delete `SessionContext` rows in same TX as family revocation |
| `SubmissionController.java` | Add `@Validated` at class level; `@Size(max=500) @NotBlank` on `changeNote` |
| `JwtKeyRegistry.java` | Add minimum 32-byte length check in `@PostConstruct` |
| `StepUpInterceptor.java` | Add 401 guard when principal is null |
| New: `SecurityStartupValidator.java` | `@Profile("prod")` + `@PostConstruct` fail-fast validator |