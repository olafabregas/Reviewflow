    # ReviewFlow — Transaction Boundary Audit Report

**Audit date:** 2026-05-18  
**Scope:** Full `scan all` — 17 targets per iteration protocol  
**Codebase:** Spring Boot / JPA / MySQL, root package `com.reviewflow`  
**Auditor:** Static analysis (RULE-01 through RULE-15)

---

## Executive Summary

| Metric | Count |
|--------|------:|
| Features / targets scanned | 17 |
| Java files scanned | 62 |
| **Total findings** | **12** |
| CRITICAL | 1 |
| HIGH | 4 |
| MEDIUM | 5 |
| INFO | 2 |

### Top risks

1. **Token-version cache invalidation inside write transactions** — if the DB transaction rolls back after `tokenVersionService.invalidate()`, the cache can hold a bumped version the database does not, weakening forced logout / password-reset guarantees.
2. **Synchronous `ApplicationEventPublisher.publishEvent` inside `@Transactional` methods** — no `@TransactionalEventListener(phase = AFTER_COMMIT)` usage was found anywhere; listeners may run before commit or observe uncommitted data.
3. **Login lockout counter uses read-modify-write** — concurrent failed logins can under-count attempts and delay lockout under credential-stuffing load.

### Clean areas

- No `@Transactional` on repository interfaces (RULE-01).
- No `REQUIRES_NEW` misuse (RULE-04).
- No `@Transactional` on private methods (RULE-10).
- Refresh-token rotation / family revocation paths are single-transaction in `RefreshTokenService` (RULE-12 satisfied for current schema — `family_id` on `refresh_tokens`; no separate `session_contexts` entity in code).
- Messaging uses short TX boundaries via `MessagingPersistenceService` with S3 outside the transaction (good pattern).
- `PasswordResetCleanupScheduler` and `NotificationCleanupScheduler` delegate writes to `@Transactional` services.
- Production profile sets `spring.jpa.open-in-view=false`.

---

## Scan Progress Log

| # | Target | Findings |
|---|--------|----------|
| 1 | auth/service/ | 3 (1 critical, 1 high, 1 medium) |
| 2 | submission/service/ | 1 (medium) |
| 3 | grading/service/ | 2 (1 high, 1 medium) |
| 4 | evaluation/service/ | 1 (high) |
| 5 | extension/service/ | 1 (high) |
| 6 | messaging/service/ | 0 |
| 7 | discussion/service/ | 1 (high) |
| 8 | notification/service/ | 0 |
| 9 | announcement/service/ | 1 (high) |
| 10 | course/service/ | 0 |
| 11 | assignment/service/ | 1 (high) |
| 12 | team/service/ | 1 (high) |
| 13 | user/service/ | 2 (1 critical†, 1 medium) |
| 14 | admin/service/ | 1 (medium) |
| 15 | system/service/ | 2 (1 critical†, 1 high) |
| 16 | infrastructure/scheduling/ | 0 |
| 17 | infrastructure/security/ | 0 |

† Counted once in CRITICAL grouped finding (RULE-14).

---

## Findings by Severity

### CRITICAL

#### [RULE-14 | CRITICAL] Token-version cache invalidated inside `@Transactional` write paths

**Locations (representative):**

| Class | Method | Line(s) |
|-------|--------|---------|
| `PasswordResetService` | `confirm` | 157–158 |
| `SessionService` | `logoutAll` | 112–113 |
| `UserService` | `deactivateUser` | 320–321 |
| `SystemService` | `forceLogout` | 274–275 |

**Issue:** After `userRepository.incrementTokenVersion(...)`, code calls `tokenVersionService.invalidate(userId)`, which immediately evicts/invalidates the Caffeine (or Redis) cache **before** the surrounding transaction commits.

**Context:** ReviewFlow uses token version for immediate access-token invalidation on password reset, logout-all, and admin force-logout. A rolled-back transaction with an already-invalidated cache can leave clients checking a **higher** cached version than persisted — or conversely allow stale reads until TTL if ordering inverts.

**Snippet (`PasswordResetService.confirm`):**

```java
userRepository.incrementTokenVersion(user.getId());
tokenVersionService.invalidate(user.getId());
```

**Fix:** Invalidate only after successful commit — e.g. `@TransactionalEventListener(phase = AFTER_COMMIT)` on a `TokenVersionInvalidatedEvent`, or move invalidation to a post-commit hook. Reads via `getCurrentVersion` should load-through after invalidation. For Redis pub/sub (`RedisTokenVersionStore`), publish invalidation after commit as well.

---

### HIGH

#### [RULE-13 | HIGH] `LoginLockoutService.recordLoginFailure` — non-atomic counter increment

**File:** `com/reviewflow/auth/service/LoginLockoutService.java:36–58`

**Issue:** Failed-attempt count uses in-memory read (`user.getFailedLoginCount()`), increment, and `userRepository.save(user)` without pessimistic lock or atomic SQL `UPDATE ... SET failed_login_count = failed_login_count + 1`.

**Context:** Under concurrent login failures (credential stuffing), two threads can read the same count and both persist `n+1` instead of `n+2`, delaying `ACCOUNT_LOCKED` and audit signals.

**Fix:** Add `@Modifying` query on `UserRepository`, e.g. `incrementFailedLoginCount(@Param id Long)`, and perform lockout timestamp set in the same transaction using the returned/queried count; or `SELECT ... FOR UPDATE` on the user row.

---

#### [RULE-08 | HIGH] Domain events published synchronously inside transactions (no AFTER_COMMIT listeners)

**Scope:** Codebase-wide — **zero** usages of `@TransactionalEventListener` / `AFTER_COMMIT` were found.

**Representative locations:**

| Feature | Class | Method (approx.) |
|---------|-------|------------------|
| Submission | `SubmissionService` | `upload` — `SubmissionUploadedEvent` |
| Evaluation | `EvaluationService` | `publishEvaluation` — `EvaluationPublishedEvent`, `GradePublishedEvent` |
| Extension | `ExtensionRequestService` | `create`, `respond` |
| Grading | `InstructorScoreService` | publish paths (via events) |
| Announcement | `AnnouncementService` | publish handler |
| Discussion | `DiscussionService` | `publishDiscussion` |
| User | `UserService` | `createUser` — `WelcomeEmailEvent` |
| System | `SystemService` | `forceLogout`, `unlockTeam`, etc. |
| Auth | `PasswordResetService` | `requestReset`, `confirm` |

**Issue:** `eventPublisher.publishEvent(...)` runs in the same thread while the transaction may still roll back. Async listeners (`@Async` on `NotificationEventListener` / `EmailEventListener`) may enqueue work that assumes committed state.

**Context:** Grade-posted and submission-received notifications are academically sensitive; duplicate or “ghost” notifications after rollback confuse students and instructors.

**Fix:** Publish domain events from `@TransactionalEventListener(phase = AFTER_COMMIT)` methods, or record outbox rows in the business transaction and dispatch post-commit. Keep `@Async` on downstream email/notification executors.

---

#### [RULE-14 | HIGH] Manual Spring `CacheManager` put/evict inside `@Transactional` methods

**Locations:**

| Class | Pattern |
|-------|---------|
| `AssignmentGroupService` | `evictCourseCaches`, `evictAssignmentCache` during create/update/delete |
| `DiscussionService` | `evictCourseDiscussionList` on create/publish/delete |
| `InstructorScoreService` | `gradeCalculationService.evictCourseGradeCaches` on save/publish |
| `EvaluationService` | `evictCourseGradeCaches` on publish |
| `GradeCalculationService` | `cache.put(courseId, built)` in `getCachedOrBuildRoster` when called under caller TX |
| `SystemService` | `cache.clear()` in `evictCache` |

**Issue:** Direct `cacheManager.getCache(...).evict/put/clear` inside a transaction can expose stale or premature cache state if the transaction rolls back.

**Context:** Grade overview and assignment group caches drive instructor dashboards; inconsistent cache vs DB after failed writes produces wrong roster/grade summaries until TTL.

**Fix:** Prefer `@CacheEvict` on service methods (Spring applies after successful commit for transactional proxies) or AFTER_COMMIT listeners. `GradeCalculationService` population paths should run read-only or post-commit.

---

#### [RULE-08 | HIGH] Extension approval couples extension + submission writes (acceptable invariant, event timing still at risk)

**File:** `com/reviewflow/extension/service/ExtensionRequestService.java:170–215`

**Issue:** `respond` updates `ExtensionRequest` and, when approved, `recalculateIsLate` updates all related `Submission` rows in one transaction — **correct for atomicity** — but still publishes `ExtensionDecidedEvent` before commit (RULE-08).

**Fix:** Keep single TX for DB; move `publishEvent` to AFTER_COMMIT listener.

---

### MEDIUM

#### [RULE-11 | MEDIUM] `GradeExportService.export` — bulk read without `readOnly = true`

**File:** `com/reviewflow/grading/service/GradeExportService.java:89`

**Issue:** `@Transactional` on a large read/export path without `readOnly = true`.

**Context:** PRD-06 exports can touch hundreds of evaluations; skipping read-only optimization increases flush/dirty-check overhead.

**Fix:** `@Transactional(readOnly = true)` on `export`.

---

#### [RULE-06 | MEDIUM] OSIV not disabled in default `application.properties`

**File:** `src/main/resources/application-prod.properties:36` sets `spring.jpa.open-in-view=false`; base `application.properties` does **not** set OSIV.

**Context:** Local/dev profiles default OSIV **on** in Spring Boot, risking lazy-load during serialization if controllers touch associations outside service DTOs.

**Fix:** Set `spring.jpa.open-in-view=false` in base properties or all active profiles; audit `submission/controller` and `grading/controller` for lazy association access.

---

#### [MEDIUM] `SubmissionService.upload` holds DB transaction during S3 upload

**File:** `com/reviewflow/submission/service/SubmissionService.java:113–313`

**Issue:** `@Transactional upload` calls `uploadToStorage` (waits on `uploadExecutor` CompletableFuture up to timeout) before `submissionRepository.save`.

**Context:** Not RULE-05 (async work is storage-only), but the DB connection and transaction remain open for the full upload duration, increasing pool pressure and rollback window.

**Fix:** Upload to S3 first (or use two-phase: upload then short TX for metadata + status), matching the messaging module’s pattern.

---

#### [RULE-07 | MEDIUM] `AuditService.log` participates in caller transaction

**File:** `com/reviewflow/admin/service/AuditService.java:31–82`

**Issue:** Audit rows use default `REQUIRED` propagation — they commit or roll back with business work. Intended for consistency, but failed audit serialization still allows business commit (metadata fallback catch).

**Context:** Security investigations may miss audit entries that rolled back with a failed business operation; conversely, business rollback removes audit evidence of the attempt.

**Fix:** For security-sensitive actions, use `REQUIRES_NEW` on audit methods (RULE-04 exception for Audit naming) or async audit outbox post-commit.

---

#### [MEDIUM] `UserService` avatar delete — DB committed before storage delete; storage failure swallowed

**File:** `com/reviewflow/user/service/UserService.java:205–222`

**Issue:** `userRepository.save` clears `avatarUrl`, then `storageService.delete` runs in try/catch that only logs on failure.

**Context:** Orphaned S3 objects or DB pointing to removed URLs depending on failure direction.

**Fix:** Delete storage first then DB in one logical operation, or use compensating transaction / background cleanup job.

---

### INFO

#### [INFO] `RefreshTokenService` — two-step `sessionGroupId` save

**File:** `com/reviewflow/auth/service/RefreshTokenService.java:67–69`

**Issue:** `createLoginSession` saves twice to set `sessionGroupId` to generated id. Both in one `@Transactional` method — **not a violation**, but could be one insert with assigned id strategy.

---

#### [INFO] `CsvImportCommitService` — exception handling on JSON parse

**File:** `com/reviewflow/grading/service/CsvImportCommitService.java:55–59`

**Issue:** `catch (Exception e) { markFailed(...); return; }` inside `@Transactional` — does not swallow DB errors mid-loop (those still roll back). Acceptable for parse failures; ensure `asyncJobService` job state updates are not rolled back unintentionally if moved to JPA later.

---

## Rule Coverage Matrix

| Rule | Status | Notes |
|------|--------|-------|
| RULE-01 Repository self-TX | Pass | No repo-level `@Transactional` |
| RULE-02 Missing TX on compound writes | Pass* | *Messaging intentionally split; schedulers delegate |
| RULE-03 Exception swallowing in TX | Pass | Minor metadata fallback in `AuditService` only |
| RULE-04 REQUIRES_NEW misuse | Pass | None found |
| RULE-05 Async DB inside TX | Pass | S3/upload executors do not write DB |
| RULE-06 OSIV | Partial | Prod disabled; default profile not |
| RULE-07 Cross-aggregate TX | Advisory | Extension+submission OK; audit coupling noted |
| RULE-08 Dual write / events | **Fail** | Widespread sync publish |
| RULE-09 Rollback config | Pass | No `noRollbackFor` abuse |
| RULE-10 TX on private methods | Pass | None found |
| RULE-11 readOnly bulk reads | Partial | `GradeExportService` |
| RULE-12 Token family atomicity | Pass | Single-TX rotation/revocation |
| RULE-13 Lockout atomicity | **Fail** | R-M-W counter |
| RULE-14 Cache dual-write | **Fail** | Token version + manual cache evict |
| RULE-15 Scheduled jobs | Pass | Cleanup schedulers bounded |

---

## Recommended Action Plan

### P0 — Before next production deploy

1. Move **token version invalidation** to after-commit (all auth/session/system/user logout paths).
2. Add **atomic failed-login increment** query + lockout in same TX.

### P1 — Next sprint

3. Introduce **AFTER_COMMIT event listeners** (or transactional outbox) for submission, grade, extension, announcement, and discussion events.
4. Replace manual **cache evict/put inside TX** with `@CacheEvict` or post-commit eviction for grade/assignment/discussion caches.

### P2 — Hardening

5. Shorten **submission upload** transaction scope (S3 before DB commit).
6. Set **`spring.jpa.open-in-view=false`** globally; add `readOnly = true` to export/report services.
7. Revisit **audit propagation** for security events.

---

## Scan Errors

None — all 62 files in target directories were readable.

---

## Files With No Violations (service layer)

`MessagingPersistenceService`, `MessagingService` (intentional TX split), `NotificationService`, `CourseService`, `ModuleService`, `TeamService` (event timing only where noted above), `AuthCookieIssuer`, `TokenVersionStore` implementations, `StepUpService`, `WsTicketService`, `PdfGenerationService`, `ImportJobService`, `GradeAggregateService`, `DiscussionParticipationService`, `AdminStatsService`, infrastructure schedulers (except noted delegations), `JwtAuthenticationFilter` (read-only token version check — no write TX).

---

*End of report.*
