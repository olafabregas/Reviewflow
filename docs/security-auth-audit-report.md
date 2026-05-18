# ReviewFlow ΓÇö Security & Auth Hardening Audit Report

**Date:** 2026-05-18  
**Scope:** Full static audit (`scan all`) ΓÇö 17 targets, rules S01ΓÇôS17  
**Codebase:** Spring Boot / Java 21, JWT HTTP-only cookies, refresh families, step-up, lockout, WebSocket STOMP, S3, in-memory rate limiting  
**Flyway:** V34 (per project conventions)

---

## Executive Summary

ReviewFlowΓÇÖs core auth design is sound: JWT secrets are externalized without unsafe defaults, refresh-token reuse revokes the token family, login enforces lockout before password verification, password-reset tokens use `SecureRandom` and single-use semantics, and the JWT filter compares `token_version` for normal access tokens. Production properties enable ClamAV and `spring.jpa.open-in-view=false`.

**Blockers before production:** fix production profile property mismatches that leave auth cookies non-`Secure` and CORS on localhost defaults, and close the instructor submission IDOR in `SubmissionService.getSubmission`.

**Total findings:** 18 (2 critical, 6 high, 7 medium, 3 info)

| Severity | Count |
|----------|------:|
| CRITICAL | 2 |
| HIGH     | 6 |
| MEDIUM   | 7 |
| INFO     | 3 |

---

## Scan Progress

| # | Target | Result |
|---|--------|--------|
| 1 | `infrastructure/security/` | 4 findings (1 critical, 1 high, 2 medium) |
| 2 | `auth/service/` | 1 finding (0 critical, 0 high, 1 medium) |
| 3 | `auth/controller/` | 1 finding (0 critical, 0 high, 1 info) |
| 4 | `application*.properties` | 5 findings (2 critical, 2 high, 1 medium) |
| 5 | `infrastructure/filter/` | 1 finding (0 critical, 0 high, 1 medium) |
| 6 | `infrastructure/ratelimit/` | ΓÇö not found; audited `RateLimiterService` in security (2 findings) |
| 7 | `system/service/` | Clean (allowlist config) |
| 8 | `system/controller/` | Clean |
| 9 | `admin/controller/` | Clean |
| 10 | `submission/service/` | 2 findings (0 critical, 2 high) |
| 11 | `submission/controller/` | 1 finding (0 critical, 0 high, 1 medium) |
| 12 | `grading/controller/` | 1 finding (0 critical, 0 high, 1 medium) |
| 13 | `evaluation/controller/` | 1 finding (0 critical, 0 high, 1 medium) |
| 14 | `messaging/controller/` | Clean (service-layer participant checks) |
| 15 | `discussion/controller/` | Clean |
| 16 | `user/controller/` | 1 finding (0 critical, 0 high, 1 medium) |
| 17 | `infrastructure/storage/` | Clean (ClamAV fail-closed on scan failure when enabled) |

**Files scanned (audit targets):** 58 Java + 3 properties

---

## Findings by Severity

### CRITICAL

#### [RULE-S02 | CRITICAL] `application-prod.properties` ΓÇö cookie flags not wired to `AuthCookieIssuer`

**Issue:** Production file sets `cookie.secure=true`, but `AuthCookieIssuer` reads `app.cookie.secure` (default `false`).

**Context:** In prod profile, access/refresh cookies may be issued **without** `Secure`, exposing tokens on HTTP and breaking expected prod hardening.

**Snippet:**

```properties
# application-prod.properties
cookie.secure=true

# AuthCookieIssuer.java
@Value("${app.cookie.secure:false}")
private boolean cookieSecure;
```

**Fix:** Align property names ΓÇö set `app.cookie.secure=true` in `application-prod.properties` (and map legacy `cookie.secure` if needed), or bind `AuthCookieIssuer` to the prod keys.

---

#### [RULE-S08 | CRITICAL] `application-prod.properties` ΓÇö CORS allowlist not applied

**Issue:** Prod file defines `cors.allowed-origins=${CORS_ALLOWED_ORIGINS}` but `SecurityConfig` and `WebSocketConfig` use `app.cors.allowed-origins` (default `http://localhost:5173`).

**Context:** With credentials enabled, wrong CORS origins break the SPA in prod or fall back to localhost-only ΓÇö misconfiguration that can lead to shipping with dev defaults.

**Snippet:**

```properties
# application-prod.properties
cors.allowed-origins=${CORS_ALLOWED_ORIGINS}

# SecurityConfig.java
@Value("${app.cors.allowed-origins:http://localhost:5173}")
private String allowedOriginsConfig;
```

**Fix:** Use a single property key (`app.cors.allowed-origins`) in all profiles and WebSocket registration; remove unused `cors.*` keys or bridge them in `@ConfigurationProperties`.

---

### HIGH

#### [RULE-S16 | HIGH] `SubmissionService.java:399-402` ΓÇö Instructor submission IDOR

**Issue:** `INSTRUCTOR` role returns any submission by ID without verifying course assignment.

**Context:** Hashids do not protect IDs. Any instructor account (or compromised instructor session) can read arbitrary studentsΓÇÖ submissions across courses.

**Snippet:**

```java
if (role == UserRole.INSTRUCTOR) {
  // This will be validated by the repository/service layer
  return sub;
}
```

**Fix:** Verify `courseInstructorRepository.existsByCourseIdAndUserId(sub.getAssignment().getCourse().getId(), userId)` (or equivalent) before return; mirror `GradeCalculationService.calculateStudentOverview`.

---

#### [RULE-S16 | HIGH] `SubmissionService.java:438-441` ΓÇö Instructor version history IDOR

**Issue:** Same missing enrollment check for `getVersionHistory` when `role == INSTRUCTOR`.

**Fix:** Apply the same course-instructor check as above.

---

#### [RULE-S09 | HIGH] Multiple controllers ΓÇö `hasRole('ADMIN')` excludes `SYSTEM_ADMIN`

**Issue:** No `RoleHierarchy` bean is registered in `SecurityConfig`, yet many endpoints use `hasRole('ADMIN')` or `hasRole('INSTRUCTOR')` without `SYSTEM_ADMIN`.

**Context:** `SYSTEM_ADMIN` users are denied endpoints they may need for operations; teams often ΓÇ£fixΓÇ¥ this by sharing `ADMIN` credentials ΓÇö a privilege-escalation risk in practice.

**Affected (sample):** `CourseController` (lines 304, 337, 365), `EvaluationController`, `GradeExportController`, `TeamController`, `AssignmentController`.

**Fix:** Register Spring Security `RoleHierarchy` (`SYSTEM_ADMIN > ADMIN > INSTRUCTOR > STUDENT`) **or** use `hasAnyRole('ADMIN','SYSTEM_ADMIN')` consistently on privileged routes.

---

#### [RULE-S12 | HIGH] `WebSocketAuthInterceptor.java:40-41` ΓÇö Only `CONNECT` authenticated

**Issue:** Interceptor returns early for non-`CONNECT` STOMP commands; no `SUBSCRIBE` authorization.

**Context:** After CONNECT with a valid ticket, a client may attempt to subscribe to another userΓÇÖs destination (e.g. `/user/{otherId}/queue/notifications`) unless the broker enforces principal-bound destinations on every frame.

**Fix:** Add `SUBSCRIBE` handling: reject destinations that do not match `accessor.getUser().getName()` / resolved user destination prefix.

---

#### [RULE-S02 | HIGH] `SecurityConfig.java:116-126` ΓÇö Swagger/OpenAPI public in all profiles

**Issue:** `/swagger-ui/**`, `/v3/api-docs/**`, `/api/v1/api-docs/**` are `permitAll()` with no prod guard.

**Context:** Exposes full API surface and operation details on production deployments.

**Fix:** Restrict to non-prod profiles or require `ADMIN`/`SYSTEM_ADMIN` authentication in prod.

---

#### [RULE-S13 | HIGH] `application-prod.properties:127` vs code ΓÇö token fingerprinting property mismatch

**Issue:** Prod sets `token.fingerprinting-enabled=true` but code reads `security.token.fingerprinting-enabled`.

**Context:** Fingerprinting may remain disabled in production, weakening session binding for roles that enable it via policy.

**Fix:** Set `security.token.fingerprinting-enabled=true` in prod profile (or unify property names).

---

### MEDIUM

#### [RULE-S03 | MEDIUM] `JwtAuthenticationFilter.java:87-94` ΓÇö Version check skipped if claims missing

**Issue:** Token version is validated only when **both** `userId` and `ver` claims are non-null.

**Context:** Tokens without `ver` (or legacy shape) skip invalidation after password change / force logout until expiry.

**Fix:** Reject access tokens missing `ver` (and `userId`) when `jwt.allow-legacy-tokens-without-kid` is false in prod; treat absent `ver` as invalid.

---

#### [RULE-S11 | MEDIUM] `ActuatorKeyAuthFilter.java:60` ΓÇö Non-constant-time key comparison

**Issue:** `providedKey.equals(actuatorInternalKey)` is vulnerable to timing analysis.

**Fix:** Use `MessageDigest.isEqual` on UTF-8 bytes of both strings (after length check).

---

#### [RULE-S04 | MEDIUM] `RefreshTokenService.java:98-108` ΓÇö Family revoke does not clear `session_contexts`

**Issue:** On reuse detection, `revokeActiveTokensInFamily` runs but `SessionContext` rows are not invalidated.

**Context:** Session listing / metadata may remain consistent with attacker-visible state until separately cleaned.

**Fix:** Revoke or delete `SessionContext` for the family in the same transaction as family token revocation.

---

#### [RULE-S15 | MEDIUM] `RateLimiterService` ΓÇö In-memory only; no Redis Bucket4j

**Issue:** Rate limits are per JVM `ConcurrentHashMap`; ineffective in horizontal scaling.

**Context:** Brute-force and reset floods can exceed per-node thresholds under load balancers.

**Fix:** Implement PRD-20 Redis-backed limits for login, refresh, step-up, password reset, and upload paths in prod (`auth.token-version.store=redis` already expects Redis).

---

#### [RULE-S15 | MEDIUM] ΓÇö No WebSocket `CONNECT` rate limit

**Issue:** `ws-ticket` issuance is authenticated but CONNECT attempts are not rate-limited per IP.

**Fix:** Add IP-based limit in `WebSocketAuthInterceptor` or edge proxy; tie to ticket issuance endpoint.

---

#### [RULE-S09 | MEDIUM] `SubmissionController.java` ΓÇö No method-level `@PreAuthorize`

**Issue:** Relies solely on `authenticated()` URL rule and service checks.

**Fix:** Add `@PreAuthorize("isAuthenticated()")` at class level; role-specific rules on instructor-only operations if any are added.

---

#### [RULE-S14 | MEDIUM] `SubmissionController.java:73-77` ΓÇö Upload without `@Valid` on params

**Issue:** Multipart upload uses bare `@RequestParam` without Bean Validation on metadata.

**Fix:** Use a validated DTO or `@Size`/`@NotBlank` on `changeNote`; keep file size limits at servlet + service layer.

---

#### [RULE-S01 | MEDIUM] `JwtKeyRegistry.java:25` ΓÇö No minimum secret length enforcement

**Issue:** `Keys.hmacShaKeyFor` accepts short secrets; startup only checks non-blank.

**Fix:** On startup, require `jwt.secret` length ΓëÑ 32 bytes (256-bit) for HS256.

---

### INFO

#### [RULE-S01 | INFO] `application.properties:36` ΓÇö Legacy tokens without `kid` allowed

**Issue:** `jwt.allow-legacy-tokens-without-kid=true` in base config.

**Fix:** Set `false` in prod after key rotation; document migration window.

---

#### [RULE-S15 | INFO] `JwtAuthenticationFilter.java:158-163` ΓÇö Bearer header fallback

**Issue:** Access token accepted from `Authorization: Bearer` in addition to cookies.

**Context:** Increases XSS/log leakage impact if tokens are ever stored in JS; acceptable for tooling if documented.

**Fix:** Disable Bearer bridge in prod via property if cookies-only is required.

---

#### [RULE-S05 | INFO] `StepUpInterceptor.java:40-42` ΓÇö Passes through when unauthenticated

**Issue:** If `@RequiresStepUp` were used without prior auth, interceptor would not block.

**Context:** Current sensitive controllers combine `@PreAuthorize` + `@RequiresStepUp`; low immediate risk.

**Fix:** Throw `StepUpRequiredException` or return 401 when principal is missing.

---

## Clean Targets

- `system/service/SystemService` ΓÇö `SAFE_CONFIG_KEYS` allowlist; no secrets in config API  
- `system/controller/SystemController` ΓÇö `SYSTEM_ADMIN` + step-up on destructive ops  
- `admin/controller/*` ΓÇö `ADMIN` / `SYSTEM_ADMIN` enforced  
- `auth/service` ΓÇö Lockout order, reset token entropy, refresh reuse revocation, password policy wired  
- `messaging/controller` + service ΓÇö `assertParticipant` on reads/writes  
- `discussion/controller` ΓÇö Role annotations present  
- `infrastructure/storage` ΓÇö ClamAV `scanAndThrow` fails closed on error/timeout when enabled  
- `auth/service/PasswordResetService` ΓÇö Rate limited, single-use, prior tokens deleted  

---

## Rule Coverage

| Rule | Status | Notes |
|------|--------|-------|
| S01 JWT secret | ΓÜá∩╕Å | No default secret; no min length; legacy kid allowed |
| S02 Cookies | Γ¥î | Issuer correct flags, prod binding broken |
| S03 Token version | ΓÜá∩╕Å | Implemented; gap if `ver` null |
| S04 Refresh family | ΓÜá∩╕Å | Reuse revoke OK; session_context gap |
| S05 Step-up | Γ£à | Password re-check; rate limited; interceptor on annotated methods |
| S06 Lockout order | Γ£à | Lock before password |
| S07 Password reset | Γ£à | SecureRandom, TTL, single-use, invalidate old |
| S08 CORS | Γ¥î | Prod property mismatch |
| S09 Roles | ΓÜá∩╕Å | Many controllers OK; hierarchy + submission controller gaps |
| S10 Config exposure | Γ£à | Allowlist |
| S11 Actuator | ΓÜá∩╕Å | Key filter OK; not constant-time; no env/heapdump exposed |
| S12 WebSocket | ΓÜá∩╕Å | Ticket CONNECT good; SUBSCRIBE not guarded |
| S13 ClamAV | Γ£à | `clamav.enabled=true` in prod |
| S14 Validation | ΓÜá∩╕Å | Auth DTOs validated; some controllers thin |
| S15 Rate limit | ΓÜá∩╕Å | Auth paths covered in-memory; no WS; not distributed |
| S16 IDOR | Γ¥î | Instructor submission paths |
| S17 Password policy | Γ£à | Enforced on reset; login bounds |

---

## Action Plan

### P0 ΓÇö Before production deploy

1. Fix `app.cookie.secure` / `app.cors.allowed-origins` / `security.token.fingerprinting-enabled` in prod profile.  
2. Implement instructor course checks in `SubmissionService.getSubmission` and `getVersionHistory`.  
3. Add WebSocket `SUBSCRIBE` destination validation.  
4. Lock down Swagger/OpenAPI in prod profile.

### P1 ΓÇö This sprint

5. Register role hierarchy or add `SYSTEM_ADMIN` to all admin/instructor `@PreAuthorize` expressions.  
6. Constant-time actuator key comparison.  
7. Reject JWTs without `ver` in prod.  
8. Invalidate `session_contexts` on family reuse / force logout.  

### P2 ΓÇö Hardening

9. Redis-backed rate limiting for multi-node.  
10. WS CONNECT rate limits.  
11. JWT secret length validation at startup.  
12. Disable legacy kid-less tokens in prod.

---

*Generated by security-auth-audit rule (`scan all`). No source code was modified.*
