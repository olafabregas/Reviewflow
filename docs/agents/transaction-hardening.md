# Agent — Transaction Boundary Hardening

**Status:** Final  
**Author:** Roqeeb Olamide Ayorinde  
**Source:** Transaction Boundary Audit Report 2026-05-18 (12 findings)  
**Tier:** 1 — CRITICAL finding blocks production  
**Migration:** Next available after V35 — confirm before writing (see pre-flight)  
**Note:** PRD-16 Content Delivery was at V36 — if this migration is added it moves to V37. Check before starting.

---

## How to Invoke This Agent

```
@docs/agents/transaction-hardening.md fix all
```

Other commands:

| Command | What it does |
|---|---|
| `fix all` | Execute Migration → P0 → P1 → P2 in order, verify each before advancing |
| `fix migration` | Agent 1 migration only |
| `fix critical` | P0 only |
| `fix p1` | P1-1 through P1-4 only |
| `fix p2` | P2-1 and P2-2 only |
| `verify` | Run full verification checklist, no code changes |
| `status` | Show which tiers are complete and which remain |

---

## Already Covered — Verify Exist, Do Not Re-Implement

Before starting, confirm these exist in the codebase:

| Finding | Covered in |
|---|---|
| `GradeExportService readOnly=true` | PRD-DB-Hardening MEDIUM-3G |
| S3 upload outside `@Transactional` | PRD-DB-Hardening + PRD-Async-Hardening |
| SessionContext not cleared on family revoke | PRD-Security-Auth-Hardening MEDIUM-3 |

---

## Agent Instructions — Read Before Starting

You are a senior Spring Boot engineer executing a structured transaction boundary hardening pass on ReviewFlow, a **Spring Boot 4.0.x / Java 21** academic submission and grading platform.

**Rules:**
- Execute in order: Migration → P0 → P1-1 → P1-2 → P1-3 → P1-4 → P2. Do not skip ahead.
- P0 (CRITICAL) must be verified before any P1 work begins.
- **Read every file before touching it.** Verify method names, field names, and propagation settings before changing anything.
- P1-2 is the highest-volume change — read the event classification table carefully before touching any `@EventListener`. Misclassifying an in-process event as AFTER_COMMIT can break same-TX invariants.
- `SystemService.evictCache()` keeps its manual `cache.clear()` — this is intentional. Do not convert it to `@CacheEvict`.
- OSIV must go in base `application.properties`, not just prod.

**Files likely touched:**

| File | Path |
|---|---|
| Migration | `src/main/resources/db/migration/V??__create_pending_s3_deletions.sql` |
| TokenVersionInvalidatedEvent | `infrastructure/security/TokenVersionInvalidatedEvent.java` (new) |
| TokenVersionInvalidationListener | `infrastructure/security/TokenVersionInvalidationListener.java` (new) |
| PasswordResetService | `auth/service/PasswordResetService.java` |
| SessionService | `auth/service/SessionService.java` |
| UserService | `user/service/UserService.java` |
| SystemService | `system/service/SystemService.java` |
| UserRepository | `user/repository/UserRepository.java` |
| LoginLockoutService | `auth/service/LoginLockoutService.java` |
| NotificationEventListener | `notification/event/NotificationEventListener.java` |
| EmailEventListener | `infrastructure/email/EmailEventListener.java` |
| GradeAggregateUpdateListener | `grading/event/GradeAggregateUpdateListener.java` |
| PdfGenerationListener | `evaluation/listener/PdfGenerationListener.java` |
| AssignmentGroupService | `assignment/service/AssignmentGroupService.java` |
| DiscussionService | `discussion/service/DiscussionService.java` |
| InstructorScoreService | `grading/service/InstructorScoreService.java` |
| EvaluationService | `evaluation/service/EvaluationService.java` |
| GradeCalculationService | `grading/service/GradeCalculationService.java` |
| AuditService | `admin/service/AuditService.java` |
| AvatarOrphanedEvent | `user/event/AvatarOrphanedEvent.java` (new) |
| AvatarCleanupListener | `user/event/AvatarCleanupListener.java` (new) |
| PendingS3Deletion | `user/model/entity/PendingS3Deletion.java` (new) |
| PendingS3DeletionRepository | `user/repository/PendingS3DeletionRepository.java` (new) |
| S3CleanupScheduler | `infrastructure/scheduling/S3CleanupScheduler.java` (new) |
| application.properties | `src/main/resources/application.properties` |

---

## Locked Decisions

| Decision | Choice |
|---|---|
| Domain events | `AFTER_COMMIT` selectively — only handlers with irreversible external side effects |
| In-process coordination events | Keep `@EventListener` — no AFTER_COMMIT needed for same-TX invariant checks |
| AuditService security events | `REQUIRES_NEW` — audit persists even if business TX rolls back |
| Avatar delete | Three-layer: S3-first ordering + AFTER_COMMIT delete event + `pending_s3_deletions` cleanup queue |
| OSIV | `spring.jpa.open-in-view=false` in base `application.properties` (not just prod) |
| `SystemService.evictCache()` | Keep manual `cache.clear()` — SYSTEM_ADMIN admin op, not a business TX |

---

## Event Classification — Which Need AFTER_COMMIT

Apply `@TransactionalEventListener(phase = AFTER_COMMIT)` only to handlers that cross process boundaries or trigger irreversible external I/O:

| Event | Handler action | Needs AFTER_COMMIT |
|---|---|---|
| `SubmissionUploadedEvent` | Email + notification to instructor | ✅ Yes |
| `EvaluationPublishedEvent` | Email + notification + PDF generation | ✅ Yes |
| `GradePublishedEvent` | Cache invalidation + grade aggregate | ✅ Yes |
| `ExtensionDecidedEvent` | Email + notification to student | ✅ Yes |
| `AnnouncementPublishedEvent` | Email + notification to enrolled students | ✅ Yes |
| `DiscussionPublishedEvent` | Email + notification to enrolled students | ✅ Yes |
| `WelcomeEmailEvent` | Email to new user | ✅ Yes |
| `TokenVersionInvalidatedEvent` | Cache invalidation (P0 fix) | ✅ Yes |
| `AvatarOrphanedEvent` | S3 delete queue (P2-2) | ✅ Yes |
| `SystemForceLogoutEvent` | Token version invalidation | ✅ Yes |
| In-process invariant checks | Same-TX aggregate coordination | ❌ No — keep `@EventListener` |

**Rule for all future events:** Any handler that calls `emailService`, `notificationService`, `s3Service`, `tokenVersionService`, or `cacheManager` must use AFTER_COMMIT. Enforce via code review.

---

## Execution Protocol for `fix all`

```
STEP 1 — Pre-flight
  Verify "Already Covered" items exist in codebase
  Check migration directory — confirm next version number
  Verify PRD-16 migration reference — update to V37 if V36 is taken

STEP 2 — Agent 1: Migration
  Create pending_s3_deletions migration at correct version number
  Run on fresh schema — verify

STEP 3 — P0: Token Version Cache (CRITICAL)
  New event + listener
  Replace direct invalidation at all four call sites
  Verify before advancing to P1

STEP 4 — P1-1: LoginLockoutService atomic increment

STEP 5 — P1-2: AFTER_COMMIT on external event handlers
  Read event classification table
  Update NotificationEventListener, EmailEventListener,
  GradeAggregateUpdateListener, PdfGenerationListener

STEP 6 — P1-3: Manual cache evictions → @CacheEvict

STEP 7 — P1-4: AuditService REQUIRES_NEW

STEP 8 — P2-1: OSIV disabled globally

STEP 9 — P2-2: Avatar three-layer delete pattern
  New entity + repository + listener + scheduler

STEP 10 — Run full verification checklist
```

---

## Agent 1 — Migration

**File:** `V??__create_pending_s3_deletions.sql` — confirm next version number before creating  
**Purpose:** Deletion intent queue for S3 cleanup job

```sql
-- V??__create_pending_s3_deletions.sql
-- Check the migration directory for the actual next version number before saving

CREATE TABLE pending_s3_deletions (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    s3_key            VARCHAR(500)  NOT NULL,
    entity_type       VARCHAR(50)   NOT NULL,
    -- 'avatar', 'submission', 'material', 'message_attachment'
    reason            VARCHAR(100)  NOT NULL,
    -- 'avatar_replaced', 'avatar_deleted'
    retry_count       INT           NOT NULL DEFAULT 0,
    max_retries       INT           NOT NULL DEFAULT 5,
    created_at        DATETIME      NOT NULL DEFAULT NOW(),
    completed_at      DATETIME      NULL,
    last_attempted_at DATETIME      NULL,
    error_message     VARCHAR(500)  NULL,
    PRIMARY KEY (id)
);

-- Cleanup job query: WHERE completed_at IS NULL AND retry_count < max_retries
-- AND created_at < NOW() - INTERVAL 1 HOUR (grace period)
CREATE INDEX idx_psd_pending
    ON pending_s3_deletions(completed_at, retry_count, created_at);

-- Idempotent dedup check
CREATE INDEX idx_psd_key
    ON pending_s3_deletions(s3_key);
```

**Agent 1 checklist:**
```
[ ] Migration number confirmed — does not conflict with V35 or PRD-16
[ ] PRD-16 migration reference updated to V37 if this takes V36
[ ] Table created with both indexes
[ ] Migration runs cleanly on fresh schema V1 → latest
```

---

## P0 — CRITICAL: Token Version Cache Invalidated Before Commit

**Files:** `PasswordResetService`, `SessionService`, `UserService`, `SystemService`  
**Issue:** `tokenVersionService.invalidate(userId)` is called inside `@Transactional` methods immediately after `userRepository.incrementTokenVersion(userId)`. If the TX rolls back, the cache holds a higher version than the DB — user is permanently locked out until TTL. If invalidation succeeds then rollback happens, force-logout is bypassed.

### Step 1 — New event (create file)

```java
// infrastructure/security/TokenVersionInvalidatedEvent.java
public record TokenVersionInvalidatedEvent(Long userId) {}
```

### Step 2 — New AFTER_COMMIT listener (create file)

```java
// infrastructure/security/TokenVersionInvalidationListener.java

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenVersionInvalidationListener {

    private final TokenVersionService tokenVersionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTokenVersionInvalidated(TokenVersionInvalidatedEvent event) {
        // Fires ONLY after DB TX commits — cache stays consistent with DB
        // If TX rolled back, this never runs — correct behaviour
        tokenVersionService.invalidate(event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleTokenVersionRollback(TokenVersionInvalidatedEvent event) {
        log.debug("Token version invalidation skipped — TX rolled back userId={}",
            event.userId());
    }
}
```

**Redis pub/sub note:** `tokenVersionService.invalidate()` internally calls `redisTemplate.convertAndSend(channel, ...)` to notify other nodes. By delegating through the listener, both local cache eviction and Redis publish happen post-commit automatically — no extra wiring needed.

### Step 3 — Replace direct invalidation at all four call sites

```java
// BEFORE (all four sites — this pattern must be removed entirely):
userRepository.incrementTokenVersion(user.getId());
tokenVersionService.invalidate(user.getId()); // ← inside TX, premature

// AFTER (all four sites):
userRepository.incrementTokenVersion(user.getId());
eventPublisher.publishEvent(new TokenVersionInvalidatedEvent(user.getId()));
// Listener holds until AFTER_COMMIT
```

**The four call sites:**
1. `PasswordResetService.confirm()` — after `incrementTokenVersion`
2. `SessionService.logoutAll()` — after `incrementTokenVersion`
3. `UserService.deactivateUser()` — after `incrementTokenVersion`
4. `SystemService.forceLogout()` — after `incrementTokenVersion`

**After replacement:** grep for `tokenVersionService.invalidate` in all four files — must return zero results.

**Verify:**
```
PasswordResetService.confirm() TX rollback:
  → tokenVersionService.invalidate() NOT called
  → Cache version matches DB — user not locked out

PasswordResetService.confirm() TX commits:
  → TokenVersionInvalidatedEvent fires post-commit
  → tokenVersionService.invalidate() called
  → All active tokens for user invalidated

SystemService.forceLogout() TX rollback:
  → Cache not touched (correct)

grep tokenVersionService.invalidate in PasswordResetService → 0 results
grep tokenVersionService.invalidate in SessionService → 0 results
grep tokenVersionService.invalidate in UserService → 0 results
grep tokenVersionService.invalidate in SystemService → 0 results
```

---

## P1-1 · LoginLockoutService — Atomic Failed Login Counter

**File:** `auth/service/LoginLockoutService.java` (around line 36–58)  
**Issue:** Read-modify-write pattern on `failedLoginCount` — concurrent failures under-count and delay lockout.

### Step 1 — Add atomic `@Modifying` queries to `UserRepository`

**Read `User` entity first** — verify exact field names for `failedLoginCount`, `lastFailedLoginAt`, and `lockedUntil` before writing JPQL.

```java
// UserRepository:

@Modifying
@Query("""
    UPDATE User u
    SET u.failedLoginCount = u.failedLoginCount + 1,
        u.lastFailedLoginAt = :now
    WHERE u.id = :userId
    """)
@Transactional
int incrementFailedLoginCount(
    @Param("userId") Long userId,
    @Param("now") Instant now);

@Modifying
@Query("""
    UPDATE User u
    SET u.lockedUntil = :lockedUntil
    WHERE u.id = :userId
    """)
int lockUser(
    @Param("userId") Long userId,
    @Param("lockedUntil") Instant lockedUntil);
```

### Step 2 — Update `LoginLockoutService.recordLoginFailure()`

```java
@Transactional
public void recordLoginFailure(Long userId) {
    // Atomic increment — no read-modify-write race
    userRepository.incrementFailedLoginCount(userId, Instant.now());

    // Single re-read after atomic write to check lockout threshold
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

    if (user.getFailedLoginCount() >= maxFailedAttempts) {
        Instant lockedUntil = Instant.now()
            .plus(lockoutDurationMinutes, ChronoUnit.MINUTES);
        userRepository.lockUser(userId, lockedUntil);

        log.warn("Account locked userId={} after {} failed attempts",
            hashidService.encode(userId), maxFailedAttempts);
        metrics.recordLockout(); // from PRD-Observability-Hardening MEDIUM-3
    }
}
```

**Verify:**
```
100 concurrent login failures for same account:
  → failedLoginCount = 100 (not under-counted due to race)
  → Lockout triggers at maxFailedAttempts threshold
  → incrementFailedLoginCount is a single UPDATE (not SELECT + UPDATE)
Single failure → count increments correctly (regression)
```

---

## P1-2 · Domain Events — Apply AFTER_COMMIT to External Side-Effect Handlers

**Files:** `NotificationEventListener`, `EmailEventListener`, `GradeAggregateUpdateListener`, `PdfGenerationListener`  
**Issue:** `@EventListener` handlers for events with external side effects run before commit. A TX rollback after the event fires sends notifications/emails for data that was never persisted.

**The fix is annotation-only on listener methods — publisher call sites do not change.**

```java
// BEFORE:
@EventListener
@Async("notificationExecutor")
public void handleEvaluationPublished(EvaluationPublishedEvent event) { ... }

// AFTER:
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async("notificationExecutor")
public void handleEvaluationPublished(EvaluationPublishedEvent event) { ... }
```

**`@TransactionalEventListener` with `@Async` works correctly** — the listener is invoked on the commit thread and immediately dispatches to the async executor. No ordering issues.

**`fallbackExecution` note:** By default, `@TransactionalEventListener` silently drops events published outside a transaction. All publishers in this codebase are inside `@Transactional` methods — no `fallbackExecution = true` needed. If any publisher is ever non-transactional, add it then.

### Apply to `NotificationEventListener` — these handlers only

Read the file first — apply AFTER_COMMIT only to handlers for these events:

| Handler | Event |
|---|---|
| `handleSubmissionUploaded` | `SubmissionUploadedEvent` |
| `handleEvaluationPublished` | `EvaluationPublishedEvent` |
| `handleGradePublished` | `GradePublishedEvent` |
| `handleExtensionDecided` | `ExtensionDecidedEvent` |
| `handleAnnouncementPublished` | `AnnouncementPublishedEvent` |
| `handleDiscussionPublished` | `DiscussionPublishedEvent` |
| `handleWelcomeUser` | `WelcomeEmailEvent` / `UserCreatedEvent` |

Leave any in-process invariant check handlers as `@EventListener`.

### Apply to `EmailEventListener` — all handlers

Every email fires post-commit. Apply `@TransactionalEventListener(phase = AFTER_COMMIT)` to every handler in the file.

### Apply to `GradeAggregateUpdateListener`

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async("gradeAggregateExecutor")
public void handleGradePublished(GradePublishedEvent event) { ... }

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async("gradeAggregateExecutor")
public void handleGradeStructureChanged(GradeStructureChangedEvent event) { ... }
```

### Apply to `PdfGenerationListener`

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async("pdfExecutor")
public void handleEvaluationPublished(EvaluationPublishedEvent event) { ... }
```

**`TokenVersionInvalidationListener`** — already AFTER_COMMIT (created in P0). No change needed.

**Verify:**
```
EvaluationService.publishEvaluation() TX rollback:
  → No notification sent (AFTER_COMMIT not fired)
  → No email sent
  → No PDF generated

EvaluationService.publishEvaluation() TX commits:
  → AFTER_COMMIT fires post-commit
  → Student notification sent
  → Email dispatched to emailTaskExecutor
  → PDF dispatched to pdfExecutor

SubmissionUploadedEvent TX rollback → no notification
All EmailEventListener handlers: TX rollback → no email
GradePublishedEvent TX rollback → no cache invalidation, no aggregate update
```

---

## P1-3 · Manual Cache Evictions — Replace with @CacheEvict

**Files:** `AssignmentGroupService`, `DiscussionService`, `InstructorScoreService`, `EvaluationService`, `GradeCalculationService`  
**Issue:** Direct `cacheManager.getCache(...).evict()` calls inside `@Transactional` methods fire during the TX. Spring's `@CacheEvict` on `@Transactional` methods applies post-commit via the transaction-aware cache proxy — the correct behavior.

**Fix pattern:**

```java
// BEFORE:
@Transactional
public AssignmentGroupDto update(Long groupId, ...) {
    // ...business logic...
    evictCourseCaches(courseId); // fires during TX
    return dto;
}
private void evictCourseCaches(Long courseId) {
    cacheManager.getCache("courseGradeGroups").evict(courseId); // manual
}

// AFTER:
@Transactional
@CacheEvict(value = "courseGradeGroups", key = "#courseId")
public AssignmentGroupDto update(Long groupId, Long courseId, ...) {
    // ...business logic...
    // @CacheEvict fires post-commit via Spring proxy
    return dto;
}
```

**Read each service file first** — understand what caches each manual eviction touches, then map to `@CacheEvict` annotations.

**Affected services:**

| Service | Manual eviction to replace | Replace with |
|---|---|---|
| `AssignmentGroupService` | `evictCourseCaches`, `evictAssignmentCache` | `@CacheEvict` on mutating methods |
| `DiscussionService` | `evictCourseDiscussionList` | `@CacheEvict(value="courseDiscussions", key="#courseId")` |
| `InstructorScoreService` | `gradeCalculationService.evictCourseGradeCaches` | `@CacheEvict(value="gradeOverview", ...)` |
| `EvaluationService` | `evictCourseGradeCaches` | `@CacheEvict` on publish |

**`SystemService.evictCache()`** — keep as manual `cache.clear()`. This is an explicit SYSTEM_ADMIN admin operation, not inside a business TX. Do not convert.

**`GradeCalculationService.getCachedOrBuildRoster`** — `cache.put()` inside a caller transaction exposes potentially uncommitted data to concurrent readers. Fix with `@Transactional(readOnly = true)`:

```java
// BEFORE:
public GradeOverview getCachedOrBuildRoster(Long courseId, ...) {
    GradeOverview built = buildRoster(courseId, request);
    cache.put(courseId, built); // ← inside caller's TX, exposes uncommitted data
    return built;
}

// AFTER:
@Transactional(readOnly = true)
public GradeOverview getCachedOrBuildRoster(Long courseId, ...) {
    // readOnly = true: no dirty data possible
    // cache.put() is safe — all reads are from committed data
    GradeOverview built = buildRoster(courseId, request);
    cache.put(courseId, built);
    return built;
}
```

**Verify:**
```
AssignmentGroupService.update() TX rollback:
  → courseGradeGroups cache NOT evicted
  → Next read returns cached committed data (consistent with DB)

AssignmentGroupService.update() TX commits:
  → @CacheEvict fires post-commit
  → Next read fetches fresh data from DB

SystemService.evictCache() → still uses manual cache.clear() (admin op, not business TX)
GradeCalculationService roster: no manual cache evictions remain inside @Transactional methods
```

---

## P1-4 · AuditService — REQUIRES_NEW for Security Events

**File:** `admin/service/AuditService.java` (around line 31–82)  
**Issue:** All audit rows use default `REQUIRED` propagation — they roll back with the business TX. For security events (login attempts, force logout, lockout), losing evidence on rollback is unacceptable. An attacker whose operation fails must still leave a trail.

**Read `AuditAction` enum first** — verify these action names exist before calling. Do not create duplicates.

```java
// Two propagation levels in AuditService:

// Security events — persists even if business TX rolls back
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void logSecurityEvent(AuditAction action, Long actorId,
                              String ipAddress, String details) {
    AuditLog entry = AuditLog.builder()
        .action(action)
        .actorId(actorId)
        .ipAddress(ipAddress)
        .details(details)
        .timestamp(Instant.now())
        .build();
    auditLogRepository.save(entry);
    // Commits in its own TX — independent of caller's TX state
}

// Business events — rolls back with business TX (correct: no operation happened)
@Transactional
public void logBusinessEvent(AuditAction action, Long actorId, String details) {
    // Same implementation as today
}
```

**Update these call sites to use `logSecurityEvent` (REQUIRES_NEW):**

| Call site | Class | Why |
|---|---|---|
| Login attempt (success + failure) | `AuthService` | Brute force evidence |
| Account lockout | `LoginLockoutService` | Lockout audit trail |
| Force logout | `SystemService` | Admin action evidence |
| Token reuse detection | `RefreshTokenService` | Security incident |
| Password reset request | `PasswordResetService` | Account recovery audit |
| IDOR attempt (403 from course check) | `SubmissionService` | Cross-course access attempt |
| Step-up failure | `StepUpService` | Failed privilege escalation |

All other audit calls remain as `logBusinessEvent` (REQUIRED).

**Verify:**
```
Login fails AND business TX rolls back:
  → Login attempt audit row PERSISTS (REQUIRES_NEW committed independently)

Force logout TX rolls back:
  → Force logout attempt audit row PERSISTS

Normal assignment creation TX rolls back:
  → Assignment creation audit row DOES NOT persist (REQUIRED — rolls back with TX)
  → Correct — no business operation happened
```

---

## P2-1 · OSIV Disabled Globally

**File:** `src/main/resources/application.properties`  
**Issue:** `spring.jpa.open-in-view=false` is only in `application-prod.properties`. Local/dev profiles default to OSIV on — lazy-load bugs in controllers are masked during development.

```properties
# ─── JPA / Hibernate ─────────────────────────────────────────────────────────
spring.jpa.open-in-view=false
# Prod already sets this. Setting globally ensures consistent behaviour across
# all profiles. Controllers must not access lazy associations — use DTOs
# from the service layer. Any LazyInitializationException after this change
# is a real bug that must be fixed in the service layer, not masked by OSIV.
```

**Verify:**
```
Local profile: spring.jpa.open-in-view → false (was true — this is a breaking change for OSIV-dependent code)
Any LazyInitializationException after this change → real bug, fix in service layer
All existing controllers and listeners still work without OSIV
```

---

## P2-2 · Avatar S3 Delete — Three-Layer Pattern

**Files:** `UserService`, new `AvatarOrphanedEvent`, new `AvatarCleanupListener`, new `PendingS3Deletion`, new `PendingS3DeletionRepository`, new `S3CleanupScheduler`

**Three layers, all required:**

### Layer 1 — S3-first ordering (new upload) + DB-first (delete)

**Avatar update (new replaces old):**

```java
@Transactional
public UserDto updateAvatar(Long userId, MultipartFile newAvatar) {
    User user = userRepository.findByIdOrThrow(userId);
    String oldKey = user.getAvatarS3Key(); // null if no existing avatar

    // 1. Upload NEW avatar first (safe — worst case unused object if TX fails)
    String newKey = s3KeyBuilder.avatarKey(hashidService.encode(userId),
        getExtension(newAvatar));
    s3Service.putObject(newKey, newAvatar.getInputStream(), newAvatar.getSize());

    // 2. Update DB — if this fails, unused S3 object exists (cleanup job handles it)
    user.setAvatarS3Key(newKey);
    userRepository.save(user);

    // 3. Queue old avatar deletion AFTER_COMMIT (never inline)
    if (oldKey != null) {
        eventPublisher.publishEvent(new AvatarOrphanedEvent(oldKey, "avatar_replaced"));
    }

    return UserDto.from(user);
}
```

**Avatar delete (remove without replacement):**

```java
@Transactional
public void deleteAvatar(Long userId) {
    User user = userRepository.findByIdOrThrow(userId);
    String oldKey = user.getAvatarS3Key();
    if (oldKey == null) return;

    // 1. Clear DB first (safe — URL gone, S3 object still exists temporarily)
    user.setAvatarS3Key(null);
    userRepository.save(user);

    // 2. Queue S3 deletion AFTER_COMMIT
    eventPublisher.publishEvent(new AvatarOrphanedEvent(oldKey, "avatar_deleted"));
}
```

### Layer 2 — AFTER_COMMIT delete listener (create new files)

```java
// user/event/AvatarOrphanedEvent.java
public record AvatarOrphanedEvent(String s3Key, String reason) {}

// user/event/AvatarCleanupListener.java
@Component
@RequiredArgsConstructor
public class AvatarCleanupListener {

    private final PendingS3DeletionRepository deletionRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAvatarOrphaned(AvatarOrphanedEvent event) {
        // DB TX committed — record deletion intent for cleanup job
        PendingS3Deletion deletion = PendingS3Deletion.builder()
            .s3Key(event.s3Key())
            .entityType("avatar")
            .reason(event.reason())
            .createdAt(Instant.now())
            .build();
        deletionRepository.save(deletion);
    }
}
```

### Layer 3 — Nightly cleanup job (create new file)

```java
// infrastructure/scheduling/S3CleanupScheduler.java
@Component
@RequiredArgsConstructor
@Slf4j
public class S3CleanupScheduler {

    private final PendingS3DeletionRepository deletionRepository;
    private final S3Service s3Service;

    @Value("${s3.cleanup.grace-period-hours:1}")
    private int gracePeriodHours;

    @Value("${s3.cleanup.batch-size:50}")
    private int batchSize;

    @Scheduled(cron = "${s3.cleanup.cron:0 0 2 * * *}") // nightly 2am
    public void reconcileOrphanedObjects() {
        Instant cutoff = Instant.now().minus(gracePeriodHours, ChronoUnit.HOURS);

        List<PendingS3Deletion> pending = deletionRepository
            .findPendingOlderThan(cutoff, PageRequest.of(0, batchSize));

        if (pending.isEmpty()) return;

        log.info("S3 cleanup: processing {} pending deletions", pending.size());

        for (PendingS3Deletion entry : pending) {
            try {
                s3Service.deleteObjectSilently(entry.getS3Key());
                entry.setCompletedAt(Instant.now());
                deletionRepository.save(entry);
            } catch (Exception e) {
                entry.setRetryCount(entry.getRetryCount() + 1);
                entry.setErrorMessage(e.getMessage());
                entry.setLastAttemptedAt(Instant.now());
                deletionRepository.save(entry);

                if (entry.getRetryCount() >= entry.getMaxRetries()) {
                    log.error("S3 cleanup: dead-lettered key={} after {} retries",
                        entry.getS3Key(), entry.getMaxRetries());
                }
            }
        }
    }
}
```

**New `PendingS3Deletion` entity:**

```java
@Entity
@Table(name = "pending_s3_deletions")
@Builder
public class PendingS3Deletion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String s3Key;
    private String entityType; // avatar, submission, material, message_attachment
    private String reason;
    private int retryCount = 0;
    private int maxRetries = 5;
    private Instant createdAt;
    private Instant completedAt;      // null until completed
    private Instant lastAttemptedAt;  // null until first attempt
    private String errorMessage;
}
```

**New `PendingS3DeletionRepository`:**

```java
public interface PendingS3DeletionRepository extends JpaRepository<PendingS3Deletion, Long> {

    @Query("""
        SELECT p FROM PendingS3Deletion p
        WHERE p.completedAt IS NULL
          AND p.retryCount < p.maxRetries
          AND p.createdAt < :cutoff
        ORDER BY p.createdAt ASC
        """)
    List<PendingS3Deletion> findPendingOlderThan(
        @Param("cutoff") Instant cutoff, Pageable pageable);
}
```

**Add to `application.properties`:**

```properties
# ─── S3 orphan cleanup ────────────────────────────────────────────────────────
s3.cleanup.cron=${S3_CLEANUP_CRON:0 0 2 * * *}
s3.cleanup.grace-period-hours=${S3_CLEANUP_GRACE_HOURS:1}
s3.cleanup.batch-size=${S3_CLEANUP_BATCH:50}
```

**Known limitation (documented, not blocking):** If a TX rolls back after S3 upload but before DB update (avatar update path), the new S3 object is orphaned with no `pending_s3_deletions` entry. `AvatarOrphanedEvent` uses AFTER_COMMIT so it does not fire on rollback. Mitigation: a future S3 bucket-scan job can find objects with no DB reference. Tracked in improvements backlog — not a blocker for this PRD.

**Verify:**
```
Avatar update TX commits:
  → New avatar in S3
  → DB updated to new key
  → AvatarOrphanedEvent fires post-commit
  → pending_s3_deletions row created for old key
  → Cleanup job deletes old key within 1hr

Avatar update TX rolls back:
  → No AvatarOrphanedEvent (AFTER_COMMIT not fired)
  → No pending_s3_deletions row created (correct)

Cleanup job: S3 delete fails transiently → retryCount incremented, retried next run
Cleanup job: after maxRetries → dead-lettered with log.error
```

---

## Verification Checklist

Run in full before opening the PR.

### CRITICAL — Token Version
```
[ ] PasswordResetService.confirm() TX rollback → tokenVersionService.invalidate() NOT called
[ ] PasswordResetService.confirm() TX commits → invalidate() called post-commit
[ ] SessionService.logoutAll() TX commits → invalidate() called post-commit
[ ] SystemService.forceLogout() TX commits → invalidate() called post-commit
[ ] TokenVersionInvalidationListener uses @TransactionalEventListener(AFTER_COMMIT)
[ ] grep tokenVersionService.invalidate in PasswordResetService → 0 results
[ ] grep tokenVersionService.invalidate in SessionService → 0 results
[ ] grep tokenVersionService.invalidate in UserService → 0 results
[ ] grep tokenVersionService.invalidate in SystemService → 0 results
```

### P1-1 — Login Lockout Atomic
```
[ ] 100 concurrent failed logins → failedLoginCount = 100 (not under-counted)
[ ] incrementFailedLoginCount is @Modifying SQL UPDATE (not read-modify-write)
[ ] Lockout triggers at correct threshold under concurrency
```

### P1-2 — AFTER_COMMIT Events
```
[ ] EvaluationPublishedEvent TX rollback → no notification, no email, no PDF
[ ] EvaluationPublishedEvent TX commits → notification + email + PDF all fire
[ ] SubmissionUploadedEvent TX rollback → no notification
[ ] All @Async("notificationExecutor") handlers → @TransactionalEventListener(AFTER_COMMIT)
[ ] All @Async("emailTaskExecutor") handlers → @TransactionalEventListener(AFTER_COMMIT)
[ ] GradeAggregateUpdateListener handlers → @TransactionalEventListener(AFTER_COMMIT)
[ ] PdfGenerationListener → @TransactionalEventListener(AFTER_COMMIT)
[ ] In-process invariant handlers kept as @EventListener (not converted)
```

### P1-3 — Cache Evictions
```
[ ] AssignmentGroupService.update() TX rollback → cache NOT evicted
[ ] AssignmentGroupService.update() TX commits → cache evicted post-commit
[ ] No manual cacheManager.getCache().evict() inside business @Transactional methods
[ ] SystemService.evictCache() still uses manual cache.clear() (admin op — unchanged)
[ ] GradeCalculationService roster cache population has @Transactional(readOnly=true)
```

### P1-4 — Audit REQUIRES_NEW
```
[ ] Login attempt audit persists when login TX rolls back
[ ] Force logout audit persists when forceLogout TX rolls back
[ ] Normal assignment creation audit rolls back with business TX
[ ] logSecurityEvent() has @Transactional(propagation=REQUIRES_NEW)
[ ] logBusinessEvent() has default @Transactional (REQUIRED)
```

### P2 — Avatar + OSIV
```
[ ] spring.jpa.open-in-view=false in base application.properties (not just prod)
[ ] Avatar update: new S3 object + DB updated + old key in pending_s3_deletions
[ ] Avatar update TX rollback → no pending_s3_deletions row created
[ ] Cleanup job processes pending deletions and marks completed
[ ] Cleanup job retries on S3 failure, dead-letters after maxRetries
[ ] pending_s3_deletions table exists in DB (migration ran)
```

### Regression
```
[ ] Force logout still works end-to-end (token invalidated post-commit, not earlier)
[ ] Login lockout still triggers (now atomic, threshold correctly hit)
[ ] Student notification on evaluation publish still fires
[ ] Email on password reset still sent
[ ] Grade aggregate cache still updated after grade publish
[ ] Avatar upload/delete still works end-to-end
[ ] All Postman tests pass
[ ] No LazyInitializationException in active controller/service paths (OSIV disabled)
```
