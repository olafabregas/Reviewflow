# ReviewFlow — Module 5: Submissions
> Controller: `SubmissionController.java`
> Base path: `/api/v1/submissions`

---

## 5.1 POST /submissions

### Must Have
- [ ] STUDENT only (must be ACCEPTED member of the team)
- [ ] `Content-Type: multipart/form-data`
- [ ] Form fields: `teamId` (required), `assignmentId` (required), `file` (required), `changeNote` (optional, max 500 chars)
- [ ] Validates file extension against allowed list
- [ ] Validates MIME type by reading file bytes (Apache Tika) — NOT client Content-Type
- [ ] Validates file size ≤ 50MB
- [ ] Auto-increments `versionNumber` per team atomically (DB transaction)
- [ ] Sets `is_late = true` if `uploadedAt > assignment.dueAt`
- [ ] Stores: `fileName`, `fileSizeBytes`, `storagePath`, `versionNumber`, `isLate`, `changeNote`, `uploadedBy`
- [ ] Sends `NEW_SUBMISSION` notification to all other team members (not the uploader)

### Responses
- [ ] `201 Created` — returns `{ id, versionNumber, fileName, fileSizeBytes, isLate, uploadedAt, changeNote, uploadedBy: { userId, firstName, lastName } }`
- [ ] `400 Bad Request` — file missing
- [ ] `400 Bad Request` — file type not allowed → `{ code: "INVALID_FILE_TYPE", message: "File type .exe is not allowed. Allowed: .zip .rar .pdf ..." }`
- [ ] `400 Bad Request` — file too large → `{ code: "FILE_TOO_LARGE", message: "File size 67MB exceeds maximum of 50MB" }`
- [ ] `400 Bad Request` — MIME type mismatch (spoofed file) → `{ code: "INVALID_MIME_TYPE", message: "File content does not match its extension" }`
- [ ] `400 Bad Request` — `changeNote` exceeds 500 chars
- [ ] `400 Bad Request` — `teamId` or `assignmentId` missing
- [ ] `403 Forbidden` — student is not an ACCEPTED member of the team → `{ code: "NOT_TEAM_MEMBER" }`
- [ ] `404 Not Found` — team or assignment doesn't exist
- [ ] `409 Conflict` — concurrent upload already in progress for this team → `{ code: "UPLOAD_IN_PROGRESS" }`

### Edge Cases
- [ ] Valid file upload → `201`, `versionNumber = 1`
- [ ] Second upload same team → `201`, `versionNumber = 2`
- [ ] Upload `.exe` file → `400 INVALID_FILE_TYPE`
- [ ] Upload `.zip` that is actually an `.exe` (renamed) → `400 INVALID_MIME_TYPE`
- [ ] Upload 51MB file → `400 FILE_TOO_LARGE`
- [ ] Upload after `dueAt` → `201` but `isLate = true`
- [ ] Upload by student NOT in team → `403 NOT_TEAM_MEMBER`
- [ ] `changeNote` of 501 chars → `400`
- [ ] Concurrent upload from two team members → one succeeds, one gets `409`

### Allowed File Types
- [ ] `.zip`, `.rar`, `.tar.gz` — archives
- [ ] `.pdf` — documents
- [ ] `.java`, `.py`, `.js`, `.ts`, `.cpp`, `.c`, `.cs`, `.go`, `.rb` — source code
- [ ] `.html`, `.css`, `.sql`, `.json`, `.xml`, `.yaml`, `.md` — other code/config

---

## 5.2 GET /submissions/{id}

### Must Have
- [ ] STUDENT: only if member of the team that submitted
- [ ] INSTRUCTOR: only if submission belongs to their course
- [ ] ADMIN: any submission
- [ ] Returns full submission detail including `changeNote`, `uploadedBy`, `versionNumber`, `isLate`

### Responses
- [ ] `200 OK` — submission detail
- [ ] `403 Forbidden` — student accessing another team's submission (return silently — no info)
- [ ] `404 Not Found` — submission doesn't exist

---

## 5.3 GET /submissions/{id}/download

### Must Have
- [ ] Same access rules as `GET /submissions/{id}`
- [ ] Streams file as download
- [ ] Sets `Content-Disposition: attachment; filename="original-filename.zip"`
- [ ] Sets correct `Content-Type` header
- [ ] Streams file — does NOT load entire file into memory

### Responses
- [ ] `200 OK` — file streamed
- [ ] `403 Forbidden` — access denied
- [ ] `404 Not Found` — submission not found or file missing from storage

### Edge Cases
- [ ] File exists in DB but missing from storage → `404` with `{ code: "FILE_NOT_FOUND_IN_STORAGE" }`
- [ ] Storage path never exposed in any response body

---

## 5.4 GET /teams/{id}/submissions

### Must Have
- [ ] Returns ALL versions for a team in descending order (latest first)
- [ ] STUDENT: only their own team
- [ ] INSTRUCTOR: any team in their course
- [ ] Each version: `{ id, versionNumber, fileName, fileSizeBytes, isLate, uploadedAt, changeNote, uploadedBy }`

### Responses
- [ ] `200 OK` — list of all versions
- [ ] `403 Forbidden` — student accessing another team
- [ ] `404 Not Found` — team doesn't exist

---

## 5.5 GET /assignments/{id}/submissions

### Must Have
- [ ] INSTRUCTOR only
- [ ] Returns LATEST submission per team (not all versions)
- [ ] Includes teams that have NOT submitted (with `submitted: false`)
- [ ] Each item: `{ teamId, teamName, memberCount, submitted, latestVersion, submittedAt, isLate, fileName }`

### Responses
- [ ] `200 OK` — submissions list (includes unsubmitted teams)
- [ ] `403 Forbidden` — not instructor for this course
- [ ] `404 Not Found`

---

## 5.6 GET /students/me/submissions ⭐ NEW

### Must Have
- [ ] STUDENT only
- [ ] Returns all submissions across all of the student's teams globally
- [ ] Each item: `{ submissionId, versionNumber, assignmentTitle, courseCode, teamName, isLate, uploadedAt, fileName }`
- [ ] Sorted by `uploadedAt DESC`

### Responses
- [ ] `200 OK` — paginated list
- [ ] `401 Unauthorized`
- [ ] `403 Forbidden` — not STUDENT
