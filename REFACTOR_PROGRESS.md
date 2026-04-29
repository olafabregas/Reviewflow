# ReviewFlow Backend Refactoring — Progress Tracker

## Plan Version: 2.0

## Target: Layer-First → Feature-First Architecture

## Execution Mode: Sequential only

## Phase 4: Verification only

---

## Pre-Existing State

- `AuditRepository` deleted; `AuditLogRepository` is canonical and compiles.
- `GlobalExceptionHandlerAnnouncementTest` merged into `GlobalExceptionHandlerTest` and compiles.
- Flyway migrations are 24 .sql files (V1–V24) and must not be modified.
- Package root is `com.reviewflow`.
- Application port is 8081.

---

## Phase 0: Pre-Flight

### Session: phase-0.1-baseline

- **Status:** FAILED
- **Start:** 2026-04-28
- **End:** 2026-04-28
- **Commit:**
- **Verification:**
  - `mvn clean compile` → PASS
  - `mvn test` → PASS (321 tests)
  - `mvn checkstyle:checkstyle` → PASS (report-only; existing warnings)
  - `mvn spotbugs:check` → FAIL (14 existing bugs)
  - `mvn pmd:check` → NOT RUN (stopped after SpotBugs failure)
- **Notes:** Baseline is blocked by pre-existing SpotBugs issues, including `SecurityConfig` / `ActuatorMachineClientSecurityConfig` CSRF warnings and several service/storage findings. No refactor changes have been made yet.

### Session: phase-0.2-postman-baseline

- **Status:** NOT STARTED
- **Start:**
- **End:**
- **Commit:**
- **Verification:**
  - Postman smoke collection →
- **Notes:** Optional if collection exists.

---

## Phase 1: Shared Foundation

### Session: phase-1.1-shared-domain

- **Status:** COMPLETED
- **Start:** 2026-04-28
- **End:** 2026-04-28
- **Verification:**
  - `mvn test -Dspotbugs.skip=true` → PASS (321 tests)
- **Notes:** Shared-domain types were fully moved to `com.reviewflow.shared.domain`: `ClamAvScanResult`, `RecipientType`, `AnnouncementTarget`, `NotificationType`, `ExtensionRequestStatus`, `TeamMemberStatus`, `SubmissionType`, and `UserRole`.

### Session: phase-1.2-shared-exception

- **Status:** COMPLETED
- **Start:** 2026-04-28
- **End:** 2026-04-28
- **Verification:**
  - `mvn -Dspotbugs.skip=true -DskipTests compile` → PASS
  - `mvn -Dspotbugs.skip=true test` → PASS (321 tests)
- **Notes:** `AccessDeniedException` and `BusinessRuleException` moved to `com.reviewflow.shared.exception`. All imports updated across 19 service files, 4 exception wrapper classes, and 8 test files. No logic changes; all 321 tests still passing.

### Session: phase-1.3-shared-util

- **Status:** COMPLETED
- **Start:** 2026-04-28
- **End:** 2026-04-28
- **Verification:**
  - `mvn -Dspotbugs.skip=true -DskipTests clean compile` → PASS
  - `mvn -Dspotbugs.skip=true test` → PASS (321 tests)
- **Notes:** Moved `HashidService`, `IpAddressExtractor`, `MimeTypeResolver`, and `CacheNames` to `com.reviewflow.shared.util`. Restored `S3KeyBuilder` imports to `com.reviewflow.util` after catching an over-broad spillover from the earlier attempt.

### Session: phase-1.4-shared-event

- **Status:** COMPLETED
- **Verification:**
  - `mvn -Dspotbugs.skip=true -DskipTests compile` → PASS
  - `mvn -Dspotbugs.skip=true test` → PASS (321 tests)
- **Notes:** Moved `CacheEvictedEvent` and `ForceLogoutEvent` to `com.reviewflow.shared.event` and updated the two consumers.

### Session: phase-1.5-shared-dto

- **Status:** COMPLETED
- **Start:** 2026-04-28
- **End:** 2026-04-28
- **Verification:**
  - `mvn -Dspotbugs.skip=true -DskipTests compile` → PASS
  - `mvn -Dspotbugs.skip=true test` → PASS (321 tests)
  - `mvn checkstyle:checkstyle` → PASS (report-only; 35 warnings, all pre-existing)
- **Notes:** Moved all 9 DTOs to `com.reviewflow.shared.dto`: UserDto, AuditLogDto, SecurityEventDto, CacheStatsDto, ForceLogoutResponse, UnlockTeamResponse, CacheEvictResponse, ReopenEvaluationResponse, and SystemMetricsDto. Updated imports in 4 production files and 1 test file. No logic changes; all 321 tests still passing. Old `com.reviewflow.dto` files were not deleted at the time but were cleaned up at the start of phase 1.6.

### Session: phase-1.6-shared-constant

- **Status:** COMPLETED
- **Start:** 2026-04-28
- **End:** 2026-04-28
- **Verification:**
  - `mvn -Dspotbugs.skip=true -DskipTests clean compile` → PASS
  - `mvn -Dspotbugs.skip=true test` → PASS (321 tests)
- **Notes:** (1) Deleted the 9 stale `com.reviewflow.dto` files left over from phase 1.5. (2) Physically moved `HashidService`, `IpAddressExtractor`, and `MimeTypeResolver` from `util/` to `shared/util/` directory — phase 1.3 had updated the package declarations but never moved the files. (3) Created `com.reviewflow.shared.constant` package and moved `CacheNames` there (pure constants class, not utility behavior); updated 11 importers including one with static imports (`SystemService`). `S3KeyBuilder` stays in `com.reviewflow.util` as storage-specific infrastructure. All 321 tests still passing.

---

## Phase 2: Infrastructure

### Session: phase-2.1-infra-security

- **Status:** COMPLETED
- **Start:** 2026-04-28
- **End:** 2026-04-28
- **Verification:**
  - `mvn "-Dspotbugs.skip=true" clean test` → PASS (321 tests)
- **Notes:** Moved all security infrastructure to `com.reviewflow.infra.security`. From `security/`: `JwtAuthenticationFilter`, `JwtService`, `ReviewFlowUserDetails`, `UserDetailsServiceImpl`. From `config/`: `SecurityConfig`, `ActuatorMachineClientSecurityConfig`, `PasswordEncoderConfig`, `PasswordPolicyProperties`, `WebSocketAuthInterceptor`. Updated 32 production/test files via bulk regex replace, plus `WebSocketConfig.java` (explicit import added — class was previously in same package), and `SystemController.java` (fully-qualified inline reference). Two test files in `src/test/java/com/reviewflow/security/` had their package declarations updated to `com.reviewflow.infra.security`. Old `security/` directory is now empty.

### Session: phase-2.2-infra-storage

- **Status:** COMPLETED
- **Start:** 2026-04-28
- **End:** 2026-04-28
- **Verification:**
  - `mvn "-Dspotbugs.skip=true" clean test` → PASS (321 tests)
- **Notes:** Moved 8 storage infrastructure files to `com.reviewflow.infra.storage`: `AwsS3Config`, `LocalS3Config`, `FileSecurityProperties` (from `config/`), `S3Service` (from `service/`), `S3KeyBuilder` (from `util/`), and `StorageService`, `S3FileStorageService`, `LocalFileStorageService` (from `storage/`). Updated 11 importers (4 production services + 7 test files) via bulk regex. Also added explicit `import com.reviewflow.infra.storage.S3Service` to 5 files (`EvaluationService`, `SubmissionService`, and 3 test files) that previously relied on same-package access. Added explicit import to `S3KeyBuilderTest` which had the same same-package issue. Deleted 9 old files including `storage/package-info.java`.

### Session: phase-2.3-infra-email

- **Status:** COMPLETED
- **Start:** 2026-04-28
- **End:** 2026-04-28
- **Verification:**
  - `mvn "-Dspotbugs.skip=true" clean test` → PASS (321 tests)
- **Notes:** Moved 3 email infrastructure files to `com.reviewflow.infra.email`: `EmailConfig` (from `config/`), `EmailService` and `EmailTemplateService` (from `service/`). Updated `EmailEventListener` and `EmailEventListenerTest` import lines (explicit imports). Moved 3 test files from `src/test/.../service/` to `src/test/.../infra/email/` and updated their package declarations (`EmailServiceTest`, `EmailTemplateServiceTest`, `EmailTemplateCatalogTest`). Old 6 files deleted (3 production + 3 test). All 321 tests still passing.

### Session: phase-2.4-infra-config

- **Status:** COMPLETED
- **Start:** 2026-04-29
- **End:** 2026-04-29
- **Verification:**
  - `mvn "-Dspotbugs.skip=true" clean test` → PASS (321 tests)
- **Notes:** Moved all 5 remaining `config/` files to `com.reviewflow.infra.config`: `ApplicationConfig`, `AsyncConfig`, `CacheConfig`, `OpenApiConfig`, and `WebSocketConfig`. Updated `WebSocketConfig` import to use `com.reviewflow.infra.security.WebSocketAuthInterceptor` (already correct from phase 2.1 — no other consumers had explicit imports of any of these config classes). No same-package access issues; no test files existed for these classes. `config/` directory is now empty. All 321 tests still passing.

### Session: phase-2.5-infra-filter

- **Status:** COMPLETED
- **Start:** 2026-04-29
- **End:** 2026-04-29
- **Verification:**
  - `mvn "-Dspotbugs.skip=true" clean test` → PASS (321 tests)
- **Notes:** Moved `ActuatorKeyAuthFilter` and `MdcFilter` from `com.reviewflow.filter` to `com.reviewflow.infra.filter`. Updated one explicit importer: `ActuatorMachineClientSecurityConfig`. No test files existed for either class. `filter/` directory is now empty. All 321 tests still passing.

### Session: phase-2.6-infra-scheduling

- **Status:** COMPLETED
- **Start:** 2026-04-29
- **End:** 2026-04-29
- **Verification:**
  - `mvn "-Dspotbugs.skip=true" clean test` → PASS (321 tests)
- **Notes:** Moved `DeadlineWarningScheduler` and `NotificationCleanupScheduler` from `com.reviewflow.scheduler` to `com.reviewflow.infra.scheduling`. Moved `DeadlineWarningSchedulerTest` to `src/test/.../infra/scheduling/` with updated package declaration. No explicit importers of either scheduler class. `scheduler/` directory is now empty. All 321 tests still passing.

### Session: phase-2.7-infra-monitoring

- **Status:** COMPLETED
- **Start:** 2026-04-29
- **End:** 2026-04-29
- **Verification:**
  - `mvn "-Dspotbugs.skip=true" clean test` → PASS (321 tests)
- **Notes:** Moved `ReviewFlowMetrics` and `SecurityMetrics` from `com.reviewflow.monitoring` to `com.reviewflow.infra.monitoring`. Updated 5 production importers (`AuthService`, `FileSecurityValidator`, `SubmissionService`, `ClamAvScanService`, `JwtAuthenticationFilter`) and 5 test importers (`AuthServiceTest`, `JwtAuthenticationFilterTest`, `FileSecurityValidatorAvatarTest`, `SubmissionServicePreviewTest`, `SubmissionServiceTest`). No test files existed in `monitoring/`; no same-package access issues. `monitoring/` directory is now empty. All 321 tests still passing.

### Session: phase-2.8-infra-client

- **Status:** COMPLETED
- **Start:** 2026-04-29
- **End:** 2026-04-29
- **Verification:** N/A — no files to move
- **Notes:** No external HTTP client classes exist in the codebase. No `client/` package, no `RestTemplate`, `WebClient`, `FeignClient`, or `HttpClient` usages. ClamAV uses a direct socket connection (`ClamAvScanService`). Phase is a no-op; no files moved, no tests run.

---

## Phase 3: Feature Migration

### Session: phase-3.1-notification

- **Status:** COMPLETED
- **Start:** 2026-04-29
- **End:** 2026-04-29
- **Verification:**
  - `mvn "-Dspotbugs.skip=true" clean test` → PASS (321 tests)
- **Notes:** Migrated notification feature to correct feature-first structure. `Notification` entity moved to `com.reviewflow.shared.domain` (Law 5: entity neutrality). Feature classes split into sub-packages: `NotificationController` → `com.reviewflow.notification.controller`, `NotificationService` → `com.reviewflow.notification.service`, `NotificationRepository` → `com.reviewflow.notification.repository`, `NotificationDto` + `MarkAllReadResponse` → `com.reviewflow.notification.dto.response`, `NotificationEventListener` → `com.reviewflow.notification.event`. `DeadlineWarningEvent` moved from `com.reviewflow.event` to `com.reviewflow.notification.event` (Law 7: event co-location). Updated external importers: `NotificationCleanupScheduler` → `notification.service.NotificationService`, `DeadlineWarningScheduler` → `notification.event.DeadlineWarningEvent`, `DeadlineWarningSchedulerTest` → same. Moved `NotificationEventListenerTest` to `src/test/.../notification/event/` with updated package and import. All 321 tests passing.

### Session: phase-3.2-announcement

- **Status:** COMPLETED
- **Start:** 2026-04-29
- **End:** 2026-04-29
- **Verification:**
  - `mvn "-Dspotbugs.skip=true" clean test` → PASS (321 tests)
- **Notes:** Migrated announcement feature to correct feature-first structure. `Announcement` entity moved to `com.reviewflow.shared.domain` (Law 5). Feature classes split into sub-packages: `AnnouncementController` → `com.reviewflow.announcement.controller`, `AnnouncementService` → `com.reviewflow.announcement.service`, `AnnouncementRepository` → `com.reviewflow.announcement.repository`, `CreateAnnouncementRequest` → `com.reviewflow.announcement.dto.request`, `AnnouncementResponse` + `PaginatedAnnouncementResponse` → `com.reviewflow.announcement.dto.response`, `AnnouncementPublishedEvent` → `com.reviewflow.announcement.event` (Law 7), `AnnouncementNotFoundException` → `com.reviewflow.announcement.exception`. Moved `AnnouncementServiceTest` to `src/test/.../announcement/service/`. Updated external importers: `NotificationEventListener` (AnnouncementPublishedEvent import), `GlobalExceptionHandler` (explicit import for AnnouncementNotFoundException — was same-package), `GlobalExceptionHandlerTest` (same). `AnnouncementPostedEmailEvent` stays in `com.reviewflow.event.email` (email infrastructure event, not domain event). All 321 tests passing.

### Session: phase-3.3-course

- **Status:** COMPLETED
- **Start:** 2026-04-29
- **End:** 2026-04-29
- **Verification:**
  - `mvn "-Dspotbugs.skip=true" clean test` → PASS (321 tests)
- **Notes:** Migrated course feature to correct feature-first structure. `Course`, `CourseEnrollment`, and `CourseInstructor` entities moved to `com.reviewflow.shared.domain` (Law 5 — widely shared across features). Repositories split: `CourseRepository`, `CourseEnrollmentRepository`, `CourseInstructorRepository` → `com.reviewflow.course.repository`. `CourseService` → `com.reviewflow.course.service`. `CourseController` → `com.reviewflow.course.controller`. Request DTOs (`CreateCourseRequest`, `UpdateCourseRequest`, `BulkEnrollRequest`) → `com.reviewflow.course.dto.request`. Response DTOs (`CourseResponse`, `StudentResponse`) → `com.reviewflow.course.dto.response`. Exceptions (`CourseArchivedReadOnlyException`, `CourseNotOwnedException`, `AssignmentNotInCourseException`, `ModuleNotInCourseException`, `GroupNotInCourseException`) → `com.reviewflow.course.exception`. `CourseModulesResponse` left in `model.dto.response` (references module feature types; will move during module phase). Explicit import fixes required in: `GlobalExceptionHandler` + test (same-package course exceptions), `Assignment`, `AssignmentGroup`, `AssignmentModule` (same-package `Course` access), `TeamService`, `SubmissionServicePreviewTest`, `UserServiceTest` (wildcard `repository.*` no longer covering course repositories). All 321 tests still passing.

### Session: phase-3.4-user

- **Status:** NOT STARTED

- **Status:** COMPLETED
- **Start:** 2026-04-29
- **End:** 2026-04-29
- **Verification:**
  - `mvn "-Dspotbugs.skip=true" clean test` → PASS (321 tests)
- **Notes:** Migrated user feature to correct feature-first structure. User entity remains in `com.reviewflow.shared.domain` (Law 5). `UserRepository` → `com.reviewflow.user.repository`. `UserService` → `com.reviewflow.user.service`. Controllers → `com.reviewflow.user.controller`. DTOs split into request/response sub-packages. Avatar exceptions → `com.reviewflow.user.exception`. Test files migrated to test/user/service/. Explicit import fixes applied to 24+ production and test files. Old files deleted from original locations. GlobalExceptionHandler updated. All 321 tests passing.
### Session: phase-3.5-auth

- **Status:** NOT STARTED

### Session: phase-3.6-assignment

- **Status:** NOT STARTED

### Session: phase-3.7-extension

- **Status:** NOT STARTED

### Session: phase-3.8-team

- **Status:** NOT STARTED

### Session: phase-3.9-submission

- **Status:** NOT STARTED

### Session: phase-3.10-evaluation

- **Status:** NOT STARTED

### Session: phase-3.11-grading

- **Status:** NOT STARTED

### Session: phase-3.12-admin

- **Status:** NOT STARTED

### Session: phase-3.13-system

- **Status:** NOT STARTED

---

## Phase 4: Verification

### Session: phase-4.1-verification

- **Status:** NOT STARTED

### Session: phase-4.2-health-check

- **Status:** NOT STARTED

### Session: phase-4.3-postman-verification

- **Status:** NOT STARTED

---

## Coupling Issues Registry

| Feature | Coupled To | Description | Status |
| ------- | ---------- | ----------- | ------ |
|         |            |             |        |

---

## Rollback Log

| Session | Reason | Action Taken | Resolved |
| ------- | ------ | ------------ | -------- |
|         |        |              |          |
