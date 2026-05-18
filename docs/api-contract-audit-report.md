# API Contract Audit Report

**Date:** 2026-05-18  
**Scope:** Full `scan all` — 17 targets per `@api-contract-audit`  
**Reference:** [controller_specs/00_Global_Rules_and_Reference.md](../controller_specs/00_Global_Rules_and_Reference.md)  
**Flyway:** V34

---

## Executive Summary

| Severity | Count |
|----------|------:|
| **CRITICAL** | 1 |
| **HIGH** | 18 |
| **MEDIUM** | 22 |
| **INFO** | 6 |
| **Total** | **47** |

**Top risks (deploy blockers / fix first):**

1. **System admin API (`/system/**`)** returns raw DTOs/Maps with no `ApiResponse` envelope — breaks the global contract for the entire SYSTEM_ADMIN surface.
2. **Unhandled domain exceptions** on system override paths (`EvaluationNotFoundException`, `TeamNotFoundException`, cache/force-logout exceptions) surface as **500 `INTERNAL_ERROR`** instead of spec status/codes.
3. **Missing `@PreAuthorize` on instructor/student mutating endpoints** — relies solely on service-layer checks; any gap becomes privilege escalation (defence-in-depth violation per global rules).

**Positive notes:** Hashid decode failures are centralized via `HashidService.decodeOrThrow` → `InvalidHashException` → **400 `INVALID_ID`**. Auth public routes in `SecurityConfig` are appropriately `permitAll`. Most feature controllers use `ResponseEntity<ApiResponse<T>>` for JSON APIs.

---

## Progress (scan targets)

| # | Target | Findings (C / H / M / I) |
|---|--------|--------------------------|
| 1 | `admin/controller/` | 0 / 1 / 0 / 0 |
| 2 | `announcement/controller/` | 0 / 0 / 2 / 1 |
| 3 | `assignment/controller/` | 0 / 4 / 5 / 0 |
| 4 | `auth/controller/` | 0 / 0 / 1 / 0 |
| 5 | `course/controller/` | 0 / 0 / 2 / 0 |
| 6 | `discussion/controller/` | 0 / 0 / 3 / 0 |
| 7 | `evaluation/controller/` | 0 / 2 / 2 / 0 |
| 8 | `extension/controller/` | 0 / 0 / 0 / 0 |
| 9 | `grading/controller/` | 0 / 3 / 4 / 1 |
| 10 | `messaging/controller/` | 0 / 0 / 2 / 0 |
| 11 | `notification/controller/` | 0 / 1 / 1 / 0 |
| 12 | `submission/controller/` | 0 / 1 / 2 / 1 |
| 13 | `system/controller/` | **1** / 2 / 2 / 0 |
| 14 | `team/controller/` | 0 / 2 / 3 / 0 |
| 15 | `user/controller/` | 0 / 0 / 2 / 1 |
| 16 | `GlobalExceptionHandler` + exceptions | 0 / 2 / 4 / 0 |
| 17 | `SecurityConfig` (spot-check) | 0 / 0 / 1 / 2 |

**Files scanned:** 27 controllers + `GlobalExceptionHandler` + `SecurityConfig` + `ApiResponse` / `ErrorResponse`

---

## Findings — CRITICAL

### [RULE-API01 | CRITICAL] SystemController.java (entire class)

**Issue:** All JSON endpoints return raw types (`List<CacheStatsDto>`, `Map<String, String>`, `ForceLogoutResponse`, etc.) without `ApiResponse` wrapping.

**Context:** Global rules require `{ success, data, timestamp }` on every public API response. Frontend and Postman tests expect a uniform envelope.

**Affected methods (representative):** `getCacheStats`, `evictCache`, `getSafeConfig`, moderation list endpoints, `getSecurityEvents`, `forceLogout`, `unlockTeam`, `reopenEvaluation`.

**Fix:** Change return types to `ResponseEntity<ApiResponse<T>>` and wrap with `ApiResponse.ok(...)`. Align error paths with the same envelope (or rely on `GlobalExceptionHandler` after exceptions are mapped).

**Spec:** `00_Global_Rules` § Response Envelope

---

## Findings — HIGH

### [RULE-API11 | HIGH] AdminStatsController.java:62–77

**Issue:** Controller injects and calls five repositories directly for aggregate counts.

**Fix:** Move stats aggregation to `admin/service/AdminStatsService` (or extend existing admin service).

---

### [RULE-API11 | HIGH] EvaluationController.java:418–419

**Issue:** `toResponse` calls `rubricScoreRepository.findByEvaluationId` in the controller.

**Fix:** Load rubric scores in `EvaluationService` and return a fully populated `EvaluationResponse` DTO.

---

### [RULE-API11 | HIGH] StudentController.java:45–48, 71, 158

**Issue:** Controller injects `TeamMemberRepository` and `RubricScoreRepository` and queries them in handler/mapping code.

**Fix:** Delegate to `TeamService` / `EvaluationService` (or a student dashboard service).

---

### [RULE-API10 | HIGH] GlobalExceptionHandler.java (missing handlers)

**Issue:** Thrown exceptions with no dedicated handler fall through to `handleGeneric` → **500** `INTERNAL_ERROR`.

| Exception | Thrown from | Expected (spec-aligned) |
|-----------|-------------|-------------------------|
| `EvaluationNotFoundException` | `SystemService.reopenEvaluation` | 404 + domain code |
| `TeamNotFoundException` | `SystemService.unlockTeam` | 404 + domain code |
| `UnknownCacheException` | `SystemService.evictCache` | 400 + cache error code |
| `CacheEvictionThrottledException` | `SystemService.evictCache` | 429 or 409 + throttle code |
| `ForceLogoutBlockedException` | `SystemService.forceLogout` | 403 + `CANNOT_FORCE_LOGOUT_SYSTEM_ADMIN` (per spec table) |

**Fix:** Add `@ExceptionHandler` methods returning `ErrorResponse`/`ApiResponse` with correct HTTP status and stable `error.code`.

---

### [RULE-API05 | HIGH] AssignmentController.java — mutating endpoints

**Issue:** `POST/PUT/PATCH/DELETE` assignment and rubric methods lack method-level `@PreAuthorize` (only gradebook GETs are annotated).

**Examples:** `create` (113), `update` (200), `publish` (245), `delete` (304), rubric CRUD.

**Fix:** Add `@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN','SYSTEM_ADMIN')")` (or match module spec) on each mutating method.

---

### [RULE-API05 | HIGH] AssignmentGroupController.java / ModuleController.java

**Issue:** No `@PreAuthorize` on any POST/PUT/PATCH/DELETE.

**Fix:** Class-level or per-method annotations matching assignment module auth (instructor+ for course-scoped writes).

---

### [RULE-API05 | HIGH] InstructorScoreController.java

**Issue:** All score create/update/publish/import mutating endpoints lack `@PreAuthorize`.

**Fix:** `@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN','SYSTEM_ADMIN')")` on instructor score paths.

---

### [RULE-API05 | HIGH] SubmissionController.java:72

**Issue:** `POST` upload has no `@PreAuthorize` (any authenticated principal can hit the endpoint).

**Fix:** `@PreAuthorize("hasRole('STUDENT')")` or `isAuthenticated()` plus documented service ownership checks; prefer explicit role.

---

### [RULE-API05 | HIGH] TeamController.java

**Issue:** `create`, `invite`, `respond`, `rename`, `removeMember` lack `@PreAuthorize` (only `autoAssign` and `lock` are protected).

**Fix:** Annotate student vs instructor operations per team module spec.

---

### [RULE-API05 | HIGH] NotificationController.java:107, 128, 157

**Issue:** `PATCH` and `DELETE` lack `@PreAuthorize`.

**Fix:** `@PreAuthorize("isAuthenticated()")` minimum; service already scopes by user.

---

### [RULE-API05 | HIGH] JobController.java

**Issue:** Job status/SSE/commit endpoints have no `@PreAuthorize` (only `authenticated()` via `SecurityConfig`).

**Fix:** `@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN','SYSTEM_ADMIN')")` on import job endpoints.

---

### [RULE-API03 | HIGH] AssignmentController.java:200–203

**Issue:** `PUT /assignments/{id}` accepts `@RequestBody CreateAssignmentRequest` without `@Valid`.

**Fix:** Add `@Valid` on request body (create path already uses `@Valid`).

---

### [RULE-API03 | HIGH] EvaluationController.java:241–243

**Issue:** `PATCH /{id}/comment` — `PatchCommentRequest` without `@Valid`.

**Fix:** Add `@Valid` and ensure DTO has Bean Validation constraints.

---

### [RULE-API03 | HIGH] AdminUserController.java:174–175

**Issue:** `PATCH /{id}` — `UpdateUserRequest` without `@Valid`.

**Fix:** Add `@Valid @RequestBody UpdateUserRequest`.

---

### [RULE-API04 | HIGH] AnnouncementController.java:224–231

**Issue:** `DELETE` returns `ResponseEntity.ok().build()` (**200** empty body) without envelope.

**Fix:** Return `204 No Content` or `200` with `ApiResponse.ok(...)` per module convention; prefer `204` for delete success.

---

### [RULE-API02 | HIGH] GlobalExceptionHandler.java:159–170

**Issue:** `IllegalArgumentException` handler returns `ex.getMessage()` as client-visible `error.message` with code `BAD_REQUEST`.

**Context:** Controllers throw `IllegalArgumentException` for validation (e.g. `TeamController` "name is required"). Spec prefers field-level `VALIDATION_ERROR`. Internal/framework IAE text may leak if propagated.

**Fix:** Map controller validation to `ValidationException` or `MethodArgumentNotValidException`; use generic safe message for catch-all IAE.

---

## Findings — MEDIUM

### [RULE-API08 | MEDIUM] Unbounded list GETs

| Location | Endpoint | Issue |
|----------|----------|--------|
| `AssignmentController.java:79` | `GET .../assignments` | Full list, no `page`/`size`/`Pageable` |
| `DiscussionController.java:67` | `GET .../discussions` | Full list |
| `TeamController.java:74` | `GET .../teams` | Full list |
| `MessagingController.java:104` | `GET .../conversations` | Map wrapper, no pagination |

**Fix:** Add `Pageable` or `page`/`size` params; include `pagination` in body and `X-Total-Elements` / `X-Total-Pages` headers per global rules.

**Note:** No controller sets `X-Total-Elements` / `X-Total-Pages` on any list response (global rules gap).

---

### [RULE-API13 | MEDIUM] Multipart endpoints missing `consumes`

| File | Endpoint |
|------|----------|
| `SubmissionController.java:72` | POST upload |
| `AvatarController.java:62` | PUT avatar |
| `MessagingController.java:62` | POST message + files |
| `InstructorScoreController.java` | CSV import `@RequestPart` |

**Fix:** `@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)` (or `PUT` as appropriate).

---

### [RULE-API03 | MEDIUM] Additional missing `@Valid`

| File | Method |
|------|--------|
| `AssignmentGroupController.java:143` | `moveAssignment` — `MoveAssignmentGroupRequest` |
| `ModuleController.java:124` | `assignModule` — `AssignModuleRequest` |
| `AssignmentController.java:386` | `updateRubric` — `UpdateRubricRequest` |
| `TeamController.java:110` | `create` — raw `Map<String,String>` (no DTO validation) |
| `CourseController.java:245,307` | instructor assign / enroll — `Map` bodies |
| `SystemController.java:218+` | `ForceLogoutRequest`, etc. without `@Valid` |

---

### [RULE-API09 | MEDIUM] Error code drift

| Actual code | Context | Spec note |
|-------------|---------|-----------|
| `BAD_REQUEST` | `IllegalArgumentException` handler | Prefer `VALIDATION_ERROR` for input errors |
| `NOT_FOUND` | `ResourceNotFoundException` | Spec allows generic `NOT_FOUND`; modules often define `*_NOT_FOUND` |
| `RATE_LIMITED` | `RateLimitException` handler | Global table lists `TOO_MANY_REQUESTS` (429 handler uses correct code) |
| `INTERNAL_ERROR` | Unhandled runtime exceptions | Correct message (no stack trace) |

**Discussion paths:** `DiscussionException` correctly returns `ApiResponse` with module codes — good pattern to extend.

---

### [RULE-API14 | MEDIUM] DiscussionController.java:131–137

**Issue:** `deletePost` returns `ResponseEntity.noContent()` — no JSON envelope (acceptable for 204 if clients expect empty body; inconsistent with other deletes returning `ApiResponse`).

**Fix:** Standardize DELETE semantics across modules (204 vs 200 + envelope).

---

### [RULE-API01 | MEDIUM] GradeExportController.java:61

**Issue:** Returns `ResponseEntity<byte[]>` without `ApiResponse` wrapper.

**Context:** Spec allows non-JSON for file download — **acceptable** if documented; flag as intentional binary response.

---

### [RULE-API06 | MEDIUM] SecurityConfig.java:131–132

**Issue:** `/ws/**` is `permitAll()` at HTTP layer.

**Context:** STOMP auth uses ws-ticket on CONNECT (separate audit). Document that HTTP upgrade is open but CONNECT is ticket-gated.

---

### [RULE-API12 | MEDIUM] EvaluationController.java:418–427

**Issue:** `toResponse` touches `RubricScore` entities and lazy `criterion` associations during mapping in controller thread.

**Fix:** Map in service with fetch join / DTO projection (pairs with API11).

---

## Findings — INFO

| Rule | Location | Note |
|------|----------|------|
| API01 | `SubmissionController.download`, `EvaluationController.downloadPdf` | Binary `Resource` — allowed per audit safe patterns |
| API01 | `GradeExportController` | CSV bytes + `Content-Disposition` — allowed |
| API04 | `JobController.commit` | Returns **202 ACCEPTED** — appropriate for async commit |
| API06 | Auth `permitAll` routes | Login, refresh, password-reset — correct |
| API06 | `/actuator/health` | Public health — correct |
| API07 | Hashids | `decodeOrThrow` + `InvalidHashException` handler — compliant |

---

## Error Code Drift Matrix (API09)

| Found in code | HTTP | Spec / expected | Severity |
|---------------|------|-----------------|----------|
| `BAD_REQUEST` | 400 | `VALIDATION_ERROR` for field validation | MEDIUM |
| `NOT_FOUND` | 404 | Generic OK; prefer domain codes where defined | INFO |
| `FORBIDDEN` | 403 | Matches spec | OK |
| `INVALID_ID` | 400 | Matches hashid rules | OK |
| `TOO_MANY_REQUESTS` | 429 | Matches spec | OK |
| `RATE_LIMITED` | 429 | Duplicate semantics vs `TOO_MANY_REQUESTS` | MEDIUM |
| `INTERNAL_ERROR` | 500 | OK for unexpected; not for domain not-found | HIGH (when domain ex unhandled) |
| *(missing)* | 401 | `UNAUTHORIZED` on token version mismatch | HIGH (filter swallows mismatch) |

---

## Unhandled Exceptions (API10)

| Exception class | Recommended handler | Status | Code |
|-----------------|---------------------|--------|------|
| `EvaluationNotFoundException` | `@ExceptionHandler` | 404 | `EVALUATION_NOT_FOUND` or `NOT_FOUND` |
| `TeamNotFoundException` | `@ExceptionHandler` | 404 | `TEAM_NOT_FOUND` |
| `UnknownCacheException` | `@ExceptionHandler` | 400 | `UNKNOWN_CACHE` |
| `CacheEvictionThrottledException` | `@ExceptionHandler` | 429 | `CACHE_EVICTION_THROTTLED` |
| `ForceLogoutBlockedException` | `@ExceptionHandler` | 403 | `CANNOT_FORCE_LOGOUT_SYSTEM_ADMIN` |
| `TokenVersionMismatchException` | Filter or handler | 401 | `UNAUTHORIZED` / `TOKEN_REVOKED` |

**Note:** `TokenVersionMismatchException` is caught in `JwtAuthenticationFilter` and **does not** write JSON — request proceeds unauthenticated → generic 401 on protected routes without spec error body.

---

## SecurityConfig Spot-Check (API06)

| Path pattern | Config | Assessment |
|--------------|--------|------------|
| `POST /api/v1/auth/login` | `permitAll` | OK |
| `POST /api/v1/auth/password-reset/**` | `permitAll` | OK |
| `POST /api/v1/auth/refresh` | `permitAll` | OK |
| `GET /api/v1/auth/ws-ticket` | `authenticated` | OK |
| `/swagger-ui/**`, `/v3/api-docs/**` | `permitAll` | OK (non-prod concern) |
| `/system/**` | `hasRole('SYSTEM_ADMIN')` | OK — matches `SystemController` |
| `/ws/**` | `permitAll` | INFO — ticket auth on STOMP CONNECT |
| `anyRequest().authenticated()` | default | OK — no broad `permitAll` on `/api/v1/**` |

**Gap:** `/api/v1/admin/**` and `/api/v1/courses/**` rely on `authenticated()` + `@PreAuthorize` — reinforces API05 findings.

---

## Clean Controllers (no violations flagged)

- `AdminAuditController.java`
- `AuthController.java`, `PasswordResetController.java`, `SessionController.java`, `StepUpController.java`
- `ExtensionRequestController.java`
- `CourseController.java` (mutations have `@PreAuthorize`; minor Map-body validation notes in MEDIUM only)
- `MessagingController.java` (strong `@PreAuthorize` coverage; multipart `consumes` only)
- `DiscussionController.java` (except pagination + delete envelope notes)
- `DocsController.java` (HTML docs — out of JSON envelope scope)

---

## Rule Coverage (API01–API14)

| Rule | Hits | Notes |
|------|-----:|-------|
| API01 | 2 | SystemController CRITICAL; binary exports INFO |
| API02 | 1 | IAE message leakage |
| API03 | 8 | Missing `@Valid` / Map bodies |
| API04 | 2 | Announcement DELETE status/body |
| API05 | 9 | Missing `@PreAuthorize` clusters |
| API06 | 0 | No incorrect permitAll on protected data |
| API07 | 0 | Hashid handling OK |
| API08 | 5 | Lists + pagination headers |
| API09 | 4 | Code drift / token mismatch |
| API10 | 6 | Missing handlers |
| API11 | 3 | Repository in controller |
| API12 | 1 | Lazy mapping in controller |
| API13 | 4 | Missing `consumes` |
| API14 | 1 | DELETE consistency |

---

## Recommended Action Plan

### P0 — Before production

1. Wrap **all** `SystemController` JSON responses in `ApiResponse`.
2. Add **exception handlers** for system + evaluation + team not-found and cache/force-logout paths.
3. Add **`@PreAuthorize`** on assignment, grading, submission, team, notification, and job mutating endpoints.
4. Fix **`AssignmentController.update`** and other HIGH `@Valid` gaps.

### P1 — Next sprint

5. Move **repository calls** out of `AdminStatsController`, `EvaluationController`, `StudentController`.
6. Add **pagination** to unbounded list endpoints and **response headers** `X-Total-Elements` / `X-Total-Pages`.
7. Add **`consumes = MULTIPART_FORM_DATA`** on upload endpoints.
8. Return **401 JSON** with stable code when token version mismatches (filter or entry point).

### P2 — Consistency

9. Standardize **DELETE** (204 vs envelope).
10. Align **`RATE_LIMITED`** vs **`TOO_MANY_REQUESTS`** naming.
11. Replace **`Map` request bodies** with validated DTOs on course/team endpoints.

---

## Scan Errors

None — all 17 targets found and read.
