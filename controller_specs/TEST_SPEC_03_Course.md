# TEST_SPEC_03 — Course Module

**Status:** Complete  
**Version:** 1.0  
**Last Updated:** 2026-04-07  
**Reference Spec:** [02_Module_Courses.md](02_Module_Courses.md) | [00_Global_Rules_and_Reference.md](00_Global_Rules_and_Reference.md)  
**Related PRDs:** [PRD-08 Logging & Monitoring](../Features/PRD_08_logging_monitoring.md) | [PRD-01 Submission Type](../Features/PRD_01_submission_type.md)

---

## 1. Endpoints Inventory

| #   | Method | Endpoint                             | Description                   | Auth Required | Role              |
| --- | ------ | ------------------------------------ | ----------------------------- | ------------- | ----------------- |
| 1   | GET    | `/courses`                           | List courses (role-filtered)  | ✅            | STUDENT+          |
| 2   | GET    | `/courses/{id}`                      | Get course detail             | ✅            | STUDENT+          |
| 3   | POST   | `/courses`                           | Create new course             | ✅            | INSTRUCTOR+       |
| 4   | PUT    | `/courses/{id}`                      | Update course                 | ✅            | ADMIN+            |
| 5   | PATCH  | `/courses/{id}/archive`              | Toggle course archive status  | ✅            | ADMIN+            |
| 6   | POST   | `/courses/{id}/instructors`          | Assign instructor to course   | ✅            | ADMIN+            |
| 7   | DELETE | `/courses/{id}/instructors/{userId}` | Remove instructor from course | ✅            | ADMIN+            |
| 8   | POST   | `/courses/{id}/enroll`               | Enroll single student         | ✅            | ADMIN+            |
| 9   | POST   | `/courses/{id}/enroll/bulk`          | Bulk enroll students by email | ✅            | ADMIN+            |
| 10  | DELETE | `/courses/{id}/enroll/{userId}`      | Unenroll student              | ✅            | ADMIN+            |
| 11  | GET    | `/courses/{id}/students`             | List enrolled students        | ✅            | ADMIN, INSTRUCTOR |

---

## 2. Role Permission Matrix

| Endpoint                                  | STUDENT            | INSTRUCTOR         | ADMIN        | SYSTEM_ADMIN |
| ----------------------------------------- | ------------------ | ------------------ | ------------ | ------------ |
| GET /courses                              | ✅ (enrolled only) | ✅ (assigned only) | ✅ (all)     | ✅ (all)     |
| GET /courses/{id}                         | ✅ (if enrolled)   | ✅ (if assigned)   | ✅           | ✅           |
| POST /courses                             | ❌ 403             | ✅ 201 (own)       | ✅ 201 (any) | ✅ 201 (any) |
| PUT /courses/{id}                         | ❌ 403             | ❌ 403             | ✅ 200       | ✅ 200       |
| PATCH /courses/{id}/archive               | ❌ 403             | ❌ 403             | ✅ 200       | ✅ 200       |
| POST /courses/{id}/instructors            | ❌ 403             | ❌ 403             | ✅ 200       | ✅ 200       |
| DELETE /courses/{id}/instructors/{userId} | ❌ 403             | ❌ 403             | ✅ 200       | ✅ 200       |
| POST /courses/{id}/enroll                 | ❌ 403             | ❌ 403             | ✅ 200       | ✅ 200       |
| POST /courses/{id}/enroll/bulk            | ❌ 403             | ❌ 403             | ✅ 200       | ✅ 200       |
| DELETE /courses/{id}/enroll/{userId}      | ❌ 403             | ❌ 403             | ✅ 200       | ✅ 200       |
| GET /courses/{id}/students                | ❌ 403             | ✅ (own course)    | ✅           | ✅           |

**Key Authorization Rules:**

- **Course creation is ADMIN/SYSTEM_ADMIN only (Fix 2)**: @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')") on POST /courses
- **INSTRUCTOR can only manage own courses**: Service-layer enforcement prevents cross-course edits
- **INSTRUCTOR cannot archive**: Only ADMIN and SYSTEM_ADMIN can toggle archive status
- **All ADMIN+ actions on enrollment**: Students cannot self-enroll or bulk-enroll
- **SYSTEM_ADMIN includes all ADMIN permissions** via role hierarchy

---

## 3. Test Data

### Real Test Users

```
ADMIN Role:
  - humberadmin@reviewflow.com
  - yorkadmin@reviewflow.com

SYSTEM_ADMIN Role:
  - main_sysadmin@reviewflow.com

INSTRUCTOR Role:
  - sarah.johnson@university.edu
  - michael.torres@university.edu

STUDENT Role:
  - jane.smith@university.edu
  - marcus.chen@university.edu

Global Test Password: Test@1234
```

### Real Test Courses

```
Existing courses in database:
  - CS-101: "Intro to Computer Science" (Fall 2025)
  - CS-201: "Data Structures" (Fall 2025)
  - MATH-101: "Calculus I" (Fall 2025)

New courses for testing:
  - CS-501: "Advanced Java" (Spring 2026) — Created by INSTRUCTOR (Fix 2)
  - CS-601: "Machine Learning" (Spring 2026) — Created by ADMIN
  - PHYS-101: "Physics I" (Spring 2026) — For archiving tests
```

### Base Configuration

```
Base URL: http://localhost:8081/api/v1
Content-Type: application/json
Authentication: HTTP-only cookies (reviewflow_access)
```

---

## 4. Success Path Tests (Happy Path)

### Prerequisites

All tests use HTTP-only cookies for session management:

- `reviewflow_access` — JWT access token (15 min expiry)
- `reviewflow_refresh` — JWT refresh token (7 day expiry)

Each test section below includes full request/response examples.

---

### 4.1 INSTRUCTOR Creates Course (FIX 2 Verification)

**Test Case:** `Create_Course_INSTRUCTOR_201`

**User:** sarah.johnson@university.edu (INSTRUCTOR)  
**Password:** Test@1234

**Step 1: Login as INSTRUCTOR**

```http
POST /auth/login
Content-Type: application/json

{
  "email": "sarah.johnson@university.edu",
  "password": "Test@1234"
}
```

**Expected Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "userId": "<instructor_id>",
    "firstName": "Sarah",
    "lastName": "Johnson",
    "email": "sarah.johnson@university.edu",
    "role": "INSTRUCTOR"
  },
  "timestamp": "2026-04-07T12:00:00.000Z"
}
```

**Verify:**

- [ ] Status code is 200
- [ ] role = "INSTRUCTOR"
- [ ] `reviewflow_access` cookie set (HttpOnly, Path=/, 15 min expiry)
- [ ] X-Trace-Id header present

---

**Step 2: Create Course CS-501 (Fix 2 — INSTRUCTOR can now create)**

```http
POST /courses
Content-Type: application/json
Cookie: reviewflow_access=<access_token>

{
  "code": "CS-501",
  "name": "Advanced Java Programming",
  "term": "Spring-2026",
  "description": "Deep dive into Java 21 features and design patterns"
}
```

**Expected Response:** `201 Created`

```json
{
  "success": true,
  "data": {
    "id": "<course_id_501>",
    "code": "CS-501",
    "name": "Advanced Java Programming",
    "term": "Spring-2026",
    "description": "Deep dive into Java 21 features and design patterns",
    "instructorCount": 1,
    "enrollmentCount": 0,
    "assignmentCount": 0,
    "isArchived": false,
    "createdAt": "2026-04-07T12:05:00.000Z",
    "createdBy": "<instructor_id>"
  },
  "timestamp": "2026-04-07T12:05:00.000Z"
}
```

**Verify:**

- [ ] Status code is 201
- [ ] Course ID is generated
- [ ] `createdBy` matches INSTRUCTOR user ID
- [ ] `instructorCount` = 1 (INSTRUCTOR automatically assigned)
- [ ] `enrollmentCount` = 0
- [ ] `isArchived` = false
- [ ] Audit log entry: `eventType=COURSE_CREATED, userId=<instructor_id>, courseCode=CS-501, courseId=<course_id_501>`

---

### 4.2 ADMIN Lists All Courses (Including INSTRUCTOR-Created CS-501)

**Test Case:** `List_Courses_ADMIN_Sees_All_200`

**User:** humberadmin@reviewflow.com (ADMIN)

**Step 1: Login as ADMIN**

```http
POST /auth/login
Content-Type: application/json

{
  "email": "humberadmin@reviewflow.com",
  "password": "Test@1234"
}
```

**Expected Response:** `200 OK`

---

**Step 2: List All Courses**

```http
GET /courses?page=0&size=20&sort=createdAt,desc
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "<course_id_501>",
        "code": "CS-501",
        "name": "Advanced Java Programming",
        "term": "Spring-2026",
        "instructorCount": 1,
        "enrollmentCount": 0,
        "assignmentCount": 0,
        "isArchived": false
      },
      {
        "id": "<course_id_101>",
        "code": "CS-101",
        "name": "Intro to Computer Science",
        "term": "Fall 2025",
        "instructorCount": 2,
        "enrollmentCount": 45,
        "assignmentCount": 8,
        "isArchived": false
      },
      {
        "id": "<course_id_201>",
        "code": "CS-201",
        "name": "Data Structures",
        "term": "Fall 2025",
        "instructorCount": 1,
        "enrollmentCount": 32,
        "isArchived": false
      }
    ],
    "totalElements": 3,
    "totalPages": 1
  },
  "timestamp": "2026-04-07T12:10:00.000Z"
}
```

**Verify:**

- [ ] Status code is 200
- [ ] ADMIN sees CS-501 created by INSTRUCTOR in the list
- [ ] ADMIN sees all courses (CS-101, CS-201, CS-501)
- [ ] Response includes pagination metadata: `totalElements`, `totalPages`
- [ ] X-Trace-Id header present
- [ ] X-Total-Elements header = 3
- [ ] X-Total-Pages header = 1

---

### 4.3 STUDENT Lists Only Enrolled Courses

**Test Case:** `List_Courses_STUDENT_Enrolled_Only_200`

**User:** jane.smith@university.edu (STUDENT)

**Prerequisite:** jane.smith@university.edu is enrolled in CS-101 and CS-201

**Request:**

```http
GET /courses?page=0&size=20
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "<course_id_201>",
        "code": "CS-201",
        "name": "Data Structures",
        "term": "Fall 2025",
        "instructorCount": 1,
        "enrollmentCount": 32,
        "isArchived": false
      },
      {
        "id": "<course_id_101>",
        "code": "CS-101",
        "name": "Intro to Computer Science",
        "term": "Fall 2025",
        "instructorCount": 2,
        "enrollmentCount": 45,
        "isArchived": false
      }
    ],
    "totalElements": 2,
    "totalPages": 1
  },
  "timestamp": "2026-04-07T12:15:00.000Z"
}
```

**Verify:**

- [ ] Status code is 200
- [ ] STUDENT sees only enrolled courses (CS-101, CS-201)
- [ ] STUDENT does NOT see CS-501 (not enrolled)
- [ ] `totalElements` = 2
- [ ] Each course shows `enrollmentCount` > 0

---

### 4.4 INSTRUCTOR Enrolls Multiple Students

**Test Case:** `Enroll_Students_INSTRUCTOR_Course_200`

**User:** humberadmin@reviewflow.com (ADMIN — required for enrollment operations)  
**Course:** CS-501 (created by INSTRUCTOR sarah.johnson@university.edu)  
**Students:** jane.smith@university.edu, marcus.chen@university.edu

**Step 1: Enroll jane.smith via POST /courses/{id}/enroll**

```http
POST /courses/<course_id_501>/enroll
Content-Type: application/json
Cookie: reviewflow_access=<access_token>

{
  "studentId": "<jane_smith_id>"
}
```

**Expected Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "id": "<enrollment_id_1>",
    "courseId": "<course_id_501>",
    "studentId": "<jane_smith_id>",
    "enrolledAt": "2026-04-07T12:20:00.000Z"
  },
  "timestamp": "2026-04-07T12:20:00.000Z"
}
```

**Verify:**

- [ ] Status code is 200
- [ ] Enrollment record created
- [ ] Audit log entry: `eventType=COURSE_ENROLLMENT_ADDED, userId=<admin_id>, courseId=<course_id_501>, studentId=<jane_smith_id>`

---

**Step 2: Enroll marcus.chen**

```http
POST /courses/<course_id_501>/enroll
Content-Type: application/json
Cookie: reviewflow_access=<access_token>

{
  "studentId": "<marcus_chen_id>"
}
```

**Expected Response:** `200 OK` (with marcus.chen enrollment)

**Verify:**

- [ ] Status code is 200
- [ ] Second enrollment created independently

---

**Step 3: Verify Enrollment Count Updated**

```http
GET /courses/<course_id_501>
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "id": "<course_id_501>",
    "code": "CS-501",
    "enrollmentCount": 2,
    "instructors": [
      {
        "userId": "<sarah_johnson_id>",
        "email": "sarah.johnson@university.edu",
        "firstName": "Sarah",
        "lastName": "Johnson"
      }
    ]
  },
  "timestamp": "2026-04-07T12:25:00.000Z"
}
```

**Verify:**

- [ ] `enrollmentCount` = 2
- [ ] Instructors array includes sarah.johnson@university.edu

---

### 4.5 STUDENT Views Course Details (Enrolled Course)

**Test Case:** `Get_Course_STUDENT_Authorized_200`

**User:** jane.smith@university.edu (STUDENT)

**Request:**

```http
GET /courses/<course_id_501>
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK` (jane.smith is enrolled)

```json
{
  "success": true,
  "data": {
    "id": "<course_id_501>",
    "code": "CS-501",
    "name": "Advanced Java Programming",
    "term": "Spring-2026",
    "description": "Deep dive into Java 21 features and design patterns",
    "instructorCount": 1,
    "enrollmentCount": 2,
    "isArchived": false,
    "instructors": [
      {
        "userId": "<sarah_johnson_id>",
        "email": "sarah.johnson@university.edu",
        "firstName": "Sarah",
        "lastName": "Johnson"
      }
    ]
  },
  "timestamp": "2026-04-07T12:30:00.000Z"
}
```

**Verify:**

- [ ] Status code is 200
- [ ] Full course details returned
- [ ] Instructors list included

---

### 4.6 ADMIN Archives/Unarchives Course (Toggle)

**Test Case:** `Archive_Course_ADMIN_Toggle_200`

**User:** humberadmin@reviewflow.com (ADMIN)  
**Course:** PHYS-101

**Step 1: Archive Course (PHYS-101: active → archived)**

```http
PATCH /courses/<course_id_phys101>/archive
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "id": "<course_id_phys101>",
    "code": "PHYS-101",
    "name": "Physics I",
    "isArchived": true,
    "archivedAt": "2026-04-07T12:35:00.000Z"
  },
  "timestamp": "2026-04-07T12:35:00.000Z"
}
```

**Verify:**

- [ ] Status code is 200
- [ ] `isArchived` changed from false to true
- [ ] `archivedAt` timestamp set
- [ ] Audit log entry: `eventType=COURSE_ARCHIVED, userId=<admin_id>, courseId=<course_id_phys101>`

---

**Step 2: Unarchive Course (PHYS-101: archived → active)**

```http
PATCH /courses/<course_id_phys101>/archive
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "id": "<course_id_phys101>",
    "code": "PHYS-101",
    "isArchived": false
  },
  "timestamp": "2026-04-07T12:40:00.000Z"
}
```

**Verify:**

- [ ] Status code is 200
- [ ] `isArchived` toggled back to false
- [ ] Audit log entry: `eventType=COURSE_UNARCHIVED, userId=<admin_id>, courseId=<course_id_phys101>`

---

### 4.7 ADMIN Bulk-Enrolls Students (10+ at Once)

**Test Case:** `Bulk_Enroll_ADMIN_10Students_200`

**User:** humberadmin@reviewflow.com (ADMIN)  
**Course:** CS-601 (empty, ready for bulk enroll)  
**Emails:** 10 valid student emails (mix of existing and non-existing for realism)

**Request:**

```http
POST /courses/<course_id_601>/enroll/bulk
Content-Type: application/json
Cookie: reviewflow_access=<access_token>

{
  "emails": [
    "jane.smith@university.edu",
    "marcus.chen@university.edu",
    "alex.patel@university.edu",
    "sam.rodriguez@university.edu",
    "taylor.kim@university.edu",
    "jordan.lee@university.edu",
    "casey.williams@university.edu",
    "morgan.davis@university.edu",
    "avery.brown@university.edu",
    "riley.jones@university.edu"
  ]
}
```

**Expected Response:** `200 OK` (always returns 200 with results)

```json
{
  "success": true,
  "data": {
    "courseId": "<course_id_601>",
    "processedAt": "2026-04-07T12:45:00.000Z",
    "results": [
      {
        "email": "jane.smith@university.edu",
        "status": "ENROLLED",
        "enrollmentId": "<enrollment_id>"
      },
      {
        "email": "marcus.chen@university.edu",
        "status": "ENROLLED",
        "enrollmentId": "<enrollment_id>"
      },
      {
        "email": "alex.patel@university.edu",
        "status": "ENROLLED",
        "enrollmentId": "<enrollment_id>"
      },
      {
        "email": "sam.rodriguez@university.edu",
        "status": "ENROLLED",
        "enrollmentId": "<enrollment_id>"
      },
      {
        "email": "taylor.kim@university.edu",
        "status": "NOT_FOUND"
      },
      {
        "email": "jordan.lee@university.edu",
        "status": "NOT_A_STUDENT"
      },
      {
        "email": "casey.williams@university.edu",
        "status": "ENROLLED",
        "enrollmentId": "<enrollment_id>"
      },
      {
        "email": "morgan.davis@university.edu",
        "status": "ENROLLED",
        "enrollmentId": "<enrollment_id>"
      },
      {
        "email": "avery.brown@university.edu",
        "status": "ALREADY_ENROLLED"
      },
      {
        "email": "riley.jones@university.edu",
        "status": "ENROLLED",
        "enrollmentId": "<enrollment_id>"
      }
    ],
    "summary": {
      "total": 10,
      "enrolled": 7,
      "alreadyEnrolled": 1,
      "notFound": 1,
      "notAStudent": 1,
      "invalidEmail": 0
    }
  },
  "timestamp": "2026-04-07T12:45:00.000Z"
}
```

**Verify:**

- [ ] Status code is 200 (always 200 for bulk operations)
- [ ] Each email has a result object with status
- [ ] Successful enrollments show `enrollmentId`
- [ ] Mixed statuses handled gracefully
- [ ] Summary shows breakdown
- [ ] Audit log entry: `eventType=COURSE_BULK_ENROLLMENT, userId=<admin_id>, courseId=<course_id_601>, enrolledCount=7, totalProcessed=10`
- [ ] Cache invalidated for affected users

---

### 4.8 Pagination Tests (Boundaries & Limits)

**Test Case:** `List_Courses_Pagination_Boundaries_200`

**User:** humberadmin@reviewflow.com (ADMIN)

**Step 1: Default Pagination (page=0, size=20)**

```http
GET /courses?page=0&size=20&sort=createdAt,desc
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK` with 3 courses (since we only have 3 in test data)

**Verify:**

- [ ] Status code is 200
- [ ] First page returns all 3 courses
- [ ] Response includes pagination metadata

---

**Step 2: Out-of-Bounds Page**

```http
GET /courses?page=99&size=20
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK` with empty array (not 404)

```json
{
  "success": true,
  "data": {
    "content": [],
    "totalElements": 3,
    "totalPages": 1
  },
  "timestamp": "2026-04-07T12:50:00.000Z"
}
```

**Verify:**

- [ ] Status code is 200 (not 404)
- [ ] Empty content array
- [ ] `totalElements` and `totalPages` still accurate

---

**Step 3: Size > 500 (enforced maximum)**

```http
GET /courses?page=0&size=1000
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK` with size capped at 500

**Verify:**

- [ ] Status code is 200
- [ ] Response contains at most 500 items (even if requested 1000)

---

### 4.9 SYSTEM_ADMIN Views All Courses + Can Create Any Course

**Test Case:** `SYSTEM_ADMIN_Full_Access_200`

**User:** main_sysadmin@reviewflow.com (SYSTEM_ADMIN)

**Step 1: Login as SYSTEM_ADMIN**

```http
POST /auth/login
Content-Type: application/json

{
  "email": "main_sysadmin@reviewflow.com",
  "password": "Test@1234"
}
```

**Expected Response:** `200 OK`, role=SYSTEM_ADMIN

---

**Step 2: List All Courses (SYSTEM_ADMIN sees all)**

```http
GET /courses?page=0&size=50
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK` with all courses visible

**Verify:**

- [ ] Status code is 200
- [ ] SYSTEM_ADMIN sees all courses including archived
- [ ] Sees CS-101, CS-201, CS-501, CS-601, PHYS-101

---

**Step 3: SYSTEM_ADMIN Creates Course**

```http
POST /courses
Content-Type: application/json
Cookie: reviewflow_access=<access_token>

{
  "code": "CS-701",
  "name": "Advanced ML",
  "term": "Summer-2026",
  "description": "Created by SYSTEM_ADMIN"
}
```

**Expected Response:** `201 Created`

**Verify:**

- [ ] Status code is 201
- [ ] Course created successfully
- [ ] SYSTEM_ADMIN can create courses like ADMIN

---

## 5. Authorization & 403 Tests (Forbidden)

### 5.1 STUDENT Cannot Create Courses

**Test Case:** `Create_Course_STUDENT_403`

**User:** jane.smith@university.edu (STUDENT)

**Request:**

```http
POST /courses
Content-Type: application/json
Cookie: reviewflow_access=<access_token>

{
  "code": "CHEM-101",
  "name": "Chemistry I",
  "term": "Spring-2026"
}
```

**Expected Response:** `403 Forbidden`

```json
{
  "success": false,
  "error": {
    "code": "FORBIDDEN",
    "message": "Insufficient permissions to create courses. Required role: INSTRUCTOR or higher"
  },
  "timestamp": "2026-04-07T13:00:00.000Z"
}
```

**Verify:**

- [ ] Status code is 403
- [ ] Error code is "FORBIDDEN"
- [ ] Course NOT created
- [ ] Audit log entry: `eventType=COURSE_CREATE_DENIED, userId=<student_id>, reason=INSUFFICIENT_ROLE`

---

### 5.2 STUDENT Cannot View Unenrolled Course

**Test Case:** `Get_Course_STUDENT_Unauthorized_403`

**User:** jane.smith@university.edu (STUDENT) — NOT enrolled in MATH-101

**Request:**

```http
GET /courses/<course_id_math101>
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `403 Forbidden`

```json
{
  "success": false,
  "error": {
    "code": "FORBIDDEN",
    "message": "You do not have access to this course. Enrollment required."
  },
  "timestamp": "2026-04-07T13:05:00.000Z"
}
```

**Verify:**

- [ ] Status code is 403
- [ ] Course details NOT returned
- [ ] Audit log entry: `eventType=COURSE_ACCESS_DENIED, userId=<student_id>, courseId=<math_id>`

---

### 5.3 INSTRUCTOR Cannot Edit Other Instructor's Course

**Test Case:** `Update_Course_INSTRUCTOR_CrossCourse_403`

**User:** sarah.johnson@university.edu (INSTRUCTOR) — not assigned to CS-601

**Request:** Attempt to modify CS-601 (created/managed by someone else)

```http
PUT /courses/<course_id_601>
Content-Type: application/json
Cookie: reviewflow_access=<access_token>

{
  "name": "Renamed by Sarah"
}
```

**Expected Response:** `403 Forbidden`

```json
{
  "success": false,
  "error": {
    "code": "FORBIDDEN",
    "message": "You are not assigned to this course"
  },
  "timestamp": "2026-04-07T13:10:00.000Z"
}
```

**Verify:**

- [ ] Status code is 403
- [ ] INSTRUCTOR cannot modify unassigned courses (service-layer validation)
- [ ] Note: ADMIN can modify any course

---

### 5.4 INSTRUCTOR Cannot Archive Course

**Test Case:** `Archive_Course_INSTRUCTOR_403`

**User:** sarah.johnson@university.edu (INSTRUCTOR) — assigned to CS-501

**Request:**

```http
PATCH /courses/<course_id_501>/archive
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `403 Forbidden`

```json
{
  "success": false,
  "error": {
    "code": "FORBIDDEN",
    "message": "Only administrators can archive courses"
  },
  "timestamp": "2026-04-07T13:15:00.000Z"
}
```

**Verify:**

- [ ] Status code is 403
- [ ] INSTRUCTOR cannot archive even own courses
- [ ] Only ADMIN+ can archive

---

### 5.5 STUDENT Cannot Bulk-Enroll

**Test Case:** `Bulk_Enroll_STUDENT_403`

**User:** jane.smith@university.edu (STUDENT)

**Request:**

```http
POST /courses/<course_id_601>/enroll/bulk
Content-Type: application/json
Cookie: reviewflow_access=<access_token>

{
  "emails": ["test1@university.edu", "test2@university.edu"]
}
```

**Expected Response:** `403 Forbidden`

**Verify:**

- [ ] Status code is 403
- [ ] Only ADMIN+ can perform bulk operations

---

## 6. Error Cases (4xx & 5xx)

### 6.1 Duplicate Course Code (409 Conflict)

**Test Case:** `Create_Course_Duplicate_Code_409`

**User:** humberadmin@reviewflow.com (ADMIN)

**Prerequisite:** CS-101 already exists

**Request:**

```http
POST /courses
Content-Type: application/json
Cookie: reviewflow_access=<access_token>

{
  "code": "CS-101",
  "name": "Different Name",
  "term": "Spring-2026"
}
```

**Expected Response:** `409 Conflict`

```json
{
  "success": false,
  "error": {
    "code": "COURSE_CODE_EXISTS",
    "message": "A course with code CS-101 already exists"
  },
  "timestamp": "2026-04-07T13:20:00.000Z"
}
```

**Verify:**

- [ ] Status code is 409
- [ ] Error code is "COURSE_CODE_EXISTS"
- [ ] Course NOT created
- [ ] Unique constraint enforced

---

### 6.2 Invalid Data — Missing Required Fields (400)

**Test Case:** `Create_Course_Missing_Name_400`

**User:** humberadmin@reviewflow.com (ADMIN)

**Request:**

```http
POST /courses
Content-Type: application/json
Cookie: reviewflow_access=<access_token>

{
  "code": "BIO-101",
  "term": "Spring-2026"
}
```

**Expected Response:** `400 Bad Request`

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Required field 'name' is missing",
    "details": {
      "field": "name",
      "violation": "must not be null"
    }
  },
  "timestamp": "2026-04-07T13:25:00.000Z"
}
```

**Verify:**

- [ ] Status code is 400
- [ ] Error includes problematic field name
- [ ] Course NOT created

---

### 6.3 Invalid Data — Future Start Date

**Test Case:** `Create_Course_Future_Start_Date_400`

**User:** humberadmin@reviewflow.com (ADMIN)

**Request:**

```http
POST /courses
Content-Type: application/json
Cookie: reviewflow_access=<access_token>

{
  "code": "ENG-101",
  "name": "English Literature",
  "term": "Spring-2026",
  "startDate": "2099-01-01"
}
```

**Expected Response:** `400 Bad Request` (if start date validation exists)

**Verify:**

- [ ] Validation error if start date in future
- [ ] Error message explains constraint

---

### 6.4 Course ID Not Found (404)

**Test Case:** `Get_Course_NotFound_404`

**User:** humberadmin@reviewflow.com (ADMIN)

**Request:**

```http
GET /courses/99999
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `404 Not Found`

```json
{
  "success": false,
  "error": {
    "code": "COURSE_NOT_FOUND",
    "message": "Course with ID 99999 not found"
  },
  "timestamp": "2026-04-07T13:30:00.000Z"
}
```

**Verify:**

- [ ] Status code is 404
- [ ] Error code is "COURSE_NOT_FOUND"

---

### 6.5 Cannot Unenroll Student with Submissions (409)

**Test Case:** `Unenroll_Student_With_Submissions_409`

**User:** humberadmin@reviewflow.com (ADMIN)  
**Course:** CS-101  
**Student:** jane.smith@university.edu (has submissions in this course)

**Request:**

```http
DELETE /courses/<course_id_101>/enroll/<jane_smith_id>
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `409 Conflict`

```json
{
  "success": false,
  "error": {
    "code": "CANNOT_UNENROLL_HAS_SUBMISSIONS",
    "message": "Student cannot be unenrolled. Remove all submissions first."
  },
  "timestamp": "2026-04-07T13:35:00.000Z"
}
```

**Verify:**

- [ ] Status code is 409
- [ ] Error message indicates submission cascade risk
- [ ] Student remains enrolled
- [ ] Audit log entry: `eventType=UNENROLL_DENIED, courseId=<>, studentId=<>, reason=HAS_SUBMISSIONS`

---

### 6.6 Bulk Enroll Rate Limit (429)

**Test Case:** `Bulk_Enroll_Too_Many_429`

**User:** humberadmin@reviewflow.com (ADMIN)

**Request:** Attempt to bulk-enroll 1000+ students in one request

```http
POST /courses/<course_id_601>/enroll/bulk
Content-Type: application/json
Cookie: reviewflow_access=<access_token>

{
  "emails": [... 1000+ email addresses ...]
}
```

**Expected Response:** `429 Too Many Requests`

```json
{
  "success": false,
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Bulk enrollment limited to 500 students per request. You requested 1000."
  },
  "timestamp": "2026-04-07T13:40:00.000Z"
}
```

**Verify:**

- [ ] Status code is 429
- [ ] Error indicates limit (e.g., 500 per request)
- [ ] No partial enrollment occurs
- [ ] No enrollments created

---

## 7. Edge Cases & Boundary Conditions

### 7.1 Archive Course — Students Can Still View (Verify Behavior)

**Test Case:** `Archive_Course_Students_Access_Verification`

**User 1:** humberadmin@reviewflow.com (ADMIN)  
**User 2:** jane.smith@university.edu (STUDENT, enrolled in CS-101)

**Step 1: Admin archives CS-101**

```http
PATCH /courses/<course_id_101>/archive
Cookie: reviewflow_access=<admin_token>
```

**Expected:** `200 OK`, `isArchived=true`

---

**Step 2: Student attempts to view archived course**

```http
GET /courses/<course_id_101>
Cookie: reviewflow_access=<student_token>
```

**Expected Response:** `200 OK` (student can still view — verify this behavior)

```json
{
  "success": true,
  "data": {
    "id": "<course_id_101>",
    "code": "CS-101",
    "isArchived": true,
    "enrollmentCount": 45
  },
  "timestamp": "2026-04-07T13:45:00.000Z"
}
```

**Verify:**

- [ ] Status code is 200 (not 403)
- [ ] Student can access archived course if enrolled
- [ ] Document behavior: archived ≠ hidden for enrolled students

---

### 7.2 Unenroll — Verify Cascade Constraints

**Test Case:** `Unenroll_Student_Cascade_Verification`

**User:** humberadmin@reviewflow.com (ADMIN)  
**Course:** CS-601 (no submissions)  
**Student:** marcus.chen@university.edu

**Request:**

```http
DELETE /courses/<course_id_601>/enroll/<marcus_chen_id>
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK`

**Verify:**

- [ ] Status code is 200
- [ ] Student unenrolled
- [ ] No submissions/grades lost (none existed)
- [ ] Audit log: `eventType=COURSE_ENROLLMENT_REMOVED, studentId=<marcus_id>, courseId=<601_id>`

---

### 7.3 Bulk Enroll with Duplicate Emails

**Test Case:** `Bulk_Enroll_Duplicate_Emails`

**User:** humberadmin@reviewflow.com (ADMIN)

**Request:**

```http
POST /courses/<course_id_601>/enroll/bulk
Content-Type: application/json
Cookie: reviewflow_access=<access_token>

{
  "emails": [
    "jane.smith@university.edu",
    "jane.smith@university.edu",
    "marcus.chen@university.edu"
  ]
}
```

**Expected Response:** `200 OK`

**Expected behavior:** Process all emails, handle duplicates gracefully

```json
{
  "success": true,
  "data": {
    "results": [
      { "email": "jane.smith@university.edu", "status": "ENROLLED" },
      { "email": "jane.smith@university.edu", "status": "ALREADY_ENROLLED" },
      { "email": "marcus.chen@university.edu", "status": "ENROLLED" }
    ],
    "summary": {
      "total": 3,
      "enrolled": 2,
      "alreadyEnrolled": 1
    }
  }
}
```

**Verify:**

- [ ] Duplicates handled (second occurrence shows ALREADY_ENROLLED)
- [ ] No error thrown
- [ ] Status 200 returned

---

### 7.4 Bulk Enroll with Invalid Emails

**Test Case:** `Bulk_Enroll_Invalid_Email_Format`

**User:** humberadmin@reviewflow.com (ADMIN)

**Request:**

```http
POST /courses/<course_id_601>/enroll/bulk
Content-Type: application/json
Cookie: reviewflow_access=<access_token>

{
  "emails": [
    "jane.smith@university.edu",
    "invalid-email-no-at",
    "test@",
    "@invalid.com"
  ]
}
```

**Expected Response:** `200 OK`

**Expected behavior:** Invalid emails flagged but not rejected

```json
{
  "success": true,
  "data": {
    "results": [
      { "email": "jane.smith@university.edu", "status": "ENROLLED" },
      { "email": "invalid-email-no-at", "status": "INVALID_EMAIL" },
      { "email": "test@", "status": "INVALID_EMAIL" },
      { "email": "@invalid.com", "status": "INVALID_EMAIL" }
    ]
  }
}
```

**Verify:**

- [ ] Invalid emails marked with status
- [ ] No exception thrown
- [ ] Valid emails processed normally

---

### 7.5 Pagination: page=999 Returns Empty Array

**Test Case:** `List_Courses_Page_OOB_Empty_Array`

**Request:**

```http
GET /courses?page=999&size=20
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "content": [],
    "totalElements": 3,
    "totalPages": 1
  },
  "timestamp": "2026-04-07T13:50:00.000Z"
}
```

**Verify:**

- [ ] Status code is 200 (not 404)
- [ ] Empty array returned (not error)
- [ ] Metadata accurate

---

### 7.6 Pagination: size > 500 Enforced

**Test Case:** `List_Courses_Size_Max_Enforced`

**Request:**

```http
GET /courses?page=0&size=5000
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK` with max 500 items

**Verify:**

- [ ] Actual items returned ≤ 500 (even though 5000 requested)
- [ ] No error thrown

---

### 7.7 Role Change + Cached Course List

**Test Case:** `Role_Change_Cache_Invalidation`

**Scenario:** INSTRUCTOR sarah.johnson@university.edu is promoted to ADMIN

**Step 1: INSTRUCTOR Lists Courses (sees only own courses)**

```http
GET /courses?page=0&size=20
Cookie: reviewflow_access=<instructor_token>
```

**Expected:** 2 courses (only those assigned)

**Cache key:** `CACHE_USER_COURSES:sarah_johnson_id:INSTRUCTOR`

---

**Step 2: Admin promotes sarah to ADMIN (role change)**

(Performed via /admin/users/{id}/role PATCH endpoint)

---

**Step 3: INSTRUCTOR Logs Out → Logs Back In**

Process:

1. POST /auth/logout
2. POST /auth/login (logs in again as new role)

---

**Step 4: New ADMIN Lists Courses (should see all)**

```http
GET /courses?page=0&size=20
Cookie: reviewflow_access=<new_admin_token>
```

**Expected:** All courses visible

**Cache key:** `CACHE_USER_COURSES:sarah_johnson_id:ADMIN` (new cache entry)

**Verify:**

- [ ] Cache not shared across role changes
- [ ] New login generates new token with new role
- [ ] Course list reflects new role permissions immediately

---

## 8. Caching Tests (PRD-08)

### 8.1 GET /courses Caches Result Per User (1 hour TTL)

**Test Case:** `Caching_GET_Courses_1Hour_TTL`

**User:** humberadmin@reviewflow.com (ADMIN)

**Step 1: First Request — Cache Miss**

```http
GET /courses?page=0&size=20
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK`

**Verify:**

- [ ] Status code is 200
- [ ] Response includes X-Cache-Status header (if exposed)
- [ ] Cache key: `CACHE_USER_COURSES:<user_id>:<role>` with value = courses list
- [ ] TTL = 1 hour (3600 seconds)

---

**Step 2: Immediate Second Request — Cache Hit**

```http
GET /courses?page=0&size=20
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK` (same response as Step 1)

**Verify:**

- [ ] Status code is 200
- [ ] Response identical to Step 1 (same data, same order)
- [ ] Response time likely faster (served from cache)
- [ ] No additional database queries logged

---

**Step 3: After Cache Expiry (> 1 hour)**

(Simulated by system clock or cache explicit invalidation)

```http
GET /courses?page=0&size=20
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK` (fresh from database)

**Verify:**

- [ ] Cache expired and new query executed
- [ ] Fresh data retrieved

---

### 8.2 POST /courses Invalidates Cache

**Test Case:** `Caching_POST_Courses_Evicts_Cache`

**User:** humberadmin@reviewflow.com (ADMIN)

**Step 1: Get Courses (cache filled)**

```http
GET /courses?page=0&size=20
Cookie: reviewflow_access=<access_token>
```

**Expected:** `200 OK`, cache filled

**Record response:** Should include CS-501, CS-601, PHYS-101, CS-101, CS-201

---

**Step 2: Create New Course**

```http
POST /courses
Content-Type: application/json
Cookie: reviewflow_access=<access_token>

{
  "code": "CS-801",
  "name": "Quantum Computing",
  "term": "Fall-2026"
}
```

**Expected:** `201 Created`

**Verify:**

- [ ] Cache key `CACHE_USER_COURSES:<user_id>:ADMIN` evicted
- [ ] Audit log: `eventType=CACHE_EVICTED, key=CACHE_USER_COURSES:*:ADMIN, reason=COURSE_CREATED`

---

**Step 3: Get Courses Again (cache miss, fresh query)**

```http
GET /courses?page=0&size=20
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK` with CS-801 now visible

**Verify:**

- [ ] Cache was invalidated
- [ ] New course immediately visible (not delayed)
- [ ] New cache entry created

---

### 8.3 PATCH /courses/{id}/archive Invalidates Cache

**Test Case:** `Caching_PATCH_Archive_Evicts_Cache`

**User:** humberadmin@reviewflow.com (ADMIN)

**Step 1: List Courses (cache filled)**

```http
GET /courses?page=0&size=20
Cookie: reviewflow_access=<access_token>
```

**Record:** All 6 courses visible

---

**Step 2: Archive a Course**

```http
PATCH /courses/<course_id_601>/archive
Cookie: reviewflow_access=<access_token>
```

**Expected:** `200 OK`, course archived

**Verify:**

- [ ] Cache evicted: `CACHE_USER_COURSES:<user_id>:ADMIN`

---

**Step 3: List Courses Again (cache miss)**

```http
GET /courses?page=0&size=20&archived=false
Cookie: reviewflow_access=<access_token>
```

**Expected Response:** `200 OK` (CS-601 now excluded if filtering out archived)

**Verify:**

- [ ] Archived course immediately hidden (if filter applied)
- [ ] Cache updated with new state

---

### 8.4 Cache Key Includes userId + Role

**Test Case:** `Caching_Key_Includes_UserRole`

**User 1:** humberadmin@reviewflow.com (ADMIN)  
**User 2:** jane.smith@university.edu (STUDENT)

**Step 1: Admin Gets All 6 Courses**

```http
GET /courses?page=0&size=20
Cookie: reviewflow_access=<admin_token>
```

**Expected:** 6 courses visible

**Cache key:** `CACHE_USER_COURSES:<admin_id>:ADMIN = [CS-101, CS-201, CS-501, CS-601, PHYS-101, CS-801]`

---

**Step 2: Student Gets Only Enrolled Courses**

```http
GET /courses?page=0&size=20
Cookie: reviewflow_access=<student_token>
```

**Expected:** 2 courses (CS-101, CS-201 — student is enrolled)

**Cache key:** `CACHE_USER_COURSES:<student_id>:STUDENT = [CS-101, CS-201]`

---

**Step 3: Verify Separate Cache Entries**

- Modify database: Add new course CS-901
- Invalidate ONLY admin cache via `/system/cache/evict?key=CACHE_USER_COURSES:<admin_id>:ADMIN`
- Admin sees 7 courses (CS-901 added)
- Student still sees 2 courses (cache not evicted)

**Verify:**

- [ ] Different users have separate cache entries
- [ ] Cache keys include both userId and role
- [ ] Selective invalidation works

---

## 9. Audit Logging Tests

All course operations must log audit events. Verify each log entry has:

| Field                     | Expected                                                                                                                                               | Example                   |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------- |
| `eventType`               | One of: COURSE_CREATED, COURSE_UPDATED, COURSE_ARCHIVED, COURSE_UNARCHIVED, COURSE_ENROLLMENT_ADDED, COURSE_ENROLLMENT_REMOVED, COURSE_BULK_ENROLLMENT | COURSE_CREATED            |
| `userId`                  | ID of user performing action                                                                                                                           | 42                        |
| `courseId`                | ID of course affected                                                                                                                                  | 5                         |
| `metadata.courseCode`     | Course code                                                                                                                                            | CS-501                    |
| `metadata.courseName`     | Course name                                                                                                                                            | Advanced Java Programming |
| `metadata.changesSummary` | For updates, what changed                                                                                                                              | name: "Old" → "New"       |
| `timestamp`               | ISO 8601 UTC                                                                                                                                           | 2026-04-07T12:05:00.000Z  |
| `ipAddress`               | Client IP (if available)                                                                                                                               | 192.168.1.1               |
| `userRole`                | Role of actor                                                                                                                                          | INSTRUCTOR                |

### 9.1 COURSE_CREATED Audit Event

**Test Case:** `Audit_Course_Created_Event`

**Trigger:** POST /courses by INSTRUCTOR

```http
POST /courses
Content-Type: application/json
Cookie: reviewflow_access=<instructor_token>

{
  "code": "CS-501",
  "name": "Advanced Java Programming",
  "term": "Spring-2026"
}
```

**Expected Audit Log Entry:**

```json
{
  "eventType": "COURSE_CREATED",
  "userId": "<sarah_johnson_id>",
  "courseId": "<course_id_501>",
  "userRole": "INSTRUCTOR",
  "metadata": {
    "courseCode": "CS-501",
    "courseName": "Advanced Java Programming",
    "term": "Spring-2026"
  },
  "timestamp": "2026-04-07T12:05:00.000Z"
}
```

---

### 9.2 COURSE_UPDATED Audit Event

**Test Case:** `Audit_Course_Updated_Event`

**Trigger:** PUT /courses/{id} by ADMIN

**Verify audit log includes:**

- [ ] `eventType = "COURSE_UPDATED"`
- [ ] Changed fields in `metadata.changesSummary` (e.g., `"name": "Old" → "New"`)

---

### 9.3 COURSE_ARCHIVED Audit Event

**Test Case:** `Audit_Course_Archived_Event`

**Trigger:** PATCH /courses/{id}/archive

**Verify audit log includes:**

- [ ] `eventType = "COURSE_ARCHIVED"`
- [ ] `metadata.isArchived = true`

---

### 9.4 COURSE_ENROLLMENT_ADDED Audit Event

**Test Case:** `Audit_Enrollment_Added_Event`

**Trigger:** POST /courses/{id}/enroll by ADMIN

**Verify audit log includes:**

- [ ] `eventType = "COURSE_ENROLLMENT_ADDED"`
- [ ] `metadata.studentId = <enrolled_student_id>`
- [ ] `metadata.studentEmail = <student_email>`

---

### 9.5 COURSE_BULK_ENROLLMENT Audit Event

**Test Case:** `Audit_Bulk_Enrollment_Event`

**Trigger:** POST /courses/{id}/enroll/bulk

**Verify audit log includes:**

- [ ] `eventType = "COURSE_BULK_ENROLLMENT"`
- [ ] `metadata.totalRequested = 10`
- [ ] `metadata.successCount = 7`
- [ ] `metadata.alreadyEnrolledCount = 1`
- [ ] `metadata.notFoundCount = 1`
- [ ] `metadata.notAStudentCount = 1`

---

## 10. Postman Workflows

### Workflow A: INSTRUCTOR Creates Course → ADMIN Lists → STUDENT Enrolls → View Enrollments

**Test Folder:** Workflows > WorkflowA_Full_Course_Lifecycle

**Request 1: INSTRUCTOR Login**

```
POST {{baseUrl}}/auth/login
{
  "email": "sarah.johnson@university.edu",
  "password": "Test@1234"
}
```

**Store:** `instructor_token`, `instructor_id`

---

**Request 2: INSTRUCTOR Creates Course CS-501**

```
POST {{baseUrl}}/courses
{
  "code": "CS-501",
  "name": "Advanced Java",
  "term": "Spring-2026"
}
```

**Expected:** `201 Created`

**Store:** `course_id_501`

---

**Request 3: ADMIN Login**

```
POST {{baseUrl}}/auth/login
{
  "email": "humberadmin@reviewflow.com",
  "password": "Test@1234"
}
```

**Store:** `admin_token`

---

**Request 4: ADMIN Lists All Courses**

```
GET {{baseUrl}}/courses?page=0&size=50
```

**Expected:** `200 OK`, includes CS-501

**Test:**

```javascript
pm.test("Course CS-501 visible to ADMIN", function () {
  var json = pm.response.json();
  var cs501 = json.data.content.find((c) => c.code === "CS-501");
  pm.expect(cs501).to.exist;
});
```

---

**Request 5: ADMIN Enrolls STUDENT jane.smith**

```
POST {{baseUrl}}/courses/{{course_id_501}}/enroll
{
  "studentId": "<jane_smith_id>"
}
```

**Expected:** `200 OK`

---

**Request 6: STUDENT Login**

```
POST {{baseUrl}}/auth/login
{
  "email": "jane.smith@university.edu",
  "password": "Test@1234"
}
```

**Store:** `student_token`

---

**Request 7: STUDENT Views Course CS-501**

```
GET {{baseUrl}}/courses/{{course_id_501}}
```

**Expected:** `200 OK`, full course details

**Test:**

```javascript
pm.test("STUDENT can view enrolled course", function () {
  pm.response.to.have.status(200);
  pm.expect(pm.response.json().data.code).to.equal("CS-501");
});
```

---

### Workflow B: ADMIN Bulk-Enrolls Students → Verify Cache Evicted → Check Enrollment Count

**Test Folder:** Workflows > WorkflowB_BulkEnrollAndCache

**Request 1: ADMIN Initial Course List**

```
GET {{baseUrl}}/courses?page=0&size=50
```

**Store response:** `initialCourses`

---

**Request 2: ADMIN Bulk-Enrolls 10 Students in CS-601**

```
POST {{baseUrl}}/courses/{{course_id_601}}/enroll/bulk
{
  "emails": [
    "jane.smith@university.edu",
    "marcus.chen@university.edu",
    "alex.patel@university.edu",
    ...
  ]
}
```

**Expected:** `200 OK` with results array

**Test:**

```javascript
pm.test("Bulk enrollment returns summary", function () {
  var json = pm.response.json();
  pm.expect(json.data.summary.enrolled).to.be.greaterThan(0);
});
```

---

**Request 3: ADMIN Gets Updated Course List**

```
GET {{baseUrl}}/courses?page=0&size=50
```

**Expected:** CS-601 now shows `enrollmentCount: 10` (or similar)

**Test:**

```javascript
pm.test("Course enrollment count updated after bulk add", function () {
  var json = pm.response.json();
  var cs601 = json.data.content.find((c) => c.code === "CS-601");
  pm.expect(cs601.enrollmentCount).to.equal(10);
});
```

---

### Workflow C: Archive Course → Unenroll Student → Reactivate Course

**Test Folder:** Workflows > WorkflowC_ArchiveUnenroll

**Request 1: ADMIN Archives CS-601**

```
PATCH {{baseUrl}}/courses/{{course_id_601}}/archive
```

**Expected:** `200 OK`, `isArchived: true`

---

**Request 2: ADMIN Unenrols Student marcus.chen from CS-601**

```
DELETE {{baseUrl}}/courses/{{course_id_601}}/enroll/{{marcus_chen_id}}
```

**Expected:** `200 OK`

---

**Request 3: ADMIN Unarchives CS-601**

```
PATCH {{baseUrl}}/courses/{{course_id_601}}/archive
```

**Expected:** `200 OK`, `isArchived: false`

---

**Request 4: Verify Course Active Again**

```
GET {{baseUrl}}/courses/{{course_id_601}}
```

**Expected:** `200 OK`, `isArchived: false`

---

## 11. Test Coverage Checklist

Use this as a verification matrix for manual testing or automation:

- [ ] **INSTRUCTOR create course (Fix 2)** — CS-501 creation by sarah.johnson@university.edu
- [ ] **ADMIN + SYSTEM_ADMIN see all courses** — Both roles list all 6+ courses
- [ ] **STUDENT sees only enrolled** — jane.smith sees only CS-101, CS-201
- [ ] **Duplicate code rejected (409)** — Second CS-101 creation fails
- [ ] **INSTRUCTOR cannot archive** — sarah.johnson@university.edu PATCH /archive returns 403
- [ ] **Deactivated instructor can view** — Disabled INSTRUCTOR account still has read access (test deactivation paths)
- [ ] **Bulk enroll 50+ students** — POST /enroll/bulk with 50+ emails returns 200 with results
- [ ] **Pagination boundaries** — page=999 returns [], size=1000 capped at 500
- [ ] **Cache eviction on data changes** — POST /courses evicts cache, new course immediately visible
- [ ] **Audit log for all write ops** — COURSE_CREATED, UPDATED, ARCHIVED, ENROLLMENT_ADDED, BULK_ENROLLMENT
- [ ] **Enrollment cascade constraints** — Cannot unenroll if submissions exist (409)
- [ ] **Role change cache invalidation** — Promoted INSTRUCTOR→ADMIN sees new courses on re-login
- [ ] **Archived course access** — Enrolled students still see archived courses
- [ ] **Invalid email handling** — Bulk enroll with bad emails marks INVALID_EMAIL, continues
- [ ] **SYSTEM_ADMIN full access** — main_sysadmin can create, list, manage all courses

---

## 12. Performance Targets (Optional Monitoring)

| Operation                | Target  | Benchmark                           |
| ------------------------ | ------- | ----------------------------------- |
| GET /courses (cached)    | < 50ms  | Hit L1 cache                        |
| GET /courses (miss)      | < 500ms | Database query + serialization      |
| POST /courses            | < 300ms | Insert + audit log + cache evict    |
| Bulk enroll 100 students | < 3s    | Batch insert + per-email validation |
| PATCH /archive toggle    | < 200ms | Update + cache evict                |

---

## 13. Integration Points with Other Modules

| Module                       | Integration                                       | Test Case                                  |
| ---------------------------- | ------------------------------------------------- | ------------------------------------------ |
| **Auth (Module 1)**          | Login required for all endpoints                  | All tests include auth headers             |
| **Assignments (Module 3)**   | Course enrollments block/affect assignment access | Course unenroll triggers assignment checks |
| **Teams (Module 4)**         | Course teams per course                           | Set course context for team tests          |
| **Submissions (Module 5)**   | Cannot unenroll if submissions exist              | 6.5: Unenroll cascade constraint           |
| **Notifications (Module 7)** | Course-wide announcements                         | Archived courses still notify? (verify)    |
| **S3 Storage (Module 9)**    | Course file storage                               | Files linked to course — cascade delete?   |
| **Admin (Module 8)**         | User role changes affect course visibility        | 7.7: Role change cache test                |
| **System Admin (Module 12)** | Cache management + metrics                        | 8.1-8.4: Caching tests + Monitoring        |

---

## Notes & Decisions

1. **Fix 2 Implementation:** Course creation remains restricted to ADMIN/SYSTEM_ADMIN via @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')") on POST /courses. Instructor operations remain course-scoped in teaching flows.

2. **Caching Strategy:** Per-user, per-role cache with 1-hour TTL. Key format: `CACHE_USER_COURSES:userId:role`. Invalidated on any course data change.

3. **Bulk Operations:** Always return 200 (never 400 for partial failures). Each email gets individual result status. Summary provided in response.

4. **Archive Behavior:** Archived courses remain visible to enrolled students (not hidden). Archived ≠ deleted.

5. **Cascade Delete:** Students cannot be unenrolled if they have submissions in the course (409 error enforces data integrity).

6. **Audit Trail:** All write operations logged with eventType, userId, courseId, metadata. Stored in `audit_log` table.

---

**End of TEST_SPEC_03_Course.md**
