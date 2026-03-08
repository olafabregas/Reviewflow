# ReviewFlow — Module 8: Admin
> Controller: `AdminController.java`
> Base path: `/api/v1/admin`

---

## 8.1 GET /admin/users

### Must Have
- [ ] ADMIN only
- [ ] Supports `?page=0&size=20`
- [ ] Supports `?role=STUDENT|INSTRUCTOR|ADMIN` filter
- [ ] Supports `?active=true|false` filter
- [ ] Supports `?search=jane` — searches firstName, lastName, email (min 2 chars)
- [ ] Each user: `{ id, firstName, lastName, email, role, isActive, createdAt }`
- [ ] Password hash NEVER returned

### Responses
- [ ] `200 OK` — paginated users
- [ ] `400 Bad Request` — search less than 2 characters
- [ ] `403 Forbidden` — not ADMIN

---

## 8.2 POST /admin/users

### Must Have
- [ ] ADMIN only
- [ ] Required: `firstName`, `lastName`, `email`, `password`, `role`
- [ ] Email must be unique
- [ ] Password BCrypt-hashed before storage
- [ ] Account created with `is_active = true`
- [ ] Logs `USER_CREATED` to `audit_log`

### Responses
- [ ] `201 Created` — user object (no password)
- [ ] `400 Bad Request` — missing fields
- [ ] `400 Bad Request` — password less than 8 chars
- [ ] `400 Bad Request` — invalid role value
- [ ] `403 Forbidden` — not ADMIN
- [ ] `409 Conflict` — email already exists → `{ code: "EMAIL_EXISTS" }`

---

## 8.3 GET /admin/users/{id}

### Must Have
- [ ] ADMIN only
- [ ] Returns single user detail
- [ ] Includes: `{ id, firstName, lastName, email, role, isActive, createdAt, courseCount, teamCount }`

### Responses
- [ ] `200 OK`
- [ ] `403 Forbidden`
- [ ] `404 Not Found`

---

## 8.4 PATCH /admin/users/{id}

### Must Have
- [ ] ADMIN only
- [ ] Allows updating: `firstName`, `lastName`, `role`
- [ ] Does NOT allow updating `email` or `password` through this endpoint
- [ ] Logs `USER_UPDATED` to `audit_log`

### Responses
- [ ] `200 OK` — updated user
- [ ] `400 Bad Request` — invalid role value
- [ ] `403 Forbidden`
- [ ] `404 Not Found`

---

## 8.5 PATCH /admin/users/{id}/deactivate

### Must Have
- [ ] ADMIN only
- [ ] Sets `is_active = false`
- [ ] Revokes ALL active refresh tokens for that user immediately
- [ ] Deactivated user cannot login
- [ ] Cannot deactivate your own admin account
- [ ] Logs `USER_DEACTIVATED` to `audit_log`

### Responses
- [ ] `200 OK` — `{ message: "User deactivated", isActive: false }`
- [ ] `400 Bad Request` — trying to deactivate own account → `{ code: "CANNOT_DEACTIVATE_SELF" }`
- [ ] `400 Bad Request` — user already deactivated → `{ code: "ALREADY_INACTIVE" }`
- [ ] `403 Forbidden`
- [ ] `404 Not Found`

---

## 8.6 PATCH /admin/users/{id}/reactivate ⭐ NEW

### Must Have
- [ ] ADMIN only
- [ ] Sets `is_active = true`
- [ ] User can log in again after reactivation
- [ ] Logs `USER_REACTIVATED` to `audit_log`

### Responses
- [ ] `200 OK` — `{ message: "User reactivated", isActive: true }`
- [ ] `400 Bad Request` — user is already active → `{ code: "ALREADY_ACTIVE" }`
- [ ] `403 Forbidden`
- [ ] `404 Not Found`

---

## 8.7 GET /admin/stats

### Must Have
- [ ] ADMIN only
- [ ] Returns: `{ totalUsers, usersByRole: { STUDENT, INSTRUCTOR, ADMIN }, totalCourses, activeCourses, archivedCourses, totalAssignments, publishedAssignments, totalTeams, totalSubmissions, storageUsedBytes, storageUsedFormatted }`
- [ ] `storageUsedBytes` = SUM of `file_size_bytes` from `submissions` table
- [ ] `storageUsedFormatted` = human readable e.g. `"4.2 GB"`
- [ ] Cache response for 60 seconds

### Responses
- [ ] `200 OK` — stats object (all fields always present, never null — use 0 as default)
- [ ] `403 Forbidden`

---

## 8.8 GET /admin/audit-log

### Must Have
- [ ] ADMIN only
- [ ] Supports `?page=0&size=20`
- [ ] Supports `?actorId=5` filter
- [ ] Supports `?action=USER_LOGIN` filter
- [ ] Supports `?dateFrom=2026-01-01&dateTo=2026-03-01` filter
- [ ] Each entry: `{ id, actorId, actorEmail, action, targetType, targetId, metadata, ipAddress, createdAt }`
- [ ] `metadata` is a JSON object (varies by action type)
- [ ] Sorted by `created_at DESC`

### Responses
- [ ] `200 OK` — paginated audit entries
- [ ] `403 Forbidden`

### Audit Actions to Log
- [ ] `USER_LOGIN` — on successful login
- [ ] `USER_LOGIN_FAILED` — on failed login attempt
- [ ] `USER_LOGOUT` — on logout
- [ ] `USER_CREATED` — when admin creates user
- [ ] `USER_UPDATED` — when admin updates user
- [ ] `USER_DEACTIVATED` — when admin deactivates user
- [ ] `USER_REACTIVATED` — when admin reactivates user
- [ ] `COURSE_CREATED` — when course is created
- [ ] `SUBMISSION_UPLOADED` — when file uploaded (metadata: `{ fileName, versionNumber, teamId, isLate }`)
- [ ] `EVALUATION_PUBLISHED` — when evaluation published (metadata: `{ teamId, totalScore }`)
- [ ] `PDF_GENERATED` — when PDF generated
