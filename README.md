# ReviewFlow

> A production-grade university project submission and peer evaluation platform built for real academic workflows.

**Status:** Backend complete (PRDs 1–18) · Infrastructure live on AWS · Frontend architecture locked, implementation not started

**Domain:** [reviewflowlms.com](https://reviewflowlms.com) · **Staging:** [staging.reviewflowlms.com](https://staging.reviewflowlms.com)

**Live demo:** Coming soon

---

## What It Is

ReviewFlow is a full-stack LMS handling the end-to-end lifecycle of university academic assessments — team formation, file submission, rubric-based evaluation, grade management, real-time notifications, course messaging, and discussion forums. Four roles — Student, Instructor, Admin, and System Admin — each have distinct workflows and permission boundaries enforced at every layer of the stack.

---

## Tech Stack

| Layer | Technology | Notes |
|---|---|---|
| Backend | Spring Boot 4, Java 21 | Port 8081 (staging) / 8082 (prod) |
| Database | MySQL 8 | Flyway V1–V27, 20+ tables |
| Auth | JWT in HTTP-only cookies | Refresh rotation, token fingerprinting, SameSite=Strict |
| File storage | AWS S3 | `reviewflow-storage`, `ca-central-1`, pre-signed URLs for submissions/PDFs |
| Real-time | WebSocket — STOMP over SockJS | `/ws` endpoint, user-scoped queues |
| App caching | Caffeine | 13 Spring @Cacheable caches — per-JVM, Redis-ready |
| Distributed state | Redis 7 | Rate limiting (Bucket4j), grade aggregates, job state, import locks, OAuth state, token versions |
| File security | `FileSecurityValidator` | 3-stage pipeline (extension → MIME → size) |
| ID obfuscation | Hashids | 8-char opaque IDs on all external endpoints |
| PDF generation | OpenPDF | Evaluation report export |
| Email | JavaMailSender + Resend SMTP | `noreply@reviewflowlms.com`, Thymeleaf HTML templates, async delivery |
| Logging | Logback + logstash-logback-encoder | JSON structured logs, MDC correlation, X-Trace-Id |
| Monitoring | AWS CloudWatch | Log groups, 10 alarms, SNS alerting, dashboard |
| API docs | SpringDoc OpenAPI / Swagger UI | `/swagger-ui/index.html` |
| Containerisation | Docker + Docker Compose | MySQL + Redis + App |
| Infrastructure | Terraform | 6 modules, S3 state backend, DynamoDB locking |
| CI/CD | GitHub Actions | ci.yml · cd.yml · nightly.yml |
| DNS / TLS | Cloudflare | `reviewflowlms.com`, SPF/DKIM/DMARC configured |
| Frontend | React 18 + Vite + TypeScript | Not started — architecture locked |

---

## System Architecture

![ReviewFlow System Architecture](ARCHITECTURE_IMAGE_URL_HERE)

```
[Client — React SPA]
        ↓  HTTP-only cookies · WebSocket upgrade
[Security Layer — JWT Filter · Token Fingerprinting · Rate Limiter (Redis) · Security Headers]
        ↓
[REST API — 98 endpoints across 17 modules]
  Auth · Courses · Assignments · Assignment Groups · Modules
  Teams · Submissions · Evaluations · Instructor Scores · Grade Overview
  Discussions · Messaging · Notifications · Announcements · Extensions · Admin · System
        ↓
[Service Layer]
  Security:   FileSecurityValidator · HashidService · AuditService
  Business:   CourseService · AssignmentService · TeamService · SubmissionService
              EvaluationService · GradeCalculationService · GradeAggregateService
              DiscussionService · MessagingService · AsyncJobService · OAuthService
  Async:      NotificationEventListener · EmailEventListener · Schedulers
        ↓                              ↓
[Infrastructure]               [Cache Layer]
  MySQL 8  → primary DB          Caffeine  → 13 Spring @Cacheable caches (per-JVM)
  AWS S3   → file storage        Redis 7   → rate limits · grade blobs · job state
  Redis 7  → distributed state             import locks · OAuth state · token versions
  WebSocket → real-time push
  Resend   → async email
  CloudWatch → logs + alarms
```

### Cache Architecture

**Two distinct systems — different concerns, different owners:**

| Layer | Technology | What It Owns |
|---|---|---|
| App caches | Caffeine | adminStats, unreadCount, userCourses, assignmentDetail, courseGradeGroups, courseModules, gradeOverview, classStatistics, courseMaterials, courseDiscussions, discussionParticipation, csvImports, oauthState |
| Distributed state | Redis 7 | Rate limit buckets, grade aggregate blobs, CSV job progress, import locks, OAuth CSRF state, token version store, messaging pub/sub (optional) |

Grade overviews use **both** — Caffeine wraps the method via `@Cacheable`, Redis stores the computed grade blob inside that method via `GradeAggregateService`.

### S3 Key Structure

```
reviewflow-storage/
├── submissions/{hashedAssignmentId}/{hashedTeamOrStudentId}/v{n}/{filename}
├── pdfs/{hashedEvaluationId}/report.pdf
├── avatars/{hashedUserId}/avatar.{ext}        ← fixed URL + ?v= cache-bust, zero SDK calls at read
├── materials/{hashedCourseId}/{hashedMaterialId}/{filename}
└── messages/{hashedConversationId}/{hashedMessageId}/{filename}
```

### Async Thread Pools

| Pool | Core/Max | Used by |
|---|---|---|
| `notificationTaskExecutor` | 2/10 | `NotificationEventListener` |
| `emailTaskExecutor` | 2/10 | `EmailEventListener` |
| Spring `@Scheduled` | — | `DueDateReminderScheduler`, `TokenCleanupScheduler` |

---

## Features

### Student
- Enroll in courses and browse published assignments
- Form or join teams — invite teammates, accept or decline invitations
- Upload project submissions (ZIP/PDF) with automatic version tracking
- View rubric-based evaluations and instructor feedback once published
- Download evaluation reports as PDF
- Real-time notifications for invites, submissions, and published grades
- Course discussions and direct/team messaging

### Instructor
- Create and publish assignments with custom rubric criteria and weighted groups
- Lock teams at a configurable deadline
- Grade submissions using per-criterion scoring — draft/publish lifecycle
- Direct score entry or bulk CSV upload with dry-run preview
- Generate and deliver PDF evaluation reports
- Manage course modules, materials, announcements, and extension requests

### Admin
- Full user management — create, deactivate, reactivate accounts
- Platform-wide statistics: users, submissions, storage usage, role breakdown
- Audit log of all significant write actions across the system
- Course and instructor assignment management

### System Admin
- Real-time system dashboard (JVM, DB pool, cache stats, security events)
- Force logout any user, unlock teams, reopen evaluations
- Cache management with eviction controls
- Safe config view — secrets never exposed
- Max 5 accounts — DB seed only, never via API

---

## API Overview

**98 REST endpoints across 17 modules.** Full OpenAPI at `/swagger-ui/index.html` when running locally.

| Module | Key Endpoints |
|---|---|
| Auth | Login, logout, refresh, `/me`, WebSocket token |
| Courses | CRUD, enrollment, bulk enroll, roster |
| Assignments | CRUD, rubric management, global feed, submission type |
| Assignment Groups | Create/list/update/delete, move assignment to group |
| Modules | Create/list/update/delete/reorder, assign/unassign |
| Teams | Create, invite, respond, lock, update |
| Submissions | Upload (INDIVIDUAL/TEAM), version history, download, preview |
| Evaluations | Create, score, publish, draft gate, PDF generate/download |
| Instructor Scores | Create/update/publish/reopen, bulk CSV dry-run + commit |
| Grade Overview | Student overview, per-student instructor view, class roster/statistics |
| Discussions | Create prompt, post, reply, deadline enforcement |
| Messaging | Direct + team conversations, cursor-based pagination, attachments |
| Notifications | List, mark read, unread count, delete, mark all read |
| Announcements | Create, publish, delete, list by course, platform-wide |
| Extensions | Request, respond (approve/deny), list by assignment/student |
| Admin | User management, stats, audit log |
| System | Dashboard, cache stats/evict, config view, security events, force logout |

All responses follow the standard envelope:
```json
{ "success": true, "data": { ... }, "timestamp": "..." }
{ "success": false, "error": { "code": "...", "message": "..." }, "timestamp": "..." }
```

All external IDs are Hashid-encoded 8-character strings. Raw integers never appear in the API.

---

## Roles & Permissions

| Role | Description | Created by |
|---|---|---|
| `STUDENT` | Enroll, submit, view own grades, team formation, messaging | Admin |
| `INSTRUCTOR` | Create assignments, grade, manage own courses, discussions | Admin |
| `ADMIN` | Academic administration — users, courses, announcements | SYSTEM_ADMIN or seed |
| `SYSTEM_ADMIN` | Platform operations — infrastructure, overrides, monitoring | DB seed only — max 5 |

**Role hierarchy:** `SYSTEM_ADMIN > ADMIN > INSTRUCTOR > STUDENT`

---

## Infrastructure

```
Cloud:      AWS ca-central-1
EC2:        t3.micro (free tier) — t3.small at first client
S3:         reviewflow-storage (encrypted, versioned, public access blocked)
ECR:        797795454732.dkr.ecr.ca-central-1.amazonaws.com/reviewflow
CloudWatch: /reviewflow/application (30d) + /reviewflow/security (90d)
Terraform:  6 modules — ec2, iam, s3, ecr, security-groups, cloudwatch
            State: reviewflow-terraform-state-ca + reviewflow-terraform-locks
DNS/TLS:    Cloudflare — reviewflowlms.com
```

---

## Running Locally

### Prerequisites
- Java 21+
- Docker Desktop

### 1. Clone and configure

```bash
git clone https://github.com/olafabregas/Reviewflow.git
cd Reviewflow
cp .env.example .env
# Fill in all values — see .env.example for required variables
```

### 2. Start dependencies

```bash
docker compose up -d mysql redis mailhog
# mysql  — primary database (port 3306)
# redis  — rate limiting + distributed state (port 6379)
# mailhog — local email catcher at localhost:8025
```

### 3. Start the backend

```bash
./mvnw spring-boot:run
```

Flyway migrations run automatically on startup.

- API: `http://localhost:8081/api/v1`
- Swagger UI: `http://localhost:8081/swagger-ui/index.html`
- Mailhog: `http://localhost:8025`

---

## CI/CD Pipeline

Three GitHub Actions workflows:

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | Every push + every PR to main | Compile → unit tests → integration tests (MySQL container) → JaCoCo coverage PR comment → OWASP scan → Docker build check |
| `cd.yml` | Merge to main + manual dispatch | Full test suite → ECR push → staging deploy with health check + rollback → manual approval gate → prod deploy |
| `nightly.yml` | 2am UTC daily | Full OWASP scan · Full Postman suite against staging · ECR image cleanup |

---

## Project Structure

```
src/main/java/com/reviewflow/
├── auth/            ├── course/          ├── assignment/
├── team/            ├── submission/      ├── evaluation/
├── grading/         ├── discussion/      ├── messaging/
├── notification/    ├── announcement/    ├── extension/
├── admin/           ├── system/          ├── user/
├── config/          # CacheConfig, RedisConfig, AsyncConfig, SecurityConfig
├── infrastructure/  # security, storage, email, ratelimit, jobs, scheduling
└── shared/          # entities, GlobalExceptionHandler, HashidService, utils
```

---

## What's Next

- [ ] React frontend (15+ screens across 4 roles)
- [ ] PRD-19 — Security Headers (written, agent-ready)
- [ ] PRD-20 — Resend Email integration (written, agent-ready)
- [ ] PRD-21 — Sentry error tracking (planned)
- [ ] PRD-22 — Account deletion / GDPR (planned)
- [ ] EC2 production deployment
- [ ] Live demo

---

## License

This project is licensed under the [MIT License](./LICENSE).

---

## Contributing

This project is currently in active development and not open for contributions.
Feel free to fork it or open an issue if you spot something worth discussing.

---

**Other projects:** [ApexWeather](https://apexweather.vercel.app) · [TechTrainers](https://techtrainers.ca)
