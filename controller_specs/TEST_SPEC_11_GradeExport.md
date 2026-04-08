# TEST_SPEC_11_GradeExport.md

## Grade Export Module Test Specification

**Module**: Grade Export & Reporting  
**Controllers**: GradeExportController  
**Endpoints**: 1  
**Last Updated**: Post-Architecture-Fix Phase 2  
**Test Coverage**: 30+ test cases

---

## 1. Endpoint Summary

| #   | Method | Endpoint                                        | Description                | Role                            |
| --- | ------ | ----------------------------------------------- | -------------------------- | ------------------------------- |
| 1   | GET    | `/api/v1/courses/{courseId}/evaluations/export` | Export grades to CSV/Excel | INSTRUCTOR, ADMIN, SYSTEM_ADMIN |

---

## 2. Permission Matrix

**SYSTEM_ADMIN**: Export any course  
**ADMIN**: Export any course  
**INSTRUCTOR**: Export own course only  
**STUDENT**: Denied (403 Forbidden)

---

## 3. Endpoint Test Cases

### 3.1 Export Course Grades

**Endpoint**: `GET /api/v1/courses/{courseId}/evaluations/export?format=csv&includeComments=true`

**Response Example (CSV)**:

```
Student ID,Student Name,Email,Assignment1,Assignment2,Assignment3,Final Grade,Status
STU001,Jane Smith,jane.smith@university.edu,85,92,88,88.3,GRADED
STU002,Marcus Chen,marcus.chen@university.edu,92,88,95,91.7,GRADED
STU003,Priya Patel,priya.patel@university.edu,0,0,0,0,NO_SUBMISSION
```

**Test Cases**:

1. ✅ Instructor exports grades (200 OK)
2. ✅ Format: CSV (200 OK)
3. ✅ Format: XLSX/Excel (200 OK)
4. ✅ Format: JSON (200 OK)
5. ✅ Include comments: true/false (200 OK)
6. ✅ Include rubric breakdown: true (200 OK)
7. ✅ Filter by assignment (200 OK)
8. ✅ Filter by section/team (200 OK)
9. ✅ Non-existent course (404 Not Found)
10. ✅ Course with no students (200 OK - headers only)
11. ✅ Student cannot export (403 Forbidden)
12. ✅ Other course's instructor cannot export (403 Forbidden)
13. ✅ Response includes header row (column names)
14. ✅ All published grades included
15. ✅ Unpublished grades excluded (0 or N/A)
16. ✅ Late submission flag in export
17. ✅ Student names PDI (encryption keys)
18. ✅ ADMIN exports any course (200 OK)
19. ✅ SYSTEM_ADMIN exports any course (200 OK)
20. ✅ File download (not JSON response)
21. ✅ Response headers: Content-Type: text/csv or application/vnd.ms-excel
22. ✅ Response headers: Content-Disposition: attachment, filename
23. ✅ Large class (500+ students, performance OK)
24. ✅ Unicode characters handled (names with accents)
25. ✅ CSV escaping (commas/quotes escaped)
26. ✅ Audit event: GRADES_EXPORTED -> export_format, student_count

---

## 4. Export Formats

### CSV Format

```
Student ID,Student Name,Email,Assignment 1,Assignment 2,Final Grade,Status
STU001,Jane Smith,jane@uni.edu,85,92,88.5,PUBLISHED
```

### Excel Format

Multiple worksheets:

- Sheet 1: Summary grades
- Sheet 2: Assignment 1 breakdown
- Sheet 3: Assignment 2 breakdown
- Sheet 4: Rubric details (if included)

### JSON Format

```json
{
  "courseId": "abc123",
  "courseName": "CS101",
  "exportDate": "2024-01-15",
  "students": [
    {
      "studentId": "STU001",
      "name": "Jane Smith",
      "grades": [
        { "assignment": "Assignment 1", "score": 85, "status": "PUBLISHED" },
        { "assignment": "Assignment 2", "score": 92, "status": "PUBLISHED" }
      ]
    }
  ]
}
```

---

## 5. Export Data Included

| Field             | Description                     | Conditional                     |
| ----------------- | ------------------------------- | ------------------------------- |
| Student ID        | System identifier               | Always                          |
| Student Name      | Full name                       | Always                          |
| Email             | Institutional email             | Always                          |
| Assignment Scores | Per-assignment grades           | If published                    |
| Final Grade       | Weighted average                | If all published                |
| Status            | PUBLISHED, DRAFT, NO_SUBMISSION | Always                          |
| Rubric Breakdown  | Score per criterion             | Optional (includeRubric=true)   |
| Comments          | Instructor feedback             | Optional (includeComments=true) |
| Submission Date   | When submitted                  | Optional                        |
| Late Flag         | Y/N if late                     | Optional                        |

---

## 6. Filtering Options

```
?format=csv
&includeComments=true
&includeRubric=true
&assignmentId=abc123
&sectionId=def456
&includeUnpublished=false
&anonymize=false
```

- **assignmentId**: Export single assignment only (optional)
- **sectionId**: Export section roster only (optional)
- **includeUnpublished**: Include draft/0 grades (false by default)
- **anonymize**: Replace names with IDs (for external review)

---

## 7. Real Test Users

| User         | Email                        | Role         |
| ------------ | ---------------------------- | ------------ |
| Instructor   | sarah.johnson@university.edu | INSTRUCTOR   |
| Admin        | humberadmin@reviewflow.com   | ADMIN        |
| System Admin | main_sysadmin@reviewflow.com | SYSTEM_ADMIN |

---

## 8. Postman Examples

### Export as CSV with Comments

```json
{
  "name": "Export Grades - CSV",
  "request": {
    "method": "GET",
    "url": "{{base_url}}/api/v1/courses/{{courseId}}/evaluations/export?format=csv&includeComments=true",
    "header": [
      { "key": "Authorization", "value": "Bearer {{instructor_token}}" }
    ]
  },
  "tests": [
    "pm.response.code === 200",
    "pm.response.headers.get('Content-Type').includes('text/csv')"
  ]
}
```

### Export as Excel with Rubric

```json
{
  "name": "Export Grades - Excel",
  "request": {
    "method": "GET",
    "url": "{{base_url}}/api/v1/courses/{{courseId}}/evaluations/export?format=xlsx&includeRubric=true"
  }
}
```

---

## 9. Error Handling

| Scenario             | Status | Resolution             |
| -------------------- | ------ | ---------------------- |
| Invalid format       | 400    | Use csv, xlsx, or json |
| No grades            | 200    | Returns headers only   |
| Course not found     | 404    | Verify courseId        |
| Permission denied    | 403    | Login as INSTRUCTOR+   |
| Large export timeout | 504    | Split by assignment    |

---

## 10. Performance & Caching

| Operation       | Time                |
| --------------- | ------------------- |
| < 100 students  | < 2 seconds         |
| < 500 students  | < 10 seconds        |
| < 1000 students | < 30 seconds        |
| > 1000 students | Recommend filtering |

---

## 11. Data Privacy

1. **PII Handling**: Anonymize option for external sharing
2. **Encryption**: Large exports sent over HTTPS only
3. **Audit Trail**: All exports logged with user + timestamp
4. **Retention**: Export logs kept for 1 year
5. **Access Control**: Only instructors and admins

---

## 12. Known Limitations

- Batch export (multiple courses) not supported
- Historical grade versions not included
- Weighted GPA calculation not supported
- Integration with SIS (Banner, Canvas) not yet available
