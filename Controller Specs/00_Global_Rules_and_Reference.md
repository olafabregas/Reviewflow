# ReviewFlow — Global Rules & Reference
> Version 1.0 | Spring Boot 3 | Base URL: `http://localhost:8081/api/v1`

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
- [ ] Every protected endpoint returns `401` when no access token cookie is present
- [ ] Every protected endpoint returns `401` when access token is expired
- [ ] Every role-restricted endpoint returns `403` when user's role is insufficient
- [ ] Every `{id}` path variable that doesn't exist returns `404`
- [ ] Every missing required field returns `400` with field-level validation errors
- [ ] Every `400` error includes which field failed and why
- [ ] Every error response uses the standard envelope `{ success: false, error: { code, message } }`
- [ ] Password hash is NEVER returned in any response ever
- [ ] Timestamps are always ISO 8601 UTC format
- [ ] All list endpoints support `?page=0&size=20` pagination
- [ ] Pagination responses include `totalElements` and `totalPages`

---

## New Endpoints to Add

| # | Method | Endpoint | Priority | Blocks |
|---|--------|----------|----------|--------|
| 1 | GET | `/assignments` | 🔴 HIGH | Student dashboard deadline widget |
| 2 | GET | `/students/me/invites` | 🔴 HIGH | Student team invite flow |
| 3 | GET | `/students/me/submissions` | 🟡 MEDIUM | Student submission history page |
| 4 | GET | `/students/me/evaluations` | 🟡 MEDIUM | Student feedback history page |
| 5 | GET | `/courses/{id}/students` | 🟡 MEDIUM | Instructor team management view |
| 6 | PATCH | `/admin/users/{id}/reactivate` | 🟡 MEDIUM | Admin user management completeness |
| 7 | PUT | `/teams/{id}` | 🟢 LOW | Team name editing |
| 8 | POST | `/teams/{id}/lock` | 🟢 LOW | Instructor manual team lock |
| 9 | DELETE | `/notifications/{id}` | 🟢 LOW | Notification cleanup |

---

## Error Codes Reference

All error responses must use one of these codes in `error.code`. Never use vague codes like `"ERROR"` or `"FAILED"`.

| Code | HTTP | When |
|------|------|------|
| `INVALID_CREDENTIALS` | 401 | Wrong email or password |
| `ACCOUNT_DEACTIVATED` | 403 | Account is_active = false |
| `UNAUTHORIZED` | 401 | No valid session / token |
| `FORBIDDEN` | 403 | Insufficient role |
| `NOT_FOUND` | 404 | Resource doesn't exist |
| `VALIDATION_ERROR` | 400 | Missing/invalid request fields |
| `COURSE_CODE_EXISTS` | 409 | Duplicate course code |
| `EMAIL_EXISTS` | 409 | Duplicate user email |
| `ALREADY_ENROLLED` | 409 | Student already enrolled |
| `NOT_ENROLLED` | 400 | Student not enrolled in course |
| `NOT_AN_INSTRUCTOR` | 400 | User is not an instructor |
| `NOT_A_STUDENT` | 400 | User is not a student |
| `ALREADY_ASSIGNED` | 409 | Instructor already assigned |
| `INVALID_DUE_DATE` | 400 | dueAt is in the past |
| `INVALID_LOCK_DATE` | 400 | teamLockAt is after dueAt |
| `ASSIGNMENT_PUBLISHED` | 400 | Cannot delete published assignment |
| `HAS_SUBMISSIONS` | 400 | Cannot delete/unpublish with submissions |
| `HAS_SCORES` | 400 | Cannot delete criterion with scores |
| `ALREADY_IN_TEAM` | 400 | Student already on a team |
| `TEAM_NAME_EXISTS` | 409 | Duplicate team name per assignment |
| `TEAM_FORMATION_CLOSED` | 409 | teamLockAt has passed |
| `TEAM_FULL` | 400 | Team at max capacity |
| `TEAM_LOCKED` | 403 | Team is locked |
| `NOT_TEAM_MEMBER` | 403 | Student not in team |
| `INVITE_ALREADY_SENT` | 400 | Duplicate invite |
| `ALREADY_RESPONDED` | 400 | Invite already accepted/declined |
| `CANNOT_REMOVE_CREATOR` | 400 | Cannot remove team creator |
| `ALL_ASSIGNED` | 400 | All students already have teams |
| `ALREADY_LOCKED` | 400 | Team already locked |
| `INVALID_FILE_TYPE` | 400 | File extension not allowed |
| `FILE_TOO_LARGE` | 400 | File exceeds 50MB |
| `INVALID_MIME_TYPE` | 400 | File content does not match extension |
| `UPLOAD_IN_PROGRESS` | 409 | Concurrent upload for same team |
| `FILE_NOT_FOUND_IN_STORAGE` | 404 | DB record exists but file missing |
| `EVALUATION_EXISTS` | 409 | Duplicate evaluation for submission |
| `SCORE_EXCEEDS_MAX` | 400 | Score above criterion maxScore |
| `NEGATIVE_SCORE` | 400 | Score below 0 |
| `INVALID_CRITERION` | 400 | criterionId not for this assignment |
| `EVALUATION_PUBLISHED` | 403 | Cannot edit published evaluation |
| `ALREADY_PUBLISHED` | 403 | Cannot publish already published |
| `ALREADY_DRAFT` | 403 | Cannot reopen already draft |
| `NOT_PUBLISHED` | 400 | Cannot generate PDF before publishing |
| `PDF_NOT_GENERATED` | 404 | PDF not yet generated |
| `CANNOT_DEACTIVATE_SELF` | 400 | Admin deactivating own account |
| `ALREADY_INACTIVE` | 400 | User already deactivated |
| `ALREADY_ACTIVE` | 400 | User already active |
