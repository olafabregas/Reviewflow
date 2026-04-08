# TEST_SPEC_08_Notification.md

## Notification Module Test Specification

**Module**: Notification Management  
**Controllers**: NotificationController  
**Endpoints**: 5  
**Last Updated**: Post-Architecture-Fix Phase 2  
**Test Coverage**: 35+ test cases

---

## 1. Endpoint Summary

| #   | Method | Endpoint                             | Description                        |
| --- | ------ | ------------------------------------ | ---------------------------------- |
| 1   | GET    | `/api/v1/notifications`              | List all notifications (paginated) |
| 2   | GET    | `/api/v1/notifications/unread-count` | Get count of unread                |
| 3   | PATCH  | `/api/v1/notifications/{id}/read`    | Mark single as read                |
| 4   | PATCH  | `/api/v1/notifications/read-all`     | Mark all as read                   |
| 5   | DELETE | `/api/v1/notifications/{id}`         | Delete notification                |

---

## 2. Permission Matrix

**SYSTEM_ADMIN**: View all users' notifications (admin ops)  
**ADMIN**: View all users' notifications (admin ops)  
**INSTRUCTOR**: View own + student notifications (course context)  
**STUDENT**: View own only

---

## 3. Notification Types

| Type                     | Description              | Triggered By        | Recipient         |
| ------------------------ | ------------------------ | ------------------- | ----------------- |
| ASSIGNMENT_PUBLISHED     | New assignment available | Assignment publish  | Enrolled students |
| SUBMISSION_RECEIVED      | Student submitted work   | Submission create   | Instructor        |
| EVALUATION_PUBLISHED     | Grades available         | Evaluation publish  | Student           |
| TEAM_INVITATION          | Invited to team          | Invite create       | Invited student   |
| TEAM_INVITATION_ACCEPTED | Team member accepted     | Invitation response | Team lead         |
| TEAM_LOCKED              | Team finalized           | Team lock           | Team members      |
| ANNOUNCEMENT_CREATED     | Course announcement      | Announcement create | Enrolled students |
| GRADE_EXTENSION_GRANTED  | Deadline extended        | Extension approve   | Affected student  |

---

## 4. Endpoint Test Cases

### 4.1 List Notifications

**Endpoint**: `GET /api/v1/notifications?page=1&size=20`

**Test Cases**:

1. ✅ Get own notifications (200 OK)
2. ✅ Unread notifications appear first
3. ✅ Pagination works (page, size)
4. ✅ Empty list (200 OK, empty array)
5. ✅ Filter by read status: true/false
6. ✅ Filter by type (e.g., ASSIGNMENT_PUBLISHED)
7. ✅ Sort by date descending (newest first)
8. ✅ Response includes all notification metadata
9. ✅ ADMIN views any user's notifications (200 OK)
10. ✅ SYSTEM_ADMIN views any notifications (200 OK)

### 4.2 Get Unread Count

**Endpoint**: `GET /api/v1/notifications/unread-count`

**Test Cases**:

1. ✅ User with unread notifications (200 OK - count > 0)
2. ✅ User with no unread (200 OK - count = 0)
3. ✅ Response: {"unreadCount": 5}
4. ✅ Count updates after mark-read
5. ✅ ADMIN gets count for any user (200 OK)
6. ✅ SYSTEM_ADMIN gets count for any user (200 OK)

### 4.3 Mark Single as Read

**Endpoint**: `PATCH /api/v1/notifications/{id}/read`

**Test Cases**:

1. ✅ Mark unread notification as read (200 OK)
2. ✅ Already read notification (idempotent - 200 OK)
3. ✅ Non-existent notification (404 Not Found)
4. ✅ Other user's notification (403 Forbidden)
5. ✅ Response includes updated read status
6. ✅ Unread count decreases (verify with separate call)
7. ✅ Timestamp updated
8. ✅ ADMIN marks any as read (200 OK)
9. ✅ SYSTEM_ADMIN marks any as read (200 OK)

### 4.4 Mark All as Read

**Endpoint**: `PATCH /api/v1/notifications/read-all`

**Test Cases**:

1. ✅ Mark multiple unread (200 OK - count updated)
2. ✅ All already read (200 OK - no change)
3. ✅ Response includes count of updated
4. ✅ Unread count becomes 0
5. ✅ Timestamps updated for all
6. ✅ ADMIN marks all for any user (200 OK)
7. ✅ SYSTEM_ADMIN marks all for any user (200 OK)

### 4.5 Delete Notification

**Endpoint**: `DELETE /api/v1/notifications/{id}`

**Test Cases**:

1. ✅ Delete notification (204 No Content)
2. ✅ Non-existent notification (404 Not Found)
3. ✅ Other user's notification (403 Forbidden)
4. ✅ Unread count doesn't change (read before delete)
5. ✅ Cannot retrieve after delete (404 on GET)
6. ✅ ADMIN deletes any (204 No Content)
7. ✅ SYSTEM_ADMIN deletes any (204 No Content)

---

## 5. Notification Triggers

**Assignment Published**: Sent to all enrolled students  
**Submission Received**: Sent to instructor(s) of course  
**Evaluation Published**: Sent to submission owner  
**Team Invitation**: Sent to invited user  
**Announcement**: Sent to all course members

---

## 6. Real Test Users

| User       | Email                        | Role       |
| ---------- | ---------------------------- | ---------- |
| Instructor | sarah.johnson@university.edu | INSTRUCTOR |
| Student 1  | jane.smith@university.edu    | STUDENT    |
| Student 2  | marcus.chen@university.edu   | STUDENT    |

---

## 7. End-to-End Postman Workflow

```json
{
  "workflowName": "Notification Management",
  "steps": [
    {
      "step": 1,
      "description": "Get unread count",
      "endpoint": "GET /unread-count"
    },
    {
      "step": 2,
      "description": "List all notifications",
      "endpoint": "GET /notifications"
    },
    {
      "step": 3,
      "description": "Mark one as read",
      "endpoint": "PATCH /notifications/{id}/read"
    },
    {
      "step": 4,
      "description": "Mark all as read",
      "endpoint": "PATCH /notifications/read-all"
    },
    {
      "step": 5,
      "description": "Verify count = 0",
      "endpoint": "GET /unread-count"
    }
  ]
}
```

---

## 8. Error Handling

| Scenario                  | Status           |
| ------------------------- | ---------------- |
| Non-existent notification | 404              |
| Already read              | 200 (idempotent) |
| Unauthorized access       | 403              |
| Other user's notification | 403              |

---

## 9. Performance & Caching

| Operation         | Cache TTL  |
| ----------------- | ---------- |
| Notification list | 1 minute   |
| Unread count      | 30 seconds |

---

## 10. Known Limitations

- Archived/deleted notifications not recoverable
- No notification preferences UI
- Bulk delete not supported
- Email notifications sent async (may delay)
