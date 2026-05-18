# ReviewFlow — Database Integrity Audit Report

**Audit date:** 2026-05-18  
**Scope:** Full `scan all` (17 targets)  
**Flyway ceiling:** V34 (V30 intentionally skipped)  
**Codebase scanned:** `c:\Desktop\Reviewflow\Backend` (working tree; includes in-flight changes)  
**Tier:** 1 — **CRITICAL findings block production deploy**

---

## Executive Summary

| Severity | Count |
|----------|------:|
| **CRITICAL** | 4 |
| **HIGH** | 8 |
| **MEDIUM** | 5 |
| **INFO** | 4 |
| **Total** | **21** |

### Top 3 risks

1. **No optimistic locking on academic-record entities** (`Submission`, `Evaluation`, `InstructorScore`, `RubricScore`) — concurrent grade/submission writes can silently overwrite each other (last-write-wins).
2. **Class roster build is O(students × grade queries)** — `GradeCalculationService.buildRoster` calls `calculateOverviewCached` once per enrollment; large courses will exhaust the connection pool under instructor dashboard load.
3. **Messaging schema gap on `conversations.course_id`** — moderation and course-scoped inbox queries filter by `course_id` without a supporting index; combined with `CascadeType.ALL` on messages, delete/load paths are risky at scale.

---

## Scan Progress (17 targets)

| # | Target | Findings (C / H / M / I) |
|---|--------|--------------------------|
| 1 | `admin/repository/` | 0 |
| 2 | `announcement/repository/` | 0 |
| 3 | `assignment/repository/` | 0 |
| 4 | `auth/repository/` | 0 |
| 5 | `course/repository/` | 0 |
| 6 | `discussion/repository/` | 0 |
| 7 | `evaluation/repository/` | 0 |
| 8 | `extension/repository/` | 0 |
| 9 | `grading/repository/` | 0 |
| 10 | `messaging/repository/` | 1 / 0 / 1 / 0 |
| 11 | `notification/repository/` | 0 |
| 12 | `submission/repository/` | 0 |
| 13 | `team/repository/` | 0 |
| 14 | `user/repository/` | 0 |
| 15 | `shared/domain/` | 4 / 3 / 2 / 1 |
| 16 | `db/migration/` (V24–V34) | 0 / 1 / 0 / 1 |
| 17 | All feature `*/service/` | 0 / 4 / 2 / 2 |

**Files scanned (approx.):** 112 (31 repositories + 36 domain entities + 38 services + 10 migrations V24–V34)

---

## Findings — CRITICAL

### [RULE-DB03 | CRITICAL] Submission.java — Missing `@Version`

**Issue:** `Submission` has `versionNumber` (business document version) but no JPA `@Version` for optimistic locking.  
**Context:** Concurrent uploads or metadata updates on the same submission row can lose data under write contention.  
**Fix:** Add `@Version private Long version;` and Flyway `ALTER TABLE submissions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;` (or align with existing column if repurposed).

---

### [RULE-DB03 | CRITICAL] Evaluation.java — Missing `@Version`

**Issue:** `Evaluation` lacks `@Version` while instructors may edit scores and publish concurrently.  
**Context:** Last-write-wins on `total_score`, `is_draft`, and `published_at` corrupts published grades.  
**Fix:** Add `@Version` + migration column on `evaluations`.

---

### [RULE-DB03 | CRITICAL] InstructorScore.java — Missing `@Version`

**Issue:** `InstructorScore` has `updated_at` but no optimistic lock column.  
**Context:** CSV import, manual entry, and publish flows can update the same `(assignment_id, student_id)` row concurrently.  
**Fix:** Add `@Version` + `ALTER TABLE instructor_scores ADD COLUMN version BIGINT NOT NULL DEFAULT 0;`

---

### [RULE-DB03 | CRITICAL] RubricScore.java — Missing `@Version`

**Issue:** `RubricScore` rows are updated per-criterion in `EvaluationService.setScores` without row-level versioning.  
**Context:** Parallel rubric edits on one evaluation overwrite criterion scores silently.  
**Fix:** Add `@Version` on `rubric_scores` or enforce single-writer via evaluation-level lock (prefer both).

---

## Findings — HIGH

### [RULE-DB02 | HIGH] GradeCalculationService.java:587

**Issue:** `buildRoster` loops enrollments and calls `calculateOverviewCached(courseId, studentId)` per student.  
**Context:** A 200-student course triggers 200+ aggregation query chains per roster refresh — classic N+1 at classroom scale.  
**Fix:** Batch-load grades/submissions/evaluations for all `studentId IN (:ids)` in one or few queries; compute standings in memory; keep cache key per course roster.

---

### [RULE-DB02 | HIGH] CsvImportCommitService.java:68–80

**Issue:** Commit loop calls `userRepository.findByEmail` once per CSV row in individual mode.  
**Context:** 500-row import → 500 user lookups inside one transaction (long TX + pool pressure).  
**Fix:** Pre-load `Map<String, User>` via `findByEmailIn(emails)` before the loop; batch `InstructorScore` writes with `saveAll` where possible.

---

### [RULE-DB02 | HIGH] AssignmentService.java:740–748

**Issue:** `getGradebookForAssignment` loads teams with `findByAssignmentId` then accesses `team.getMembers()` per team (lazy `OneToMany`).  
**Context:** 40 teams → 1 + 40 queries for member lists on every gradebook view.  
**Fix:** `@EntityGraph(attributePaths = "members.user")` or `JOIN FETCH` query on `TeamRepository.findByAssignmentId`.

---

### [RULE-DB06 | HIGH] AssignmentService.java:715–733

**Issue:** `getSubmissionsForAssignment` returns unbounded `List<Submission>` (all versions loaded, filtered in memory).  
**Context:** Large classes with many resubmissions cause memory pressure and slow instructor queues.  
**Fix:** Use existing `findLatestTeamSubmissionsByAssignmentId` / `findLatestIndividualSubmissionsByAssignmentId` with `Pageable`, or cap with spec-mandated pagination.

---

### [RULE-DB01 | HIGH] V32__create_messaging.sql — `conversations.course_id` without index

**Issue:** `conversations.course_id` is a FK but has no `CREATE INDEX` on `course_id` alone.  
**Context:** `ConversationRepository.findByCourse_Id` and moderation list paths scan by course.  
**Fix:** `CREATE INDEX idx_conv_course ON conversations(course_id);`

---

### [RULE-DB09 | HIGH] Conversation.java:54

**Issue:** `@OneToMany(..., cascade = CascadeType.ALL, orphanRemoval = true)` on messages.  
**Context:** Accidental `remove(conversation)` or merge-delete cascades entire message history.  
**Fix:** Remove `ALL`/`orphanRemoval` from messages; delete via explicit service with retention policy.

---

### [RULE-DB03 | HIGH] ExtensionRequest.java — Missing `@Version`

**Issue:** No optimistic lock on approve/deny workflow.  
**Context:** Two instructors acting on the same pending request can produce inconsistent `status` / `responded_at`.  
**Fix:** Add `@Version` on `extension_requests`.

---

### [RULE-DB02 | MEDIUM→HIGH] EvaluationService.java:161–195

**Issue:** `setScores` calls `rubricCriterionRepository.findById` per score entry in a loop.  
**Context:** Bounded by rubric size (typically &lt;15) — lower severity than roster N+1 but still extra round-trips per save.  
**Fix:** Load all criteria for `assignmentId` once into `Map<Long, RubricCriterion>`.

---

## Findings — MEDIUM

### [RULE-DB07 | MEDIUM] GradeExportService.java:89

**Issue:** `export(...)` uses `@Transactional` without `readOnly = true` on a multi-table read/export path.  
**Context:** Hibernate dirty-checking and write-capable TX on read-only export waste CPU and hold connections.  
**Fix:** `@Transactional(readOnly = true)` on `export` (writes only to stream/byte array outside JPA).

---

### [RULE-DB06 | MEDIUM] MessageRepository.java:26

**Issue:** `findAllByConversationIdForModeration` returns full message history without `Pageable` or `LIMIT`.  
**Context:** Long team chats can return thousands of rows for moderators.  
**Fix:** Paginate moderation export; add `Pageable` parameter aligned with API spec.

---

### [RULE-DB10 | MEDIUM] Submission.java — Missing audit timestamps

**Issue:** `Submission` has `uploaded_at` but no `created_at` / `updated_at` consistent with siblings (`Evaluation`, `ExtensionRequest`).  
**Context:** Sorting/auditing submission lifecycle across features is inconsistent; harder to correlate with `audit_log`.  
**Fix:** Add `created_at` / `updated_at` with `@PrePersist` / `@PreUpdate` or auditing listener.

---

### [RULE-DB09 | MEDIUM] Course.java:46–51

**Issue:** `CascadeType.ALL` + `orphanRemoval` on `CourseInstructor` and `CourseEnrollment` sets.  
**Context:** Detaching or deleting a `Course` entity in persistence context can cascade-remove all enrollments unintentionally.  
**Fix:** Use `PERSIST`/`MERGE` only, or no cascade with explicit enrollment service deletes.

---

### [RULE-DB09 | MEDIUM] Team.java:43

**Issue:** `CascadeType.ALL` + `orphanRemoval` on `TeamMember` list.  
**Context:** Team entity operations can mass-delete membership rows.  
**Fix:** Narrow cascade; use `TeamService` for explicit member removal.

---

## Findings — INFO

### [RULE-DB11 | INFO] V25__add_session_tracking_to_refresh_tokens.sql

**Issue:** `last_used_at` added without index.  
**Context:** Only matters if batch jobs filter `WHERE last_used_at < ?` at scale; login path uses `token_hash` (unique) and `idx_refresh_user_active` (V29).  
**Fix:** Add index if idle-timeout sweeps are implemented.

---

### [RULE-DB06 | INFO] CourseService.java:113

**Issue:** `listCoursesForUser` calls `courseRepository.findAll()` for `ADMIN` role.  
**Context:** Not used by `CourseController` (paged API uses `listCoursesForUserPaged`); cached method may still load all courses on cache miss.  
**Fix:** Remove dead path or paginate even for admin cache warm.

---

### [Law 2 | INFO] Cross-feature repository imports (multiple services)

**Issue:** Services import repositories outside their feature (e.g. `GradeCalculationService` → `evaluation`, `submission`, `course` repos).  
**Context:** Increases coupling and makes query-plan ownership unclear (not a direct index bug).  
**Fix:** Prefer facade services or read models per bounded context over time.

---

### [RULE-DB09 | INFO] Evaluation.java:57, Message.java:55–58

**Issue:** `CascadeType.ALL` on rubric scores and attachments within aggregate roots.  
**Context:** Acceptable for small child collections when parent lifecycle owns children; document as intentional aggregate boundary.

---

## Clean Targets (zero DB rule violations)

- `admin/repository/` — `AuditLogRepository` uses filtered/paged queries only
- `announcement/repository/`
- `assignment/repository/`
- `auth/repository/` — token lookups covered by V2/V28/V29 indexes
- `course/repository/`
- `discussion/repository/` — V34 indexes on `course_id`, `discussion_id`, participation
- `evaluation/repository/`
- `extension/repository/` — V19 status indexes present
- `grading/repository/` — `InstructorScore` entity indexes match V24
- `notification/repository/` — V11 user/read/created_at indexes
- `submission/repository/` — V15 `student_id` index; batch latest-submission queries are well-shaped
- `team/repository/`
- `user/repository/`
- **Positive patterns:** No `FetchType.EAGER` on collections; no `@Formula` / `@SecondaryTable` on hot entities; messaging list path uses batch `findByConversationIdInWithUser` + native metadata query

---

## Rule Coverage Matrix

| Rule | Description | Hits |
|------|-------------|-----:|
| DB01 | FK / filter column without index | 1 |
| DB02 | N+1 in service layer | 4 |
| DB03 | Missing `@Version` | 5 |
| DB04 | High-frequency WHERE without index | 1 |
| DB05 | EAGER collection fetch | 0 |
| DB06 | Unpaginated list API path | 2 |
| DB07 | Heavy read without `readOnly` | 1 |
| DB08 | Native query full scan | 0 |
| DB09 | `CascadeType.ALL` on large collections | 3 |
| DB10 | Missing audit timestamps | 1 |
| DB11 | Flyway column without follow-up index | 1 |
| DB12 | `@Formula` / `@SecondaryTable` | 0 |

---

## Recommended Action Plan

### Phase 1 — Block deploy (indexes + locking)

1. Add `@Version` + migrations for `submissions`, `evaluations`, `instructor_scores`, `rubric_scores`, `extension_requests`.
2. `CREATE INDEX idx_conv_course ON conversations(course_id);`

### Phase 2 — Hot-path performance (N+1 + pagination)

3. Refactor `GradeCalculationService.buildRoster` to batch per-course grade reads.
4. Batch email→user resolution in `CsvImportCommitService`.
5. Add `JOIN FETCH` for team members in gradebook; paginate `getSubmissionsForAssignment`.
6. Paginate moderation message history query.

### Phase 3 — Integrity hardening

7. Narrow `CascadeType.ALL` on `Conversation`, `Course`, `Team` where not required.
8. Add `Submission` audit timestamps; align export TX with `readOnly = true`.

---

## Scan Errors

None. All 17 targets were reachable and read successfully.

---

*Generated by Database Integrity Auditor (`database-integrity-audit.mdc`).*
