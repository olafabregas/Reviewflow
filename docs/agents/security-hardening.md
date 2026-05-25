# Agent — Security & Auth Hardening

**Status:** Final  
**Author:** Roqeeb Olamide Ayorinde  
**Source:** Security & Auth Hardening Audit Report 2026-05-18 (18 findings)  
**Tier:** 1 — CRITICAL findings block production deploy  
**Migration:** None (session_context cleanup is application-layer, no schema change)

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
| `verify` | Run full verification checklist, no code changes |
| `status` | Show which tiers are complete and which remain |

---

## Already Covered — Verify Exist, Do Not Re-Implement

Before starting, confirm these are already in the codebase. If any are missing, flag and stop — do not implement them here.

| Finding | Covered in |
|---|---|
| In-memory rate limiting not distributed | PRD-20 (Bucket4j + Redis) |
| No WebSocket CONNECT rate limit | PRD-RateLimit-Hardening HIGH-2 (`AUTH_WS_TICKET`) |
| SubmissionController no `@PreAuthorize` | PRD-API-Hardening P0-3 |
| ClamAV fail-closed on error/timeout | PRD-File-Hardening CRITICAL-1 |

---

## Agent Instructions — Read Before Starting

You are a senior Spring Boot security engineer executing a structured security hardening pass on ReviewFlow, a **Spring Boot 4.0.x / Java 21** academic submission and grading platform.

**Rules:**
- CRITICAL must be fully implemented and verified before any HIGH or MEDIUM work begins.
- **This PRD touches security-critical code. Read every file completely before modifying.**
- Do not guess property names — verify exact `@Value` bindings in Java source, then fix properties to match.
- HIGH-5 is already resolved by CRITICAL-1 — verify only, no code change.
- MEDIUM-3 requires reading `SessionContext` entity and `SessionContextRepository` before writing — do not assume the FK column name.

**Files likely touched:**

| File | Path |
|---|---|
| application-prod.properties | `src/main/resources/application-prod.properties` |
| application.properties | `src/main/resources/application.properties` |
| SecurityConfig | `infrastructure/security/SecurityConfig.java` |
| WebSocketAuthInterceptor | `infrastructure/security/WebSocketAuthInterceptor.java` |
| SubmissionService | `submission/service/SubmissionService.java` |
| JwtAuthenticationFilter | `infrastructure/security/JwtAuthenticationFilter.java` |
| ActuatorKeyAuthFilter | `infrastructure/security/ActuatorKeyAuthFilter.java` |
| RefreshTokenService | `auth/service/RefreshTokenService.java` |
| SessionContextRepository | `auth/repository/SessionContextRepository.java` |
| JwtKeyRegistry | `auth/service/JwtKeyRegistry.java` |
| SubmissionController | `submission/controller/SubmissionController.java` |
| StepUpInterceptor | `infrastructure/security/StepUpInterceptor.java` |

---

## Locked Decisions

| Decision | Choice |
|---|---|
| SYSTEM_ADMIN role hierarchy | Register `RoleHierarchy` + `DefaultWebSecurityExpressionHandler` beans |
| Swagger in prod | `springdoc.api-docs.enabled=false` + `springdoc.swagger-ui.enabled=false` |
| Legacy JWT tokens | `jwt.allow-legacy-tokens-without-kid=false` in prod |
| Bearer fallback | Disabled in prod — cookies only |
| Property alignment | Fix all three mismatches: cookie.secure, cors, fingerprinting |

---

## Execution Protocol for `fix all`

```
STEP 1 — Pre-flight
  Verify all "Already Covered" items exist in codebase
  Flag any missing — do not implement here

STEP 2 — Create fix branch
  git checkout -b fix/security-hardening

STEP 3 — CRITICAL tier (must be verified before advancing)
  CRITICAL-1: Three property mismatches
  CRITICAL-2: Swagger disabled in prod
  Full CRITICAL checklist must pass before HIGH begins

STEP 4 — HIGH tier (HIGH-1 through HIGH-4; HIGH-5 verify only)
  HIGH-1: SubmissionService IDOR on getSubmission
  HIGH-2: SubmissionService IDOR on getVersionHistory
  HIGH-3: SecurityConfig RoleHierarchy beans
  HIGH-4: WebSocketAuthInterceptor SUBSCRIBE validation
  HIGH-5: Token fingerprinting — verify only (resolved by CRITICAL-1)

STEP 5 — MEDIUM tier (MEDIUM-1 through MEDIUM-6)

STEP 6 — INFO tier

STEP 7 — Run full verification checklist
```

---

## CRITICAL — Fix Before Production

### CRITICAL-1 · Three Property Name Mismatches

**Files:** `application-prod.properties`, `AuthCookieIssuer.java`, `SecurityConfig.java`, `WebSocketConfig.java`, `TokenFingerprintingService.java` or `JwtAuthenticationFilter.java`  
**Issue:** Three security properties in `application-prod.properties` use wrong key names. Java reads different names with insecure defaults. In production: cookies are not Secure, CORS falls back to localhost, fingerprinting is disabled.

**Step 1 — Read the Java source first (mandatory):**

Before changing any property, read exact `@Value` bindings in:
- `AuthCookieIssuer.java` — what property name does it bind for `cookieSecure`?
- `SecurityConfig.java` and `WebSocketConfig.java` — what property name for allowed origins?
- `TokenFingerprintingService.java` or `JwtAuthenticationFilter.java` — what property for fingerprinting enabled?

The correct property names are what the Java code reads. The prod properties must match those names — not the other way around.

**Step 2 — Fix `application-prod.properties` based on what Java reads:**

The audit found these mismatches (verify names against actual `@Value` bindings before committing):

```properties
# REMOVE these broken keys (wrong names — never applied):
# cookie.secure=true
# cors.allowed-origins=${CORS_ALLOWED_ORIGINS}
# token.fingerprinting-enabled=true

# ADD the correct keys (verify exact names from @Value bindings):
app.cookie.secure=true
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS}
security.token.fingerprinting-enabled=true
```

**Step 3 — Scan for other mismatches:**

Search `application-prod.properties` for every property that overrides something in `application.properties`. For each one, verify the key matches what the Java code reads. The audit found three mismatches — verify there are no others.

**Verify:**
```
Start application with prod profile
POST /auth/login → Set-Cookie response header must contain: Secure; HttpOnly; SameSite=Strict
  If Secure is absent → cookie.secure fix failed

Request from a non-CORS_ALLOWED_ORIGINS origin
  → CORS rejected (not allowed through with localhost fallback)

Authenticated request with wrong User-Agent (fingerprint mismatch)
  → 401 rejected (fingerprinting enabled)
  → If accepted → security.token.fingerprinting-enabled fix failed

No other property key mismatches between prod properties and Java @Value bindings
```

---

### CRITICAL-2 · Swagger Disabled in Production

**File:** `application-prod.properties`  
**Issue:** `/swagger-ui/**`, `/v3/api-docs/**`, `/api/v1/api-docs/**` are `permitAll()` with no profile guard. Full API surface visible in production.

```properties
# application-prod.properties — add:
springdoc.api-docs.enabled=false
springdoc.swagger-ui.enabled=false
```

SpringDoc respects these properties — when `api-docs.enabled=false`, the `/v3/api-docs/**` endpoint returns 404. When `swagger-ui.enabled=false`, the UI is not served.

**The `permitAll()` rules in SecurityConfig remain unchanged** — they are harmless when the endpoints return 404. No SecurityConfig change needed.

**Verify:**
```
Prod profile: GET /swagger-ui/index.html → 404 (not 403, not 200)
Prod profile: GET /v3/api-docs → 404
Local profile: GET /swagger-ui/index.html → 200 (unchanged, dev experience unaffected)
```

---

## HIGH — Fix Before Production

### HIGH-1 · SubmissionService — Instructor IDOR on getSubmission

**File:** `submission/service/SubmissionService.java` (around line 399–402)  
**Issue:** Instructor role returns any submission by ID without verifying the instructor teaches the course the submission belongs to. Any instructor account can read arbitrary students' submissions across all courses.

**Current vulnerable code:**
```java
if (role == UserRole.INSTRUCTOR) {
    // This will be validated by the repository/service layer
    return sub;  // returns without any course check
}
```

**Fix — read `GradeCalculationService.calculateStudentOverview` first and mirror its course-instructor check exactly:**

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
Instructor A teaches CS401, requests submission from CS402 student → 403 FORBIDDEN
Instructor A teaches CS401, requests submission from CS401 student → 200 OK
ADMIN role → no course check required (admins see all)
STUDENT role → existing check unchanged
```

---

### HIGH-2 · SubmissionService — Instructor IDOR on getVersionHistory

**File:** `submission/service/SubmissionService.java` (around line 438–441)  
**Issue:** Same missing course-instructor check for `getVersionHistory`. Identical vulnerability — any instructor can access any student's version history across all courses.

**Fix:** Apply the identical `courseInstructorRepository.existsByCourseIdAndUserId` check from HIGH-1 to the `role == INSTRUCTOR` branch in `getVersionHistory`.

**Verify:**
```
Instructor A requests version history for CS402 student submission → 403 FORBIDDEN
Instructor A requests version history for CS401 student submission → 200 OK
```

---

### HIGH-3 · SecurityConfig — RoleHierarchy + ExpressionHandler Beans

**File:** `infrastructure/security/SecurityConfig.java`  
**Issue:** No `RoleHierarchy` bean registered. `hasRole('ADMIN')` in `@PreAuthorize` blocks `SYSTEM_ADMIN` because Spring Security treats roles as flat without an explicit hierarchy.

**Two beans are required — missing either one causes the fix to silently fail:**
- `RoleHierarchy` bean — defines the hierarchy
- `DefaultWebSecurityExpressionHandler` bean — wires the hierarchy into `@PreAuthorize` SpEL. Without this, the `RoleHierarchy` bean exists but `@PreAuthorize` expressions do not use it (only URL-based `HttpSecurity` rules do)

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

**No `@PreAuthorize` annotations need changing.** `hasRole('ADMIN')` automatically implies `SYSTEM_ADMIN` once the hierarchy is registered. `hasRole('INSTRUCTOR')` implies `ADMIN` and `SYSTEM_ADMIN`.

**Spring Boot 3+ note:** Method security may use a separate expression handler. If `@PreAuthorize` expressions still do not see the hierarchy after adding these beans, verify `@EnableMethodSecurity` is present and that Spring Security is using the registered `expressionHandler` bean for method security, not just URL-based security.

**Verify:**
```
SYSTEM_ADMIN calls GET /admin/users → 200 (not 403)
SYSTEM_ADMIN calls POST /courses → 200 (instructor-tier endpoint, hierarchy grants it)
ADMIN calls SYSTEM_ADMIN-only endpoint → 403 (hierarchy is downward only — no upward grant)
STUDENT calls INSTRUCTOR endpoint → 403 (unchanged)
Regression: all existing @PreAuthorize checks still function correctly
```

---

### HIGH-4 · WebSocketAuthInterceptor — Validate SUBSCRIBE Destinations

**File:** `infrastructure/security/WebSocketAuthInterceptor.java` (around line 40–41)  
**Issue:** Only `CONNECT` frames are authenticated. After a valid CONNECT, a client can send `SUBSCRIBE` frames to arbitrary destinations including other users' private queues.

**Read `JwtHandshakeInterceptor` or `WebSocketAuthInterceptor.authenticateConnect` first** to confirm what `principal.getName()` returns — it must match the userId format used in destination paths.

```java
@Override
public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null) return message;

    StompCommand command = accessor.getCommand();

    if (StompCommand.CONNECT.equals(command)) {
        // Existing CONNECT validation — unchanged
        authenticateConnect(accessor);

    } else if (StompCommand.SUBSCRIBE.equals(command)) {
        // NEW: validate subscription destination
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

    // User-queue destinations must match the authenticated user
    // Pattern: /user/{userId}/queue/...
    if (destination.startsWith("/user/")) {
        String destinationUserId = extractUserIdFromDestination(destination);
        String principalName = principal.getName(); // raw userId string — verify format

        if (!principalName.equals(destinationUserId)) {
            log.warn("WebSocket SUBSCRIBE to foreign destination: " +
                     "principal={} destination={}", principalName, destination);
            throw new MessagingException(
                "Cannot subscribe to another user's destination");
        }
    }
    // Topic subscriptions (/topic/**) are public — no user restriction
}

private String extractUserIdFromDestination(String destination) {
    // /user/{userId}/queue/messages → extract userId segment
    String[] parts = destination.split("/");
    if (parts.length >= 3) return parts[2]; // index: ["", "user", "{userId}", ...]
    return null;
}
```

**Verify:**
```
Authenticated as userId=42, SUBSCRIBE /user/42/queue/messages → allowed
Authenticated as userId=42, SUBSCRIBE /user/99/queue/messages → rejected (MessagingException)
Authenticated as userId=42, SUBSCRIBE /topic/announcements → allowed (public topic)
CONNECT without SUBSCRIBE → unchanged behaviour
```

---

### HIGH-5 · Token Fingerprinting — Verify Only

Already resolved by CRITICAL-1. `security.token.fingerprinting-enabled=true` is added to `application-prod.properties` in the three-property fix. Fingerprinting code already exists and works correctly once the property is set.

**Verify only:**
```
Authenticated request with wrong User-Agent in prod → 401 rejected
(covered in CRITICAL-1 verification block)
```

---

## MEDIUM — Fix in This PR

### MEDIUM-1 · JwtAuthenticationFilter — Reject Tokens Without `ver` Claim

**File:** `infrastructure/security/JwtAuthenticationFilter.java` (around line 87–94)  
**Issue:** Token version validation is skipped when `ver` claim is absent. Tokens without `ver` bypass force-logout and password-change invalidation until natural expiry.

```java
@Value("${jwt.allow-legacy-tokens-without-kid:true}")
private boolean allowLegacyTokensWithoutKid;

// Token version check block:
String verClaim = claims.get("ver", String.class);
Long userId = claims.get("userId", Long.class);

if (userId == null || verClaim == null) {
    if (!allowLegacyTokensWithoutKid) {
        // Prod: reject tokens missing required claims
        log.warn("Rejecting token with missing ver or userId claim from ip={}",
            ipExtractor.extract(request));
        httpErrorJsonWriter.writeError(response, 401, "INVALID_TOKEN",
            "Token is missing required claims");
        return;
    }
    // Non-prod: allow legacy tokens
    log.debug("Legacy token without ver claim — allowing (legacy mode)");
}
```

**Add to `application-prod.properties`:**
```properties
jwt.allow-legacy-tokens-without-kid=false
```

**Verify:**
```
Prod profile: token without ver claim → 401 INVALID_TOKEN
Prod profile: token with ver claim → processed normally (unchanged)
Local profile: token without ver claim → allowed with debug log
```

---

### MEDIUM-2 · ActuatorKeyAuthFilter — Constant-Time Key Comparison

**File:** `infrastructure/security/ActuatorKeyAuthFilter.java` (around line 60)  
**Issue:** `providedKey.equals(actuatorInternalKey)` exits early on first differing character. Timing analysis can reveal key length and prefix.

```java
// BEFORE:
if (providedKey.equals(actuatorInternalKey)) {

// AFTER:
byte[] provided = providedKey.getBytes(StandardCharsets.UTF_8);
byte[] expected = actuatorInternalKey.getBytes(StandardCharsets.UTF_8);
if (MessageDigest.isEqual(provided, expected)) {
```

`MessageDigest.isEqual` compares all bytes in constant time regardless of where the first difference occurs. Handles different-length arrays by returning `false` without leaking the length difference.

**Verify:**
```
Valid actuator key → 200 OK (unchanged)
Invalid actuator key → 401 (unchanged)
Timing is consistent between valid and invalid (document as reviewed — not unit testable)
```

---

### MEDIUM-3 · RefreshTokenService — Clear SessionContext on Family Revocation

**File:** `auth/service/RefreshTokenService.java` (around line 98–108)  
**Issue:** On refresh token reuse detection, all tokens in the family are revoked but `SessionContext` rows (device/IP/UA metadata) are not deleted. The attacker who stole the token remains visible in the user's active sessions.

**Read `SessionContext` entity and `SessionContextRepository` before writing this fix.** Confirm the FK relationship between `session_contexts` and `refresh_token_families` and the exact FK column name.

```java
@Transactional
public void handleReuseDetection(String familyId) {
    // Existing: revoke all tokens in family
    refreshTokenRepository.revokeAllByFamilyId(familyId);

    // NEW: delete session context rows for this family in same TX
    sessionContextRepository.deleteByFamilyId(familyId);

    // Publish audit event
    eventPublisher.publishEvent(
        new RefreshTokenReuseDetectedEvent(familyId, Instant.now()));
}
```

**Why same transaction:** If token revocation succeeds but session context deletion fails, the attacker's device still appears in the session list. `@Transactional` ensures both happen or neither does.

**Verify:**
```
Reuse detected → all tokens in family revoked (existing behaviour preserved)
Reuse detected → session_context rows for family deleted in same TX
After reuse, user's active session list does not show attacker's device
Family revocation failure → session context not partially deleted (full rollback)
```

---

### MEDIUM-4 · SubmissionController — @Valid on changeNote

**File:** `submission/controller/SubmissionController.java` (around line 73–77)  
**Issue:** Multipart upload accepts `@RequestParam String changeNote` without Bean Validation. A 10,000-character `changeNote` is accepted without constraint.

```java
// Add @Validated at class level (enables parameter-level validation):
@RestController
@Validated
public class SubmissionController {

// Fix the upload method signature:
public ResponseEntity<ApiResponse<SubmissionDto>> upload(
        @PathVariable String assignmentId,
        @RequestParam
        @Size(max = 500, message = "Change note must not exceed 500 characters")
        @NotBlank(message = "Change note is required")
        String changeNote,
        @RequestParam MultipartFile file) {
```

**Verify:**
```
Upload with changeNote of 501 chars → 400 VALIDATION_ERROR
Upload with blank changeNote → 400 VALIDATION_ERROR
Upload with valid changeNote → proceeds normally
```

---

### MEDIUM-5 · JwtKeyRegistry — Minimum 32-Byte Secret on Startup

**File:** `auth/service/JwtKeyRegistry.java` (around line 25)  
**Issue:** Only checks non-blank. `Keys.hmacShaKeyFor` accepts any length secret — a 1-byte secret is valid but cryptographically useless for HS256 (requires 256-bit / 32-byte minimum).

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

**Add comment to `application.properties`:**
```properties
# JWT_SECRET must be at least 32 bytes (256 bits) for HS256
# Generate: openssl rand -base64 32
```

**Verify:**
```
Startup with JWT_SECRET of 31 bytes → fails to start with clear message
Startup with JWT_SECRET of 32+ bytes → starts normally
Startup with empty JWT_SECRET → existing blank check triggers (no regression)
```

---

### MEDIUM-6 · JwtAuthenticationFilter — Disable Bearer Fallback in Prod

**File:** `infrastructure/security/JwtAuthenticationFilter.java`  
**Issue:** Access token accepted from `Authorization: Bearer` header in addition to cookies. In prod, cookies-only is the correct posture — Bearer header increases XSS/log leakage surface.

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

**`application.properties` (base):**
```properties
security.jwt.allow-bearer-header=${SECURITY_JWT_ALLOW_BEARER_HEADER:true}
```

**Postman note:** Verify no Postman prod environment config uses `Authorization: Bearer` — prod tests must use cookie-based auth only.

**Verify:**
```
Prod profile: request with Authorization: Bearer {valid_token} → 401 (bearer ignored)
Prod profile: request with cookie → 200 OK (unchanged)
Local profile: both bearer and cookie work (allowBearerHeader=true default)
```

---

## INFO — Fix in This PR

### INFO-1 · StepUpInterceptor — 401 When Unauthenticated

**File:** `infrastructure/security/StepUpInterceptor.java` (around line 40–42)  
**Issue:** If `@RequiresStepUp` were applied without prior auth (misconfiguration), the interceptor passes through without blocking. Low immediate risk given all sensitive controllers combine `@PreAuthorize` + `@RequiresStepUp`.

```java
// StepUpInterceptor.preHandle() — add defensive guard at the top:

Principal principal = request.getUserPrincipal();
if (principal == null) {
    // Defensive: reject unauthenticated requests that reach @RequiresStepUp
    // Should not occur if @PreAuthorize is correctly applied first
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    return false;
}

// ... existing step-up validation logic (unchanged)
```

**Verify:**
```
Unauthenticated request to @RequiresStepUp endpoint → 401 (not passthrough)
Authenticated + step-up valid → 200 (unchanged)
Authenticated + step-up expired → 403 STEP_UP_REQUIRED (unchanged)
```

---

## Verification Checklist

Run in full before opening the PR. Every item must pass.

### CRITICAL — Property Mismatches
```
[ ] Prod cookies: Set-Cookie contains Secure flag
[ ] Prod cookies: Set-Cookie contains HttpOnly flag
[ ] Prod cookies: Set-Cookie contains SameSite=Strict
[ ] CORS: non-allowed origin rejected in prod (not localhost fallback)
[ ] Token fingerprinting: wrong User-Agent → 401 in prod
[ ] Swagger: GET /swagger-ui/index.html in prod → 404
[ ] Swagger: GET /v3/api-docs in prod → 404
[ ] Swagger: GET /swagger-ui/index.html locally → 200 (unchanged)
[ ] No other property name mismatches found between prod properties and Java @Value bindings
```

### HIGH — IDOR and Role Hierarchy
```
[ ] Instructor A requests CS402 submission → 403 FORBIDDEN
[ ] Instructor A requests CS401 submission → 200 OK
[ ] Instructor A requests version history from CS402 → 403 FORBIDDEN
[ ] Instructor A requests version history from CS401 → 200 OK
[ ] ADMIN can access all instructor-level endpoints (RoleHierarchy active)
[ ] SYSTEM_ADMIN can access all admin-level endpoints (RoleHierarchy active)
[ ] ADMIN cannot access SYSTEM_ADMIN-only endpoints (hierarchy is downward only)
[ ] Existing @PreAuthorize annotations unchanged and still function
[ ] DefaultWebSecurityExpressionHandler bean wired to RoleHierarchy
[ ] WebSocket SUBSCRIBE to own destination → allowed
[ ] WebSocket SUBSCRIBE to other user's destination → rejected (MessagingException)
[ ] WebSocket SUBSCRIBE to /topic/** → allowed (public topic)
```

### MEDIUM
```
[ ] Prod profile: token without ver claim → 401 INVALID_TOKEN
[ ] Local profile: token without ver claim → allowed with debug log
[ ] ActuatorKeyAuthFilter: valid key → 200 (unchanged)
[ ] ActuatorKeyAuthFilter: invalid key → 401 (unchanged)
[ ] ActuatorKeyAuthFilter: uses MessageDigest.isEqual (not String.equals)
[ ] Refresh token reuse: session_context rows deleted in same TX as token revocation
[ ] Attacker device absent from active session list after reuse detection
[ ] Family revocation failure → session context not partially deleted (rollback)
[ ] changeNote > 500 chars → 400 VALIDATION_ERROR
[ ] changeNote blank → 400 VALIDATION_ERROR
[ ] JWT_SECRET < 32 bytes → startup failure with clear message
[ ] JWT_SECRET = 32 bytes → starts normally
[ ] JWT_SECRET empty → existing check triggers (no regression)
[ ] Prod profile: Authorization: Bearer header → 401 (bearer ignored)
[ ] Prod profile: cookie auth → 200 OK (unchanged)
[ ] Local profile: both bearer and cookie work
```

### INFO
```
[ ] StepUpInterceptor: unauthenticated request → 401 (not passthrough)
[ ] Authenticated + valid step-up → 200 (unchanged)
[ ] Authenticated + expired step-up → 403 STEP_UP_REQUIRED (unchanged)
```

### Regression
```
[ ] Login still works — cookie issued with all three flags in prod
[ ] Refresh token rotation still works end-to-end
[ ] Force logout still works (session context cleanup does not break normal flow)
[ ] Step-up on grade import still works
[ ] WebSocket CONNECT still works for legitimate clients
[ ] All Postman tests pass with prod-profile equivalent settings
[ ] No new 500 errors across the test suite
[ ] SYSTEM_ADMIN dashboard endpoints still accessible to SYSTEM_ADMIN role
```

---

## Summary of All Changed Files

| File | Change |
|---|---|
| `application-prod.properties` | Fix 3 property name mismatches; add `springdoc.*.enabled=false`; add `jwt.allow-legacy-tokens-without-kid=false`; add `security.jwt.allow-bearer-header=false`; remove broken `cookie.secure`, `cors.allowed-origins`, `token.fingerprinting-enabled` |
| `application.properties` | Add `security.jwt.allow-bearer-header=${...:true}`; add JWT secret length comment |
| `SecurityConfig.java` | Add `RoleHierarchy` bean + `DefaultWebSecurityExpressionHandler` bean |
| `WebSocketAuthInterceptor.java` | Add SUBSCRIBE destination validation |
| `SubmissionService.java` | Add `courseInstructorRepository` course check on `getSubmission` and `getVersionHistory` for INSTRUCTOR role |
| `JwtAuthenticationFilter.java` | Reject tokens without `ver` claim when `allowLegacyTokensWithoutKid=false`; add bearer header property guard |
| `ActuatorKeyAuthFilter.java` | Replace `String.equals` with `MessageDigest.isEqual` |
| `RefreshTokenService.java` | Delete `SessionContext` rows in same TX as family revocation |
| `SubmissionController.java` | Add `@Validated` at class level; `@Size(max=500)` + `@NotBlank` on `changeNote` |
| `JwtKeyRegistry.java` | Add minimum 32-byte length check in `@PostConstruct` |
| `StepUpInterceptor.java` | Add 401 guard when principal is null |
