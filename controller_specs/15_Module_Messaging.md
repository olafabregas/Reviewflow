# ReviewFlow — Module 15: Messaging (stub)

> **Controller:** `com.reviewflow.messaging.controller.MessagingController`  
> **PRD:** [PRD_18_messaging](../Features/PRD_18_messaging.md) · **Migration:** V32  
> **Ship state:** [MASTER_PROJECT_SUMMARY.md](../orchestration/MASTER_PROJECT_SUMMARY.md) §5–§6

Minimal route table from code — expand checklists when QA needs full coverage.

| Method | Path | Roles (summary) |
|---|---|---|
| POST | `/api/v1/courses/{courseId}/conversations` | STUDENT, INSTRUCTOR |
| GET | `/api/v1/courses/{courseId}/conversations` | STUDENT, INSTRUCTOR |
| POST | `/api/v1/conversations/{conversationId}/messages` | STUDENT, INSTRUCTOR (multipart) |
| GET | `/api/v1/conversations/{conversationId}/messages` | STUDENT, INSTRUCTOR |
| PATCH | `/api/v1/conversations/{conversationId}/read` | STUDENT, INSTRUCTOR |
| PATCH | `/api/v1/messages/{messageId}` | STUDENT, INSTRUCTOR |
| DELETE | `/api/v1/messages/{messageId}` | STUDENT, INSTRUCTOR, SYSTEM_ADMIN |
| GET | `/api/v1/conversations/unread-count` | STUDENT, INSTRUCTOR |

Course-scoped moderation and system-admin flows may also live on `SystemController` — see PRD-18. Standard envelope: [00_Global_Rules_and_Reference.md](./00_Global_Rules_and_Reference.md).
