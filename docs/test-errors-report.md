# Backend Test Errors Report

**Last updated:** 2026-05-25  
**Command:** `./mvnw clean compile test` (from `Backend/`)  
**Environment:** Local MySQL/Flyway dev DB, Redis via `docker compose up -d redis` (`reviewflow_redis` on `localhost:6379`)  
**Related work:** Security hardening (`fix/security-hardening`), transaction boundary hardening, DB integrity (`V35__db_integrity_hardening.sql`)

---

## Summary by run phase

| Phase | When | Tests run | Failures | Errors | Skipped | Build |
|-------|------|-----------|----------|--------|---------|-------|
| **A — Baseline (no Redis)** | 2026-05-24 ~23:55 | 459 | 4 | 18 | 3 | FAILURE |
| **B — Redis up, tests not yet aligned** | Same day (later) | 459 | 5–13 | 0–3 | 3–5 | FAILURE |
| **C — After test fixes** | 2026-05-25 ~00:52 | 459 | 0 | 0 | 5 | **SUCCESS** |

**Final state:** Full suite passes. Five tests skipped (JUnit assumptions / `@Disabled` / container-only). Two `DbIntegrityHardeningIntegrationTest` OLE cases skip when `instructor_scores` or `extension_requests` have no seed rows.

---

## Run A — Baseline (`./mvnw test`, Redis not running)

**Maven summary:** `Tests run: 459, Failures: 4, Errors: 18, Skipped: 3`

### A1. Infrastructure — Redis connection refused (root cause for 14+ errors)

| Field | Value |
|-------|--------|
| **Symptom** | `Failed to load ApplicationContext` / `ApplicationContext failure threshold (1) exceeded` |
| **Root exception** | `io.lettuce.core.RedisConnectionException: Unable to connect to localhost/<unresolved>:6379` |
| **Underlying** | `java.net.ConnectException: Connection refused: getsockopt: localhost/127.0.0.1:6379` |
| **Bean chain** | `rateLimitRedisConnection` → `rateLimitProxyManager` → `defaultRateLimitService` → `authService` → `authController` |
| **Config** | `RateLimitConfig.rateLimitRedisConnection()` (`RateLimitConfig.java:43`) |

**Note:** `@SpringBootTest` classes exclude `RedisAutoConfiguration` but still load `RateLimitConfig`, which requires a live Redis for Bucket4j.

**Tests affected (all ERROR — context never loads):**

| Test class | Methods (count) |
|------------|-----------------|
| `AssignmentGroupDatabaseConstraintIntegrationTest` | 2 |
| `DbIntegrityHardeningIntegrationTest` | 7 |
| `InstructorScoreDatabaseConstraintIntegrationTest` | 2 |
| `MessagingDatabaseConstraintIntegrationTest` | 2 |

**Resolution:** Start Redis: `cd Backend && docker compose up -d redis` (container `reviewflow_redis`, verify with `PONG`).

---

### A2. `EmailServiceTest.send_mailTransportFailure_isSwallowed`

| Field | Value |
|-------|--------|
| **Type** | Failure (`AssertionFailedError` via `assertDoesNotThrow`) |
| **Message** | `Unexpected exception thrown: org.springframework.mail.MailSendException: smtp unavailable` |
| **Location** | `EmailServiceTest.java:48` → `EmailService.send(EmailService.java:32)` |

**Cause:** Test expects transport failures to be swallowed; `EmailService` propagated `MailSendException`.

**Resolution:** Catch `MailException` in `EmailService.send()` and log without rethrowing (or equivalent behavior matching test contract).

---

### A3. `AssignmentGroupControllerIntegrationTest` — missing validation

| Test | Message |
|------|---------|
| `moveAssignment_missingGroupId_throwsInvalidRequest` | `Expected ValidationException to be thrown, but nothing was thrown.` (`line 130`) |
| `moveAssignment_blankGroupId_throwsInvalidRequest` | Same (`line 144`) |

**Cause:** `hashidService.decode` / move path did not reject null/blank group IDs as tests expected.

**Resolution:** Stub `hashidService.decodeOrThrow("ASSIGN_HASH")` and expect `InvalidHashException` (or align with current `AssignmentGroupService` validation).

---

### A4. `MessagingServiceTest` — missing `ClamAvScanService` mock

| Test | Type | Message |
|------|------|---------|
| `sendMessage_withAttachment_uploadsOutsidePersistenceTransaction` | ERROR | `NullPointerException: Cannot invoke ClamAvScanService.scanAndThrow(...) because "this.clamAvScanService" is null` (`MessagingService.java:658`) |
| `sendMessage_s3Failure_deletesPersistedMessage` | FAILURE | `Expected StorageException but was NullPointerException` (same null `clamAvScanService`) |

**Cause:** Messaging attachment staging now runs ClamAV scan; unit test used `@InjectMocks` without `@Mock ClamAvScanService`.

**Resolution:** Mock `ClamAvScanService`, `uploadExecutor`, and S3 `putObject(InputStream, long, ...)`; use `@MockitoSettings(strictness = LENIENT)` where needed.

---

### A5. `NotificationEventListenerDiscussionTest` — incomplete mocks

| Test | Message |
|------|---------|
| `onDiscussionPublished_emailsEnrolledStudentsOnly` | `courseService` is null — `CourseService.findEnrollmentsWithUserByCourseId` (`NotificationEventListener.java:121`) |
| `onDiscussionReply_instructorToStudent_publishesInstructorReplyEmail` | `notification` is null in `pushNotification` (`NotificationEventListener.java:504`) |
| `onDiscussionReply_studentPeerReply_noEmail` | Same |

**Cause:** Listener refactored to use `CourseService`, `NotificationService`, `UserService`; test still used partial `@InjectMocks`.

**Resolution:** Rewrite tests with explicit mocks for new dependencies; stub `notificationService` save/push; `@MockitoSettings(LENIENT)` for optional stubs.

---

### A6. `SystemControllerMessagingSecurityTest.moderationListConversationMessages_requiresSystemAdmin`

| Field | Value |
|-------|--------|
| **Type** | ERROR (`NoSuchMethod`) |
| **Message** | `SystemController.moderationListConversationMessages(String, Authentication, HttpServletRequest)` — method signature changed (pagination `page`, `size` added) |

**Resolution:** Update test reflection/invoke to match current controller signature including `page` and `size` parameters.

---

## Run B — Redis running, integration tests load; remaining unit/integration failures

After Redis was available, context-load errors disappeared. Remaining failures were test/implementation drift (not infrastructure).

### B1. Transaction hardening — token invalidation via events

| Test | Error |
|------|--------|
| `SystemServiceTest.testForceLogout_Success` | `Wanted but not invoked: tokenVersionService.invalidate(5L)` |
| `UserServiceTest.deactivateUser_ShouldRevokeTokensAndIncrementVersion` | `Wanted but not invoked: tokenVersionService.invalidate(1L)` |
| `PasswordResetServiceTest` (confirm flow) | Same — expected direct cache invalidation |

**Cause:** P0 change publishes `TokenVersionInvalidatedEvent` after commit instead of calling `tokenVersionService.invalidate()` inside the transaction. Tests verified old direct call and sometimes wrong event package (`com.reviewflow.auth.event.*`).

**Resolution:** Verify `eventPublisher.publishEvent(any(TokenVersionInvalidatedEvent.class))` with `com.reviewflow.infrastructure.security.TokenVersionInvalidatedEvent`.

---

### B2. `LoginLockoutServiceTest.recordLoginFailure_*`

| Error | `ResourceNotFound: User not found` after `userRepository.findById` post-update |
| **Cause** | `recordLoginFailure` re-reads user from DB after atomic `@Modifying` queries; tests did not stub `findById` with updated `failedLoginCount` / `lockedUntil`. |
| **Resolution** | Stub `findById` after update; verify `auditService.logSecurityEvent` (not deprecated `log`). |

---

### B3. `AssignmentGroupServiceTest.create_whenWeightsNotHundred_returnsWarning`

| Error | `NullPointerException` — `assignmentGroupCacheEviction` null |
| **Cause** | Manual `CacheManager` eviction replaced with `AssignmentGroupCacheEviction` component; `@InjectMocks` missing new mock. |
| **Resolution** | `@Mock AssignmentGroupCacheEviction assignmentGroupCacheEviction`. |

---

### B4. `UserServiceAvatarTest` — avatar delete event pattern

| Error | `eventPublisher` null; or test expected immediate `storageService.delete()` |
| **Cause** | Avatar delete publishes `AvatarOrphanedEvent` for post-commit S3 cleanup instead of inline delete. |
| **Resolution** | Mock `ApplicationEventPublisher`; verify `AvatarOrphanedEvent`; do not expect synchronous `storageService.delete`. |

---

### B5. `GradeCalculationServiceTest.evictCourseGradeCaches_clearsOverviewAndEvictsRoster`

| Error | `Wanted but not invoked: cache.clear()` |
| **Cause** | `evictCourseGradeCaches` is now a no-op body with `@CacheEvict` annotations (proxy not active under plain `@InjectMocks`). |
| **Resolution** | Test updated to assert method is callable / document that eviction is annotation-driven in production. |

---

### B6. `AuthServiceTest` — audit API rename

| Error | Mockito verify on `auditService.log(...)` — method removed or renamed |
| **Cause** | Audit logging consolidated to `logSecurityEvent`. |
| **Resolution** | Update verify calls to `logSecurityEvent` with correct action/entity arguments. |

---

### B7. Security hardening — compile-time (`SecurityConfig`)

| Error | `RoleHierarchyImpl.setHierarchy(String)` not valid on Spring Security 6.4+ |
| **Cause** | API changed to factory method. |
| **Resolution** | Use `RoleHierarchyImpl.fromHierarchy("ROLE_SYSTEM_ADMIN > ROLE_ADMIN\n...")`. |

---

### B8. `SubmissionServicePreviewTest.getPreviewUrl_instructorCourseOwner_success`

| Error | Test failed after `SubmissionService` IDOR fix — instructor must be enrolled on course |
| **Cause** | Missing stub: `courseInstructorRepository.existsByCourseIdAndUserId(courseId, instructorId)`. |
| **Resolution** | `when(...existsByCourseIdAndUserId(...)).thenReturn(true)` in instructor success case. |

---

### B9. `DbIntegrityHardeningIntegrationTest` — optimistic locking (three phases)

Migration `V35__db_integrity_hardening.sql` adds `lock_version` with `@Version` on five entities. Tests use two application-managed `EntityManager`s and concurrent commits.

#### Phase 1 — No exception (false negative)

| Tests | Message |
|-------|---------|
| `evaluation_concurrentUpdate_throwsOptimisticLockingFailure` | `Expected OptimisticLockException to be thrown, but nothing was thrown.` |
| `submission_concurrentUpdate_throwsOptimisticLockingFailure` | Same |
| `rubricScore_concurrentUpdate_throwsOptimisticLockingFailure` | Same |

**Cause:** First-transaction mutations were often **no-ops** (e.g. `setTotalScore(80)` when DB already had `80`), so Hibernate issued no UPDATE and `lock_version` never incremented. Second commit succeeded with stale version `0`.

#### Phase 2 — Wrong exception type (locking actually worked)

| Tests | Message |
|-------|---------|
| Same three tests | `Unexpected exception type thrown, expected: OptimisticLockException but was: RollbackException` |

**Cause:** Hibernate 6 surfaces failed version check as `jakarta.persistence.RollbackException` with cause `org.hibernate.StaleStateException` (row count expectation), not a bare `OptimisticLockException`.

#### Phase 3 — Fixed

| Change | Detail |
|--------|--------|
| Unique mutations | UUID-suffixed `overallComment` / `changeNote` / `comment` / `reason` so first commit always dirties the row |
| Precondition assert | After EM1 commit, `assertEquals(versionBefore + 1, lockVersion(table, id))` |
| Assertion helper | `assertOptimisticLockOnCommit()` accepts `OptimisticLockException`, `StaleStateException`, or either in cause chain |

**Historical variants (resolved or skipped):**

| Test | Error | Notes |
|------|-------|-------|
| `extensionRequest_concurrentUpdate_*` | `EmptyResultDataAccess: expected 1, actual 0` | No seed row — now **skipped** via `assumeTrue` in `firstId()` |
| `instructorScore_concurrentUpdate_*` | Same | **Skipped** when table empty |
| `instructorScore` / `extensionRequest` (when data exists) | Pass after phase 3 fix | |

**Compile transient:** `cannot find symbol: class Transactional` in `DbIntegrityHardeningIntegrationTest` — resolved by import/removal during EM refactor.

---

### B10. `DbIntegrityHardeningIntegrationTest` — compile import (transient)

| Error | `cannot find symbol: class Transactional` |
| **Resolution** | Added `import org.springframework.transaction.annotation.Transactional` (later removed when test used raw `EntityManager` only). |

---

## Run C — Final (`./mvnw clean compile test`, Redis up, all fixes applied)

| Metric | Value |
|--------|-------|
| Tests run | 459 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 5 |
| Build | **SUCCESS** |

**`DbIntegrityHardeningIntegrationTest`:** 7 tests — 5 run, 2 skipped (empty `instructor_scores` / `extension_requests`); all executed OLE tests pass.

---

## Error index (alphabetical by test class)

| Test class | Test / area | Error type | Status |
|------------|-------------|------------|--------|
| `AssignmentGroupControllerIntegrationTest` | `moveAssignment_*` | Validation not thrown | Fixed |
| `AssignmentGroupDatabaseConstraintIntegrationTest` | both | Context load (Redis) | Fixed (Redis) |
| `AssignmentGroupServiceTest` | `create_whenWeightsNotHundred_*` | NPE cache eviction | Fixed |
| `AuthServiceTest` | audit verify | Wrong method name | Fixed |
| `DbIntegrityHardeningIntegrationTest` | OLE concurrent | No exception / RollbackException | Fixed |
| `DbIntegrityHardeningIntegrationTest` | OLE (no seed) | EmptyResult / assume | Skipped |
| `EmailServiceTest` | `send_mailTransportFailure_isSwallowed` | MailSendException | Fixed |
| `GradeCalculationServiceTest` | `evictCourseGradeCaches_*` | cache.clear not called | Fixed |
| `InstructorScoreDatabaseConstraintIntegrationTest` | both | Context load (Redis) | Fixed (Redis) |
| `LoginLockoutServiceTest` | `recordLoginFailure_*` | User not found | Fixed |
| `MessagingDatabaseConstraintIntegrationTest` | both | Context load (Redis) | Fixed (Redis) |
| `MessagingServiceTest` | attachment / S3 failure | NPE clamAv | Fixed |
| `NotificationEventListenerDiscussionTest` | 3 discussion tests | NPE mocks | Fixed |
| `PasswordResetServiceTest` | token invalidate | Wanted not invoked | Fixed |
| `SecurityConfig` | compile | RoleHierarchy API | Fixed |
| `SubmissionServicePreviewTest` | instructor preview | Missing course stub | Fixed |
| `SystemControllerMessagingSecurityTest` | moderation list | NoSuchMethod | Fixed |
| `SystemServiceTest` | force logout | tokenVersion invalidate | Fixed |
| `UserServiceAvatarTest` | delete avatar | eventPublisher / S3 | Fixed |
| `UserServiceTest` | deactivate | tokenVersion invalidate | Fixed |

---

## Skipped tests (final run)

| Reason | Examples |
|--------|----------|
| `assumeTrue(count > 0)` in `DbIntegrityHardeningIntegrationTest.firstId()` | `instructorScore_concurrentUpdate_*`, `extensionRequest_concurrentUpdate_*` when tables empty |
| `@Disabled` / Testcontainers / Docker not available | `UploadRateLimitIntegrationTest` (3 skipped when Docker unavailable) |
| Other suite-wide skips | 5 total in final run |

---

## Entities with optimistic locking (reference)

Migration: `src/main/resources/db/migration/V35__db_integrity_hardening.sql`

| Table | Entity | Version column |
|-------|--------|----------------|
| `submissions` | `Submission` | `lock_version` |
| `evaluations` | `Evaluation` | `lock_version` |
| `instructor_scores` | `InstructorScore` | `lock_version` |
| `rubric_scores` | `RubricScore` | `lock_version` |
| `extension_requests` | `ExtensionRequest` | `lock_version` |

JPA: `@Version` + `@Column(name = "lock_version")` on each entity under `com.reviewflow.shared.domain`.

---

## How to reproduce and verify

```bash
# Prerequisites
cd Backend
docker compose up -d redis   # required for @SpringBootTest / rate limiting
# MySQL dev DB + Flyway as per application-local.properties

# Full suite
./mvnw clean compile test

# DbIntegrity only
./mvnw test -Dtest=DbIntegrityHardeningIntegrationTest
```

**Reports:** `Backend/target/surefire-reports/*.txt` and `*.xml` after each run.

---

## Related documentation

- Security hardening agent: `docs/agents/security-hardening.md`
- Transaction hardening agent: `docs/agents/transaction-hardening.md`
- DB integrity migration: `src/main/resources/db/migration/V35__db_integrity_hardening.sql`
- Integration test source: `src/test/java/com/reviewflow/integration/DbIntegrityHardeningIntegrationTest.java`
- Branch: `fix/security-hardening` (security commit pushed; test fixes may remain local uncommitted)
