# Agent — API Contract Hardening

**Status:** Final  
**Author:** Roqeeb Olamide Ayorinde  
**Source:** API Contract Audit Report 2026-05-18 (47 findings)  
**Depends on:** No migration. No new PRD dependencies.

---

## How to Invoke This Agent

```
@docs/agents/api-contract-hardening.md fix all
```

Other commands:

| Command | What it does |
|---|---|
| `fix all` | Execute CRITICAL → P0 → P1 → P2 in order |
| `fix critical` | CRITICAL tier only |
| `fix p0` | HIGH P0 tier only |
| `fix p1` | HIGH P1 tier only |
| `fix p2` | MEDIUM P2 tier only |
| `verify` | Run full verification checklist, no code changes |
| `status` | Show which tiers are complete and which remain |

---

## Agent Instructions — Read Before Starting

You are a senior Spring Boot engineer executing a structured fix pass on ReviewFlow, a **Spring Boot 4.0.x / Java 21** academic submission and grading platform.

**Rules:**
- Implement in strict priority order: CRITICAL → P0 → P1 → P2. Do not skip ahead.
- **Read every file before touching it.** Do not assume method signatures, return types, or existing annotations.
- Do not fix INFO items — they are correct by design.
- Never commit a tier until every item in that tier is verified.
- Postman collection must be updated as part of this pass, not after.

**Package references:**

| Symbol | Path |
|---|---|
| Controllers | `com.reviewflow.{feature}.controller` |
| GlobalExceptionHandler | `com.reviewflow.shared.exception.GlobalExceptionHandler` |
| HttpErrorJsonWriter | `com.reviewflow.infrastructure.security.HttpErrorJsonWriter` |
| JwtAuthenticationFilter | `com.reviewflow.infrastructure.security.JwtAuthenticationFilter` |
| ApiResponse | `com.reviewflow.shared.api.ApiResponse` |

---

## Locked Decisions

| Decision | Choice |
|---|---|
| DELETE endpoints | `204 No Content`, empty body — **all** deletes |
| `@Valid` failures | `VALIDATION_ERROR` + `fields` array |
| Other 400s | `BAD_REQUEST` — `IllegalArgumentException`, structural errors |
| Token version mismatch code | `TOKEN_REVOKED` |
| Submission POST auth | `STUDENT + INSTRUCTOR` |
| AnnouncementController DELETE | Fix in P0 |

---

## Execution Protocol for `fix all`

```
STEP 1 — Create fix branch
  git checkout -b fix/api-contract-hardening

STEP 2 — CRITICAL tier
  Read each affected file → apply fix → verify → commit

STEP 3 — HIGH P0 tier (in sub-item order P0-1 through P0-6)
  Read each affected file → apply fix → verify → commit

STEP 4 — HIGH P1 tier (P1-1 through P1-4)
  Read each affected file → apply fix → verify → commit

STEP 5 — MEDIUM P2 tier (P2-1 through P2-6)
  Read each affected file → apply fix → verify → commit

STEP 6 — Run full verification checklist
  Report pass/fail per item

STEP 7 — Final summary
  Total files changed, findings resolved, tiers completed
```

---

## CRITICAL — Fix First

### CRITICAL-1 · SystemController — ApiResponse Envelope

**File:** `com.reviewflow.system.controller.SystemController`  
**Rule:** RULE-API01  
**Issue:** All JSON-returning methods return raw types (`List<CacheStatsDto>`, `Map<String, String>`, `ForceLogoutResponse`, etc.) with no `ApiResponse` wrapping.

**Fix pattern — apply to every `@GetMapping`, `@PostMapping`, `@PatchMapping`, `@DeleteMapping` that returns JSON. Binary/file responses are exempt. Do not change method logic.**

```java
// BEFORE:
@GetMapping("/cache/stats")
public ResponseEntity<List<CacheStatsDto>> getCacheStats() {
    return ResponseEntity.ok(systemService.getCacheStats());
}

// AFTER:
@GetMapping("/cache/stats")
public ResponseEntity<ApiResponse<List<CacheStatsDto>>> getCacheStats() {
    return ResponseEntity.ok(ApiResponse.ok(systemService.getCacheStats()));
}
```

**Verify:**
```
GET  /system/cache/stats        → { "success": true, "data": [...], "timestamp": "..." }
POST /system/cache/evict/{n}    → { "success": true, "data": {...}, "timestamp": "..." }
POST /system/force-logout       → { "success": true, "data": {...}, "timestamp": "..." }
POST /system/unlock-team        → { "success": true, "data": {...}, "timestamp": "..." }
POST /system/reopen-evaluation  → { "success": true, "data": {...}, "timestamp": "..." }
GET  /system/config             → { "success": true, "data": {...}, "timestamp": "..." }
GET  /system/security-events    → { "success": true, "data": {...}, "timestamp": "..." }
```

---

## HIGH P0 — Fix Before Production

### P0-1 · GlobalExceptionHandler — Five Missing Handlers

**File:** `com.reviewflow.shared.exception.GlobalExceptionHandler`  
**Rule:** RULE-API10  
**Issue:** Five domain exceptions from `SystemService` fall through to `handleGeneric` → 500.

**Read `SystemService` first — verify exception class names match exactly before adding handlers.**

```java
@ExceptionHandler(EvaluationNotFoundException.class)
public ResponseEntity<ApiResponse<Void>> handleEvaluationNotFound(
        EvaluationNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error("EVALUATION_NOT_FOUND", ex.getMessage()));
}

@ExceptionHandler(TeamNotFoundException.class)
public ResponseEntity<ApiResponse<Void>> handleTeamNotFound(
        TeamNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error("TEAM_NOT_FOUND", ex.getMessage()));
}

@ExceptionHandler(UnknownCacheException.class)
public ResponseEntity<ApiResponse<Void>> handleUnknownCache(
        UnknownCacheException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("UNKNOWN_CACHE", ex.getMessage()));
}

@ExceptionHandler(CacheEvictionThrottledException.class)
public ResponseEntity<ApiResponse<Void>> handleCacheEvictionThrottled(
        CacheEvictionThrottledException ex) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .body(ApiResponse.error("CACHE_EVICTION_THROTTLED", ex.getMessage()));
}

@ExceptionHandler(ForceLogoutBlockedException.class)
public ResponseEntity<ApiResponse<Void>> handleForceLogoutBlocked(
        ForceLogoutBlockedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiResponse.error("CANNOT_FORCE_LOGOUT_SYSTEM_ADMIN", ex.getMessage()));
}
```

**Verify:**
```
POST /system/reopen-evaluation (bad id)  → 404 EVALUATION_NOT_FOUND (not 500)
POST /system/unlock-team (bad id)        → 404 TEAM_NOT_FOUND (not 500)
POST /system/cache/evict (unknown cache) → 400 UNKNOWN_CACHE (not 500)
POST /system/cache/evict (throttled)     → 429 CACHE_EVICTION_THROTTLED (not 500)
POST /system/force-logout (self)         → 403 CANNOT_FORCE_LOGOUT_SYSTEM_ADMIN (not 500)
```

---

### P0-2 · GlobalExceptionHandler — VALIDATION_ERROR Split

**File:** `GlobalExceptionHandler`, `ApiResponse`, `ErrorResponse`  
**Rule:** RULE-API09  
**Issue:** `MethodArgumentNotValidException` maps to `BAD_REQUEST`. Must be `VALIDATION_ERROR` with `fields` array.

**New `FieldError` record (add to shared package):**
```java
public record FieldError(
    String field,
    String message,
    String rejectedValue
) {}
```

**Add `validationError` factory to `ApiResponse`:**
```java
public static <T> ApiResponse<T> validationError(List<FieldError> fields) {
    return new ApiResponse<>(
        false,
        null,
        new ErrorResponse("VALIDATION_ERROR",
            "One or more fields failed validation", fields),
        Instant.now().toString()
    );
}
```

**Update `ErrorResponse`** — add nullable `fields` field. Use `@JsonInclude(NON_NULL)` so existing error responses without fields serialize cleanly.

**Update `MethodArgumentNotValidException` handler:**
```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiResponse<Void>> handleValidationErrors(
        MethodArgumentNotValidException ex) {

    List<FieldError> fieldErrors = ex.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(fe -> new FieldError(
            fe.getField(),
            fe.getDefaultMessage(),
            fe.getRejectedValue() != null ? fe.getRejectedValue().toString() : null))
        .toList();

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.validationError(fieldErrors));
}
```

**`IllegalArgumentException` handler stays as `BAD_REQUEST` — sanitise message, do not leak internals:**
```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
        IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("BAD_REQUEST", "Invalid request parameters"));
}
```

**Verify:**
```
POST /assignments (missing required field)  → 400 VALIDATION_ERROR { fields: [{...}] }
PUT /assignments/{id} (field too long)      → 400 VALIDATION_ERROR { fields: [{...}] }
Any endpoint (bad business logic argument)  → 400 BAD_REQUEST, no internal message
```

---

### P0-3 · @PreAuthorize — All Missing Controller Annotations

**Rule:** RULE-API05  
**Issue:** Nine controllers have mutating endpoints with no method-level `@PreAuthorize`. Read each controller before adding — only add what is missing.

#### AssignmentController
```java
// All POST/PUT/PATCH/DELETE methods (create, update, publish, delete, createRubric, updateRubric, deleteRubric):
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'SYSTEM_ADMIN')")
// GET methods do not need this — students read assignments
```

#### AssignmentGroupController
```java
// Class-level if ALL methods are instructor+, otherwise per-method:
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'SYSTEM_ADMIN')")
```

#### ModuleController
```java
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'SYSTEM_ADMIN')")
```

#### InstructorScoreController
```java
// All score create/update/publish/import endpoints + job commit:
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'SYSTEM_ADMIN')")
```

#### SubmissionController POST upload
```java
@PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
```

#### TeamController
```java
// Student operations (create, invite, respond, rename):
@PreAuthorize("hasRole('STUDENT')")

// Instructor/admin operations (autoAssign, lock, removeMember):
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'SYSTEM_ADMIN')")
```

#### NotificationController
```java
// PATCH and DELETE:
@PreAuthorize("isAuthenticated()")
```

#### JobController
```java
// All endpoints (status, progress, errors download, commit):
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'SYSTEM_ADMIN')")
```

#### AnnouncementController DELETE
```java
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'SYSTEM_ADMIN')")
// Read AnnouncementService.delete() to confirm role — may require ADMIN+ for platform-wide
```

**Verify for each controller:**
```
Unauthenticated request to mutating endpoint → 401 (not 403, not 200)
Wrong role (STUDENT hits instructor endpoint) → 403 FORBIDDEN with ApiResponse error body
Correct role                                  → normal response
```

---

### P0-4 · @Valid — Three HIGH Gaps

**Rule:** RULE-API03

#### AssignmentController.update (PUT /assignments/{id})
```java
// AFTER:
public ResponseEntity<ApiResponse<AssignmentResponse>> update(
        @PathVariable String id,
        @Valid @RequestBody CreateAssignmentRequest request) {
```
Verify `CreateAssignmentRequest` has `@NotBlank` on title, `@NotNull` on required fields. Add if missing.

#### EvaluationController.patchComment (PATCH /{id}/comment)
```java
// AFTER:
public ResponseEntity<ApiResponse<Void>> patchComment(
        @PathVariable String id,
        @Valid @RequestBody PatchCommentRequest request) {
```
Add `@NotNull` minimum to `PatchCommentRequest`. Add `@Size(max=2000)` on comment field if not present.

#### AdminUserController.update (PATCH /{id})
```java
// AFTER:
public ResponseEntity<ApiResponse<UserResponse>> update(
        @PathVariable String id,
        @Valid @RequestBody UpdateUserRequest request) {
```
Add `@Email` on email field, `@Size` on name fields if missing.

**Verify:**
```
PUT /assignments/{id} (empty title)        → 400 VALIDATION_ERROR { fields: [{field: "title", ...}] }
PATCH /evaluations/{id}/comment (null)     → 400 VALIDATION_ERROR
PATCH /admin/users/{id} (invalid email)    → 400 VALIDATION_ERROR
```

---

### P0-5 · JwtAuthenticationFilter — TOKEN_REVOKED JSON Response

**File:** `com.reviewflow.infrastructure.security.JwtAuthenticationFilter`  
**Rule:** RULE-API09  
**Issue:** `TokenVersionMismatchException` is caught in the filter but does not write a JSON response. Request proceeds unauthenticated, producing a bare 401 with no body.

**Read `HttpErrorJsonWriter` first — verify `writeError` method signature before calling it.**

```java
} catch (TokenVersionMismatchException ex) {
    log.warn("Token version mismatch for request {}: {}",
        request.getRequestURI(), ex.getMessage());
    httpErrorJsonWriter.writeError(
        response,
        HttpStatus.UNAUTHORIZED.value(),
        "TOKEN_REVOKED",
        "Your session has been invalidated. Please log in again."
    );
    return; // Stop filter chain — do not proceed
}
```

**Verify:**
```
Request with revoked token → 401 { "success": false, "error": { "code": "TOKEN_REVOKED", "message": "..." } }
Not → bare 401 with no body
Not → proceeds to route and gets generic 401
```

---

### P0-6 · AnnouncementController DELETE — 204 + @PreAuthorize

**File:** `com.reviewflow.announcement.controller.AnnouncementController`  
**Rule:** RULE-API04 / RULE-API14  
**Issue:** Announcement DELETE returns inconsistent status/body and is missing `@PreAuthorize`.

```java
@DeleteMapping("/{id}")
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'SYSTEM_ADMIN')")
public ResponseEntity<Void> deleteAnnouncement(@PathVariable String id) {
    announcementService.delete(HashidService.decodeOrThrow(id));
    return ResponseEntity.noContent().build();
}
```

**Verify:**
```
DELETE /announcements/{id} (valid, authorised)  → 204 No Content, empty body
DELETE /announcements/{id} (wrong role)         → 403 FORBIDDEN with ApiResponse error body
```

---

## HIGH P1 — Next Sprint

### P1-1 · Repository Calls in Controllers — Move to Service Layer

**Rule:** RULE-API11  
**Issue:** Three controllers directly inject and call repositories, bypassing the service layer.

#### AdminStatsController
Create `AdminStatsService` (or extend existing admin service) with a `getStats()` method that encapsulates all aggregation logic. Remove all repository injections from the controller.

```java
// AdminStatsController — AFTER:
@GetMapping("/stats")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public ResponseEntity<ApiResponse<AdminStatsDto>> getStats() {
    return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getStats()));
}
```

#### EvaluationController
Move `rubricScoreRepository.findByEvaluationId` call into `EvaluationService.getById()`. Controller only maps the returned DTO.

```java
// EvaluationService.getById() — AFTER:
public EvaluationResponse getById(Long evaluationId, Long requestingUserId) {
    Evaluation evaluation = evaluationRepository.findByIdOrThrow(evaluationId);
    List<RubricScore> scores = rubricScoreRepository.findByEvaluationId(evaluationId);
    return EvaluationResponse.from(evaluation, scores);
}
```

#### StudentController
Replace `TeamMemberRepository` and `RubricScoreRepository` injections with `TeamService` and `EvaluationService`. Read the specific queries first to understand what service methods to add.

**Verify for all three:**
```
No repository injection in: AdminStatsController, EvaluationController, StudentController
Stats endpoint still returns correct data
Evaluation response still includes rubric scores
Student endpoint still returns correct team/evaluation data
```

---

### P1-2 · Pagination on Unbounded List Endpoints

**Rule:** RULE-API08  
**Issue:** List endpoints return all records with no pagination. `X-Total-Elements` and `X-Total-Pages` headers are missing.

**Affected:** `GET /admin/users`, `GET /courses/{id}/students` (roster), any other list endpoint returning `List<>` without `Page<>`.

**Fix pattern:**
```java
@GetMapping
public ResponseEntity<ApiResponse<Page<UserDto>>> listUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String direction) {

    Page<UserDto> result = adminUserService.listUsers(
        PageRequest.of(page, Math.min(size, 100),
            Sort.by(Sort.Direction.fromString(direction), sortBy)));

    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Total-Elements", String.valueOf(result.getTotalElements()));
    headers.add("X-Total-Pages", String.valueOf(result.getTotalPages()));

    return ResponseEntity.ok().headers(headers).body(ApiResponse.ok(result));
}
```

Cap page size at `Math.min(size, 100)` server-side. Never allow unbounded requests.

**Verify:**
```
GET /admin/users?page=0&size=20 → 20 users, X-Total-Elements header present
GET /admin/users?size=9999      → capped at 100 results
```

---

### P1-3 · consumes = MULTIPART_FORM_DATA on Upload Endpoints

**Rule:** RULE-API13  
**Issue:** Four upload endpoints do not declare `consumes`.

```java
// SubmissionController:
@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)

// AvatarController:
@PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)

// MessagingController:
@PostMapping(value = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)

// InstructorScoreController:
@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
```

**Verify:**
```
Swagger UI shows correct consumes type on all four endpoints
POST with wrong content-type → 415 Unsupported Media Type
```

---

### P1-4 · AuthenticationEntryPoint — JSON Body on 401

**File:** `SecurityConfig`  
**Rule:** RULE-API09  
**Issue:** `AuthenticationEntryPoint` may return a bare 401 with no JSON body when Spring Security rejects a request before the filter catch.

Check `SecurityConfig` for the configured entry point. If it returns no JSON body, update it:

```java
.exceptionHandling(ex -> ex
    .authenticationEntryPoint((request, response, authException) -> {
        httpErrorJsonWriter.writeError(
            response,
            HttpStatus.UNAUTHORIZED.value(),
            "UNAUTHORIZED",
            "Authentication required"
        );
    })
)
```

**Verify:**
```
Protected route hit with no token     → 401 { "error": { "code": "UNAUTHORIZED" } }
Protected route hit with revoked token → 401 { "error": { "code": "TOKEN_REVOKED" } }
Both → JSON body, not bare 401
```

---

## MEDIUM P2 — Consistency Pass

### P2-1 · DELETE Endpoint Standardisation — 204 No Content

**Rule:** RULE-API14  
**Issue:** Some DELETEs return a JSON envelope instead of 204.

Scan `DiscussionController`, `EvaluationController`, `AssignmentController`, `NotificationController`, `TeamController`, `CourseController`, `AdminUserController` for DELETE methods.

**Fix pattern:**
```java
// BEFORE (any variant):
public ResponseEntity<ApiResponse<Void>> deleteResource(@PathVariable String id) {
    service.delete(HashidService.decodeOrThrow(id));
    return ResponseEntity.ok(ApiResponse.ok(null));
}

// AFTER:
public ResponseEntity<Void> deleteResource(@PathVariable String id) {
    service.delete(HashidService.decodeOrThrow(id));
    return ResponseEntity.noContent().build();
}
```

**Verify:**
```
DELETE any resource (authorised) → 204, empty body, Content-Length: 0
```

---

### P2-2 · RATE_LIMITED → TOO_MANY_REQUESTS Error Code Alignment

**Rule:** RULE-API09  
**Issue:** Two codes exist for rate limiting: `RATE_LIMITED` and `TOO_MANY_REQUESTS`. Unify on `TOO_MANY_REQUESTS`.

Find `RateLimitException` handler in `GlobalExceptionHandler` — change code from `"RATE_LIMITED"` to `"TOO_MANY_REQUESTS"`. Update any Postman assertions that check for `"RATE_LIMITED"`.

**Verify:**
```
Any rate limit hit → 429 { "error": { "code": "TOO_MANY_REQUESTS" } }
Never             → "RATE_LIMITED"
```

---

### P2-3 · Map Request Bodies → Proper DTOs

**Rule:** RULE-API03  
**Issue:** `TeamController.create` and `CourseController` instructor assign/enroll accept `Map<String, String>` as request body, bypassing `@Valid`.

**Fix pattern:**
```java
// BEFORE:
public ResponseEntity<ApiResponse<TeamDto>> create(
        @PathVariable String courseId,
        @RequestBody Map<String, String> body) {
    String name = body.get("name");
}

// AFTER:
public record CreateTeamRequest(
    @NotBlank @Size(max = 100) String name
) {}

public ResponseEntity<ApiResponse<TeamDto>> create(
        @PathVariable String courseId,
        @Valid @RequestBody CreateTeamRequest request) {
}
```

Create the DTO record, add Bean Validation constraints, add `@Valid`, remove `Map` injection.

---

### P2-4 · Remaining @Valid Gaps

**Rule:** RULE-API03

Add `@Valid` to each (verify P0-4 has not already covered some):

| Controller | Method | Request DTO |
|---|---|---|
| `AssignmentGroupController` | `moveAssignment` | `MoveAssignmentGroupRequest` |
| `ModuleController` | `assignModule` | `AssignModuleRequest` |
| `AssignmentController` | `updateRubric` | `UpdateRubricRequest` |
| `SystemController` | `forceLogout` + other mutating endpoints | Respective DTOs |

For each: add `@Valid` to the `@RequestBody` parameter. Verify the DTO has at least `@NotNull` on required fields.

---

### P2-5 · DiscussionController.deletePost — DELETE Consistency

**Rule:** RULE-API14  
Verify `deletePost` returns `ResponseEntity.noContent()` (204, empty body). If it already does, no change needed. If it returns a JSON envelope, apply the P2-1 fix pattern.

---

### P2-6 · DiscussionController — Pagination Gap

**Rule:** RULE-API08  
**Issue:** `GET /courses/{id}/discussions` returns an unbounded list.

Apply the P1-2 pagination pattern: add `page`, `size` params, return `Page<DiscussionDto>`, add `X-Total-Elements` / `X-Total-Pages` headers.

> `GET /discussions/{id}/posts` already has cursor pagination from PRD-17 — do not touch that endpoint.

---

## INFO — Do Not Touch

These are correct by design. Do not "fix" them.

| Finding | Location | Reason |
|---|---|---|
| Binary response, no envelope | `SubmissionController.download`, `EvaluationController.downloadPdf` | Binary responses exempt from ApiResponse envelope |
| Binary response, no envelope | `GradeExportController` | CSV bytes with Content-Disposition header |
| 202 on async commit | `JobController.commit` | Async operation per PRD-21 |
| Auth permitAll routes | Login, refresh, password-reset | Public auth endpoints |
| `/actuator/health` permitAll | SecurityConfig | UptimeRobot health check |
| `/ws/**` permitAll | SecurityConfig | STOMP CONNECT is ticket-gated; HTTP upgrade open is intentional |
| Hashid handling | All controllers | Compliant — `decodeOrThrow` + `InvalidHashException` handler |

---

## Verification Checklist

Run this after completing all tiers. Every item must pass before the PR is opened.

### CRITICAL
```
[ ] SystemController: every JSON endpoint returns ApiResponse envelope
[ ] No raw List<>, Map<>, or domain object returned directly from SystemController
```

### P0 Handlers
```
[ ] EvaluationNotFoundException → 404 EVALUATION_NOT_FOUND (not 500)
[ ] TeamNotFoundException        → 404 TEAM_NOT_FOUND (not 500)
[ ] UnknownCacheException        → 400 UNKNOWN_CACHE (not 500)
[ ] CacheEvictionThrottledException → 429 CACHE_EVICTION_THROTTLED (not 500)
[ ] ForceLogoutBlockedException  → 403 CANNOT_FORCE_LOGOUT_SYSTEM_ADMIN (not 500)
```

### P0 Validation
```
[ ] MethodArgumentNotValidException → 400 VALIDATION_ERROR with fields array
[ ] fields array contains: field name, message, rejectedValue
[ ] IllegalArgumentException → 400 BAD_REQUEST (no message leakage)
[ ] @Valid on AssignmentController.update
[ ] @Valid on EvaluationController.patchComment
[ ] @Valid on AdminUserController.update
```

### P0 Auth
```
[ ] AssignmentController: all POST/PUT/PATCH/DELETE have @PreAuthorize
[ ] AssignmentGroupController: all write endpoints have @PreAuthorize
[ ] ModuleController: all write endpoints have @PreAuthorize
[ ] InstructorScoreController: all write endpoints have @PreAuthorize
[ ] SubmissionController POST: @PreAuthorize("hasAnyRole('STUDENT','INSTRUCTOR')")
[ ] TeamController: student ops STUDENT role, instructor ops INSTRUCTOR+ role
[ ] NotificationController PATCH/DELETE: @PreAuthorize("isAuthenticated()")
[ ] JobController all endpoints: @PreAuthorize INSTRUCTOR+
[ ] AnnouncementController DELETE: @PreAuthorize + 204 response
[ ] Unauthenticated request to any mutating endpoint → 401 (not 403, not 200)
[ ] Wrong role request → 403 FORBIDDEN with ApiResponse error body
```

### P0 Token Revocation
```
[ ] Revoked token → 401 TOKEN_REVOKED JSON body
[ ] Does not proceed to route handler
[ ] AuthenticationEntryPoint also returns JSON (not bare 401)
```

### P1 Architecture
```
[ ] AdminStatsController: no repository injection
[ ] EvaluationController: no rubricScoreRepository injection
[ ] StudentController: no TeamMemberRepository or RubricScoreRepository injection
[ ] All three still return correct data via service layer
[ ] Unbounded list endpoints accept page/size params
[ ] X-Total-Elements and X-Total-Pages headers present on paginated responses
[ ] Page size capped at 100 server-side
[ ] All four upload endpoints have consumes = MULTIPART_FORM_DATA_VALUE
```

### P2 Consistency
```
[ ] All DELETE endpoints return 204 No Content with empty body
[ ] No DELETE returns ApiResponse envelope
[ ] All rate limit errors return TOO_MANY_REQUESTS (not RATE_LIMITED)
[ ] Map<String,String> request bodies replaced with validated DTOs
[ ] Remaining @Valid gaps filled
[ ] Discussion list endpoint paginated
```

### Regression
```
[ ] Postman collection updated for all changed endpoints
[ ] Postman DELETE tests assert 204 (not 200 with body)
[ ] Postman VALIDATION_ERROR tests assert fields array present
[ ] No previously passing test now fails
[ ] Binary download endpoints unchanged (still return Resource/byte[])
[ ] /ws/** still accessible without auth at HTTP layer
[ ] Auth endpoints (login/refresh/password-reset) still permitAll
```
