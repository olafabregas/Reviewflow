# TEST_SPEC_13 - Assignment Groups

**Status:** Complete  
**Version:** 1.0  
**Last Updated:** 2026-04-16  
**Reference Spec:** [13_Module_AssignmentGroups.md](13_Module_AssignmentGroups.md) | [00_Global_Rules_and_Reference.md](00_Global_Rules_and_Reference.md)  
**Related PRDs:** [PRD-10 Assignment Groups & Grade Weighting](../Features/PRD_10_assignment_groups.md), [PRD-15 Assignment Groups Controller Specs Expansion](../Features/PRD_15_assignment_groups_controller_specs_expansion.md)

---

## 1. Endpoints Inventory

| #   | Method | Endpoint                                     | Description                          | Role                                   |
| --- | ------ | -------------------------------------------- | ------------------------------------ | -------------------------------------- |
| 1   | POST   | /api/v1/courses/{courseId}/assignment-groups | Create assignment group for a course | INSTRUCTOR, ADMIN, SYSTEM_ADMIN        |
| 2   | GET    | /api/v1/courses/{courseId}/assignment-groups | List groups and group-weight summary | Authenticated users with course access |
| 3   | PUT    | /api/v1/assignment-groups/{id}               | Update group settings                | INSTRUCTOR, ADMIN, SYSTEM_ADMIN        |
| 4   | DELETE | /api/v1/assignment-groups/{id}               | Delete empty non-uncategorized group | INSTRUCTOR, ADMIN, SYSTEM_ADMIN        |
| 5   | PATCH  | /api/v1/assignments/{id}/group               | Move assignment to target group      | INSTRUCTOR, ADMIN, SYSTEM_ADMIN        |

---

## 2. Role Permission Matrix

| Endpoint                                          | STUDENT                 | INSTRUCTOR      | ADMIN | SYSTEM_ADMIN |
| ------------------------------------------------- | ----------------------- | --------------- | ----- | ------------ |
| POST /api/v1/courses/{courseId}/assignment-groups | ❌                      | ✅ (own course) | ✅    | ✅           |
| GET /api/v1/courses/{courseId}/assignment-groups  | ✅ (enrolled in course) | ✅              | ✅    | ✅           |
| PUT /api/v1/assignment-groups/{id}                | ❌                      | ✅ (own course) | ✅    | ✅           |
| DELETE /api/v1/assignment-groups/{id}             | ❌                      | ✅ (own course) | ✅    | ✅           |
| PATCH /api/v1/assignments/{id}/group              | ❌                      | ✅ (own course) | ✅    | ✅           |

Notes:

- SERVICE layer enforces ownership for instructor paths.
- ADMIN and SYSTEM_ADMIN bypass instructor-course ownership check by role.
- GET contract requires course enrollment for non-elevated users.

---

## 3. Happy Path Scenarios

### 3.1 Instructor creates group

**User:** sarah.johnson@university.edu  
**HTTP Method:** POST  
**Endpoint:** /api/v1/courses/{courseId}/assignment-groups

**Request Body**

```json
{
  "name": "Projects",
  "weight": 40.0,
  "dropLowestN": 0,
  "displayOrder": 1
}
```

**Expected Response:** 201

```json
{
  "success": true,
  "data": {
    "id": "grpAbC123",
    "name": "Projects",
    "weight": 40.0,
    "dropLowestN": 0,
    "displayOrder": 1,
    "isUncategorized": false,
    "assignmentCount": 0,
    "weightWarning": "Group weights total 40%. Grades will be normalised to completed work."
  },
  "timestamp": "2026-04-16T09:30:00Z"
}
```

**Verify**

- [ ] Status code is 201.
- [ ] `success=true` and `data.id` present.
- [ ] `isUncategorized=false`.
- [ ] audit event `ASSIGNMENT_GROUP_CREATED` is written.
- [ ] `courseGradeGroups` cache for course is evicted.

### 3.2 Instructor lists groups

**User:** sarah.johnson@university.edu  
**HTTP Method:** GET  
**Endpoint:** /api/v1/courses/{courseId}/assignment-groups

**Expected Response:** 200 with groups payload

**Verify**

- [ ] Returns all groups including `Uncategorized`.
- [ ] Groups ordered by `displayOrder` ascending.
- [ ] `totalConfiguredWeight` present.
- [ ] `weightWarning` null only when total is exactly 100.
- [ ] Assignment summaries include `id`, `title`, `dueAt`, `submissionType`.

### 3.3 Instructor updates group

**User:** sarah.johnson@university.edu  
**HTTP Method:** PUT  
**Endpoint:** /api/v1/assignment-groups/{id}

**Request Body**

```json
{
  "name": "Projects Updated",
  "weight": 35.0,
  "dropLowestN": 0,
  "displayOrder": 2
}
```

**Verify**

- [ ] Status 200.
- [ ] Values changed in response payload.
- [ ] audit event `ASSIGNMENT_GROUP_UPDATED` logged.
- [ ] `courseGradeGroups` evicted.
- [ ] if name changed, assignment detail cache eviction path executed.

### 3.4 Instructor deletes empty custom group

**User:** sarah.johnson@university.edu  
**HTTP Method:** DELETE  
**Endpoint:** /api/v1/assignment-groups/{id}

**Verify**

- [ ] Status 200.
- [ ] Message indicates group deleted.
- [ ] audit event `ASSIGNMENT_GROUP_DELETED` logged.
- [ ] Subsequent GET list no longer includes deleted group.

### 3.5 Instructor moves assignment to another group

**User:** sarah.johnson@university.edu  
**HTTP Method:** PATCH  
**Endpoint:** /api/v1/assignments/{assignmentId}/group

**Request Body**

```json
{
  "groupId": "grpTarget123"
}
```

**Verify**

- [ ] Status 200.
- [ ] Response contains `assignmentId`, `newGroupId`, `newGroupName`.
- [ ] Assignment appears under new group on list endpoint.
- [ ] audit event `ASSIGNMENT_MOVED_TO_GROUP` logged.
- [ ] assignment and group caches are evicted.

---

## 4. Endpoint Test Cases

### 4.1 POST /api/v1/courses/{courseId}/assignment-groups

1. ✅ Valid instructor payload returns 201.
2. ✅ ADMIN can create group for target course.
3. ✅ SYSTEM_ADMIN can create group for target course.
4. ✅ STUDENT receives 403.
5. ✅ `name` blank returns 400 `VALIDATION_ERROR`.
6. ✅ Missing body returns 400 `INVALID_REQUEST`.
7. ✅ `weight=-1` returns 400 `INVALID_GROUP_WEIGHT`.
8. ✅ `weight=101` returns 400 `INVALID_GROUP_WEIGHT`.
9. ✅ `dropLowestN=-1` returns 400 `DROP_LOWEST_EXCEEDS_GROUP_SIZE`.
10. ✅ Invalid `courseId` hash returns 400 `INVALID_ID`.
11. ✅ Nonexistent course returns 404 `NOT_FOUND`.
12. ✅ Weight total != 100 still returns 201 with warning.
13. ✅ Audit log record exists after success.
14. ✅ Group cache is evicted after success.

### 4.2 GET /api/v1/courses/{courseId}/assignment-groups

1. ✅ Instructor receives 200 with group list.
2. ✅ Enrolled student receives 200 with group list.
3. ✅ Admin receives 200 with group list.
4. ✅ System admin receives 200 with group list.
5. ✅ Non-enrolled student returns 403 `FORBIDDEN`.
6. ✅ Invalid course hash returns 400 `INVALID_ID`.
7. ✅ Nonexistent course returns 404 `NOT_FOUND`.
8. ✅ Empty custom groups still return `Uncategorized`.
9. ✅ Group order follows `displayOrder`.
10. ✅ Assignment summaries are present when assignments exist.
11. ✅ `totalConfiguredWeight` field present.
12. ✅ `weightWarning` null when total exactly 100.
13. ✅ `weightWarning` populated when total != 100.
14. ✅ Repeated request benefits from cache path behavior.

### 4.3 PUT /api/v1/assignment-groups/{id}

1. ✅ Valid update returns 200.
2. ✅ ADMIN update returns 200.
3. ✅ SYSTEM_ADMIN update returns 200.
4. ✅ STUDENT update returns 403.
5. ✅ Invalid group hash returns 400 `INVALID_ID`.
6. ✅ Group not found returns 404 `NOT_FOUND`.
7. ✅ `weight=-1` returns 400 `INVALID_GROUP_WEIGHT`.
8. ✅ `weight=101` returns 400 `INVALID_GROUP_WEIGHT`.
9. ✅ `dropLowestN=-1` returns 400 `DROP_LOWEST_EXCEEDS_GROUP_SIZE`.
10. ✅ `dropLowestN` >= assignment count returns 400 `DROP_LOWEST_EXCEEDS_GROUP_SIZE`.
11. ✅ Name change triggers assignment-detail cache eviction path.
12. ✅ Audit event `ASSIGNMENT_GROUP_UPDATED` captured.

### 4.4 DELETE /api/v1/assignment-groups/{id}

1. ✅ Delete empty custom group returns 200.
2. ✅ Delete uncategorized group returns 409 `CANNOT_DELETE_UNCATEGORIZED`.
3. ✅ Delete non-empty group returns 409 `GROUP_NOT_EMPTY`.
4. ✅ STUDENT delete returns 403.
5. ✅ Invalid group hash returns 400 `INVALID_ID`.
6. ✅ Missing group returns 404 `NOT_FOUND`.
7. ✅ Audit event logged only on successful delete.
8. ✅ Cache eviction occurs on successful delete.

### 4.5 PATCH /api/v1/assignments/{id}/group

1. ✅ Valid move returns 200.
2. ✅ ADMIN valid move returns 200.
3. ✅ SYSTEM_ADMIN valid move returns 200.
4. ✅ STUDENT move returns 403.
5. ✅ Invalid assignment hash returns 400 `INVALID_ID`.
6. ✅ Invalid group hash returns 400 `INVALID_ID`.
7. ✅ Missing `groupId` body key returns 400 `INVALID_REQUEST`.
8. ✅ Assignment not found returns 404 `NOT_FOUND`.
9. ✅ Group not found returns 404 `NOT_FOUND`.
10. ✅ Cross-course target group returns 400 `GROUP_NOT_IN_COURSE`.
11. ✅ Audit event `ASSIGNMENT_MOVED_TO_GROUP` logged on success.
12. ✅ `assignmentDetail` and `courseGradeGroups` cache paths evicted on success.

---

## 5. Error Code Matrix

| Code                           | HTTP | Trigger                                      |
| ------------------------------ | ---- | -------------------------------------------- |
| INVALID_REQUEST                | 400  | Missing/invalid JSON body                    |
| VALIDATION_ERROR               | 400  | Bean validation failures                     |
| INVALID_ID                     | 400  | Invalid hashid in path/body                  |
| INVALID_GROUP_WEIGHT           | 400  | Weight outside 0..100                        |
| DROP_LOWEST_EXCEEDS_GROUP_SIZE | 400  | Invalid `dropLowestN` constraints            |
| GROUP_NOT_IN_COURSE            | 400  | Assignment move target from different course |
| FORBIDDEN                      | 403  | Role/ownership denial                        |
| NOT_FOUND                      | 404  | Course/group/assignment/user missing         |
| CANNOT_DELETE_UNCATEGORIZED    | 409  | Attempt to delete uncategorized group        |
| GROUP_NOT_EMPTY                | 409  | Attempt to delete group with assignments     |

---

## 6. Postman Workflow (Suggested)

### Workflow A - Setup

1. Login as instructor.
2. Create a test course.
3. Create 2 assignments in that course.
4. Verify course has auto-created uncategorized group via GET list.

### Workflow B - Group lifecycle

1. Create `Projects` group.
2. Move one assignment to `Projects`.
3. Update `Projects` weight.
4. Attempt invalid `dropLowestN` update and confirm 400.
5. Attempt to delete `Projects` while non-empty and confirm 409.
6. Move assignment back to uncategorized.
7. Delete `Projects` and confirm 200.

### Workflow C - Permission and security checks

1. Repeat create/delete endpoints as student and confirm 403.
2. Attempt invalid hash ids and confirm 400.
3. Attempt cross-course move and confirm 400 `GROUP_NOT_IN_COURSE`.

---

## 7. Performance and Cache Checks

1. Repeat GET list to validate cache hit behavior on stable data.
2. Trigger create/update/delete/move and verify cache invalidation effects on subsequent reads.
3. Confirm no stale group composition after assignment move.

---

## 8. Audit Verification Checklist

- [ ] `ASSIGNMENT_GROUP_CREATED` exists for create flow.
- [ ] `ASSIGNMENT_GROUP_UPDATED` exists for update flow.
- [ ] `ASSIGNMENT_GROUP_DELETED` exists for delete success flow.
- [ ] `ASSIGNMENT_MOVED_TO_GROUP` exists for move flow.
- [ ] Failed operations do not emit success audit actions.

---

## 9. Data Integrity Checks

- [ ] DB check constraints enforce weight bounds.
- [ ] DB check constraints enforce non-negative `drop_lowest_n`.
- [ ] `assignments.group_id` remains not-null after move operations.
- [ ] `Uncategorized` remains undeletable via API.

---

## 10. Verification Sources

- `src/main/java/com/reviewflow/controller/AssignmentGroupController.java`
- `src/main/java/com/reviewflow/service/AssignmentGroupService.java`
- `src/main/java/com/reviewflow/exception/GlobalExceptionHandler.java`
- `src/main/resources/db/migration/V21__create_assignment_groups.sql`
- `src/main/java/com/reviewflow/config/CacheConfig.java`

## 11. Runtime Alignment Follow-up

Current docs reflect the intended stable contract.
Runtime alignment for GET enrollment enforcement and typed move DTO validation is tracked in `PRD_16_assignment_groups_runtime_contract_alignment.md`.
