# ReviewFlow — Module 4: Teams
> Controller: `TeamController.java`
> Base path: `/api/v1`

---

## 4.1 GET /assignments/{id}/teams

### Must Have
- [ ] STUDENT: returns only their own team for this assignment
- [ ] INSTRUCTOR: returns ALL teams for this assignment
- [ ] ADMIN: returns ALL teams
- [ ] Each team includes: `id, name, isLocked, memberCount, members: [{ userId, firstName, lastName, email, status }]`

### Responses
- [ ] `200 OK` — team(s) returned
- [ ] `401 Unauthorized`
- [ ] `403 Forbidden` — student not enrolled in course
- [ ] `404 Not Found` — assignment doesn't exist

### Edge Cases
- [ ] Student not on any team → `200` with empty array (not `404`)
- [ ] Instructor sees all teams including locked ones → `200`

---

## 4.2 POST /assignments/{id}/teams

### Must Have
- [ ] STUDENT only
- [ ] Student must be enrolled in the course
- [ ] Assignment must be published
- [ ] `teamLockAt` must not have passed
- [ ] Student must NOT already be on a team for this assignment
- [ ] Team name must be unique per assignment
- [ ] Creating student is auto-added as first ACCEPTED member
- [ ] Body: `{ "name": "Team Alpha" }`

### Responses
- [ ] `201 Created` — team created, creator added as ACCEPTED member
- [ ] `400 Bad Request` — name missing or blank
- [ ] `400 Bad Request` — student already on a team → `{ code: "ALREADY_IN_TEAM", message: "You are already a member of a team for this assignment" }`
- [ ] `403 Forbidden` — student not enrolled
- [ ] `404 Not Found` — assignment doesn't exist or not published
- [ ] `409 Conflict` — team name already taken for this assignment → `{ code: "TEAM_NAME_EXISTS" }`
- [ ] `409 Conflict` — `teamLockAt` has passed → `{ code: "TEAM_FORMATION_CLOSED", message: "Team formation is closed for this assignment" }`

### Edge Cases
- [ ] Create team after `teamLockAt` → `409 TEAM_FORMATION_CLOSED`
- [ ] Create team with name already used in same assignment → `409 TEAM_NAME_EXISTS`
- [ ] Same name in DIFFERENT assignment → `201` (allowed)
- [ ] Student already on team for this assignment → `400 ALREADY_IN_TEAM`

---

## 4.3 GET /teams/{id}

### Must Have
- [ ] STUDENT: only if they are a member of the team
- [ ] INSTRUCTOR: only if team belongs to their course
- [ ] ADMIN: always
- [ ] Returns: `{ id, name, isLocked, assignmentId, assignmentTitle, members: [{ userId, firstName, lastName, email, status, joinedAt }] }`

### Responses
- [ ] `200 OK`
- [ ] `403 Forbidden` — student not a member
- [ ] `404 Not Found`

---

## 4.4 PUT /teams/{id} ⭐ NEW

### Must Have
- [ ] STUDENT (team creator only)
- [ ] Only allows updating `name`
- [ ] Team must not be locked
- [ ] Name must still be unique per assignment

### Responses
- [ ] `200 OK` — name updated
- [ ] `400 Bad Request` — name blank or already taken
- [ ] `403 Forbidden` — not the team creator OR team is locked
- [ ] `404 Not Found`

---

## 4.5 POST /teams/{id}/invite

### Must Have
- [ ] STUDENT (team creator only)
- [ ] Body: `{ "inviteeEmail": "student@university.edu" }`
- [ ] Team must not be locked
- [ ] Team must not be full (member count < `maxTeamSize`)
- [ ] Invitee must be enrolled in the same course
- [ ] Invitee must NOT already be on a team for this assignment (any team)
- [ ] Invitee must NOT already have a pending invite for this team
- [ ] Creates `team_member` record with `status = PENDING`
- [ ] Sends `TEAM_INVITE` notification to invitee

### Responses
- [ ] `200 OK` — invite sent, returns team member record with `status: PENDING`
- [ ] `400 Bad Request` — `inviteeEmail` missing
- [ ] `400 Bad Request` — invitee not enrolled in course → `{ code: "NOT_ENROLLED" }`
- [ ] `400 Bad Request` — invitee already on a team → `{ code: "ALREADY_IN_TEAM" }`
- [ ] `400 Bad Request` — invitee already has pending invite for this team → `{ code: "INVITE_ALREADY_SENT" }`
- [ ] `400 Bad Request` — team is full → `{ code: "TEAM_FULL", message: "Team has reached maximum size of X" }`
- [ ] `403 Forbidden` — not the team creator
- [ ] `403 Forbidden` — team is locked → `{ code: "TEAM_LOCKED" }`
- [ ] `404 Not Found` — team doesn't exist or invitee email not found in system

### Edge Cases
- [ ] Invite student not in system → `404`
- [ ] Invite student not enrolled in course → `400 NOT_ENROLLED`
- [ ] Invite student already on another team for same assignment → `400 ALREADY_IN_TEAM`
- [ ] Invite when team already has `maxTeamSize` ACCEPTED members → `400 TEAM_FULL`
- [ ] Invite after `teamLockAt` → `403 TEAM_LOCKED`
- [ ] Creator invites themselves → `400` with message "You cannot invite yourself"
- [ ] Duplicate invite to same student → `400 INVITE_ALREADY_SENT`

---

## 4.6 PATCH /team-members/{id}/respond

### Must Have
- [ ] STUDENT only (must be the invitee — not just any student)
- [ ] Body: `{ "accept": true }` or `{ "accept": false }`
- [ ] Team must not be locked
- [ ] If accepting: student must not already be on another team for this assignment
- [ ] If accepting: auto-decline ALL other pending invites for same assignment
- [ ] Updates `team_member` record status to `ACCEPTED` or `DECLINED`

### Responses
- [ ] `200 OK` — responded, returns updated team member record
- [ ] `400 Bad Request` — invite already responded to → `{ code: "ALREADY_RESPONDED" }`
- [ ] `400 Bad Request` — accepting but already on another team → `{ code: "ALREADY_IN_TEAM" }`
- [ ] `400 Bad Request` — team is full → `{ code: "TEAM_FULL" }`
- [ ] `403 Forbidden` — this invite does not belong to the current user
- [ ] `403 Forbidden` — team is locked → `{ code: "TEAM_LOCKED" }`
- [ ] `404 Not Found` — team member record doesn't exist

### Edge Cases
- [ ] Accept invite → status = ACCEPTED, all other pending invites for same assignment auto-declined → `200`
- [ ] Decline invite → status = DECLINED, other invites unaffected → `200`
- [ ] Accept invite but team now full (race condition) → `400 TEAM_FULL`
- [ ] Try to respond to someone else's invite → `403`
- [ ] Try to respond twice → `400 ALREADY_RESPONDED`

---

## 4.7 DELETE /teams/{id}/members/{userId}

### Must Have
- [ ] STUDENT (team creator only) OR INSTRUCTOR
- [ ] Team must not be locked
- [ ] Cannot remove the team creator (creator must disband the team instead)
- [ ] Removes the team member record

### Responses
- [ ] `200 OK` — member removed
- [ ] `400 Bad Request` — trying to remove the team creator → `{ code: "CANNOT_REMOVE_CREATOR" }`
- [ ] `403 Forbidden` — not the team creator or instructor
- [ ] `403 Forbidden` — team is locked
- [ ] `404 Not Found` — team or user not found, or user not in team

---

## 4.8 POST /assignments/{id}/teams/assign (Instructor bulk assign)

### Must Have
- [ ] INSTRUCTOR only
- [ ] Assigns unteamed students into teams automatically
- [ ] Body: `{ "strategy": "RANDOM" }` (only RANDOM required for now)
- [ ] Only assigns students who have NO team yet
- [ ] Respects `maxTeamSize` from the assignment
- [ ] Returns list of created/updated teams

### Responses
- [ ] `200 OK` — students assigned, returns created teams
- [ ] `400 Bad Request` — all students already have teams → `{ code: "ALL_ASSIGNED" }`
- [ ] `403 Forbidden` — not instructor for this course
- [ ] `404 Not Found` — assignment doesn't exist

---

## 4.9 POST /teams/{id}/lock ⭐ NEW

### Must Have
- [ ] INSTRUCTOR only
- [ ] Manually locks a team before `teamLockAt` date
- [ ] Sets `is_locked = true`
- [ ] Sends `TEAM_LOCKED` notification to all members
- [ ] Returns updated team

### Responses
- [ ] `200 OK` — team locked
- [ ] `400 Bad Request` — team already locked → `{ code: "ALREADY_LOCKED" }`
- [ ] `403 Forbidden` — not instructor for this course
- [ ] `404 Not Found`

---

## 4.10 GET /students/me/invites ⭐ NEW

### Must Have
- [ ] STUDENT only
- [ ] Returns ALL pending invites for current student across ALL assignments
- [ ] Each invite: `{ teamMemberId, teamId, teamName, assignmentId, assignmentTitle, courseCode, invitedByName, invitedAt }`
- [ ] Only returns `status = PENDING` records

### Responses
- [ ] `200 OK` — list of pending invites (empty array if none)
- [ ] `401 Unauthorized`
- [ ] `403 Forbidden` — not a STUDENT
