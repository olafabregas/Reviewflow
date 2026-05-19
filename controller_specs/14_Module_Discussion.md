# ReviewFlow — Module 14: Discussions (stub)

> **Controller:** `com.reviewflow.discussion.controller.DiscussionController`  
> **PRD:** [PRD_17_discussion_forum](../Features/PRD_17_discussion_forum.md) · **Migration:** V34  
> **Ship state:** [MASTER_PROJECT_SUMMARY.md](../orchestration/MASTER_PROJECT_SUMMARY.md) §5–§6

Minimal route table from code — expand checklists when QA needs full coverage.

| Method | Path | Roles (summary) |
|---|---|---|
| POST | `/api/v1/courses/{courseId}/discussions` | INSTRUCTOR, ADMIN, SYSTEM_ADMIN |
| PATCH | `/api/v1/discussions/{discussionId}/publish` | INSTRUCTOR, ADMIN, SYSTEM_ADMIN |
| GET | `/api/v1/courses/{courseId}/discussions` | STUDENT+ |
| GET | `/api/v1/discussions/{discussionId}` | STUDENT+ |
| DELETE | `/api/v1/discussions/{discussionId}` | INSTRUCTOR, ADMIN, SYSTEM_ADMIN |
| POST | `/api/v1/discussions/{discussionId}/posts` | STUDENT+ |
| GET | `/api/v1/discussions/{discussionId}/posts` | STUDENT+ |
| PUT | `/api/v1/discussion-posts/{postId}` | STUDENT+ |
| DELETE | `/api/v1/discussion-posts/{postId}` | STUDENT+ (204) |
| PATCH | `/api/v1/discussion-posts/{postId}/pin` | INSTRUCTOR, ADMIN, SYSTEM_ADMIN |
| GET | `/api/v1/discussions/{discussionId}/not-posted` | INSTRUCTOR, ADMIN, SYSTEM_ADMIN |

All IDs are Hashids in path variables. Standard envelope: see [00_Global_Rules_and_Reference.md](./00_Global_Rules_and_Reference.md).
