# TEST_SPEC_06_Submission.md

## Submission Module Test Specification

**Module**: Submission Management  
**Controllers**: SubmissionController  
**Endpoints**: 5  
**Last Updated**: Post-Architecture-Fix Phase 2  
**Test Coverage**: 40+ test cases

---

## 1. Endpoint Summary

| #   | Method | Endpoint                                                                | Description                 | Role                                         |
| --- | ------ | ----------------------------------------------------------------------- | --------------------------- | -------------------------------------------- |
| 1   | POST   | `/api/v1/submissions`                                                   | Submit assignment           | STUDENT                                      |
| 2   | GET    | `/api/v1/submissions/{id}`                                              | Get submission details      | Student, INSTRUCTOR, ADMIN, SYSTEM_ADMIN     |
| 3   | GET    | `/api/v1/submissions/teams/{teamId}/assignments/{assignmentId}/history` | Get submission history      | Team member, INSTRUCTOR, ADMIN, SYSTEM_ADMIN |
| 4   | GET    | `/api/v1/submissions/{id}/download`                                     | Download submission file    | Student, INSTRUCTOR, ADMIN, SYSTEM_ADMIN     |
| 5   | GET    | `/api/v1/submissions/{id}/preview`                                      | Preview submission (inline) | Student, INSTRUCTOR, ADMIN, SYSTEM_ADMIN     |

---

## 2. Permission Matrix

**SYSTEM_ADMIN**: All endpoints (audit events)  
**ADMIN**: All endpoints (audit events)  
**INSTRUCTOR**: View submissions, download, preview (course access required)  
**STUDENT**: Submit own work, view own submissions, download own submissions

---

## 3. Submission Lifecycle

```
DRAFT -> SUBMITTED -> GRADED -> RETURNED
```

| State     | Can Edit | Can Submit     | Can Grade | Can View           |
| --------- | -------- | -------------- | --------- | ------------------ |
| DRAFT     | Yes      | Yes            | No        | Owner only         |
| SUBMITTED | No       | Yes (resubmit) | Yes       | Owner + INSTRUCTOR |
| GRADED    | No       | No             | No        | Owner + INSTRUCTOR |
| RETURNED  | Yes      | Yes            | No        | Owner + INSTRUCTOR |

---

## 4. Endpoint Test Cases

### 4.1 Submit Assignment

**Endpoint**: `POST /api/v1/submissions`

**Request Body**:

```json
{
  "assignmentId": "abc123",
  "userOrTeamId": "user_1",
  "fileUrls": ["s3://bucket/file1.pdf"],
  "submissionText": "Submission notes",
  "isGroupSubmission": false
}
```

**Test Cases**:

1. ✅ Student submits before deadline (201 Created)
2. ✅ Student submits after deadline (201 Created - late, flagged)
3. ✅ Student cannot submit unpublished assignment (400 Bad Request)
4. ✅ Team submits for team assignment (201 Created)
5. ✅ Non-team member cannot submit for team (403 Forbidden)
6. ✅ Team cannot submit twice (409 Conflict - "Submission already exists")
7. ✅ Individual cannot submit for team assignment (400 Bad Request)
8. ✅ Team cannot submit for individual assignment (400 Bad Request)
9. ✅ Upload file size > 100MB (413 Payload Too Large)
10. ✅ Response includes submission_id, timestamp, status
11. ✅ Audit event: SUBMISSION_CREATED -> userId/teamId, assignmentId, isLate
12. ✅ Notification sent to instructor (async)
13. ✅ File validation: size, type, virus scan (async)
14. ✅ Multiple file upload (all files in single request)
15. ✅ Empty submission allowed (text-only, no files)

### 4.2 Get Submission Details

**Endpoint**: `GET /api/v1/submissions/{id}`

**Test Cases**:

1. ✅ Student views own submission (200 OK)
2. ✅ Student views other's submission (403 Forbidden)
3. ✅ Instructor views submission from course (200 OK)
4. ✅ Instructor views submission from other course (403 Forbidden)
5. ✅ Response includes submission_id, status, files, grades
6. ✅ Response includes file URLs (pre-signed S3 URLs, 1-hour expiry)
7. ✅ Response includes submission timestamp, late flag
8. ✅ Non-existent submission (404 Not Found)
9. ✅ Invalid submission hash (400 Bad Request)
10. ✅ Team member views team submission (200 OK)
11. ✅ ADMIN views any submission (200 OK)
12. ✅ SYSTEM_ADMIN views any submission (200 OK)
13. ✅ Audit event: SUBMISSION_VIEWED -> Logged (ADMIN/SYSTEM_ADMIN only)

### 4.3 Get Submission History

**Endpoint**: `GET /api/v1/submissions/teams/{teamId}/assignments/{assignmentId}/history`

**Test Cases**:

1. ✅ Team lead views history (200 OK)
2. ✅ Team member views history (200 OK)
3. ✅ Non-member cannot view (403 Forbidden)
4. ✅ Instructor views history (200 OK)
5. ✅ Response includes all previous submissions (timeline)
6. ✅ Response includes status changes
7. ✅ Pagination works (default: 10 per page)
8. ✅ Empty history (200 OK, empty array)
9. ✅ Non-existent team (404 Not Found)
10. ✅ Non-existent assignment (404 Not Found)
11. ✅ ADMIN views history (200 OK)
12. ✅ SYSTEM_ADMIN views history (200 OK)
13. ✅ Audit event: SUBMISSION_HISTORY_VIEWED -> Logged (ADMIN/SYSTEM_ADMIN only)

### 4.4 Download Submission File

**Endpoint**: `GET /api/v1/submissions/{id}/download`

**Test Cases**:

1. ✅ Student downloads own submission (200 OK - file stream)
2. ✅ Student downloads other's submission (403 Forbidden)
3. ✅ Instructor downloads submission (200 OK)
4. ✅ Instructor from other course cannot download (403 Forbidden)
5. ✅ Non-existent submission (404 Not Found)
6. ✅ No files in submission (400 Bad Request - "No files to download")
7. ✅ Multiple files download as ZIP (200 OK - application/zip)
8. ✅ Single file download direct (200 OK - file mime type)
9. ✅ Response headers set correct Content-Disposition
10. ✅ Response includes Cache-Control: no-cache
11. ✅ Audit event: SUBMISSION_DOWNLOADED -> userId, submissionId (ADMIN/SYSTEM_ADMIN only)
12. ✅ ADMIN downloads any submission (200 OK)
13. ✅ SYSTEM_ADMIN downloads any submission (200 OK)
14. ✅ Virus scanned files only (fail if scan pending)

### 4.5 Preview Submission

**Endpoint**: `GET /api/v1/submissions/{id}/preview`

**Test Cases**:

1. ✅ Student previews own submission (200 OK - HTML iframe)
2. ✅ Student previews other's (403 Forbidden)
3. ✅ Instructor previews submission (200 OK)
4. ✅ Non-existent submission (404 Not Found)
5. ✅ No files in submission (400 Bad Request)
6. ✅ Non-previewable file type (400 Bad Request - "Cannot preview file type")
7. ✅ Large file preview (truncated to first 1000 lines)
8. ✅ Binary file cannot preview (400 Bad Request)
9. ✅ Preview includes file metadata (size, type, lines)
10. ✅ ADMIN previews any submission (200 OK)
11. ✅ SYSTEM_ADMIN previews any submission (200 OK)
12. ✅ Audit event: SUBMISSION_PREVIEWED -> Logged (ADMIN/SYSTEM_ADMIN only)
13. ✅ Code syntax highlighting applied (if code file)
14. ✅ Response includes file type and encoding

---

## 5. Submission File Types

| Type     | Format                | Preview                | Download  | Virus Scan   |
| -------- | --------------------- | ---------------------- | --------- | ------------ |
| Code     | .py, .java, .js, .cpp | ✅ Syntax highlighting | ✅ Direct | ✅ Required  |
| Document | .pdf, .docx, .txt     | ✅ Partial             | ✅ Direct | ✅ Required  |
| Archive  | .zip, .tar, .gz       | ❌ Extract list only   | ✅ Direct | ✅ Required  |
| Binary   | .exe, .dll, .bin      | ❌ Blocked             | ✅ Direct | ✅ Scan only |
| Image    | .png, .jpg, .gif      | ✅ Thumbnail           | ✅ Direct | ✅ Required  |

---

## 6. Audit Events

| Event                     | Description                  | Triggered By          |
| ------------------------- | ---------------------------- | --------------------- |
| SUBMISSION_CREATED        | New submission created       | POST endpoint         |
| SUBMISSION_UPDATED        | Submission modified          | (future resubmit)     |
| SUBMISSION_DOWNLOADED     | File(s) downloaded           | GET download endpoint |
| SUBMISSION_PREVIEWED      | Preview accessed             | GET preview endpoint  |
| SUBMISSION_VIEWED         | Details accessed             | GET details endpoint  |
| SUBMISSION_HISTORY_VIEWED | History accessed             | GET history endpoint  |
| SUBMISSION_STATUS_CHANGED | Status updated (GRADED, etc) | Evaluation endpoint   |

---

## 7. Real Test User Credentials

| User         | Email                        | Role         |
| ------------ | ---------------------------- | ------------ |
| Admin        | humberadmin@reviewflow.com   | ADMIN        |
| System Admin | main_sysadmin@reviewflow.com | SYSTEM_ADMIN |
| Instructor   | sarah.johnson@university.edu | INSTRUCTOR   |
| Student 1    | jane.smith@university.edu    | STUDENT      |
| Student 2    | marcus.chen@university.edu   | STUDENT      |

---

## 8. End-to-End Postman Workflows

### Workflow 1: Individual Submission Lifecycle

```json
{
  "workflowName": "Submit & Grade Cycle",
  "steps": [
    {
      "step": 1,
      "description": "Student uploads submission",
      "endpoint": "POST /submissions"
    },
    {
      "step": 2,
      "description": "Instructor downloads for grading",
      "endpoint": "GET /submissions/download"
    },
    {
      "step": 3,
      "description": "Submission status changes to GRADED",
      "endpoint": "View audit logs"
    },
    {
      "step": 4,
      "description": "Student views grades",
      "endpoint": "GET /submissions/details"
    }
  ]
}
```

### Workflow 2: File Preview & Download

```json
{
  "workflowName": "File Operations",
  "steps": [
    {
      "step": 1,
      "description": "Instructor previews PDF",
      "endpoint": "GET /submissions/preview"
    },
    {
      "step": 2,
      "description": "Instructor downloads compressed (ZIP)",
      "endpoint": "GET /submissions/download"
    }
  ]
}
```

---

## 9. Late Submission Handling

**Rules**:

- Timestamp compared against assignment.due_at
- Late flag set automatically if submitted after deadline
- INSTRUCTOR can accept/reject late submissions
- Audit logged with grace period applied
- Penalties not applied by system (manual grade adjustment)

---

## 10. File Security

1. **Virus Scanning**: All uploads scanned (clamav async job)
2. **Size Limits**: 100MB per file, 500MB per submission
3. **Type Validation**: Whitelist accepted types
4. **S3 Presigned URLs**: 1-hour expiry, time-limited access
5. **Quarantine**: Failed scans held in quarantine folder
6. **User Isolation**: Students can only access own submissions

---

## 11. Error Handling

| Scenario               | Status | Resolution                     |
| ---------------------- | ------ | ------------------------------ |
| Virus detected         | 400    | Reupload after scan            |
| After deadline         | 201    | Marked late, notify instructor |
| File too large         | 413    | Compress or split files        |
| Unpublished assignment | 400    | Wait for publication           |
| Already submitted      | 409    | Resubmit allowed (new version) |

---

## 12. Performance & Caching

| Operation           | Cache TTL |
| ------------------- | --------- |
| Submission details  | 2 minutes |
| History list        | 1 minute  |
| File preview (HTML) | 5 minutes |
| Pre-signed URL      | 1 hour    |

---

## 13. Known Limitations

- Resubmission overwrites previous (no version history per endpoint)
- Batch download (all submissions) not supported
- Asynchronous file scanning means immediate response before scan completes
- Preview unavailable during scan
