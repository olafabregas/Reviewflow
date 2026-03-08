# ReviewFlow — Module 6: Evaluations
> Controller: `EvaluationController.java`
> Base path: `/api/v1/evaluations`

---

## 6.1 POST /evaluations

### Must Have
- [ ] INSTRUCTOR only (must teach the course this submission belongs to)
- [ ] Body: `{ "submissionId": 42 }`
- [ ] Creates evaluation with `is_draft = true`, `total_score = 0`
- [ ] UNIQUE per submission — only one evaluation per submission allowed

### Responses
- [ ] `201 Created` — returns `{ id, submissionId, isDraft: true, totalScore: 0, rubricScores: [], createdAt }`
- [ ] `400 Bad Request` — `submissionId` missing
- [ ] `403 Forbidden` — instructor does not teach the course this submission belongs to
- [ ] `404 Not Found` — submission doesn't exist
- [ ] `409 Conflict` — evaluation already exists for this submission → `{ code: "EVALUATION_EXISTS", message: "An evaluation already exists", data: { existingEvaluationId: 5 } }`

### Edge Cases
- [ ] Create second evaluation for same submission → `409 EVALUATION_EXISTS` with the existing `evaluationId` in response
- [ ] Instructor from different course tries to evaluate → `403`

---

## 6.2 GET /evaluations/{id}

### Must Have
- [ ] INSTRUCTOR: any evaluation they created
- [ ] STUDENT: only published evaluations (`is_draft = false`) for their own team
- [ ] ADMIN: any evaluation
- [ ] Returns: `{ id, submissionId, isDraft, totalScore, overallComment, publishedAt, rubricScores: [{ criterionId, criterionName, score, maxScore, comment }] }`

### Responses
- [ ] `200 OK` — evaluation detail
- [ ] `403 Forbidden` — student accessing another team's evaluation
- [ ] `404 Not Found` — doesn't exist OR is a draft (for student) — always `404` for student viewing draft, never `403`

### Edge Cases
- [ ] Student views draft evaluation → `404` (NOT `403` — don't reveal it exists)
- [ ] Student views published evaluation for their team → `200`
- [ ] Student views published evaluation for DIFFERENT team → `403`

---

## 6.3 PUT /evaluations/{id}/scores

### Must Have
- [ ] INSTRUCTOR only (creator of this evaluation)
- [ ] Evaluation must be a draft (`is_draft = true`)
- [ ] Body: array of `{ criterionId, score, comment }`
- [ ] Each `score` must be: `0 ≤ score ≤ criterion.maxScore`
- [ ] Upserts all scores (insert or update)
- [ ] Recalculates `total_score = SUM of all scores` after saving
- [ ] Returns updated evaluation with new `totalScore`

### Responses
- [ ] `200 OK` — scores saved, `totalScore` updated
- [ ] `400 Bad Request` — any score exceeds `maxScore` → `{ code: "SCORE_EXCEEDS_MAX", message: "Score 25 exceeds maximum of 20 for criterion 'Code Quality'" }`
- [ ] `400 Bad Request` — negative score → `{ code: "NEGATIVE_SCORE" }`
- [ ] `400 Bad Request` — unknown `criterionId` for this assignment → `{ code: "INVALID_CRITERION" }`
- [ ] `403 Forbidden` — evaluation is published → `{ code: "EVALUATION_PUBLISHED", message: "Reopen the evaluation before editing scores" }`
- [ ] `403 Forbidden` — not the instructor who created this evaluation
- [ ] `404 Not Found` — evaluation or criterion doesn't exist

### Edge Cases
- [ ] Score = 0 → `200` (valid)
- [ ] Score = maxScore exactly → `200` (valid)
- [ ] Score = maxScore + 1 → `400 SCORE_EXCEEDS_MAX`
- [ ] Score = -1 → `400 NEGATIVE_SCORE`
- [ ] Submit scores for published evaluation → `403 EVALUATION_PUBLISHED`
- [ ] Partial scores (not all criteria scored) → `200` (allowed — can save partial drafts)

---

## 6.4 PATCH /evaluations/{id}/scores/{criterionId}

### Must Have
- [ ] INSTRUCTOR only
- [ ] Evaluation must be draft
- [ ] Updates single criterion score and comment
- [ ] Recalculates `total_score`

### Responses
- [ ] `200 OK` — score updated, `totalScore` recalculated
- [ ] `400 Bad Request` — score invalid (same rules as above)
- [ ] `403 Forbidden` — published evaluation or not instructor
- [ ] `404 Not Found`

---

## 6.5 PATCH /evaluations/{id}/comment

### Must Have
- [ ] INSTRUCTOR only
- [ ] Evaluation must be draft
- [ ] Body: `{ "comment": "Great work overall..." }`
- [ ] Max 2000 characters
- [ ] Sets `overall_comment` field on evaluation

### Responses
- [ ] `200 OK` — comment saved
- [ ] `400 Bad Request` — comment exceeds 2000 chars
- [ ] `403 Forbidden` — published or not instructor
- [ ] `404 Not Found`

---

## 6.6 PATCH /evaluations/{id}/publish

### Must Have
- [ ] INSTRUCTOR only (creator)
- [ ] Sets `is_draft = false`, `published_at = NOW()`
- [ ] Does NOT require all criteria to be scored (partial evaluation is valid)
- [ ] Sends `FEEDBACK_PUBLISHED` notification to ALL team members
- [ ] Logs `EVALUATION_PUBLISHED` to `audit_log`

### Responses
- [ ] `200 OK` — returns updated evaluation with `isDraft: false` and `publishedAt`
- [ ] `403 Forbidden` — not the instructor who created this evaluation
- [ ] `403 Forbidden` — already published → `{ code: "ALREADY_PUBLISHED" }`
- [ ] `404 Not Found`

### Edge Cases
- [ ] Publish evaluation with `totalScore = 0` (no scores entered) → `200` (valid — instructor may publish empty feedback with just a comment)
- [ ] Publish already published evaluation → `403 ALREADY_PUBLISHED`
- [ ] After publish, all team members receive notification → verify `FEEDBACK_PUBLISHED` in notifications table

---

## 6.7 PATCH /evaluations/{id}/reopen

### Must Have
- [ ] INSTRUCTOR only
- [ ] Sets `is_draft = true`, clears `published_at`
- [ ] Students immediately lose visibility to this evaluation
- [ ] Returns updated evaluation with `isDraft: true`

### Responses
- [ ] `200 OK` — evaluation reopened as draft
- [ ] `403 Forbidden` — not instructor or already a draft → `{ code: "ALREADY_DRAFT" }`
- [ ] `404 Not Found`

### Edge Cases
- [ ] After reopen: student tries to view → `404`
- [ ] After reopen: instructor edits scores → `200`
- [ ] After reopen: publish again → `200`

---

## 6.8 POST /evaluations/{id}/pdf

### Must Have
- [ ] INSTRUCTOR only
- [ ] Evaluation must be published (`is_draft = false`)
- [ ] Generates PDF server-side using OpenPDF
- [ ] Stores PDF reference in DB
- [ ] Returns `{ message: "PDF generated", downloadUrl: "/api/v1/evaluations/{id}/pdf" }`
- [ ] Logs `PDF_GENERATED` to `audit_log`

### Responses
- [ ] `200 OK` — PDF generated
- [ ] `400 Bad Request` — evaluation is still a draft → `{ code: "NOT_PUBLISHED", message: "Publish the evaluation before generating a PDF" }`
- [ ] `403 Forbidden`
- [ ] `404 Not Found`
- [ ] `500 Internal Server Error` — PDF generation failed (should never reach client — handle gracefully)

---

## 6.9 GET /evaluations/{id}/pdf

### Must Have
- [ ] INSTRUCTOR or STUDENT (student: own team's published evaluation only)
- [ ] Streams PDF as download
- [ ] Sets `Content-Disposition: attachment; filename="feedback-TeamAlpha-Phase1.pdf"`

### Responses
- [ ] `200 OK` — PDF streamed
- [ ] `403 Forbidden` — access denied
- [ ] `404 Not Found` — evaluation not found OR PDF not yet generated → `{ code: "PDF_NOT_GENERATED", message: "PDF has not been generated yet. Ask your instructor to generate it." }`

---

## 6.10 GET /assignments/{id}/gradebook

### Must Have
- [ ] INSTRUCTOR only
- [ ] Returns one row per team for the assignment
- [ ] Each row: `{ teamId, teamName, members: [name,...], latestVersionNumber, submittedAt, isLate, totalScore, maxPossibleScore, evaluationStatus: NOT_STARTED|DRAFT|PUBLISHED, evaluationId }`
- [ ] Teams with no submission still appear with `submitted: false`
- [ ] Supports `?sort=teamName|score|submittedAt`

### Responses
- [ ] `200 OK` — gradebook array
- [ ] `403 Forbidden`
- [ ] `404 Not Found`

---

## 6.11 GET /students/me/evaluations ⭐ NEW

### Must Have
- [ ] STUDENT only
- [ ] Returns all PUBLISHED evaluations for current student's teams globally
- [ ] Each item: `{ evaluationId, assignmentTitle, courseCode, teamName, totalScore, maxPossibleScore, publishedAt, hasPdf }`
- [ ] `maxPossibleScore` = sum of all `maxScore` values for the assignment's rubric
- [ ] Only returns `is_draft = false` evaluations

### Responses
- [ ] `200 OK` — paginated list
- [ ] `401 Unauthorized`
- [ ] `403 Forbidden` — not STUDENT
