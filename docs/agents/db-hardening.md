# Agent — Database Integrity Hardening

**Status:** Final  
**Author:** Roqeeb Olamide Ayorinde  
**Source:** Database Integrity Audit Report 2026-05-18 (21 findings)  
**Tier:** 1 — CRITICAL findings block production deploy  
**Migration:** V35__db_integrity_hardening.sql  
**PRD-16 content delivery:** Moves from V35 → V36 (conflict resolved here — update PRD-16 migration reference before starting)

---

## How to Invoke This Agent

```
@docs/agents/db-hardening.md fix all
```

Other commands:

| Command | What it does |
|---|---|
| `fix all` | Execute Agent 1 → 2 → 3 in order, verify each before advancing |
| `fix migration` | Agent 1 — V35 migration only |
| `fix entities` | Agent 2 — entity changes only |
| `fix services` | Agent 3 — service + handler changes only |
| `write tests` | Agent 5 — generate all test stubs |
| `verify` | Run full Agent 6 checklist, no code changes |
| `status` | Show which agents are complete and which remain |

---

## Agent Instructions — Read Before Starting

You are a senior Spring Boot / JPA engineer executing a structured database integrity hardening pass on ReviewFlow, a **Spring Boot 4.0.x / Java 21 / MySQL** academic submission and grading platform. Highest existing migration: **V34**.

**Rules:**
- Execute in agent order: Agent 1 → Agent 2 → Agent 3 → Agent 5. Do not skip ahead.
- **Read every file before touching it.** Never assume column names, entity field names, or existing annotations.
- V35 is claimed by this PRD. PRD-16 content delivery moves to V36 — update that PRD's migration reference before starting.
- The Agent 6 checklist must pass fully before the PR is opened.

---

## Locked Decisions

| Decision | Choice |
|---|---|
| Optimistic lock error code | `CONCURRENT_MODIFICATION` |
| 409 response body | Includes `currentVersion` + `conflictedAt` |
| Submission lock column | `lock_version BIGINT` (not `version` — avoids clash with business `versionNumber`) |
| `buildRoster` strategy | Single bulk DB query — compute all standings in DB, return directly |
| CSV commit inserts | `saveAll()` batch — faster, still atomic in one `@Transactional` |

---

## Execution Protocol for `fix all`

```
STEP 1 — Pre-flight
  Verify PRD-16 migration reference updated to V36
  Verify highest existing migration is V34

STEP 2 — Agent 1: Migration V35
  Create V35__db_integrity_hardening.sql
  Run cleanly on fresh schema V1 → V35
  Run verification queries — all assert 0 / 1 row as noted

STEP 3 — Agent 2: Entity Changes
  Add @Version lockVersion to 5 entities
  Add createdAt/updatedAt to Submission
  Narrow CascadeType.ALL on Conversation, Course, Team
  Verify project compiles

STEP 4 — Agent 3: Service + Handler Changes
  3A GlobalExceptionHandler — OptimisticLockingFailureException
  3B GradeCalculationService — bulk roster query
  3C CsvImportCommitService — batch user lookup + saveAll
  3D AssignmentService — JOIN FETCH team members
  3E AssignmentService — paginate submissions
  3F EvaluationService — batch criteria lookup
  3G GradeExportService — readOnly = true
  3H MessageRepository — Pageable on moderation query
  Add properties to application.properties

STEP 5 — Agent 5: Tests
  Write optimistic locking tests (5 entities)
  Write N+1 elimination tests
  Write cascade safety tests
  Write DB constraint existence tests

STEP 6 — Agent 6: Review checklist
  Run full checklist — report pass/fail per item
```

---

## Agent 1 — Migration V35

**File:** `src/main/resources/db/migration/V35__db_integrity_hardening.sql`

```sql
-- V35__db_integrity_hardening.sql
-- Database integrity hardening: optimistic locking, missing indexes,
-- cascade safety, and audit timestamps.
-- After V34 (discussions). PRD-16 content delivery moves to V36.

-- ── Phase 1: Optimistic locking columns ──────────────────────────────────────
-- Column name: lock_version on all 5 tables
-- Submission: lock_version is JPA lock, versionNumber is the business document revision

ALTER TABLE submissions
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0
    COMMENT 'JPA optimistic lock version — distinct from versionNumber (document revision)';

ALTER TABLE evaluations
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0
    COMMENT 'JPA optimistic lock version';

ALTER TABLE instructor_scores
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0
    COMMENT 'JPA optimistic lock version';

ALTER TABLE rubric_scores
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0
    COMMENT 'JPA optimistic lock version';

ALTER TABLE extension_requests
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0
    COMMENT 'JPA optimistic lock version';

-- ── Phase 2: Missing indexes ──────────────────────────────────────────────────
-- conversations.course_id — missing from V32, needed for course inbox + moderation
CREATE INDEX idx_conv_course
    ON conversations(course_id);

-- ── Phase 3: Submission audit timestamps ─────────────────────────────────────
-- Add created_at / updated_at aligned with sibling entities (Evaluation, ExtensionRequest)

ALTER TABLE submissions
    ADD COLUMN created_at DATETIME NOT NULL DEFAULT NOW()
    COMMENT 'Populated from uploaded_at for existing rows via backfill below',
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT NOW() ON UPDATE NOW();

-- Backfill created_at from uploaded_at for existing rows
UPDATE submissions SET created_at = uploaded_at WHERE created_at = '1970-01-01 00:00:00';

-- ── Verification queries (run after migration, assert zero/one rows) ──────────
-- SELECT COUNT(*) FROM submissions WHERE lock_version IS NULL;          → 0
-- SELECT COUNT(*) FROM evaluations WHERE lock_version IS NULL;          → 0
-- SELECT COUNT(*) FROM instructor_scores WHERE lock_version IS NULL;    → 0
-- SELECT COUNT(*) FROM rubric_scores WHERE lock_version IS NULL;        → 0
-- SELECT COUNT(*) FROM extension_requests WHERE lock_version IS NULL;   → 0
-- SHOW INDEX FROM conversations WHERE Key_name = 'idx_conv_course';     → 1 row
```

**Agent 1 checklist:**
```
[ ] lock_version added to 5 tables with DEFAULT 0
[ ] idx_conv_course created on conversations(course_id)
[ ] Submission created_at / updated_at added
[ ] Backfill UPDATE runs cleanly against existing seed data
[ ] No existing migration files modified
[ ] Migration runs cleanly on fresh schema V1 → V35
```

---

## Agent 2 — Entity Changes

**Read each entity file before modifying.** Verify existing field names do not conflict.

### Submission.java

```java
// Add JPA optimistic lock — MUST use lock_version column, NOT 'version' (clashes with versionNumber)
@Version
@Column(name = "lock_version")
private Long lockVersion;

// Add audit timestamps — aligned with sibling entities
@Column(name = "created_at", nullable = false, updatable = false)
private Instant createdAt;

@Column(name = "updated_at", nullable = false)
private Instant updatedAt;

@PrePersist
protected void onCreate() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
}

@PreUpdate
protected void onUpdate() {
    this.updatedAt = Instant.now();
}
```

> **Critical:** `versionNumber` is the existing business document version (submission revision 1, 2, 3...). `lockVersion` is the JPA optimistic lock. They must coexist. Never merge or rename them.

### Evaluation.java

```java
@Version
@Column(name = "lock_version")
private Long lockVersion;
```

### InstructorScore.java

```java
@Version
@Column(name = "lock_version")
private Long lockVersion;
```

### RubricScore.java

```java
@Version
@Column(name = "lock_version")
private Long lockVersion;
```

### ExtensionRequest.java

```java
@Version
@Column(name = "lock_version")
private Long lockVersion;
```

### Conversation.java — Narrow CascadeType.ALL on messages

```java
// BEFORE:
@OneToMany(mappedBy = "conversation",
           cascade = CascadeType.ALL,
           orphanRemoval = true)
private List<Message> messages;

// AFTER:
@OneToMany(mappedBy = "conversation",
           cascade = {CascadeType.PERSIST, CascadeType.MERGE})
private List<Message> messages;
// orphanRemoval removed — message deletion is explicit via MessagingService
// CascadeType.ALL removed — accidental conversation remove no longer cascades history
```

### Course.java — Narrow CascadeType.ALL on enrollment sets

Read the current field annotations on both `enrollments` and instructor sets before changing.

```java
// BEFORE (pattern on both sets):
@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
private Set<CourseEnrollment> enrollments;

// AFTER:
@OneToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
private Set<CourseEnrollment> enrollments;
// Apply same pattern to CourseInstructor set
```

### Team.java — Narrow CascadeType.ALL on members

```java
// BEFORE:
@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
private List<TeamMember> members;

// AFTER:
@OneToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
private List<TeamMember> members;
// orphanRemoval removed — member removal is explicit via TeamService
```

**Agent 2 checklist:**
```
[ ] @Version field (lockVersion) added to 5 entities
[ ] Each @Version maps to column name 'lock_version' via @Column(name=...)
[ ] Submission.versionNumber (business field) is UNCHANGED
[ ] Submission has createdAt + updatedAt with @PrePersist / @PreUpdate
[ ] Conversation.messages: CascadeType.ALL removed, orphanRemoval removed
[ ] Course enrollment/instructor sets: CascadeType narrowed to PERSIST/MERGE
[ ] Team.members: CascadeType narrowed to PERSIST/MERGE
[ ] Project compiles with no errors
[ ] No existing DTO fields broken
```

---

## Agent 3 — Service + Handler Changes

### 3A · GlobalExceptionHandler — OptimisticLockingFailureException (409)

**File:** `com.reviewflow.shared.exception.GlobalExceptionHandler`

**Read `ApiResponse` and `ErrorResponse` first.** If `ErrorResponse` does not support extra fields, add a nullable `Map<String, Object> details` field with `@JsonInclude(NON_NULL)` so existing handlers are unaffected.

```java
@ExceptionHandler(OptimisticLockingFailureException.class)
public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailure(
        OptimisticLockingFailureException ex) {

    Long currentVersion = extractCurrentVersion(ex); // attempt to extract from ex; null if unavailable
    String conflictedAt = Instant.now().toString();

    Map<String, Object> details = new LinkedHashMap<>();
    if (currentVersion != null) {
        details.put("currentVersion", currentVersion);
    }
    details.put("conflictedAt", conflictedAt);

    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.errorWithDetails(
            "CONCURRENT_MODIFICATION",
            "This record was modified by another user. " +
            "Please review the latest version and resubmit.",
            details));
}
```

**Response body the client receives:**
```json
{
  "success": false,
  "error": {
    "code": "CONCURRENT_MODIFICATION",
    "message": "This record was modified by another user. Please review the latest version and resubmit.",
    "currentVersion": 7,
    "conflictedAt": "2026-05-19T14:32:00Z"
  },
  "timestamp": "..."
}
```

**Where this fires:**
- `EvaluationService` — concurrent score edits or publish
- `InstructorScoreService` — concurrent CSV import + manual entry
- `SubmissionService` — concurrent metadata updates
- `ExtensionRequestService` — two instructors approve/deny simultaneously

**Verify:**
```
Two concurrent PATCH requests to same evaluation → first 200 OK, second 409 CONCURRENT_MODIFICATION
409 body contains currentVersion and conflictedAt
Not → 500 ObjectOptimisticLockingFailureException stack trace
```

---

### 3B · GradeCalculationService.buildRoster — Single Bulk Query

**File:** `grading/service/GradeCalculationService.java` (around line 587)  
**Issue:** Loops enrollments, calls `calculateOverviewCached(courseId, studentId)` per student → N+1.

**Read the actual entity column names before writing the native query.** The query template below uses expected names — verify `evaluations.total_score`, `assignments.max_score`, `assignment_groups.weight` match the real columns.

**New repository method (add to `GradeOverviewRepository` or appropriate repository):**

```java
@Query(value = """
    SELECT
        u.id                                    AS studentId,
        u.first_name                            AS firstName,
        u.last_name                             AS lastName,
        u.email                                 AS email,
        u.avatar_url                            AS avatarUrl,
        COALESCE(AVG(
            CASE WHEN e.is_draft = false AND e.total_score IS NOT NULL
                 THEN (e.total_score / a.max_score) * ag.weight
                 ELSE NULL
            END
        ), 0)                                   AS weightedAverage,
        COUNT(DISTINCT CASE
            WHEN e.is_draft = false THEN e.id END)  AS gradedCount,
        COUNT(DISTINCT a.id)                    AS totalAssignments,
        MAX(s.uploaded_at)                      AS lastSubmissionAt
    FROM course_enrollments ce
    JOIN users u ON ce.user_id = u.id
    LEFT JOIN submissions s
        ON (s.student_id = u.id OR s.team_id IN (
            SELECT tm.team_id FROM team_members tm WHERE tm.user_id = u.id
        ))
        AND s.assignment_id IN (
            SELECT id FROM assignments WHERE course_id = :courseId
        )
    LEFT JOIN evaluations e ON e.submission_id = s.id
    LEFT JOIN assignments a ON a.id = s.assignment_id
    LEFT JOIN assignment_groups ag ON ag.id = a.group_id
    WHERE ce.course_id = :courseId
        AND u.role = 'STUDENT'
    GROUP BY u.id, u.first_name, u.last_name, u.email, u.avatar_url
    ORDER BY u.last_name ASC, u.first_name ASC
    """, nativeQuery = true)
List<StudentStandingRow> buildRosterBulk(@Param("courseId") Long courseId);
```

**New projection interface:**
```java
public interface StudentStandingRow {
    Long getStudentId();
    String getFirstName();
    String getLastName();
    String getEmail();
    String getAvatarUrl();
    Double getWeightedAverage();
    Integer getGradedCount();
    Integer getTotalAssignments();
    Instant getLastSubmissionAt();
}
```

**Updated `buildRoster` in `GradeCalculationService`:**
```java
public List<StudentStandingDto> buildRoster(Long courseId, RosterRequest request) {
    List<StudentStandingRow> rows = gradeOverviewRepository.buildRosterBulk(courseId);

    return rows.stream()
        .map(row -> {
            double avg = row.getWeightedAverage() != null ? row.getWeightedAverage() : 0.0;
            return StudentStandingDto.builder()
                .studentId(hashidService.encode(row.getStudentId()))
                .firstName(row.getFirstName())
                .lastName(row.getLastName())
                .email(row.getEmail())
                .avatarUrl(row.getAvatarUrl())
                .weightedAverage(avg)
                .letterGrade(letterGradeFor(avg))
                .atRisk(avg < atRiskThreshold)
                .gradedCount(row.getGradedCount())
                .totalAssignments(row.getTotalAssignments())
                .lastSubmissionAt(row.getLastSubmissionAt())
                .build();
        })
        .toList();
}
```

**Also:** Add `classStatistics` cache eviction on grade publish. Cache roster result for `courseId` with 3-minute TTL.

**Verify:**
```
buildRoster for 200-student course → ≤ 5 DB queries total (not 200+)
Roster result matches per-student calculateOverview results
```

---

### 3C · CsvImportCommitService — Batch User Lookup + saveAll

**File:** `grading/service/CsvImportCommitService.java` (around line 68–80)  
**Issue:** `userRepository.findByEmail` called once per CSV row → 500 lookups for a 500-row import.

```java
@Transactional
public void commitImport(String jobId, Long assignmentId) {
    List<ValidatedRow> rows = fetchValidatedRowsFromS3(jobId); // S3 read outside TX

    // Pre-load ALL users in one query
    Set<String> emails = rows.stream()
        .map(ValidatedRow::getStudentEmail)
        .collect(Collectors.toSet());

    Map<String, User> usersByEmail = userRepository
        .findByEmailIn(emails) // single IN query
        .stream()
        .collect(Collectors.toMap(User::getEmail, Function.identity()));

    // Build all InstructorScore objects in memory
    List<InstructorScore> scores = rows.stream()
        .map(row -> {
            User student = usersByEmail.get(row.getStudentEmail());
            if (student == null) {
                throw new ImportCommitException(
                    "Student not found: " + row.getStudentEmail());
            }
            return InstructorScore.builder()
                .assignmentId(assignmentId)
                .studentId(student.getId())
                .score(row.getScore())
                .maxScore(row.getMaxScore())
                .comment(row.getComment())
                .isPublished(false)
                .enteredBy(/* instructorId from job state */)
                .build();
        })
        .toList();

    // Batch INSERT — single round trip, still atomic in same @Transactional
    instructorScoreRepository.saveAll(scores);

    deleteImportS3Objects(jobId); // cleanup after successful commit
}
```

**New repository method:**
```java
// UserRepository:
List<User> findByEmailIn(Collection<String> emails);
```

**Note on batch behaviour:** If `InstructorScore` uses `@GeneratedValue(strategy = GenerationType.IDENTITY)`, Hibernate batching is disabled at the SQL level (each INSERT needs the generated ID immediately). `saveAll` still reduces application-level round trips vs individual `save`. Document this constraint in a code comment.

**Verify:**
```
500-row CSV commit → userRepository.findByEmail called 0 times
userRepository.findByEmailIn called exactly 1 time
All 500 rows committed atomically — rollback on any failure
```

---

### 3D · AssignmentService — JOIN FETCH for Team Members in Gradebook

**File:** `assignment/service/AssignmentService.java` (around line 740–748)  
**Issue:** `getGradebookForAssignment` lazy-loads `team.getMembers()` per team → 1+N queries.

**New fetch query in `TeamRepository`:**
```java
@Query("""
    SELECT DISTINCT t FROM Team t
    LEFT JOIN FETCH t.members m
    LEFT JOIN FETCH m.user
    WHERE t.assignment.id = :assignmentId
    """)
List<Team> findByAssignmentIdWithMembers(@Param("assignmentId") Long assignmentId);
```

**Update `AssignmentService.getGradebookForAssignment`:**
```java
// BEFORE:
List<Team> teams = teamRepository.findByAssignmentId(assignmentId);
// (triggers lazy member load per team)

// AFTER:
List<Team> teams = teamRepository.findByAssignmentIdWithMembers(assignmentId);
// (single query with JOIN FETCH — no additional member queries)
```

**Verify:**
```
getGradebookForAssignment for 40-team assignment → 1 query, not 41
All team members still present in response
```

---

### 3E · AssignmentService — Paginate getSubmissionsForAssignment

**File:** `assignment/service/AssignmentService.java` (around line 715–733)  
**Issue:** Returns unbounded `List<Submission>` — all versions loaded and filtered in memory.

```java
// Replace in-memory filtering with paginated latest-only queries:
Page<Submission> submissions = assignmentType == TEAM
    ? submissionRepository.findLatestTeamSubmissionsByAssignmentId(
          assignmentId, PageRequest.of(page, size))
    : submissionRepository.findLatestIndividualSubmissionsByAssignmentId(
          assignmentId, PageRequest.of(page, size));
```

Expose `page` and `size` from the controller endpoint. Add `X-Total-Elements` header per the API hardening PRD.

**Verify:**
```
GET /assignments/{id}/submissions?page=0&size=20 → 20 submissions max
X-Total-Elements header present
No full table scan in DB
```

---

### 3F · EvaluationService.setScores — Batch Criteria Lookup

**File:** `evaluation/service/EvaluationService.java` (around line 161–195)  
**Issue:** `rubricCriterionRepository.findById` called per criterion in loop → N queries per rubric save.

```java
// BEFORE (in loop):
for (ScoreEntry entry : scoreEntries) {
    RubricCriterion criterion = rubricCriterionRepository
        .findById(entry.getCriterionId()) // N queries
        .orElseThrow(...);
}

// AFTER — pre-load all criteria in one query:
Set<Long> criterionIds = scoreEntries.stream()
    .map(ScoreEntry::getCriterionId)
    .collect(Collectors.toSet());

Map<Long, RubricCriterion> criteriaMap = rubricCriterionRepository
    .findAllById(criterionIds) // single IN query
    .stream()
    .collect(Collectors.toMap(RubricCriterion::getId, Function.identity()));

for (ScoreEntry entry : scoreEntries) {
    RubricCriterion criterion = criteriaMap.get(entry.getCriterionId());
    if (criterion == null) throw new ResourceNotFoundException(
        "Rubric criterion not found: " + entry.getCriterionId());
    // ... rest of loop body unchanged
}
```

**Verify:**
```
setScores for 10-criterion rubric → 1 query (not 10)
All rubric scores still saved correctly
```

---

### 3G · GradeExportService — Add readOnly = true

**File:** `grading/service/GradeExportService.java` (around line 89)

```java
// BEFORE:
@Transactional
public byte[] export(Long courseId, Long assignmentId) {

// AFTER:
@Transactional(readOnly = true)
public byte[] export(Long courseId, Long assignmentId) {
```

Hibernate disables dirty checking, write-capable TX, and flush on `readOnly = true`. Meaningful CPU saving on large roster exports.

**Verify:**
```
Grade export still produces correct CSV output
No write operations attempted during export
```

---

### 3H · MessageRepository — Add Pageable to Moderation Query

**File:** `messaging/repository/MessageRepository.java` (around line 26)  
**Issue:** `findAllByConversationIdForModeration` returns full unbounded message history.

```java
// BEFORE:
List<Message> findAllByConversationIdForModeration(Long conversationId);

// AFTER:
Page<Message> findAllByConversationIdForModeration(
    Long conversationId, Pageable pageable);
```

Update the `SystemService` or `SystemMessagingService` caller to pass `PageRequest.of(page, size)`. Expose `page` and `size` on the moderation endpoint.

**Verify:**
```
Moderation query accepts page/size
Large conversation history does not return unbounded results
```

---

### Properties Additions

Add to `application.properties` if not already present:

```properties
# Hibernate batch settings for saveAll performance
spring.jpa.properties.hibernate.jdbc.batch_size=${HIBERNATE_BATCH_SIZE:50}
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.generate_statistics=${HIBERNATE_STATS:false}
# Set HIBERNATE_STATS=true in test profile to count queries
```

---

## Agent 5 — Tests

### Critical — Optimistic Locking Tests (one per entity, 5 total)

```java
// Template — apply to each of: Evaluation, Submission, InstructorScore, RubricScore, ExtensionRequest

@Test
void concurrentUpdate_shouldThrowOptimisticLockingFailure() {
    // 1. Load entity in two separate persistence contexts
    Evaluation e1 = evaluationRepository.findById(id).get();
    Evaluation e2 = evaluationRepository.findById(id).get();

    // 2. Update first — succeeds, increments lock_version
    e1.setTotalScore(85.0);
    evaluationRepository.saveAndFlush(e1);

    // 3. Update second with stale version — must throw
    e2.setTotalScore(90.0);
    assertThrows(OptimisticLockingFailureException.class,
        () -> evaluationRepository.saveAndFlush(e2));
}

@Test
void concurrentUpdate_endToEnd_returns409WithConcurrentModificationCode() {
    // Integration test via MockMvc / TestRestTemplate
    // Two concurrent PATCH requests to same evaluation
    // First → 200 OK
    // Second (stale version) → 409 CONCURRENT_MODIFICATION
    //   body contains currentVersion and conflictedAt
}
```

### HIGH — N+1 Elimination Tests

```java
@Test
void buildRoster_200Students_executesOneQuery() {
    // Use @DataJpaTest with Hibernate statistics
    Statistics stats = sessionFactory.getStatistics();
    stats.setStatisticsEnabled(true);

    gradeCalculationService.buildRoster(courseId, request);

    assertThat(stats.getPrepareStatementCount()).isLessThan(5);
}

@Test
void commitImport_500Rows_executesOneUserLookup() {
    // 500 rows with unique emails
    verify(userRepository, times(0)).findByEmail(any());
    verify(userRepository, times(1)).findByEmailIn(any());
}

@Test
void getGradebookForAssignment_40Teams_executesOneQuery() {
    Statistics stats = sessionFactory.getStatistics();
    stats.setStatisticsEnabled(true);

    assignmentService.getGradebookForAssignment(assignmentId);

    // No lazy-load queries on team.getMembers()
    assertThat(stats.getPrepareStatementCount()).isLessThan(5);
}
```

### MEDIUM — Cascade Safety Tests

```java
@Test
void deleteConversation_doesNotCascadeMessages() {
    // Verify messages remain in DB after conversation entity operation
    // (now requires explicit MessagingService.deleteConversation call)
}

@Test
void narrowedCascade_courseEnrollment_notDeletedOnCourseDetach() {
    // Detach course entity from persistence context
    // Verify enrollments not cascaded
}
```

### DB Constraint Existence Tests

```java
@Test
void lockVersionColumn_existsOnAllFiveEntities() {
    // Direct JDBC query:
    // SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
    // WHERE TABLE_NAME IN ('submissions','evaluations','instructor_scores',
    //                      'rubric_scores','extension_requests')
    //   AND COLUMN_NAME = 'lock_version'
    // → must return exactly 5 rows
}

@Test
void idx_conv_course_exists() {
    // SHOW INDEX FROM conversations WHERE Key_name = 'idx_conv_course'
    // → must return 1 row
}
```

---

## Agent 6 — Review Checklist

Run this in full before opening the PR. Every item must pass.

### CRITICAL — Optimistic Locking
```
[ ] lock_version column present in V35 on all 5 tables
[ ] @Version field (lockVersion) present on all 5 entities
[ ] @Column(name = "lock_version") correct on all 5 entities
[ ] Submission.versionNumber (business field) is UNCHANGED
[ ] OptimisticLockingFailureException handler returns 409 CONCURRENT_MODIFICATION
[ ] 409 body contains currentVersion + conflictedAt
[ ] No existing write path bypasses optimistic locking
```

### CRITICAL — Index
```
[ ] idx_conv_course exists on conversations(course_id) in V35
[ ] No existing migration files modified
```

### HIGH — N+1 Fixes
```
[ ] buildRoster uses single bulk native query — no per-student loop
[ ] buildRoster result is consistent with per-student calculateOverview results
[ ] CsvImportCommitService uses findByEmailIn (not findByEmail per row)
[ ] saveAll used for batch InstructorScore insert
[ ] Hibernate batch_size configured in application.properties
[ ] TeamRepository uses JOIN FETCH for gradebook query
[ ] getSubmissionsForAssignment uses paginated repository methods
```

### HIGH — Cascade Safety
```
[ ] Conversation.messages: CascadeType.PERSIST/MERGE only, orphanRemoval removed
[ ] Course enrollment sets: CascadeType narrowed to PERSIST/MERGE
[ ] Team.members: CascadeType narrowed to PERSIST/MERGE
[ ] MessagingService.deleteMessage explicitly deletes message rows (does not rely on cascade)
```

### MEDIUM
```
[ ] GradeExportService.export has @Transactional(readOnly = true)
[ ] MessageRepository moderation query accepts Pageable
[ ] Submission.createdAt / updatedAt present in entity and V35 migration
[ ] Backfill UPDATE ran cleanly (created_at populated from uploaded_at)
```

### Tests
```
[ ] Each of 5 entities has optimistic lock concurrency test
[ ] 409 CONCURRENT_MODIFICATION end-to-end test passes
[ ] buildRoster query count test: ≤ 5 queries for 200-student course
[ ] CSV commit query count test: 1 user lookup regardless of row count
[ ] lock_version column DB existence test passes for all 5 tables
[ ] idx_conv_course DB existence test passes
```

### Regression
```
[ ] Grade export still produces correct CSV output
[ ] Roster still returns correct standing for all students
[ ] CSV import commit still atomic (rollback on any failure)
[ ] Team gradebook still shows all members per team
[ ] Extension request approve/deny still works (now with 409 on race)
[ ] Submission upload still works (lock_version does not interfere with versionNumber)
[ ] No new 500 errors on previously passing Postman collection
```

---

## Summary of All Changed Files

| File | Change |
|---|---|
| `V35__db_integrity_hardening.sql` | New migration — lock_version columns, idx_conv_course, submission timestamps |
| `Submission.java` | `@Version lockVersion`, `createdAt`, `updatedAt`, `@PrePersist/@PreUpdate` |
| `Evaluation.java` | `@Version lockVersion` |
| `InstructorScore.java` | `@Version lockVersion` |
| `RubricScore.java` | `@Version lockVersion` |
| `ExtensionRequest.java` | `@Version lockVersion` |
| `Conversation.java` | Narrow `CascadeType.ALL` on messages, remove orphanRemoval |
| `Course.java` | Narrow cascade on enrollment sets |
| `Team.java` | Narrow cascade on members |
| `GlobalExceptionHandler` | Add `OptimisticLockingFailureException` handler → 409 CONCURRENT_MODIFICATION |
| `ErrorResponse` | Add nullable `details` map for `currentVersion` / `conflictedAt` |
| `GradeCalculationService` | Replace per-student loop with bulk native query |
| `GradeOverviewRepository` | Add `buildRosterBulk(courseId)` native query + `StudentStandingRow` projection |
| `CsvImportCommitService` | `findByEmailIn()` pre-load + `saveAll()` batch insert |
| `UserRepository` | Add `findByEmailIn(Collection<String>)` |
| `AssignmentService` | Use `findByAssignmentIdWithMembers()` + paginated submissions |
| `TeamRepository` | Add `findByAssignmentIdWithMembers()` with JOIN FETCH |
| `EvaluationService.setScores` | Pre-load criteria map with `findAllById()` |
| `GradeExportService` | `@Transactional(readOnly = true)` |
| `MessageRepository` | Add `Pageable` to moderation query |
| `application.properties` | Hibernate batch + stats properties |
