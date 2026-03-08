# ReviewFlow — Module 2: Courses
> Controller: `CourseController.java`
> Base path: `/api/v1/courses` and `/api/v1/admin/courses`

---

## 2.1 GET /courses

### Must Have
- [ ] Endpoint exists — returns courses WITHOUT requiring a `courseId`
- [ ] Role-filtered results:
  - ADMIN → all courses in the system
  - INSTRUCTOR → only courses they are assigned to
  - STUDENT → only courses they are enrolled in
- [ ] Supports `?page=0&size=20&sort=createdAt,desc`
- [ ] Supports `?archived=false` filter (default: exclude archived)
- [ ] Each course item includes: `id, code, name, term, description, isArchived, instructorCount, enrollmentCount, assignmentCount`

### Responses
- [ ] `200 OK` — paginated list of courses
- [ ] `401 Unauthorized` — no valid session

### Edge Cases
- [ ] Admin sees all courses including archived → `200`
- [ ] Student only sees enrolled courses → `200` (not all courses)
- [ ] Instructor only sees assigned courses → `200`
- [ ] User enrolled in 0 courses → `200` with empty array (not `404`)
- [ ] `?archived=true` returns only archived → `200`

---

## 2.2 POST /courses

### Must Have
- [ ] Endpoint exists, ADMIN only
- [ ] Required fields: `code`, `name`, `term`
- [ ] Optional field: `description`
- [ ] `code` must be unique across all courses
- [ ] Returns created course object with generated `id`
- [ ] Logs `COURSE_CREATED` to `audit_log`

### Responses
- [ ] `201 Created` — course created
- [ ] `400 Bad Request` — missing required fields with field names in error
- [ ] `403 Forbidden` — user is not ADMIN
- [ ] `409 Conflict` — course code already exists → `{ code: "COURSE_CODE_EXISTS", message: "A course with code CS401 already exists" }`

### Edge Cases
- [ ] Create with unique code → `201`
- [ ] Create with duplicate code → `409 COURSE_CODE_EXISTS`
- [ ] Create with missing `name` → `400` specifying `name` field
- [ ] Create with missing `term` → `400` specifying `term` field
- [ ] INSTRUCTOR tries to create → `403`
- [ ] STUDENT tries to create → `403`

---

## 2.3 GET /courses/{id}

### Must Have
- [ ] Returns course detail including instructor list
- [ ] STUDENT: only accessible if enrolled in the course
- [ ] INSTRUCTOR: only accessible if assigned to the course
- [ ] ADMIN: accessible for any course

### Responses
- [ ] `200 OK` — course detail with instructors array
- [ ] `401 Unauthorized` — no session
- [ ] `403 Forbidden` — student not enrolled, instructor not assigned
- [ ] `404 Not Found` — course does not exist

### Edge Cases
- [ ] Student requests course they are enrolled in → `200`
- [ ] Student requests course they are NOT enrolled in → `403`
- [ ] Non-existent course ID → `404`
- [ ] Archived course accessible to admin → `200`

---

## 2.4 PUT /courses/{id}

### Must Have
- [ ] ADMIN only
- [ ] Allows updating: `code`, `name`, `term`, `description`
- [ ] `code` uniqueness check still applies on update
- [ ] Returns updated course object

### Responses
- [ ] `200 OK` — course updated
- [ ] `400 Bad Request` — invalid fields
- [ ] `403 Forbidden` — not ADMIN
- [ ] `404 Not Found` — course doesn't exist
- [ ] `409 Conflict` — new code conflicts with another course

---

## 2.5 PATCH /courses/{id}/archive

### Must Have
- [ ] ADMIN only
- [ ] Toggles `is_archived` — if false sets to true, if true sets to false
- [ ] Returns updated course with new `isArchived` value

### Responses
- [ ] `200 OK` — archive status toggled
- [ ] `403 Forbidden` — not ADMIN
- [ ] `404 Not Found` — course doesn't exist

### Edge Cases
- [ ] Archive active course → `200`, `isArchived = true`
- [ ] Archive already-archived course (unarchive) → `200`, `isArchived = false`
- [ ] Students cannot access archived course assignments → `403`

---

## 2.6 POST /courses/{id}/instructors

### Must Have
- [ ] ADMIN only
- [ ] Assigns an instructor to a course
- [ ] Body: `{ "instructorId": 5 }`
- [ ] Target user must have `role = INSTRUCTOR`
- [ ] Instructor must not already be assigned to this course

### Responses
- [ ] `200 OK` — instructor assigned
- [ ] `400 Bad Request` — `instructorId` missing
- [ ] `400 Bad Request` — target user is not an INSTRUCTOR → `{ code: "NOT_AN_INSTRUCTOR" }`
- [ ] `403 Forbidden` — not ADMIN
- [ ] `404 Not Found` — course or user doesn't exist
- [ ] `409 Conflict` — instructor already assigned to this course → `{ code: "ALREADY_ASSIGNED" }`

---

## 2.7 DELETE /courses/{id}/instructors/{userId}

### Must Have
- [ ] ADMIN only
- [ ] Removes instructor assignment from course
- [ ] Does NOT delete the user account

### Responses
- [ ] `200 OK` — instructor removed, returns `{ message: "Instructor removed" }`
- [ ] `403 Forbidden` — not ADMIN
- [ ] `404 Not Found` — course or user doesn't exist, or user not assigned to this course

---

## 2.8 POST /courses/{id}/enroll

### Must Have
- [ ] ADMIN only
- [ ] Body: `{ "studentId": 4 }`
- [ ] Target user must have `role = STUDENT`
- [ ] Student must not already be enrolled

### Responses
- [ ] `200 OK` — student enrolled
- [ ] `400 Bad Request` — `studentId` missing
- [ ] `400 Bad Request` — target user is not a STUDENT → `{ code: "NOT_A_STUDENT" }`
- [ ] `403 Forbidden` — not ADMIN
- [ ] `404 Not Found` — course or student doesn't exist
- [ ] `409 Conflict` — student already enrolled → `{ code: "ALREADY_ENROLLED" }`

---

## 2.9 POST /courses/{id}/enroll/bulk

### Must Have
- [ ] ADMIN only
- [ ] Body: `{ "emails": ["a@uni.edu", "b@uni.edu", ...] }`
- [ ] Processes ALL emails — never fails entirely on partial errors
- [ ] Returns per-email result for every email in the array
- [ ] Each result: `{ email, status: ENROLLED | ALREADY_ENROLLED | NOT_FOUND | NOT_A_STUDENT | INVALID_EMAIL }`

### Responses
- [ ] `200 OK` — always returns 200 with results array even if all fail
- [ ] `400 Bad Request` — `emails` array is missing or empty
- [ ] `403 Forbidden` — not ADMIN
- [ ] `404 Not Found` — course doesn't exist

### Edge Cases
- [ ] All valid emails → `200`, all `ENROLLED`
- [ ] Mix of valid, already enrolled, invalid → `200` with per-result statuses
- [ ] Invalid email format → result shows `INVALID_EMAIL`
- [ ] Email belongs to INSTRUCTOR not STUDENT → result shows `NOT_A_STUDENT`
- [ ] Email not in system → result shows `NOT_FOUND`
- [ ] Empty emails array → `400`

---

## 2.10 DELETE /courses/{id}/enroll/{userId}

### Must Have
- [ ] ADMIN only
- [ ] Removes student enrollment from course
- [ ] Does NOT delete user account

### Responses
- [ ] `200 OK` — student unenrolled
- [ ] `403 Forbidden` — not ADMIN
- [ ] `404 Not Found` — course, user, or enrollment doesn't exist

---

## 2.11 GET /courses/{id}/students ⭐ NEW

### Must Have
- [ ] ADMIN and INSTRUCTOR only
- [ ] Returns list of all enrolled students for a course
- [ ] Each student includes: `userId, firstName, lastName, email, enrolledAt`
- [ ] Supports pagination

### Responses
- [ ] `200 OK` — paginated list of students
- [ ] `403 Forbidden` — STUDENT tries to access, or INSTRUCTOR not assigned to course
- [ ] `404 Not Found` — course doesn't exist
