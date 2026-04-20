# ReviewFlow - Module 13: Assignment Groups

> Controller: `AssignmentGroupController.java`  
> Base path: `/api/v1`  
> Last Updated: 2026-04-16
>
> **Related PRDs:** [PRD-10 Assignment Groups & Grade Weighting](../Features/PRD_10_assignment_groups.md), [PRD-15 Assignment Groups Controller Specs Expansion](../Features/PRD_15_assignment_groups_controller_specs_expansion.md)  
> **Related Modules:** [03_Module_Assignments.md](./03_Module_Assignments.md), [04_Module_Teams.md](./04_Module_Teams.md), [11_Module_SystemAdmin.md](./11_Module_SystemAdmin.md)  
> **Related Decisions:** [Caffeine over Redis at current scale](../DECISIONS.md), [Fixed 5-account ceiling for system administration](../DECISIONS.md)

---

## Module Scope

Assignment Groups provide grade-category organization per course and power weighted grade workflows. Every assignment belongs to exactly one group. Each course has an immutable auto-created `Uncategorized` group.

Implemented endpoints:

1. `POST /api/v1/courses/{courseId}/assignment-groups`
2. `GET /api/v1/courses/{courseId}/assignment-groups`
3. `PUT /api/v1/assignment-groups/{id}`
4. `DELETE /api/v1/assignment-groups/{id}`
5. `PATCH /api/v1/assignments/{id}/group`

---

## Role Permission Matrix

| Endpoint                                          | STUDENT                | INSTRUCTOR     | ADMIN | SYSTEM_ADMIN |
| ------------------------------------------------- | ---------------------- | -------------- | ----- | ------------ |
| POST /api/v1/courses/{courseId}/assignment-groups | ✗                      | ✓ (own course) | ✓     | ✓            |
| GET /api/v1/courses/{courseId}/assignment-groups  | ✓ (enrolled in course) | ✓              | ✓     | ✓            |
| PUT /api/v1/assignment-groups/{id}                | ✗                      | ✓ (own course) | ✓     | ✓            |
| DELETE /api/v1/assignment-groups/{id}             | ✗                      | ✓ (own course) | ✓     | ✓            |
| PATCH /api/v1/assignments/{id}/group              | ✗                      | ✓ (own course) | ✓     | ✓            |

Notes:

- Instructor authorization is verified by course-instructor membership.
- ADMIN and SYSTEM_ADMIN are allowed by role check in service layer.
- Course member visibility for GET is the documented contract and is enforced in follow-up runtime alignment work.

---

## 13.1 POST /api/v1/courses/{courseId}/assignment-groups

### Must Have

- [ ] Endpoint exists and accepts `POST`.
- [ ] Path variable `courseId` is hashid and decoded via `HashidService.decodeOrThrow`.
- [ ] Request body fields:
  - `name` required, non-blank
  - `weight` required, 0-100
  - `dropLowestN` required, >= 0
  - `displayOrder` optional, >= 0 if present
- [ ] Service verifies actor can manage target course.
- [ ] Service validates `weight` and `dropLowestN`.
- [ ] Group is created with `isUncategorized=false`.
- [ ] Audit event logged: `ASSIGNMENT_GROUP_CREATED`.
- [ ] `courseGradeGroups` cache entry for the course is evicted.
- [ ] Weight summary warning returned when configured total != 100.

### Responses

- [ ] `201 Created` with standard success envelope and created group payload.
- [ ] `400 Bad Request` on validation failure (`INVALID_GROUP_WEIGHT`, `DROP_LOWEST_EXCEEDS_GROUP_SIZE`, `INVALID_ID`, `VALIDATION_ERROR`, `INVALID_REQUEST`).
- [ ] `403 Forbidden` if actor is not authorized for course.
- [ ] `404 Not Found` if course/user not found.

### Edge Cases

- [ ] `weight=0` allowed.
- [ ] `weight=100` allowed.
- [ ] `dropLowestN=0` allowed.
- [ ] `displayOrder` omitted defaults to `0`.
- [ ] Total configured weight not equal to 100 is warning-only, not rejection.

---

## 13.2 GET /api/v1/courses/{courseId}/assignment-groups

### Must Have

- [ ] Endpoint exists and accepts `GET`.
- [ ] Course id is decoded via hashid decoder.
- [ ] Access is limited to enrolled course users, ADMIN, and SYSTEM_ADMIN.
- [ ] Returns groups ordered by `displayOrder` ascending.
- [ ] Returns group list with:
  - id, name, weight, dropLowestN, displayOrder
  - isUncategorized, assignmentCount
  - assignment summaries (id, title, dueAt, submissionType)
- [ ] Returns `totalConfiguredWeight` and optional `weightWarning`.
- [ ] Uses cache key `courseId` in `courseGradeGroups` cache.

### Responses

- [ ] `200 OK` success envelope with list payload.
- [ ] `400 Bad Request` on invalid hashid.
- [ ] `403 Forbidden` for authenticated users without course enrollment and without elevated role.
- [ ] `404 Not Found` when course does not exist.

### Edge Cases

- [ ] Course with only `Uncategorized` group returns valid list.
- [ ] Empty assignment list in group returns `assignments: []`.
- [ ] `weightWarning` is null only when total exactly equals 100.

---

## 13.3 PUT /api/v1/assignment-groups/{id}

### Must Have

- [ ] Endpoint exists and accepts `PUT`.
- [ ] Path id decoded using hashid decoder.
- [ ] Same request contract as create.
- [ ] Service verifies actor can manage owning course.
- [ ] Service validates weight and `dropLowestN` against assignment count.
- [ ] Group fields updated and `updatedAt` refreshed.
- [ ] Audit event logged: `ASSIGNMENT_GROUP_UPDATED`.
- [ ] `courseGradeGroups` cache evicted for course.
- [ ] If name changed, `assignmentDetail` cache evicted for assignments in course.

### Responses

- [ ] `200 OK` success envelope with updated group payload.
- [ ] `400 Bad Request` on validation errors (`INVALID_GROUP_WEIGHT`, `DROP_LOWEST_EXCEEDS_GROUP_SIZE`, `GROUP_NOT_IN_COURSE`, `INVALID_ID`, request validation errors).
- [ ] `403 Forbidden` on authorization failure.
- [ ] `404 Not Found` if group/course/user missing.

### Edge Cases

- [ ] `dropLowestN` cannot be >= assignment count when group has assignments.
- [ ] Name change triggers broader assignment cache eviction.
- [ ] Total weight warning recalculated after update.

---

## 13.4 DELETE /api/v1/assignment-groups/{id}

### Must Have

- [ ] Endpoint exists and accepts `DELETE`.
- [ ] Path id decoded via hashid decoder.
- [ ] Actor authorization verified.
- [ ] Deletion is blocked for `isUncategorized=true`.
- [ ] Deletion is blocked when group has assignments.
- [ ] Audit event logged: `ASSIGNMENT_GROUP_DELETED`.
- [ ] `courseGradeGroups` cache evicted.

### Responses

- [ ] `200 OK` success envelope with message payload.
- [ ] `409 Conflict` for:
  - `CANNOT_DELETE_UNCATEGORIZED`
  - `GROUP_NOT_EMPTY`
- [ ] `403 Forbidden` for unauthorized actor.
- [ ] `404 Not Found` for missing group/course/user.

### Edge Cases

- [ ] Deleting empty, non-uncategorized group succeeds.
- [ ] Attempting to delete group with assignments returns conflict and no state change.

---

## 13.5 PATCH /api/v1/assignments/{id}/group

### Must Have

- [ ] Endpoint exists and accepts `PATCH`.
- [ ] Assignment path id and body `groupId` are hashids and decoded.
- [ ] Request body uses typed DTO with validated required field `groupId`.
- [ ] Actor authorization validated against assignment course.
- [ ] Target group must belong to same course as assignment.
- [ ] Assignment group relation updated.
- [ ] Audit event logged: `ASSIGNMENT_MOVED_TO_GROUP`.
- [ ] Cache evictions:
  - `assignmentDetail` for assignment
  - `courseGradeGroups` for course
  - `gradeOverview` clear if configured

### Responses

- [ ] `200 OK` with assignment id/new group id/new group name.
- [ ] `400 Bad Request` for `GROUP_NOT_IN_COURSE`, invalid ids, and `INVALID_REQUEST` on missing `groupId`.
- [ ] `403 Forbidden` for unauthorized actor.
- [ ] `404 Not Found` for missing assignment/group/user.

### Edge Cases

- [ ] Moving to a group in another course is rejected.
- [ ] Moving to current group is treated as idempotent update path (no explicit conflict defined).

---

## Validation Rules

| Rule                                               | Outcome                              |
| -------------------------------------------------- | ------------------------------------ |
| `weight` < 0 or > 100                              | `400 INVALID_GROUP_WEIGHT`           |
| `dropLowestN` < 0                                  | `400 DROP_LOWEST_EXCEEDS_GROUP_SIZE` |
| `dropLowestN` >= assignment count (when count > 0) | `400 DROP_LOWEST_EXCEEDS_GROUP_SIZE` |
| Missing `groupId` in move request body             | `400 INVALID_REQUEST`                |
| Cross-course move target                           | `400 GROUP_NOT_IN_COURSE`            |
| Delete uncategorized group                         | `409 CANNOT_DELETE_UNCATEGORIZED`    |
| Delete non-empty group                             | `409 GROUP_NOT_EMPTY`                |

---

## Data Model and Migration Anchors

Source migration: `V21__create_assignment_groups.sql`

Key schema points:

- `assignment_groups` table with checks:
  - `weight >= 0 and weight <= 100`
  - `drop_lowest_n >= 0`
- `assignments.group_id` added, backfilled, then made `NOT NULL`.
- One uncategorized group backfilled per existing course.

---

## Caching, Audit, and Observability

### Caching

- Cache name: `courseGradeGroups`
- TTL: 5 minutes
- Key: `courseId`
- Eviction triggers:
  - create/update/delete group
  - assignment move between groups

Additional affected caches:

- `assignmentDetail` (on assignment move and name-change cascade)
- `gradeOverview` (if configured in cache manager)

### Audit Events

- `ASSIGNMENT_GROUP_CREATED`
- `ASSIGNMENT_GROUP_UPDATED`
- `ASSIGNMENT_GROUP_DELETED`
- `ASSIGNMENT_MOVED_TO_GROUP`

---

## References

- `src/main/java/com/reviewflow/controller/AssignmentGroupController.java`
- `src/main/java/com/reviewflow/service/AssignmentGroupService.java`
- `src/main/java/com/reviewflow/config/CacheConfig.java`
- `src/main/java/com/reviewflow/exception/GlobalExceptionHandler.java`
- `src/main/resources/db/migration/V21__create_assignment_groups.sql`

## Runtime Alignment Note

The documented API contract in this file is authoritative for QA and onboarding.
Any runtime mismatch is handled in follow-up implementation work tracked by `PRD_16_assignment_groups_runtime_contract_alignment.md`.
