# PRD 15 — Course Roster

**Status:** Draft  
**Author:** Roqeeb Olamide Ayorinde  
**Depends on:** PRD-02 (avatar_url exists on users)  
**Blocks:** Nothing  
**Migration:** None — permissions change + endpoint update only

---

## Purpose

Students currently have no way to see who else is in their course. Instructors have `GET /courses/{id}/students` but it returns raw data with no formatting, no avatars, and is not accessible to students. This PRD adds a properly structured roster endpoint accessible to all course members with role-appropriate data visibility.

---

## Roles & Permissions

| Action | STUDENT | INSTRUCTOR | ADMIN | SYSTEM_ADMIN |
|---|---|---|---|---|
| View course roster | ✓ — own enrolled courses | ✓ — own courses | ✓ | ✓ |
| See classmate emails | ✗ — privacy | ✓ | ✓ | ✓ |
| See enrollment date | ✗ | ✓ | ✓ | ✓ |
| See submission count | ✗ | ✓ | ✓ | ✓ |

---

## Full Request Flow

```
GET /courses/{id}/roster

→ CourseController.getRoster(courseId, currentUser)
→ CourseService.getRoster(courseId, userId, role)
    → Verify user is enrolled in or teaches this course → 403 if not
    → Fetch instructors: SELECT users WHERE course_instructors.course_id = ?
    → Fetch students: SELECT users WHERE course_enrollments.course_id = ?
    → If role == STUDENT:
        Return limited student view (name + avatar only, no emails)
    → If role == INSTRUCTOR/ADMIN/SYSTEM_ADMIN:
        Return full view (name + email + avatar + enrolled_at + submission_count)
    → submission_count = COUNT(submissions WHERE student_id = userId
        OR team_id IN (SELECT team_id FROM team_members WHERE user_id = userId))

→ 200 OK { instructors: [...], students: [...], totalStudents: N }
```

---

## Data Model

No new tables. Uses existing `users`, `course_enrollments`, `course_instructors`.

Deprecate `GET /courses/{id}/students` — replaced by `GET /courses/{id}/roster`. Keep old endpoint returning 301 redirect to `/roster` for backward compatibility.

---

## API Contract

### GET /courses/{id}/roster

**Student view:**
```json
{
  "success": true,
  "data": {
    "instructors": [
      {
        "id": "usr111",
        "firstName": "Sarah",
        "lastName": "Johnson",
        "avatarUrl": "https://...",
        "role": "INSTRUCTOR"
      }
    ],
    "students": [
      {
        "id": "stu111",
        "firstName": "Jane",
        "lastName": "Smith",
        "avatarUrl": null,
        "role": "STUDENT"
      }
    ],
    "totalStudents": 20
  }
}
```

**Instructor view (additional fields):**
```json
{
  "students": [
    {
      "id": "stu111",
      "firstName": "Jane",
      "lastName": "Smith",
      "email": "jane.smith@university.edu",
      "avatarUrl": null,
      "role": "STUDENT",
      "enrolledAt": "2026-01-15T09:00:00Z",
      "submissionCount": 4
    }
  ]
}
```

---

## Edge Cases

| Scenario | Behaviour |
|---|---|
| Student not enrolled in course | 403 FORBIDDEN |
| Course has no students yet | Returns empty students array |
| Student has no avatar | avatarUrl: null — frontend shows initials |
| Instructor teaches multiple sections | Only students in requested course shown |

---

## Impact on Existing Modules

| Module | Change |
|---|---|
| `CourseController` | Add `GET /courses/{id}/roster`, deprecate `/students` |
| `CourseService` | Add `getRoster()` with role-based field filtering |
| `CourseDto` | Existing — no change |
| New: `RosterDto`, `RosterMemberDto` | Response shapes |
