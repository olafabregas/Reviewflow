# ReviewFlow — Global Rules & Reference

> Version 2.1 | Spring Boot **4** | Base URL: `http://localhost:8081/api/v1`  
> Last Updated: 2026-05-18 | Package layout: [orchestration/REFACTOR_STRATEGY.md](../orchestration/REFACTOR_STRATEGY.md) · Ship state: [orchestration/MASTER_PROJECT_SUMMARY.md](../orchestration/MASTER_PROJECT_SUMMARY.md)  
> Cross-ref: [DECISIONS.md](../DECISIONS.md) | [ARCHITECTURE.md](../ARCHITECTURE.md) | New modules: [14_Module_Discussion.md](./14_Module_Discussion.md), [15_Module_Messaging.md](./15_Module_Messaging.md)

---

## Response Envelope

All endpoints must use this consistent envelope:

```json
// Success
{ "success": true, "data": { ... }, "timestamp": "2026-02-27T22:11:26.182Z" }

// Error
{ "success": false, "error": { "code": "ERROR_CODE", "message": "Human readable message" }, "timestamp": "..." }
```

---

## Global Rules (apply to every single endpoint)

### Authentication & Session

- [ ] Every protected endpoint returns `401` when no access token cookie is present
- [ ] Every protected endpoint returns `401` when access token is expired
- [ ] Every role-restricted endpoint returns `403` when user's role is insufficient
- [ ] Every `{id}` path variable that doesn't exist returns `404`
- [ ] Every missing required field returns `400` with field-level validation errors
- [ ] Every `400` error includes which field failed and why
- [ ] Every error response uses the standard envelope `{ success: false, error: { code, message } }`
- [ ] Password hash is NEVER returned in any response ever

### Data & Formatting

- [ ] Timestamps are always ISO 8601 UTC format
- [ ] All list endpoints support `?page=0&size=20` pagination (see [Pagination Standards](#pagination-standards) below)
- [ ] Pagination responses include `totalElements` and `totalPages`
- [ ] All response bodies include `X-Trace-Id` response header for request tracing (see [Response Headers](#response-headers) below)

---

## Response Headers

All responses return these headers:

| Header             | Value                           | Purpose                                                                                                |
| ------------------ | ------------------------------- | ------------------------------------------------------------------------------------------------------ |
| `X-Trace-Id`       | UUID string                     | Request correlation ID for CloudWatch log tracing (PRD-08). Injected by MDC filter into all log lines. |
| `X-Total-Elements` | Integer _(list responses only)_ | Total number of records available (pagination support). Example: `150`                                 |
| `X-Total-Pages`    | Integer _(list responses only)_ | Total number of pages at current page size (pagination support). Example: `8`                          |

**Example list response headers:**

```
X-Trace-Id: 550e8400-e29b-41d4-a716-446655440000
X-Total-Elements: 150
X-Total-Pages: 8
```

---

## Request Authentication & Authorization Rules

### Role Hierarchy

ReviewFlow enforces a strict role hierarchy for permission checks:

| Role             | Capabilities                                                                                                                                                             | Ceiling                          | Notes                                                                                                                                            |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| **SYSTEM_ADMIN** | Can call ANY endpoint (including /system/\*\*). Can manage cache, force logout, unlock teams, reopen evaluations. Can view metrics/dashboard.                            | **Max 5 accounts platform-wide** | Created via Flyway migrations only (no API endpoint). Cannot be created, modified, or deleted via API. Cannot force-logout another SYSTEM_ADMIN. |
| **ADMIN**        | Can do everything INSTRUCTOR can do, plus: user management, course assignment, platform-wide announcements, access /admin/\*\* endpoints.                                | Unlimited                        | Cannot access /system/\*\* endpoints (returns 403 FORBIDDEN).                                                                                    |
| **INSTRUCTOR**   | Can do everything STUDENT can do, plus: create/manage courses, assign students, create assignments, grade submissions, manage team formation, send course announcements. | Unlimited                        | Cannot access /admin/\*\* or /system/\*\* endpoints.                                                                                             |
| **STUDENT**      | Can form teams, submit assignments, view own grades, receive notifications, respond to team invites.                                                                     | Unlimited                        | Cannot access instructor/admin endpoints; role-checks prevent access to other students' data.                                                    |

### Permission Checking Rules

- **Hierarchy Property:** `SYSTEM_ADMIN > ADMIN > INSTRUCTOR > STUDENT`  
  SYSTEM_ADMIN can call any ADMIN endpoint without additional checks (implicit qualification).
- **Insufficient Role:** If user's role is below required role, return `403 FORBIDDEN` with code `FORBIDDEN`.
- **No Token:** If no access token cookie or token expired, return `401 UNAUTHORIZED` with code `UNAUTHORIZED`.
- **Account Deactivated:** If user.is_active = false, return `403 FORBIDDEN` with code `ACCOUNT_DEACTIVATED` (even with valid token).

### Endpoint Auth Examples

```
GET  /courses/{id}            → Role: INSTRUCTOR (or higher)
POST /admin/users/{id}/avatar → Role: ADMIN or SYSTEM_ADMIN
POST /system-admin/cache/evict → Role: SYSTEM_ADMIN only (403 if SYSTEM_ADMIN not exactly true)
GET  /students/me/submissions → Role: STUDENT or higher (any auth)
```

---

## Pagination Standards

All list endpoints support standard pagination query parameters and responses.

### Query Parameters

```
GET /assignments?page=0&size=20{&sort=...}
```

| Parameter | Type                | Default | Range                 | Notes                                                  |
| --------- | ------------------- | ------- | --------------------- | ------------------------------------------------------ |
| `page`    | Integer             | `0`     | 0–N                   | Zero-indexed page number. `page=0` fetches first page. |
| `size`    | Integer             | `20`    | 1–500                 | Records per page. Max capped at 500 (server enforces). |
| `sort`    | String _(optional)_ | (none)  | field,ASC\|field,DESC | Example: `?sort=createdAt,DESC&sort=id,ASC`            |

### Response Format

All list responses include pagination metadata in response headers AND in body (for client convenience):

```json
{
  "success": true,
  "data": [
    { "id": "abc123", "name": "Assignment 1" },
    { "id": "def456", "name": "Assignment 2" }
  ],
  "pagination": {
    "currentPage": 0,
    "pageSize": 20,
    "totalElements": 150,
    "totalPages": 8
  },
  "timestamp": "2026-03-30T10:15:20Z"
}
```

| Field           | Type    | Notes                                                                                    |
| --------------- | ------- | ---------------------------------------------------------------------------------------- |
| `currentPage`   | Integer | Zero-indexed current page number.                                                        |
| `pageSize`      | Integer | Records returned in this response (may be less than requested size on last page).        |
| `totalElements` | Integer | Total records matching filters across all pages.                                         |
| `totalPages`    | Integer | Total pages at current page size. Example: `totalPages = ceil(totalElements / pageSize)` |

**Also returned in headers:**

```
X-Total-Elements: 150
X-Total-Pages: 8
```

---

## WebSocket Standards

### Subscription for System Metrics (PRD-09)

**Path (STOMP):** `/queue/system-metrics`  
**Auth:** SYSTEM_ADMIN role required. Client must be authenticated with valid token.  
**Pattern:** WebSocket subscription only (no HTTP GET). Client subscribes via STOMP frame.

### Publish Cadence

- **Scheduled Push:** Every 30 seconds, system metrics pushed to all connected clients
- **Event-Triggered Push:** Immediately on alarm triggers (e.g., JVM memory exceeds threshold)
- **Reconnect Strategy:** Client auto-reconnects on disconnect; connection is not persisted server-side

### Payload Format

```json
{
  "timestamp": "2026-03-30T10:15:20Z",
  "jvmMemory": {
    "used": 512,
    "max": 1024,
    "percent": 50
  },
  "dbConnections": {
    "active": 5,
    "max": 10
  },
  "caches": [
    { "name": "assignments", "size": 2401, "hits": 15234, "misses": 892 }
  ],
  "uptime": 86400,
  "instanceId": "i-1234567890abcdef0"
}
```

---

## Audit Log Standards (PRD-08)

### Audit Log Fundamentals

- **Immutability:** Audit entries are INSERT-ONLY. Never updated, never soft-deleted.
- **Coverage:** All write operations logged: CREATE, UPDATE, DELETE, OVERRIDE (permission bypass by SYSTEM_ADMIN).
- **Actor Tracking:** Every log entry captures userId, email, role of the actor who made the change.
- **Integration:** SYSTEM_ADMIN actions visible to ADMIN, but actor details redacted when viewed by non-SYSTEM_ADMIN (shows as "System Administrator").

### Logged Events

| Operation | Trigger                                            | Logged Fields                                                                                                 |
| --------- | -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| CREATE    | Resource creation (user, course, assignment, etc.) | actor_id, actor_email, actor_role, resource_type, resource_id, delta (new values)                             |
| UPDATE    | Resource modification                              | actor_id, actor_email, actor_role, resource_type, resource_id, delta (changed fields before/after)            |
| DELETE    | Resource deletion (soft or hard)                   | actor_id, actor_email, actor_role, resource_type, resource_id, reason (if available)                          |
| OVERRIDE  | SYSTEM_ADMIN bypassing restriction                 | actor_id="system_admin", reason (mandatory), resource_id, override_type (e.g., "force_logout", "team_unlock") |

**Example audit log row (JSON in logs):**

```json
{
  "timestamp": "2026-03-30T10:15:20.123Z",
  "eventType": "UPDATE",
  "actorId": "HASH_8765",
  "actorEmail": "instructor@example.com",
  "actorRole": "INSTRUCTOR",
  "resourceType": "Assignment",
  "resourceId": "HASH_1234",
  "delta": {
    "title": { "old": "Lab 1", "new": "Lab 1 - Revised" },
    "dueAt": { "old": "2026-04-15T23:59:59Z", "new": "2026-04-20T23:59:59Z" }
  }
}
```

---

## Comprehensive Error Codes Reference

All error responses must use one of these codes in `error.code`. Never use vague codes like `"ERROR"` or `"FAILED"`.

### PRD-01: Submission Type (INDIVIDUAL vs TEAM)

| Code                             | HTTP         | Trigger                                                                                                                       | Recovery                                                                                                                                                                          |
| -------------------------------- | ------------ | ----------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SUBMISSION_TYPE_LOCKED`         | 409 Conflict | Attempt to change `submission_type` field after any team has been formed OR any submission uploaded for this assignment.      | Instructor cannot modify type once assignment is in use. Data integrity is prioritized. If type was set incorrectly, create new assignment with correct type and archive old one. |
| `INDIVIDUAL_SUBMISSION_REQUIRED` | 409 Conflict | Student attempts to submit as team (uploads via team ID) on assignment with `submission_type=INDIVIDUAL`.                     | Resubmit as individual (via own student ID). Team members must submit separately if assignment is INDIVIDUAL.                                                                     |
| `TEAM_SUBMISSION_REQUIRED`       | 409 Conflict | Student attempts to submit individually (via own ID) on assignment with `submission_type=TEAM`.                               | Join or form a team first, then submit as team. Solo students cannot submit on TEAM assignments.                                                                                  |
| `TEAM_NOT_ALLOWED`               | 409 Conflict | Instructor attempts to enable team formation (POST /teams or set teamLockAt) on assignment with `submission_type=INDIVIDUAL`. | Remove team-related parameters. INDIVIDUAL assignments do not support teams.                                                                                                      |

### PRD-02: Profile Pictures (Avatar Upload + S3)

| Code                   | HTTP                      | Trigger                                                                  | Recovery                                                                                                              |
| ---------------------- | ------------------------- | ------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------- |
| `AVATAR_INVALID_TYPE`  | 400 Bad Request           | File extension or MIME type not in whitelist (.jpg, .jpeg, .png, .webp). | Upload file with supported image format. Server validates both extension and actual MIME type via content inspection. |
| `AVATAR_TOO_LARGE`     | 413 Payload Too Large     | File exceeds 2MB size limit.                                             | Compress image or resize to <2MB. Use online compressor or image editor.                                              |
| `AVATAR_UPLOAD_FAILED` | 500 Internal Server Error | S3 PUT operation failed (AWS service error, network, IAM permissions).   | Retry request. If persists, contact system admin (S3 bucket unreachable or IAM credentials revoked).                  |
| `AVATAR_NOT_FOUND`     | 404 Not Found             | DELETE /users/me/avatar called but user has no existing avatar.          | No action needed; user has no avatar to delete. Check user profile; avatar_url is NULL.                               |

### PRD-03: Email Notifications

| Code                | HTTP                      | Trigger                                                                                                                                                         | Recovery                                                                                                               |
| ------------------- | ------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `EMAIL_SEND_FAILED` | 500 Internal Server Error | JavaMailSender threw exception (SMTP timeout, authentication failure, SES quota exceeded). Logged but NOT returned to client in sync response (async delivery). | Retry email manually from admin panel (when implemented). Check email provider status (Mailhog locally, AWS SES prod). |

**Note:** Email failures are async and logged separately. Client receives 200 OK; email delivery is fire-and-forget with logging.

### PRD-04: Announcements (Course + Platform)

| Code                         | HTTP          | Trigger                                                                                                                      | Recovery                                                                                                                                     |
| ---------------------------- | ------------- | ---------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `ANNOUNCEMENT_NOT_FOUND`     | 404 Not Found | GET or DELETE /announcements/{id} where announcement does not exist OR was hard-deleted.                                     | Verify announcement ID. Check if announcement was deleted by creator/admin. List course announcements to find correct ID.                    |
| `ANNOUNCEMENT_NOT_PUBLISHED` | 404 Not Found | Student (INSTRUCTOR=or lower) attempts to read draft announcement (is_published=false). Draft only visible to creator/ADMIN. | Wait for creator to publish announcement, or contact instructor. DRAFT announcements hidden from most users.                                 |
| `ALREADY_PUBLISHED`          | 409 Conflict  | Attempt to PATCH /announcements/{id}/publish when announcement is already published (is_published=true).                     | Cannot re-publish. If edit needed, delete and create new announcement, or request creator to unpublish (if soft-delete supported in future). |

### PRD-05: Assignment Extensions

| Code                          | HTTP            | Trigger                                                                                                                                                     | Recovery                                                                                                                |
| ----------------------------- | --------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `EXTENSION_REQUEST_NOT_FOUND` | 404 Not Found   | GET or PATCH /extension-requests/{id} where request does not exist.                                                                                         | Verify request ID. List assignment requests: GET /assignments/{id}/extension-requests                                   |
| `EXTENSION_EXPIRED`           | 410 Gone        | Student/team attempts to REQUEST an extension but original assignment dueDate has already passed AND no active extension is in effect. Deadline has closed. | Too late; submit late work if instructor accepts late submissions (outside extension flow).                             |
| `EXTENSION_ALREADY_USED`      | 409 Conflict    | Student/team attempts REQUEST second active extension while one is already PENDING or APPROVED. Only one active request per assignment.                     | Wait for instructor to respond to existing request (APPROVE/DENY), or withdraw current request if supported.            |
| `EXTENSION_NOT_APPROVED`      | 403 Forbidden   | Student/team attempts to submit after original dueDate assuming extension was approved, but extension_request.status != APPROVED.                           | Check extension request status. If PENDING, wait for instructor response. If DENIED, submit as late work.               |
| `EXTENSION_INVALID_DURATION`  | 400 Bad Request | Requested new due date is not after the original assignment due date OR is unreasonably far in future.                                                      | Set requested_due_at to a valid future date beyond original dueAt. Server validates: requested_due_at > original_dueAt. |

### PRD-06: Grade Export (CSV)

| Code                       | HTTP            | Trigger                                                                                                                | Recovery                                                                                                  |
| -------------------------- | --------------- | ---------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `NO_SUBMISSIONS_FOUND`     | 404 Not Found   | GET /courses/{courseId}/evaluations/export?assignmentId={id} where assignment has zero submissions. Nothing to export. | Verify assignment ID and course ID. Check if students have submitted work. No export possible if no data. |
| `ASSIGNMENT_NOT_IN_COURSE` | 400 Bad Request | Assignment ID does not belong to provided course ID (data integrity check). Likely incorrect course/assignment pair.   | Verify both IDs are correct and associated. List assignments for course to find correct pairing.          |

### PRD-07: File Preview (Presigned S3 URLs)

| Code                         | HTTP          | Trigger                                                                                                                                        | Recovery                                                                                                       |
| ---------------------------- | ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| `PREVIEW_NOT_SUPPORTED`      | 409 Conflict  | File MIME type not in preview whitelist (e.g., .zip, .docx, .exe, etc.). Only whitelisted: application/pdf, image/jpeg, image/png, image/webp. | Download file instead of preview. Contact instructor if file type should be previewable.                       |
| `FILE_TOO_LARGE_FOR_PREVIEW` | 409 Conflict  | File exceeds 50MB preview limit. (Presigned URL generation skipped to avoid browser memory spike.)                                             | Download file instead. For very large files, use external viewer or split into chunks.                         |
| `PDF_NOT_GENERATED`          | 404 Not Found | GET /evaluations/{id}/pdf/preview called but evaluation PDF not yet generated (async job in progress or pending).                              | Wait 5-10 seconds and retry. PDF generation triggered on evaluation publish. If persists >1min, contact admin. |

### PRD-08: Logging & Monitoring (Structured Logging + CloudWatch + Actuator)

| Code                 | HTTP          | Trigger                                                                                                                                                                      | Recovery                                                                                   |
| -------------------- | ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `ACTUATOR_FORBIDDEN` | 403 Forbidden | Non-SYSTEM_ADMIN role attempts GET /actuator/health, /actuator/metrics, or /actuator/metrics/{name}. (Phase 2: restricted to SYSTEM_ADMIN; Phase 1 initially masked as 404.) | Only SYSTEM_ADMIN accounts can access metrics endpoints. Recalibrate user role if mistake. |

### PRD-09: System Admin Role & Platform Operations

| Code                               | HTTP                  | Trigger                                                                                                                                                          | Recovery                                                                                                     |
| ---------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `UNKNOWN_CACHE`                    | 400 Bad Request       | POST /system-admin/cache/evict with cache name not recognized by Spring Cache Manager. Name does not match any registered cache bean.                            | Verify cache name. Call GET /system-admin/config to list available caches.                                   |
| `EVICTION_TOO_SOON`                | 429 Too Many Requests | Cache eviction attempted within 60 seconds of last eviction of the same cache. Anti-DDoS throttle. Response includes `Retry-After: 45` (seconds until eligible). | Wait 60 seconds since last eviction. Throttle protects against abuse. Retry header indicates safe wait time. |
| `SYSTEM_ADMIN_LIMIT_EXCEEDED`      | 409 Conflict          | Flyway migration or API attempt to create SYSTEM_ADMIN account when 5 accounts already exist. Hard ceiling enforced.                                             | Remove/deactivate existing SYSTEM_ADMIN account first, or manage via direct DB migration rollback.           |
| `CANNOT_FORCE_LOGOUT_SYSTEM_ADMIN` | 403 Forbidden         | SYSTEM_ADMIN attempts POST /admin/users/{id}/force-logout on another SYSTEM_ADMIN account (protected).                                                           | Cannot force-logout peer admins. Only SYSTEM_ADMIN account itself can deactivate.                            |
| `TEAM_NOT_LOCKED`                  | 409 Conflict          | PATCH /admin/teams/{id}/unlock called on team with is_locked=false (already unlocked). No-op.                                                                    | Verify team lock status. Team is already unlocked; no action needed.                                         |
| `EVALUATION_NOT_PUBLISHED`         | 409 Conflict          | PATCH /admin/evaluations/{id}/reopen called on evaluation with is_draft=true (already draft). Cannot reopen draft.                                               | Verify evaluation state. Only published evaluations can be reopened to draft.                                |

### PRD-S3: S3 Storage Backend

| Code                    | HTTP                    | Trigger                                                                                                                                             | Recovery                                                                                                           |
| ----------------------- | ----------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `S3_UNAVAILABLE`        | 503 Service Unavailable | S3 service is unreachable (AmazonServiceException during GET/PUT operation). AWS region down or network timeout.                                    | Retry after 10 seconds. Contact AWS support if persists. Check AWS status page.                                    |
| `PRESIGNED_URL_EXPIRED` | 403 Forbidden           | Client attempts to use presigned URL beyond expiry window (15 min for submissions, 60 min for avatars). URL timestamp validation failed on S3 side. | Request fresh presigned URL from server. URLs are time-limited; regenerate via Submission/Avatar preview endpoint. |
| `S3_KEY_NOT_FOUND`      | 404 Not Found           | Referenced S3 object key does not exist (file deleted from S3 or never uploaded). DB record references non-existent S3 object.                      | File is missing from S3. Contact admin to reupload or clean up DB record.                                          |

### Base/Legacy Error Codes (Apply to All Modules)

| Code                        | HTTP                  | Trigger                                                                                                 | Recovery                                                                                             |
| --------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `INVALID_CREDENTIALS`       | 401 Unauthorized      | Login POST with wrong email or password.                                                                | Verify email and password. Reset password via recovery flow if forgotten.                            |
| `ACCOUNT_DEACTIVATED`       | 403 Forbidden         | User attempts login or access endpoint with is_active=false (account deactivated by admin).             | Contact admin to reactivate account.                                                                 |
| `UNAUTHORIZED`              | 401 Unauthorized      | No valid session / access token present.                                                                | Login first. Check if token cookie is set and not expired.                                           |
| `FORBIDDEN`                 | 403 Forbidden         | User role insufficient for endpoint (role hierarchy check failed).                                      | User cannot access this endpoint. Request elevated role or contact admin.                            |
| `NOT_FOUND`                 | 404 Not Found         | Resource path variable {id} does not exist.                                                             | Verify resource ID. List resources to find correct ID.                                               |
| `VALIDATION_ERROR`          | 400 Bad Request       | Missing or invalid request body fields.                                                                 | Check request payload. Refer to endpoint spec for required fields and formats.                       |
| `COURSE_CODE_EXISTS`        | 409 Conflict          | POST /courses with course_code matching existing course (unique constraint).                            | Use different course code.                                                                           |
| `EMAIL_EXISTS`              | 409 Conflict          | POST /users or /register with email matching existing user.                                             | Use different email or login if account already exists.                                              |
| `ALREADY_ENROLLED`          | 409 Conflict          | Student already enrolled in course. Duplicate enrollment attempt.                                       | Student is already in course. No action needed.                                                      |
| `NOT_ENROLLED`              | 400 Bad Request       | Student not enrolled in course yet attempts course-restricted operation.                                | Enroll in course first.                                                                              |
| `NOT_AN_INSTRUCTOR`         | 400 Bad Request       | User role is not INSTRUCTOR (or higher) when instructor operation attempted.                            | Only instructors can perform this action. User must be promoted to INSTRUCTOR role.                  |
| `NOT_A_STUDENT`             | 400 Bad Request       | User role is not STUDENT when student operation attempted (e.g., submit assignment).                    | Only students can perform this action. Check user role.                                              |
| `ALREADY_ASSIGNED`          | 409 Conflict          | Instructor already assigned to course. Duplicate assignment.                                            | Instructor is already teaching this course.                                                          |
| `INVALID_DUE_DATE`          | 400 Bad Request       | dueAt is in the past or invalid relative to today.                                                      | Set due date to future date.                                                                         |
| `INVALID_LOCK_DATE`         | 400 Bad Request       | teamLockAt is after dueAt (team formation ends after submissions due).                                  | Set teamLockAt before dueAt. Teams must lock before due date.                                        |
| `ASSIGNMENT_PUBLISHED`      | 400 Bad Request       | Attempt to DELETE published assignment. Published assignments are immutable.                            | Unpublish first if allowed, or create new assignment.                                                |
| `HAS_SUBMISSIONS`           | 400 Bad Request       | Attempt to DELETE/UNPUBLISH assignment with existing submissions. Cannot remove when data exists.       | Archive assignment instead. Or contact admin to force delete (with audit trail).                     |
| `HAS_SCORES`                | 400 Bad Request       | Attempt to DELETE rubric criterion when evaluations reference it (scores exist).                        | Archive criterion instead. Cannot delete when in use.                                                |
| `ALREADY_IN_TEAM`           | 400 Bad Request       | Student attempts to create/join team when already in team for this assignment. One team per assignment. | Leave current team first, then join/create new one.                                                  |
| `TEAM_NAME_EXISTS`          | 409 Conflict          | Duplicate team name within same assignment (unique per assignment).                                     | Use different team name.                                                                             |
| `TEAM_FORMATION_CLOSED`     | 409 Conflict          | teamLockAt has passed. Team formation window closed; cannot create/join teams.                          | Teams can only form before lock date. Wait for next assignment or request extension from instructor. |
| `TEAM_FULL`                 | 400 Bad Request       | Team at max capacity (max_team_size reached). Cannot join.                                              | Join different team or create new team.                                                              |
| `TEAM_LOCKED`               | 403 Forbidden         | Team is locked (is_locked=true). Cannot modify, add, or remove members.                                 | Request admin to unlock team if needed.                                                              |
| `NOT_TEAM_MEMBER`           | 403 Forbidden         | Student not in team; cannot access team resources (submissions, reviews, etc.).                         | Join team first or request access.                                                                   |
| `INVITE_ALREADY_SENT`       | 400 Bad Request       | Invite to same email already pending. Duplicate invite.                                                 | Withdraw previous invite or wait for response.                                                       |
| `ALREADY_RESPONDED`         | 400 Bad Request       | Student has already responded (ACCEPTED/DECLINED) to invite. Cannot respond twice.                      | Check invite history. Response is final.                                                             |
| `CANNOT_REMOVE_CREATOR`     | 400 Bad Request       | Attempt to remove team creator from team. Creator must remain.                                          | Assign new creator role before removing, or delete team.                                             |
| `ALL_ASSIGNED`              | 400 Bad Request       | All students already in teams. No unassigned students for auto-assignment.                              | Verify all students have teams.                                                                      |
| `ALREADY_LOCKED`            | 400 Bad Request       | Team already locked; cannot update lock state.                                                          | Team is already locked.                                                                              |
| `INVALID_FILE_TYPE`         | 400 Bad Request       | File extension not in whitelist (e.g., .exe, .bat).                                                     | Upload supported file type.                                                                          |
| `FILE_TOO_LARGE`            | 413 Payload Too Large | File exceeds 50MB limit.                                                                                | Compress file or split into multiple uploads.                                                        |
| `INVALID_MIME_TYPE`         | 400 Bad Request       | File content does not match declared extension (e.g., .txt file with binary content).                   | Upload correct file type or verify file integrity.                                                   |
| `UPLOAD_IN_PROGRESS`        | 409 Conflict          | Concurrent upload for same team/student. Another upload in flight.                                      | Wait for current upload to complete before retrying.                                                 |
| `FILE_NOT_FOUND_IN_STORAGE` | 404 Not Found         | DB record exists but file missing from S3. Orphaned submission.                                         | Reupload file or contact admin to clean up record.                                                   |
| `EVALUATION_EXISTS`         | 409 Conflict          | Duplicate evaluation for submission. One evaluation per submission.                                     | Submission already evaluated. Edit existing evaluation instead.                                      |
| `SCORE_EXCEEDS_MAX`         | 400 Bad Request       | Score above criterion maxScore.                                                                         | Reduce score to ≤maxScore.                                                                           |
| `NEGATIVE_SCORE`            | 400 Bad Request       | Score below 0.                                                                                          | Score must be ≥0.                                                                                    |
| `INVALID_CRITERION`         | 400 Bad Request       | criterionId not for this assignment's rubric.                                                           | Use valid criterion.                                                                                 |
| `EVALUATION_PUBLISHED`      | 403 Forbidden         | Attempt to edit published evaluation. Published evaluations immutable.                                  | Reopen first (SYSTEM_ADMIN override) or create new evaluation.                                       |
| `ALREADY_PUBLISHED`         | 403 Forbidden         | Attempt to publish already-published evaluation. Cannot publish twice.                                  | Evaluation already published. Edit requires reopen.                                                  |
| `ALREADY_DRAFT`             | 403 Forbidden         | Attempt to reopen already-draft evaluation. No-op.                                                      | Evaluation is already draft. No action needed.                                                       |
| `NOT_PUBLISHED`             | 400 Bad Request       | Cannot generate PDF before evaluation is published.                                                     | Publish evaluation first.                                                                            |
| `PDF_NOT_GENERATED`         | 404 Not Found         | PDF not yet generated. Async job pending.                                                               | Wait and retry.                                                                                      |
| `CANNOT_DEACTIVATE_SELF`    | 400 Bad Request       | Admin deactivating own account. Cannot disable yourself.                                                | Contact another admin or SYSTEM_ADMIN.                                                               |
| `ALREADY_INACTIVE`          | 400 Bad Request       | User already deactivated; cannot deactivate again.                                                      | User is already inactive.                                                                            |
| `ALREADY_ACTIVE`            | 400 Bad Request       | User already active; cannot reactivate twice.                                                           | User is already active.                                                                              |

---

## S3 Key Naming Convention (PRD-S3)

All files stored in S3 follow standardized naming for auditability and organization:

```
submissions/{hashedAssignmentId}/{hashedOwnerIdTeamOrStudent}/v{n}/{originalFilename}
pdfs/{hashedEvaluationId}/report.pdf
avatars/{hashedUserId}/avatar.{ext}
test/connection-check.txt  ← S3ConnectionTest only; deleted immediately after verification
```

**Rules:**

- Never raw DB integers; always hashed IDs via `HashidService`
- Version number from database (submission.versionNumber)
- Filenames sanitised (alphanumeric, hyphens, dots; max 100 chars) and lowercase
- Prevents ID guessing (consistent with API layer security model)

---

## Cross-References & Documentation Links

This document is the central reference for global rules. For detailed information on specific features:

| Topic                               | Document                                               | Notes                                                                  |
| ----------------------------------- | ------------------------------------------------------ | ---------------------------------------------------------------------- |
| Architectural decisions & rationale | [DECISIONS.md](DECISIONS.md)                           | Why each error code, each endpoint exists; alternatives considered     |
| System design & data flow diagrams  | [ARCHITECTURE.md](ARCHITECTURE.md)                     | Data model, async flows, state machines, integration points            |
| Feature PRDs (individual specs)     | `Features/PRD_*.md`                                    | Detailed requirements for each PRD (1-9, S3)                           |
| Implementation checklist            | `orchestration/ENGINEERING_STANDARDS.md`               | Code style, testing standards, migration process                       |
| Local testing setup                 | `Controller_specs/ReviewFlow_Postman_Guide.md`         | Postman collections for manual testing                                 |
| API module specifications           | `01_Module_Auth.md` through `06_Module_Evaluations.md` | Endpoint details, request/response examples, error handling per module |

---

## Quick Navigation: Error Codes by HTTP Status

### 4xx Client Errors

**400 Bad Request**

- `VALIDATION_ERROR`, `NOT_ENROLLED`, `NOT_AN_INSTRUCTOR`, `NOT_A_STUDENT`, `INVALID_DUE_DATE`, `INVALID_LOCK_DATE`, `TEAM_FULL`, `INVITE_ALREADY_SENT`, `ALREADY_RESPONDED`, `CANNOT_REMOVE_CREATOR`, `INVALID_FILE_TYPE`, `INVALID_MIME_TYPE`, `SCORE_EXCEEDS_MAX`, `NEGATIVE_SCORE`, `INVALID_CRITERION`, `NOT_PUBLISHED`, `CANNOT_DEACTIVATE_SELF`, `ALREADY_INACTIVE`, `ALREADY_ACTIVE`, `ALL_ASSIGNED`, `ALREADY_LOCKED`, `EXTENSION_INVALID_DURATION`, `ASSIGNMENT_NOT_IN_COURSE`, `UNKNOWN_CACHE`

**401 Unauthorized**

- `INVALID_CREDENTIALS`, `UNAUTHORIZED`

**403 Forbidden**

- `ACCOUNT_DEACTIVATED`, `FORBIDDEN`, `TEAM_LOCKED`, `NOT_TEAM_MEMBER`, `EVALUATION_PUBLISHED`, `ALREADY_PUBLISHED`, `ALREADY_DRAFT`, `EXTENSION_NOT_APPROVED`, `CANNOT_FORCE_LOGOUT_SYSTEM_ADMIN`, `ACTUATOR_FORBIDDEN`, `PRESIGNED_URL_EXPIRED`

**404 Not Found**

- `NOT_FOUND`, `ANNOUNCEMENT_NOT_FOUND`, `ANNOUNCEMENT_NOT_PUBLISHED`, `EXTENSION_REQUEST_NOT_FOUND`, `NO_SUBMISSIONS_FOUND`, `PDF_NOT_GENERATED`, `FILE_NOT_FOUND_IN_STORAGE`, `S3_KEY_NOT_FOUND`, `AVATAR_NOT_FOUND`

**409 Conflict**

- `COURSE_CODE_EXISTS`, `EMAIL_EXISTS`, `ALREADY_ENROLLED`, `ALREADY_ASSIGNED`, `TEAM_NAME_EXISTS`, `TEAM_FORMATION_CLOSED`, `UPLOAD_IN_PROGRESS`, `EVALUATION_EXISTS`, `ALREADY_PUBLISHED` (when context requires 409), `SUBMISSION_TYPE_LOCKED`, `INDIVIDUAL_SUBMISSION_REQUIRED`, `TEAM_SUBMISSION_REQUIRED`, `TEAM_NOT_ALLOWED`, `EXTENSION_REQUEST_EXISTS`, `ALREADY_RESPONDED` (extension context), `EXTENSION_ALREADY_USED`, `EXTENSION_CUTOFF_PASSED`, `SYSTEM_ADMIN_LIMIT_EXCEEDED`, `TEAM_NOT_LOCKED`, `EVALUATION_NOT_PUBLISHED`

**413 Payload Too Large**

- `FILE_TOO_LARGE`, `AVATAR_TOO_LARGE`

**429 Too Many Requests**

- `EVICTION_TOO_SOON`

### 5xx Server Errors

**500 Internal Server Error**

- `EMAIL_SEND_FAILED`, `AVATAR_UPLOAD_FAILED`

**503 Service Unavailable**

- `S3_UNAVAILABLE`

---

## Implementation Checklist for Global Rules

- [ ] **Response Envelope:** All endpoints wrap responses in envelope; error responses include `code` field
- [ ] **Trace ID:** MDC filter injects X-Trace-Id UUID into all log lines; response headers include X-Trace-Id
- [ ] **Pagination Headers:** List endpoints return X-Total-Elements and X-Total-Pages headers
- [ ] **Role Hierarchy:** Spring Security configured with SYSTEM_ADMIN > ADMIN > INSTRUCTOR > STUDENT
- [ ] **Auth Filter:** Checks role for each endpoint; returns 403 FORBIDDEN if insufficient; returns 401 UNAUTHORIZED if no token
- [ ] **Error Handling:** GlobalExceptionHandler maps all error codes to correct HTTP status
- [ ] **Audit Logging:** All CREATE, UPDATE, DELETE operations logged to audit table (immutable, INSERT-only)
- [ ] **WebSocket Auth:** SYSTEM_ADMIN-only subscription to /queue/system-metrics
- [ ] **S3 Keys:** All S3 keys use hashed IDs; follow naming convention; never raw integers

---

**Document Version:** 2.0  
**Last Updated:** 2026-03-30  
**Maintained By:** Agent D (Global Rules Agent)  
**Status:** Consolidated from audit summary; ready for implementation
