# TEST_SPEC_04_Assignment.md

## Assignment Module Test Specification

**Module**: Assignment Management  
**Controllers**: AssignmentController  
**Endpoints**: 12  
**Last Updated**: Post-Architecture-Fix Phase 2  
**Test Coverage**: 60+ test cases

---

## 1. Endpoint Summary

| #   | Method | Endpoint                                        | Description                 | Role                            |
| --- | ------ | ----------------------------------------------- | --------------------------- | ------------------------------- |
| 1   | GET    | `/api/v1/courses/{courseId}/assignments`        | List assignments for course | ALL                             |
| 2   | POST   | `/api/v1/courses/{courseId}/assignments`        | Create assignment           | INSTRUCTOR, ADMIN, SYSTEM_ADMIN |
| 3   | GET    | `/api/v1/assignments/{id}`                      | Get assignment details      | ALL (with course access)        |
| 4   | PUT    | `/api/v1/assignments/{id}`                      | Update assignment           | INSTRUCTOR, ADMIN, SYSTEM_ADMIN |
| 5   | PATCH  | `/api/v1/assignments/{id}/publish`              | Publish assignment          | INSTRUCTOR, ADMIN, SYSTEM_ADMIN |
| 6   | GET    | `/api/v1/assignments`                           | Get my assignments          | ALL                             |
| 7   | DELETE | `/api/v1/assignments/{id}`                      | Delete assignment           | INSTRUCTOR, ADMIN, SYSTEM_ADMIN |
| 8   | POST   | `/api/v1/assignments/{id}/rubric`               | Add rubric criterion        | INSTRUCTOR, ADMIN, SYSTEM_ADMIN |
| 9   | PUT    | `/api/v1/assignments/{id}/rubric/{criterionId}` | Update rubric criterion     | INSTRUCTOR, ADMIN, SYSTEM_ADMIN |
| 10  | DELETE | `/api/v1/assignments/{id}/rubric/{criterionId}` | Delete rubric criterion     | INSTRUCTOR, ADMIN, SYSTEM_ADMIN |
| 11  | GET    | `/api/v1/assignments/{id}/submissions`          | List assignment submissions | INSTRUCTOR, ADMIN, SYSTEM_ADMIN |
| 12  | GET    | `/api/v1/assignments/{id}/gradebook`            | Get gradebook/scoresheet    | INSTRUCTOR, ADMIN, SYSTEM_ADMIN |

---

## 2. Permission Matrix

### Role-Based Access Control

**SYSTEM_ADMIN**: All endpoints (5 audit events tracked)  
**ADMIN**: All endpoints (5 audit events tracked)  
**INSTRUCTOR**: Create, update, publish assignments in own courses; view own assignments  
**STUDENT**: List assignments for enrolled courses; view published assignments only

### Key Rules

- Students see only **published** assignments in courses they're enrolled in
- Instructors manage assignments in their courses
- Unpublished assignments hidden from students
- Published assignments are locked from edits

---

## 3. Authentication & Authorization Tests

### 3.1 Token-Based Access Control

```json
{
  "testName": "Assignment List - Unauthorized",
  "method": "GET",
  "endpoint": "/api/v1/courses/abc123/assignments",
  "headers": {},
  "expectedStatus": 401,
  "expectedError": "Unauthorized - authentication required"
}
```

**Test Cases**:

1. ✅ Valid token - 200 OK
2. ✅ Expired token - 401 Unauthorized
3. ✅ Missing Authorization header - 401 Unauthorized
4. ✅ Invalid token format - 401 Unauthorized
5. ✅ Token from different institution - 403 Forbidden

---

## 4. Endpoint Test Cases

### 4.1 List Assignments for Course

**Endpoint**: `GET /api/v1/courses/{courseId}/assignments`

**Test Cases**:

1. ✅ Instructor lists assignments in own course (200 OK)
2. ✅ Student lists published assignments in enrolled course (200 OK, only published)
3. ✅ Student lists unpublished - filtered out (200 OK, empty or only published)
4. ✅ User lists assignments in course not enrolled (403 Forbidden)
5. ✅ Non-existent course (404 Not Found)
6. ✅ Pagination - page=1, size=10 (200 OK)
7. ✅ Pagination - page=999, size=10 (200 OK, empty)
8. ✅ Pagination - size>500 (400 Bad Request)
9. ✅ Invalid courseId hash (400 Bad Request)
10. ✅ ADMIN lists assignments from any course (200 OK)
11. ✅ SYSTEM_ADMIN lists assignments from any course (200 OK)
12. ✅ Audit event: ASSIGNMENT_LIST_VIEWED -> Logged for ADMIN, SYSTEM_ADMIN

**Postman Example**:

```json
{
  "name": "List Course Assignments",
  "request": {
    "method": "GET",
    "url": "{{base_url}}/api/v1/courses/{{courseId}}/assignments?page=1&size=10",
    "header": [{ "key": "Authorization", "value": "Bearer {{student_token}}" }]
  },
  "tests": ["pm.response.code === 200", "pm.response.json().data.length <= 10"]
}
```

### 4.2 Create Assignment

**Endpoint**: `POST /api/v1/courses/{courseId}/assignments`

**Request Body**:

```json
{
  "title": "Midterm Project",
  "description": "Build a full-stack app",
  "dueAt": "2024-04-15T23:59:59Z",
  "maxTeamSize": 3,
  "submissionType": "FILE",
  "teamLockAt": "2024-04-14T23:59:59Z",
  "isPublished": false
}
```

**Test Cases**:

1. ✅ Instructor creates assignment in own course (201 Created)
2. ✅ Student cannot create assignment (403 Forbidden)
3. ✅ ADMIN creates assignment in any course (201 Created)
4. ✅ SYSTEM_ADMIN creates assignment (201 Created)
5. ✅ Invalid title (empty) (400 Bad Request)
6. ✅ Due date in past (400 Bad Request)
7. ✅ Max team size < 1 (400 Bad Request)
8. ✅ Submission type invalid (400 Bad Request)
9. ✅ Non-existent course (404 Not Found)
10. ✅ Audit event: ASSIGNMENT_CREATED -> userId, courseId, title logged
11. ✅ Response includes assignment_id (hashid encoded)
12. ✅ isPublished defaults to false

**Postman Example**:

```json
{
  "name": "Create Assignment",
  "request": {
    "method": "POST",
    "url": "{{base_url}}/api/v1/courses/{{courseId}}/assignments",
    "header": [
      { "key": "Authorization", "value": "Bearer {{instructor_token}}" },
      { "key": "Content-Type", "value": "application/json" }
    ],
    "body": {
      "mode": "raw",
      "raw": "{\"title\": \"Midterm\", \"dueAt\": \"2024-04-15T23:59:59Z\", \"maxTeamSize\": 3, \"submissionType\": \"FILE\"}"
    }
  },
  "tests": [
    "pm.response.code === 201",
    "pm.response.json().data.assignment_id !== undefined"
  ]
}
```

### 4.3 Get Assignment Details

**Endpoint**: `GET /api/v1/assignments/{id}`

**Test Cases**:

1. ✅ Instructor gets own assignment (200 OK, full details)
2. ✅ Student gets published assignment from enrolled course (200 OK)
3. ✅ Student gets unpublished assignment from enrolled course (403 Forbidden)
4. ✅ Student gets assignment from non-enrolled course (403 Forbidden)
5. ✅ Non-existent assignment (404 Not Found)
6. ✅ Invalid assignment hash (400 Bad Request)
7. ✅ Response includes rubric criteria array
8. ✅ ADMIN gets any assignment (200 OK)
9. ✅ SYSTEM_ADMIN gets any assignment (200 OK)
10. ✅ Audit event: ASSIGNMENT_VIEWED -> Logged for ADMIN, SYSTEM_ADMIN

### 4.4 Update Assignment

**Endpoint**: `PUT /api/v1/assignments/{id}`

**Request Body**: Same as create

**Test Cases**:

1. ✅ Instructor updates unpublished assignment (200 OK)
2. ✅ Instructor cannot update published assignment (403 Forbidden)
3. ✅ Student cannot update assignment (403 Forbidden)
4. ✅ Invalid assignment ID (404 Not Found)
5. ✅ New title with special characters (200 OK)
6. ✅ Audit event: ASSIGNMENT_UPDATED -> title, old_values logged
7. ✅ Cannot extend past deadline (business logic)
8. ✅ ADMIN can update published assignment (200 OK - override)
9. ✅ SYSTEM_ADMIN can update published assignment (200 OK - override)

### 4.5 Publish Assignment

**Endpoint**: `PATCH /api/v1/assignments/{id}/publish`

**Test Cases**:

1. ✅ Instructor publishes unpublished assignment (200 OK)
2. ✅ Instructor publishes already published (403 Forbidden - idempotent)
3. ✅ Student cannot publish (403 Forbidden)
4. ✅ Assignment locked after publish (subsequent PUT fails)
5. ✅ Students receive notification on publish (async)
6. ✅ Audit event: ASSIGNMENT_PUBLISHED -> userId, assignmentId logged
7. ✅ isPublished=true in response
8. ✅ ADMIN can publish (200 OK)
9. ✅ SYSTEM_ADMIN can publish (200 OK)

### 4.6 Get My Assignments

**Endpoint**: `GET /api/v1/assignments?status=UPCOMING`

**Test Cases**:

1. ✅ Student gets only published assignments (200 OK)
2. ✅ Instructor gets all assignments (200 OK, published + unpublished)
3. ✅ Filter status=UPCOMING (200 OK, only future due dates)
4. ✅ Filter status=PAST_DUE (200 OK, only past)
5. ✅ Filter status=ALL (200 OK, all)
6. ✅ Pagination: page, size parameters work
7. ✅ Empty result set (200 OK, empty array)
8. ✅ Multiple courses included (200 OK)
9. ✅ ADMIN sees all assignments (200 OK)
10. ✅ SYSTEM_ADMIN sees all assignments (200 OK)

### 4.7 Delete Assignment

**Endpoint**: `DELETE /api/v1/assignments/{id}`

**Test Cases**:

1. ✅ Instructor deletes unpublished assignment (204 No Content)
2. ✅ Instructor cannot delete published assignment (403 Forbidden)
3. ✅ Student cannot delete (403 Forbidden)
4. ✅ Non-existent assignment (404 Not Found)
5. ✅ Cascade deletes: rubric criteria, submissions, evaluations
6. ✅ Audit event: ASSIGNMENT_DELETED -> assignmentId, title logged
7. ✅ ADMIN can delete published assignment (204 No Content)
8. ✅ SYSTEM_ADMIN can delete published assignment (204 No Content)

### 4.8 Add Rubric Criterion

**Endpoint**: `POST /api/v1/assignments/{id}/rubric`

**Request Body**:

```json
{
  "name": "Code Quality",
  "description": "How well-structured is the code?",
  "maxPoints": 25,
  "weight": 1.0
}
```

**Test Cases**:

1. ✅ Instructor adds criterion to unpublished assignment (201 Created)
2. ✅ Instructor cannot add criterion to published assignment (403 Forbidden)
3. ✅ Max points must be positive (400 Bad Request if <= 0)
4. ✅ Weight must be positive (400 Bad Request if <= 0)
5. ✅ Duplicate criterion names allowed (business decision)
6. ✅ Very long description (<=1000 chars) (201 Created)
7. ✅ Audit event: RUBRIC_CRITERION_ADDED -> assignmentId, criterionName logged
8. ✅ ADMIN can add to any assignment (201 Created)
9. ✅ SYSTEM_ADMIN can add to any assignment (201 Created)

### 4.9 Update Rubric Criterion

**Endpoint**: `PUT /api/v1/assignments/{id}/rubric/{criterionId}`

**Test Cases**:

1. ✅ Update criterion name/description (200 OK)
2. ✅ Cannot update once evaluations started (403 Forbidden - business logic)
3. ✅ Invalid criterion ID (404 Not Found)
4. ✅ Non-existent assignment (404 Not Found)
5. ✅ Audit event: RUBRIC_CRITERION_UPDATED -> changes logged

### 4.10 Delete Rubric Criterion

**Endpoint**: `DELETE /api/v1/assignments/{id}/rubric/{criterionId}`

**Test Cases**:

1. ✅ Delete criterion from unpublished assignment (204 No Content)
2. ✅ Cannot delete if evaluations exist (403 Forbidden)
3. ✅ Invalid criterion ID (404 Not Found)
4. ✅ Audit event: RUBRIC_CRITERION_DELETED -> criterionId, name logged

### 4.11 List Assignment Submissions

**Endpoint**: `GET /api/v1/assignments/{id}/submissions`

**Test Cases**:

1. ✅ Instructor lists submissions (200 OK)
2. ✅ Student cannot list (403 Forbidden)
3. ✅ Pagination works (page, size)
4. ✅ Filter by status: DRAFT, SUBMITTED, EVALUATED (future)
5. ✅ Non-existent assignment (404 Not Found)
6. ✅ ADMIN lists submissions (200 OK)
7. ✅ SYSTEM_ADMIN lists submissions (200 OK)

### 4.12 Get Gradebook/Scoresheet

**Endpoint**: `GET /api/v1/assignments/{id}/gradebook`

**Test Cases**:

1. ✅ Instructor gets gradebook (200 OK)
2. ✅ Student cannot access (403 Forbidden)
3. ✅ Returns entries with scores, evaluations
4. ✅ Calculates final grade from rubric scores
5. ✅ Export format (CSV, JSON support)
6. ✅ Non-existent assignment (404 Not Found)
7. ✅ ADMIN gets gradebook (200 OK)
8. ✅ SYSTEM_ADMIN gets gradebook (200 OK)

---

## 5. Audit Events

| Event                    | Description                      | Triggered By      | Logged Data                              |
| ------------------------ | -------------------------------- | ----------------- | ---------------------------------------- |
| ASSIGNMENT_CREATED       | Assignment created               | POST endpoint     | userId, courseId, title                  |
| ASSIGNMENT_UPDATED       | Assignment details updated       | PUT endpoint      | assignmentId, title, changes             |
| ASSIGNMENT_PUBLISHED     | Assignment published to students | PATCH publish     | assignmentId, userId                     |
| ASSIGNMENT_DELETED       | Assignment deleted (cascade)     | DELETE endpoint   | assignmentId, title, courseId            |
| RUBRIC_CRITERION_ADDED   | Rubric criterion added           | POST rubric       | criterionId, name, maxPoints             |
| RUBRIC_CRITERION_UPDATED | Rubric criterion updated         | PUT rubric        | criterionId, changes                     |
| RUBRIC_CRITERION_DELETED | Rubric criterion removed         | DELETE rubric     | criterionId, assignmentId                |
| ASSIGNMENT_VIEWED        | Assignment details accessed      | GET endpoint      | userId, assignmentId (ADMIN/SYSTEM only) |
| ASSIGNMENT_LIST_VIEWED   | Course assignments listed        | GET list endpoint | userId, courseId (ADMIN/SYSTEM only)     |

---

## 6. Real Test User Credentials

**Base URL**: `http://localhost:8080`  
**Default Password**: `Test@1234` (all users bcrypt hashed)

### Test Users by Role

| User         | Email                         | Role         | Purpose                              |
| ------------ | ----------------------------- | ------------ | ------------------------------------ |
| System Admin | main_sysadmin@reviewflow.com  | SYSTEM_ADMIN | Override all permissions             |
| Admin User   | humberadmin@reviewflow.com    | ADMIN        | Administrative assignment management |
| Instructor 1 | sarah.johnson@university.edu  | INSTRUCTOR   | Create/publish assignments           |
| Instructor 2 | michael.torres@university.edu | INSTRUCTOR   | Alternative instructor               |
| Student 1    | jane.smith@university.edu     | STUDENT      | View published assignments           |
| Student 2    | marcus.chen@university.edu    | STUDENT      | Alternative student                  |

---

## 7. End-to-End Postman Workflows

### Workflow 1: Assignment Lifecycle (Instructor)

```json
{
  "workflowName": "Assignment Lifecycle",
  "steps": [
    {
      "step": 1,
      "description": "Instructor logs in",
      "request": {
        "method": "POST",
        "url": "{{base_url}}/api/v1/auth/login",
        "body": {
          "email": "sarah.johnson@university.edu",
          "password": "Test@1234"
        }
      },
      "extract": "token"
    },
    {
      "step": 2,
      "description": "Instructor creates assignment",
      "request": {
        "method": "POST",
        "url": "{{base_url}}/api/v1/courses/{{courseId}}/assignments",
        "body": {
          "title": "Midterm Project",
          "dueAt": "2024-05-01T23:59:59Z",
          "maxTeamSize": 3,
          "submissionType": "FILE"
        },
        "headers": { "Authorization": "Bearer {{token}}" }
      },
      "extract": "assignmentId"
    },
    {
      "step": 3,
      "description": "Instructor adds rubric criterion",
      "request": {
        "method": "POST",
        "url": "{{base_url}}/api/v1/assignments/{{assignmentId}}/rubric",
        "body": { "name": "Code Quality", "maxPoints": 25 },
        "headers": { "Authorization": "Bearer {{token}}" }
      }
    },
    {
      "step": 4,
      "description": "Instructor publishes assignment",
      "request": {
        "method": "PATCH",
        "url": "{{base_url}}/api/v1/assignments/{{assignmentId}}/publish",
        "headers": { "Authorization": "Bearer {{token}}" }
      }
    },
    {
      "step": 5,
      "description": "Verify students see published assignment",
      "request": {
        "method": "GET",
        "url": "{{base_url}}/api/v1/assignments/{{assignmentId}}",
        "headers": { "Authorization": "Bearer {{student_token}}" }
      },
      "tests": [
        "pm.response.code === 200",
        "pm.response.json().data.isPublished === true"
      ]
    }
  ]
}
```

### Workflow 2: Multi-Rubric Assignment

```json
{
  "workflowName": "Multi-Rubric Assignment",
  "steps": [
    {
      "step": 1,
      "description": "Create assignment with multiple rubric criteria",
      "criteria": [
        "Code Quality",
        "Design Pattern Usage",
        "Testing Coverage",
        "Documentation"
      ]
    }
  ]
}
```

### Workflow 3: Assignment Access Control

```json
{
  "workflowName": "Access Control Tests",
  "steps": [
    {
      "step": 1,
      "description": "Student cannot update published assignment",
      "expectedStatus": 403
    },
    {
      "step": 2,
      "description": "Admin can override and update",
      "expectedStatus": 200
    }
  ]
}
```

---

## 8. Error Handling

| Scenario                       | Status | Error Message                        | Resolution                                            |
| ------------------------------ | ------ | ------------------------------------ | ----------------------------------------------------- |
| Invalid course ID              | 404    | Course not found                     | Verify courseId exists and is properly hashid-encoded |
| Published assignment update    | 403    | Cannot modify published assignment   | Reopen (ADMIN only) or create new assignment          |
| Insufficient permissions       | 403    | Forbidden - INSTRUCTOR role required | Login as INSTRUCTOR, ADMIN, or SYSTEM_ADMIN           |
| Missing rubric criterion       | 404    | Criterion not found                  | Verify criterionId exists                             |
| Duplicate team name            | 400    | Team name already exists             | Use unique team names                                 |
| Non-existent student in course | 400    | Student not enrolled                 | Only assign students enrolled in course               |

---

## 9. Performance & Caching

| Operation              | Cache TTL | Invalidation Trigger          |
| ---------------------- | --------- | ----------------------------- |
| List assignments       | 5 minutes | POST/PUT/DELETE on assignment |
| Get assignment details | 5 minutes | Any update to assignment      |
| Rubric criteria        | 5 minutes | POST/PUT/DELETE criteria      |
| Submissions list       | 1 minute  | New submission created        |
| Gradebook              | 1 minute  | New evaluation created        |

---

## 10. Security Considerations

1. **Hashid Encoding**: All IDs (assignment, course, user) are hashid-encoded in responses
2. **Role-Based Filtering**: Students only see published assignments
3. **Course Isolation**: Cross-course access blocked at service layer
4. **Audit Logging**: All modifications logged with user context
5. **Rate Limiting**: Assignment creation/update throttled per course
6. **CSRF Protection**: POST/PUT/DELETE require CSRF token in production

---

## 11. Known Issues & Limitations

- Published assignment unlock requires ADMIN override (no self-service reopening for INSTRUCTOR)
- Bulk assignment operations not supported (create multiple at once)
- Rubric criterion ordering not preserved across updates
- No version history for assignment drafts

---

## 12. Future Enhancements

- [ ] Assignment templates/cloning
- [ ] Bulk rubric import (CSV)
- [ ] Anonymous grading mode
- [ ] Assignment retry/resubmission limits
- [ ] Weighted grading with curving
