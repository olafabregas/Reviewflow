# TEST_SPEC_05_Team.md

## Team Module Test Specification

**Module**: Team Management  
**Controllers**: TeamController  
**Endpoints**: 10  
**Last Updated**: Post-Architecture-Fix Phase 2  
**Test Coverage**: 50+ test cases

---

## 1. Endpoint Summary

| #   | Method | Endpoint                                          | Description                     | Role                                         |
| --- | ------ | ------------------------------------------------- | ------------------------------- | -------------------------------------------- |
| 1   | GET    | `/api/v1/assignments/{assignmentId}/teams`        | List teams for assignment       | INSTRUCTOR, ADMIN, SYSTEM_ADMIN              |
| 2   | POST   | `/api/v1/assignments/{assignmentId}/teams`        | Create team                     | INSTRUCTOR, ADMIN, SYSTEM_ADMIN              |
| 3   | POST   | `/api/v1/assignments/{assignmentId}/teams/assign` | Assign students to teams (bulk) | INSTRUCTOR, ADMIN, SYSTEM_ADMIN              |
| 4   | GET    | `/api/v1/teams/{id}`                              | Get team details                | Team member, INSTRUCTOR, ADMIN, SYSTEM_ADMIN |
| 5   | PUT    | `/api/v1/teams/{id}`                              | Update team (name, max size)    | Team lead, INSTRUCTOR, ADMIN, SYSTEM_ADMIN   |
| 6   | POST   | `/api/v1/teams/{id}/invite`                       | Invite student to team          | Team lead, INSTRUCTOR, ADMIN, SYSTEM_ADMIN   |
| 7   | PATCH  | `/api/v1/team-members/{id}/respond`               | Accept/reject invite            | Invited student                              |
| 8   | DELETE | `/api/v1/teams/{id}/members/{userId}`             | Remove member from team         | Team lead, INSTRUCTOR, ADMIN, SYSTEM_ADMIN   |
| 9   | POST   | `/api/v1/teams/{id}/lock`                         | Lock team (finalize members)    | INSTRUCTOR, ADMIN, SYSTEM_ADMIN              |
| 10  | GET    | `/api/v1/teams/{id}/submissions`                  | Get team submissions            | Team member, INSTRUCTOR, ADMIN, SYSTEM_ADMIN |

---

## 2. Permission Matrix

**SYSTEM_ADMIN**: All endpoints (audit events tracked)  
**ADMIN**: All endpoints (audit events tracked)  
**INSTRUCTOR**: Create, manage, lock teams; invite/remove members  
**STUDENT**: Accept invites, view own team, respond to invitations (limited)

### Key Rules

- Teams only exist if assignment has `teamSize > 1` or `teamSize = 0` (flexible)
- Team leads manage invitations until team is locked
- Students can only see details of their own team
- Once locked, team members cannot be changed
- Invitations auto-expire after 7 days (configurable)

---

## 3. Team Lifecycle States

```
INITIAL -> OPEN -> LOCKED -> DISBANDED
```

| State     | Members Changeable | Can Invite | Can Submit        |
| --------- | ------------------ | ---------- | ----------------- |
| INITIAL   | Yes                | Yes        | No                |
| OPEN      | Yes                | Yes        | Yes (provisional) |
| LOCKED    | No                 | No         | Yes               |
| DISBANDED | N/A                | N/A        | No                |

---

## 4. Endpoint Test Cases

### 4.1 List Teams for Assignment

**Endpoint**: `GET /api/v1/assignments/{assignmentId}/teams`

**Test Cases**:

1. ✅ Instructor lists teams (200 OK)
2. ✅ Student cannot list teams (403 Forbidden)
3. ✅ Pagination: page, size work correctly
4. ✅ Empty team list (200 OK, empty array)
5. ✅ Filter by team status: INITIAL, OPEN, LOCKED
6. ✅ Non-existent assignment (404 Not Found)
7. ✅ ADMIN lists teams from any course (200 OK)
8. ✅ SYSTEM_ADMIN lists teams (200 OK)
9. ✅ Audit event: TEAM_LIST_VIEWED -> Logged (ADMIN, SYSTEM_ADMIN only)
10. ✅ Response includes member counts

### 4.2 Create Team

**Endpoint**: `POST /api/v1/assignments/{assignmentId}/teams`

**Request Body**:

```json
{
  "name": "Awesome Developers",
  "maxMembers": 4,
  "initialMembers": ["user_id_1", "user_id_2"]
}
```

**Test Cases**:

1. ✅ Instructor creates team (201 Created)
2. ✅ Student cannot create team (403 Forbidden)
3. ✅ Team name is required (400 Bad Request if empty)
4. ✅ Max members must be >= 2 (400 Bad Request)
5. ✅ Duplicate team name in assignment (400 Bad Request)
6. ✅ Initial members must be enrolled in course (400 Bad Request)
7. ✅ Audit event: TEAM_CREATED -> teamId, name, memberCount logged
8. ✅ Response includes team_id (hashid)
9. ✅ Default status: INITIAL
10. ✅ No duplicate members in initial members (400 Bad Request)

### 4.3 Assign Students to Teams (Bulk)

**Endpoint**: `POST /api/v1/assignments/{assignmentId}/teams/assign`

**Request Body**:

```json
{
  "assignments": [
    { "teamId": "abc123", "userIds": ["user_1", "user_2", "user_3"] },
    { "teamId": "def456", "userIds": ["user_4", "user_5"] }
  ]
}
```

**Test Cases**:

1. ✅ Instructor bulk assigns students (200 OK)
2. ✅ Student cannot bulk assign (403 Forbidden)
3. ✅ Invalid user IDs (400 Bad Request)
4. ✅ User already in another team (400 Bad Request - "User already assigned")
5. ✅ Exceeds max team size (400 Bad Request)
6. ✅ Non-existent team (404 Not Found)
7. ✅ Atomic operation: all or nothing (rollback on any error)
8. ✅ Audit event: TEAM_MEMBER_ASSIGNED -> teamId, userIds, assignmentId
9. ✅ Creates audit event per user assigned
10. ✅ Non-enrolled student assignment (400 Bad Request)

### 4.4 Get Team Details

**Endpoint**: `GET /api/v1/teams/{id}`

**Test Cases**:

1. ✅ Team member gets team details (200 OK)
2. ✅ Instructor gets team details (200 OK)
3. ✅ Non-member student cannot access (403 Forbidden)
4. ✅ Response includes all members and their roles
5. ✅ Response includes pending invitations
6. ✅ Response includes team status (INITIAL, OPEN, LOCKED)
7. ✅ Response includes submission status (if any)
8. ✅ Non-existent team (404 Not Found)
9. ✅ Audit event: TEAM_VIEWED -> Logged (ADMIN, SYSTEM_ADMIN only)
10. ✅ Invalid team hash (400 Bad Request)

### 4.5 Update Team

**Endpoint**: `PUT /api/v1/teams/{id}`

**Request Body**:

```json
{
  "name": "Updated Team Name",
  "maxMembers": 5
}
```

**Test Cases**:

1. ✅ Team lead updates team info (200 OK)
2. ✅ Non-lead member cannot update (403 Forbidden)
3. ✅ Cannot update if team is locked (403 Forbidden)
4. ✅ Name must be unique in assignment (400 Bad Request)
5. ✅ New max members must accommodate current size (400 Bad Request)
6. ✅ Audit event: TEAM_UPDATED -> teamId, changes logged
7. ✅ ADMIN can update any team (200 OK)
8. ✅ SYSTEM_ADMIN can update any team (200 OK)
9. ✅ Empty name (400 Bad Request)
10. ✅ Response includes updated timestamp

### 4.6 Invite Student to Team

**Endpoint**: `POST /api/v1/teams/{id}/invite`

**Request Body**:

```json
{
  "userIds": ["user_1", "user_2"]
}
```

**Test Cases**:

1. ✅ Team lead invites student (201 Created)
2. ✅ Non-lead cannot invite (403 Forbidden)
3. ✅ Cannot invite if team locked (403 Forbidden)
4. ✅ User already in team (400 Bad Request)
5. ✅ User already invited (duplicate invite - 409 Conflict)
6. ✅ User not enrolled in course (400 Bad Request)
7. ✅ Exceeds max team size (400 Bad Request)
8. ✅ Non-enrolled user (400 Bad Request)
9. ✅ Audit event: TEAM_INVITATION_SENT -> inviteeId, senderId
10. ✅ Invited user receives notification (async)
11. ✅ Invitation expires after 7 days (auto-clean)
12. ✅ ADMIN can invite on any team (201 Created)

### 4.7 Respond to Invitation

**Endpoint**: `PATCH /api/v1/team-members/{id}/respond`

**Request Body**:

```json
{
  "response": "ACCEPT"
}
```

**Test Cases**:

1. ✅ Student accepts invitation (200 OK)
2. ✅ Student rejects invitation (200 OK)
3. ✅ Accept adds user to team (200 OK, status changes)
4. ✅ Reject removes invitation (200 OK)
5. ✅ Non-invited student cannot respond (403 Forbidden)
6. ✅ Expired invitation (400 Bad Request - "Invitation expired")
7. ✅ Already accepted (409 Conflict)
8. ✅ Team is full after accept (400 Bad Request)
9. ✅ Audit event: TEAM_INVITATION_ACCEPTED -> userId, teamId
10. ✅ Response: "ACCEPT" or "REJECT" only (400 Bad Request - invalid response)
11. ✅ Invitation response idempotent (same response twice -> same result)
12. ✅ Accepting notification sent to team lead

### 4.8 Remove Team Member

**Endpoint**: `DELETE /api/v1/teams/{id}/members/{userId}`

**Test Cases**:

1. ✅ Team lead removes member (204 No Content)
2. ✅ Non-lead cannot remove (403 Forbidden)
3. ✅ Cannot remove from locked team (403 Forbidden)
4. ✅ Remove self (204 No Content - self-removal allowed)
5. ✅ Remove non-existent member (404 Not Found)
6. ✅ Remove last member (soft-delete team if no members)
7. ✅ Cannot remove if submission exists (403 Forbidden)
8. ✅ Audit event: TEAM_MEMBER_REMOVED -> memberId, removedBy
9. ✅ ADMIN can remove from any team (204 No Content)
10. ✅ SYSTEM_ADMIN can remove from any team (204 No Content)
11. ✅ Non-existent team (404 Not Found)
12. ✅ Removed user can reapply via new invite

### 4.9 Lock Team

**Endpoint**: `POST /api/v1/teams/{id}/lock`

**Test Cases**:

1. ✅ Instructor locks team (200 OK)
2. ✅ Student cannot lock team (403 Forbidden)
3. ✅ Already locked team (409 Conflict - "Team already locked")
4. ✅ Requires minimum 1 member (400 Bad Request if empty)
5. ✅ All pending invitations auto-rejected on lock
6. ✅ Status changes to LOCKED (200 OK)
7. ✅ Audit event: TEAM_LOCKED -> teamId, memberCount
8. ✅ No more invitations accepted after lock (403 Forbidden)
9. ✅ ADMIN can lock any team (200 OK)
10. ✅ SYSTEM_ADMIN can lock any team (200 OK)
11. ✅ Response includes final member list
12. ✅ Locked teams cannot be modified (PUT fails)

### 4.10 Get Team Submissions

**Endpoint**: `GET /api/v1/teams/{id}/submissions`

**Test Cases**:

1. ✅ Team member views own submissions (200 OK)
2. ✅ Instructor views team submissions (200 OK)
3. ✅ Non-member cannot view (403 Forbidden)
4. ✅ Empty submissions list (200 OK, empty array)
5. ✅ Multiple submissions for different assignments
6. ✅ Pagination works
7. ✅ Filter by submission status
8. ✅ Includes evaluation status (if graded)
9. ✅ Non-existent team (404 Not Found)
10. ✅ Audit event: TEAM_SUBMISSIONS_VIEWED -> Logged (ADMIN, SYSTEM_ADMIN only)

---

## 5. Audit Events

| Event                    | Description              | Triggered By                          |
| ------------------------ | ------------------------ | ------------------------------------- |
| TEAM_CREATED             | Team created             | POST endpoint                         |
| TEAM_UPDATED             | Team name/size modified  | PUT endpoint                          |
| TEAM_LOCKED              | Team finalized           | POST lock endpoint                    |
| TEAM_MEMBER_ASSIGNED     | Student assigned to team | Bulk assign endpoint                  |
| TEAM_MEMBER_REMOVED      | Member removed from team | DELETE member endpoint                |
| TEAM_INVITATION_SENT     | Invitation created       | POST invite endpoint                  |
| TEAM_INVITATION_ACCEPTED | Invitation accepted      | PATCH respond (ACCEPT)                |
| TEAM_INVITATION_REJECTED | Invitation rejected      | PATCH respond (REJECT)                |
| TEAM_VIEWED              | Team details accessed    | GET endpoint (ADMIN/SYSTEM only)      |
| TEAM_LIST_VIEWED         | Teams list accessed      | GET list endpoint (ADMIN/SYSTEM only) |

---

## 6. Real Test User Credentials

| User         | Email                        | Role         | Purpose               |
| ------------ | ---------------------------- | ------------ | --------------------- |
| System Admin | main_sysadmin@reviewflow.com | SYSTEM_ADMIN | Override permissions  |
| Admin User   | humberadmin@reviewflow.com   | ADMIN        | Team admin operations |
| Instructor 1 | sarah.johnson@university.edu | INSTRUCTOR   | Create/manage teams   |
| Student 1    | jane.smith@university.edu    | STUDENT      | Team lead             |
| Student 2    | marcus.chen@university.edu   | STUDENT      | Accept invites        |
| Student 3    | priya.patel@university.edu   | STUDENT      | Team member           |

---

## 7. End-to-End Postman Workflows

### Workflow 1: Team Creation & Membership

```json
{
  "workflowName": "Team Lifecycle",
  "steps": [
    {
      "step": 1,
      "description": "Instructor creates team",
      "endpoint": "POST /teams"
    },
    {
      "step": 2,
      "description": "Team lead invites students",
      "endpoint": "POST /teams/invite"
    },
    {
      "step": 3,
      "description": "Students accept invitations",
      "endpoint": "PATCH /team-members/respond"
    },
    {
      "step": 4,
      "description": "Instructor locks team",
      "endpoint": "POST /teams/lock"
    },
    {
      "step": 5,
      "description": "Verify team is locked",
      "endpoint": "GET /teams",
      "expectedStatus": 200
    }
  ]
}
```

### Workflow 2: Bulk Assignment

```json
{
  "workflowName": "Bulk Team Assignment",
  "description": "Assign multiple students to multiple teams at once"
}
```

---

## 8. Business Logic Rules

1. **Team Isolation**: Students only see their own team members and pending invites
2. **Lead Assignment**: First team creator is the lead (auto-assigned)
3. **Invitation Expiry**: Invitations expire after 7 days (auto-clean on next GET)
4. **Cascade Delete**: Deleting assignment soft-deletes all teams
5. **Submission Lock**: Cannot remove team members after submission created
6. **Size Validation**: Current members <= Max members at all times

---

## 9. Error Handling

| Scenario                 | Status | Resolution                                      |
| ------------------------ | ------ | ----------------------------------------------- |
| Team full                | 400    | Increase maxMembers or remove inactive          |
| Invitation expired       | 400    | Instructor must send new invite                 |
| User already in team     | 409    | Cannot be in multiple teams for same assignment |
| Team locked              | 403    | Unlock requires ADMIN (INSTRUCTOR cannot)       |
| Non-enrolled user invite | 400    | Only invite enrolled students                   |

---

## 10. Performance & Caching

| Operation        | Cache TTL | Invalidation                 |
| ---------------- | --------- | ---------------------------- |
| List teams       | 5 minutes | Any POST/PUT/DELETE on teams |
| Team details     | 5 minutes | Any update to team           |
| Team submissions | 2 minutes | New submission created       |

---

## 11. Security Considerations

1. Students cannot create teams (INSTRUCTOR only)
2. Cross-team invite blocking (no poaching)
3. Audit trail for all membership changes
4. Team lead role verification on sensitive ops
5. Invitation tokens include expiry + signature
