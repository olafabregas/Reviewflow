# TEST_SPEC_07_Evaluation.md

## Evaluation Module Test Specification

**Module**: Evaluation & Grading  
**Controllers**: EvaluationController  
**Endpoints**: 10  
**Last Updated**: Post-Architecture-Fix Phase 2  
**Test Coverage**: 55+ test cases

---

## 1. Endpoint Summary

| #   | Method | Endpoint                                 | Description                   | Role                                           |
| --- | ------ | ---------------------------------------- | ----------------------------- | ---------------------------------------------- |
| 1   | POST   | `/evaluation/submissions/{submissionId}` | Create evaluation             | INSTRUCTOR, ADMIN, SYSTEM_ADMIN                |
| 2   | GET    | `/evaluation/{id}`                       | Get evaluation details        | INSTRUCTOR, ADMIN, SYSTEM_ADMIN, Student (own) |
| 3   | PUT    | `/evaluation/{id}/scores`                | Update all rubric scores      | INSTRUCTOR, ADMIN, SYSTEM_ADMIN                |
| 4   | PATCH  | `/evaluation/{id}/scores/{criterionId}`  | Update single criterion score | INSTRUCTOR, ADMIN, SYSTEM_ADMIN                |
| 5   | PATCH  | `/evaluation/{id}/comment`               | Add/update overall feedback   | INSTRUCTOR, ADMIN, SYSTEM_ADMIN                |
| 6   | PATCH  | `/evaluation/{id}/publish`               | Publish grades to student     | INSTRUCTOR, ADMIN, SYSTEM_ADMIN                |
| 7   | PATCH  | `/evaluation/{id}/reopen`                | Reopen evaluation for editing | ADMIN, SYSTEM_ADMIN only                       |
| 8   | POST   | `/evaluation/{id}/pdf`                   | Generate PDF report           | INSTRUCTOR, ADMIN, SYSTEM_ADMIN                |
| 9   | GET    | `/evaluation/{id}/pdf`                   | Download PDF report           | INSTRUCTOR, ADMIN, SYSTEM_ADMIN, Student       |
| 10  | GET    | `/evaluation/{id}/pdf/preview`           | Preview PDF report            | INSTRUCTOR, ADMIN, SYSTEM_ADMIN, Student       |

---

## 2. Permission Matrix

**SYSTEM_ADMIN**: All endpoints, force reopen published evaluations  
**ADMIN**: All endpoints, force reopen published evaluations  
**INSTRUCTOR**: Create, edit, publish (course access required)  
**STUDENT**: View own published evaluations, download PDF

---

## 3. Evaluation Lifecycle

```
DRAFT -> IN_PROGRESS -> PUBLISHED -> RETURNED (if reopened)
```

| State       | Can Edit | Can Add Comments | Can Publish | Can Download       |
| ----------- | -------- | ---------------- | ----------- | ------------------ |
| DRAFT       | Yes      | Yes              | No          | INSTRUCTOR only    |
| IN_PROGRESS | Yes      | Yes              | Yes         | INSTRUCTOR only    |
| PUBLISHED   | No       | No               | No          | Owner + INSTRUCTOR |
| RETURNED    | Yes      | Yes              | Yes         | INSTRUCTOR only    |

---

## 4. Endpoint Test Cases

### 4.1 Create Evaluation

**Endpoint**: `POST /evaluation/submissions/{submissionId}`

**Test Cases**:

1. ✅ Instructor creates evaluation (201 Created)
2. ✅ Student cannot create (403 Forbidden)
3. ✅ Duplicate evaluation (409 Conflict - "Evaluation already exists")
4. ✅ Non-existent submission (404 Not Found)
5. ✅ Unpublished access prevents creation (400 Bad Request)
6. ✅ Response includes evaluation_id, status (DRAFT)
7. ✅ Response includes all rubric criteria (0 points initially)
8. ✅ Audit event: EVALUATION_CREATED -> submissionId, assignmentId
9. ✅ Defaults to DRAFT state
10. ✅ ADMIN creates evaluation (201 Created)
11. ✅ SYSTEM_ADMIN creates evaluation (201 Created)

### 4.2 Get Evaluation Details

**Endpoint**: `GET /evaluation/{id}`

**Test Cases**:

1. ✅ Instructor views draft evaluation (200 OK)
2. ✅ Student views own published evaluation (200 OK)
3. ✅ Student views unpublished evaluation (403 Forbidden)
4. ✅ Student views other's evaluation (403 Forbidden)
5. ✅ Response includes all scores, comments, PDF status
6. ✅ Response includes rubric breakdown (points per criterion)
7. ✅ Response includes final grade calculation
8. ✅ Response includes publish timestamp (if published)
9. ✅ Non-existent evaluation (404 Not Found)
10. ✅ Invalid evaluation hash (400 Bad Request)
11. ✅ Audit event: EVALUATION_VIEWED -> Logged (ADMIN/SYSTEM_ADMIN)
12. ✅ ADMIN views any evaluation (200 OK)
13. ✅ SYSTEM_ADMIN views any evaluation (200 OK)

### 4.3 Update All Rubric Scores

**Endpoint**: `PUT /evaluation/{id}/scores`

**Request Body**:

```json
{
  "scores": {
    "criterion_1": 18,
    "criterion_2": 22,
    "criterion_3": 24
  }
}
```

**Test Cases**:

1. ✅ Instructor updates scores for draft (200 OK)
2. ✅ Cannot update published evaluation (403 Forbidden)
3. ✅ Scores exceeding max points (400 Bad Request)
4. ✅ Negative scores (400 Bad Request)
5. ✅ Missing criteria (400 Bad Request - "All criteria required")
6. ✅ Extra criteria (400 Bad Request - "Unknown criteria")
7. ✅ Response includes updated scores, recalculated total
8. ✅ Audit event: EVALUATION_SCORES_UPDATED -> changes logged
9. ✅ Each criterion change logged individually
10. ✅ ADMIN can update published (200 OK - override)
11. ✅ SYSTEM_ADMIN can update published (200 OK - override)
12. ✅ Student cannot update (403 Forbidden)

### 4.4 Update Single Criterion Score

**Endpoint**: `PATCH /evaluation/{id}/scores/{criterionId}`

**Request Body**:

```json
{
  "score": 22,
  "feedback": "Excellent design pattern implementation"
}
```

**Test Cases**:

1. ✅ Instructor updates single criterion (200 OK)
2. ✅ Cannot update published (403 Forbidden)
3. ✅ Score exceeds max points (400 Bad Request)
4. ✅ Non-existent criterion (404 Not Found)
5. ✅ Invalid criterionId hash (400 Bad Request)
6. ✅ Response includes updated scores + new total
7. ✅ Feedback optional (can be null)
8. ✅ Feedback max 500 chars (400 Bad Request if exceeds)
9. ✅ Audit event: RUBRIC_SCORE_UPDATED -> criterionId, oldScore, newScore
10. ✅ ADMIN can update published (200 OK)
11. ✅ SYSTEM_ADMIN can update published (200 OK)

### 4.5 Add/Update Overall Comment

**Endpoint**: `PATCH /evaluation/{id}/comment`

**Request Body**:

```json
{
  "comment": "Great work overall! See feedback on individual criteria."
}
```

**Test Cases**:

1. ✅ Instructor adds comment (200 OK)
2. ✅ Instructor updates comment (200 OK)
3. ✅ Empty comment clears feedback (200 OK)
4. ✅ Comment max 2000 chars (400 Bad Request if exceeds)
5. ✅ Cannot comment on published after publish (403 Forbidden - no edits)
6. ✅ Response includes updated comment
7. ✅ Audit event: EVALUATION_COMMENT_ADDED -> comment text (truncated)
8. ✅ ADMIN can comment (200 OK)
9. ✅ SYSTEM_ADMIN can comment (200 OK)
10. ✅ Student cannot comment (403 Forbidden)

### 4.6 Publish Evaluation

**Endpoint**: `PATCH /evaluation/{id}/publish`

**Test Cases**:

1. ✅ Instructor publishes evaluation (200 OK)
2. ✅ Already published (409 Conflict - "Already published")
3. ✅ Student receives notification (async)
4. ✅ Status changes to PUBLISHED (200 OK)
5. ✅ Publish timestamp set (current time)
6. ✅ Cannot edit after publish (subsequent PUTs fail 403)
7. ✅ Audit event: EVALUATION_PUBLISHED -> publishedAt, finalGrade
8. ✅ Final grade calculated and locked
9. ✅ ADMIN can publish (200 OK)
10. ✅ SYSTEM_ADMIN can publish (200 OK)
11. ✅ Student cannot publish (403 Forbidden)
12. ✅ Response includes publish confirmation + timestamp

### 4.7 Reopen Evaluation

**Endpoint**: `PATCH /evaluation/{id}/reopen`

**Test Cases**:

1. ✅ ADMIN reopens published evaluation (200 OK)
2. ✅ SYSTEM_ADMIN reopens published evaluation (200 OK)
3. ✅ Instructor cannot reopen (403 Forbidden - ADMIN only)
4. ✅ Draft or in-progress cannot reopen (400 Bad Request)
5. ✅ Status changes to RETURNED
6. ✅ Can edit scores again (PUT succeeds after reopen)
7. ✅ Audit event: EVALUATION_REOPENED -> reopenedBy, reason
8. ✅ Timestamps preserved (original publish date logged)
9. ✅ Student notified of reopening (async)
10. ✅ Response includes new status: RETURNED

### 4.8 Generate PDF Report

**Endpoint**: `POST /evaluation/{id}/pdf`

**Test Cases**:

1. ✅ Generate PDF (200 OK or 202 Accepted - async)
2. ✅ PDF includes rubric scores, comments, total grade
3. ✅ PDF includes student name, assignment title, date
4. ✅ PDF generation is asynchronous (returns job_id)
5. ✅ Cannot generate for draft (403 Forbidden - not approved)
6. ✅ Can regenerate for published (200 OK - overwrites)
7. ✅ Audit event: EVALUATION_PDF_GENERATED -> pdfUrl
8. ✅ Response includes pdf_url (time-limited S3 URL)
9. ✅ ADMIN generates PDF (200 OK)
10. ✅ SYSTEM_ADMIN generates PDF (200 OK)
11. ✅ Student cannot generate (403 Forbidden)

### 4.9 Download PDF Report

**Endpoint**: `GET /evaluation/{id}/pdf`

**Test Cases**:

1. ✅ Instructor downloads PDF (200 OK - file stream)
2. ✅ Student downloads own published PDF (200 OK)
3. ✅ Student cannot download unpublished PDF (403 Forbidden)
4. ✅ Non-existent evaluation (404 Not Found)
5. ✅ PDF not yet generated (400 Bad Request - "PDF not available")
6. ✅ Response headers: Content-Type: application/pdf
7. ✅ Response headers: Content-Disposition: attachment
8. ✅ Audit event: EVALUATION_PDF_DOWNLOADED -> userId, evaluationId (ADMIN/SYSTEM_ADMIN)
9. ✅ ADMIN downloads any PDF (200 OK)
10. ✅ SYSTEM_ADMIN downloads any PDF (200 OK)

### 4.10 Preview PDF Report

**Endpoint**: `GET /evaluation/{id}/pdf/preview`

**Test Cases**:

1. ✅ Instructor previews PDF (200 OK - inline HTML)
2. ✅ Student previews own published PDF (200 OK)
3. ✅ Student cannot preview unpublished (403 Forbidden)
4. ✅ Non-existent evaluation (404 Not Found)
5. ✅ PDF not generated (400 Bad Request)
6. ✅ Browser renders inline (not download)
7. ✅ Audit event: EVALUATION_PDF_PREVIEWED -> Logged (ADMIN/SYSTEM_ADMIN only)
8. ✅ ADMIN previews any PDF (200 OK)
9. ✅ SYSTEM_ADMIN previews any PDF (200 OK)

---

## 5. Grading Calculation

**Formula**:

```
FinalGrade = SUM(score_i for each criterion i) / SUM(maxPoints_i)
```

**Scale**:

- 90-100: A
- 80-89: B
- 70-79: C
- 60-69: D
- 0-59: F

**Audit**: All calculations logged with components

---

## 6. Audit Events

| Event                     | Description                     |
| ------------------------- | ------------------------------- |
| EVALUATION_CREATED        | Evaluation created              |
| EVALUATION_SCORES_UPDATED | Rubric scores changed           |
| RUBRIC_SCORE_UPDATED      | Single criterion score updated  |
| EVALUATION_COMMENT_ADDED  | Overall feedback added/updated  |
| EVALUATION_PUBLISHED      | Evaluation published to student |
| EVALUATION_REOPENED       | Published evaluation reopened   |
| EVALUATION_PDF_GENERATED  | PDF report generated            |
| EVALUATION_PDF_DOWNLOADED | PDF downloaded                  |
| EVALUATION_PDF_PREVIEWED  | PDF previewed                   |
| EVALUATION_VIEWED         | Details accessed                |

---

## 7. Real Test User Credentials

| User         | Email                        | Role         |
| ------------ | ---------------------------- | ------------ |
| Admin        | humberadmin@reviewflow.com   | ADMIN        |
| System Admin | main_sysadmin@reviewflow.com | SYSTEM_ADMIN |
| Instructor   | sarah.johnson@university.edu | INSTRUCTOR   |
| Student      | jane.smith@university.edu    | STUDENT      |

---

## 8. End-to-End Postman Workflows

### Workflow: Complete Evaluation Cycle

```json
{
  "workflowName": "Grading & Publication",
  "steps": [
    {
      "step": 1,
      "description": "Create evaluation",
      "endpoint": "POST /evaluation"
    },
    {
      "step": 2,
      "description": "Update rubric scores",
      "endpoint": "PUT /evaluation/scores"
    },
    {
      "step": 3,
      "description": "Add comment",
      "endpoint": "PATCH /evaluation/comment"
    },
    {
      "step": 4,
      "description": "Generate PDF",
      "endpoint": "POST /evaluation/pdf"
    },
    {
      "step": 5,
      "description": "Publish grades",
      "endpoint": "PATCH /evaluation/publish"
    },
    {
      "step": 6,
      "description": "Student views grades",
      "endpoint": "GET /evaluation (as student)"
    }
  ]
}
```

---

## 9. Error Handling

| Scenario               | Status | Resolution                  |
| ---------------------- | ------ | --------------------------- |
| Published locked       | 403    | Reopen (ADMIN only)         |
| Scores exceed max      | 400    | Verify max points in rubric |
| PDF generation failed  | 500    | Retry or contact admin      |
| Non-existent criterion | 404    | Refresh rubric definition   |

---

## 10. Performance & Caching

| Operation          | Cache TTL |
| ------------------ | --------- |
| Evaluation details | 2 minutes |
| PDF URL            | 1 hour    |
| PDF content        | 5 minutes |

---

## 11. Known Limitations

- PDF generation is async (may take 10-30 seconds)
- No batch grading UI (individual evaluations only)
- Grade curving not supported
- Anonymous grading not supported
