 # ReviewFlow

> A university project submission and evaluation platform built for real academic workflows.

**Status:** Backend complete · Frontend in development · Deployment pending  

**Live demo:** Coming soon

-----

## What It Is

ReviewFlow is a full-stack web application that handles the end-to-end lifecycle of university project submissions: team formation, file submission, rubric-based evaluation, and feedback delivery. Three roles — Student, Instructor, and Admin — each have distinct workflows and permission boundaries enforced at every layer of the stack.

It covers security hardening, caching strategy, real-time notifications, ID obfuscation, PDF generation, and a multi-stage file validation pipeline.

-----

## Tech Stack

|Layer           |Technology                      |Notes                                      |
|----------------|--------------------------------|-------------------------------------------|
|Backend         |Spring Boot 4, Java 21          |                                           |
|Database        |MySQL 8                         |14 tables, Flyway migrations               |
|Auth            |JWT in HTTP-only cookies        |Refresh rotation, token fingerprinting     |
|File storage    |AWS S3                          |Pre-signed URLs                            |
|Real-time       |WebSocket — STOMP over SockJS   |                                           |
|Caching         |Caffeine                        |4 caches, Redis-ready by design            |
|File security   |`FileSecurityValidator` + ClamAV|4-stage pipeline                           |
|ID obfuscation  |Hashids                         |8-char opaque IDs on all external endpoints|
|PDF generation  |OpenPDF                         |Evaluation report export                   |
|API docs        |SpringDoc OpenAPI / Swagger UI  |                                           |
|Containerization|Docker + Docker Compose         |                                           |
|Frontend        |React                           |In development                             |

-----

## Features

### Student

- Enroll in courses and browse published assignments
- Form or join teams — invite teammates, accept or decline invitations
- Upload project submissions (ZIP/PDF) with automatic version tracking
- View rubric-based evaluations and instructor feedback once published
- Download evaluation reports as PDF
- Real-time notifications for invites, new submissions, and published grades

### Instructor

- Create and publish assignments with custom rubric criteria
- Lock teams at a configurable deadline
- Grade team submissions using per-criterion scoring with comments
- Save evaluations as drafts — students see nothing until explicitly published
- Generate and deliver PDF evaluation reports
- Bulk-enroll students into courses

### Admin

- Full user management — create, deactivate, and reactivate accounts
- Platform-wide statistics: users, submissions, storage usage, role breakdown
- Audit log of all significant write actions across the system
- Course and instructor assignment management

-----

## System Architecture
![ReviewFlow System Architecture](https://res.cloudinary.com/dwij0smbq/image/upload/v1773799191/diagram-export-3-17-2026-9_56_37-PM_s5fytp.png)

```
[Client (React SPA)]
        ↓
[Security Layer — JWT Filter · Rate Limiter · Token Fingerprinting]
        ↓
[REST API Layer (Spring Boot · 52 endpoints · 9 modules)]
        ↓
[Service Layer]
  ├── CourseService          ├── SubmissionService
  ├── AssignmentService      ├── EvaluationService
  ├── TeamService            ├── NotificationService
  ├── AdminStatsService      └── AuthService
        ↓
[Infrastructure Layer]
  ├── MySQL 8 (14 tables · Flyway migrations)
  ├── AWS S3 (pre-signed URLs)
  ├── Caffeine Cache (Redis-ready · 4 caches)
  └── WebSocket (STOMP over SockJS)
```
> System Flows with diagrams and summaries: [ARCHITECTURE.md](./ARCHITECTURE.md)
Design decisions and tradeoff reasoning: [DECISIONS.md](./DECISIONS.md)

-----

## API Overview

52 endpoints across 9 modules. Full OpenAPI spec at `http://localhost:8081/swagger-ui.html` when running locally.

|Module       |Endpoints                                      |
|-------------|-----------------------------------------------|
|Auth         |Login, logout, refresh, `/me`, WebSocket token |
|Courses      |CRUD, enrollment, student roster               |
|Assignments  |CRUD, rubric management, global feed           |
|Teams        |Create, invite, respond, lock, update          |
|Submissions  |Upload, version history, download, student view|
|Evaluations  |Create, score, publish, draft management       |
|PDF          |Generate and download evaluation reports       |
|Notifications|List, mark read, unread count, delete          |
|Admin        |User management, stats, audit log              |

-----

## Project Structure

```
src/main/java/com/reviewflow/
├── config/          # Security, CORS, cache, WebSocket, S3, OpenAPI config
├── controller/      # REST controllers — thin, no business logic
├── service/         # All business logic, caching annotations
├── repository/      # Spring Data JPA interfaces
├── model/           # JPA entities
├── dto/             # Request/response DTOs with Hashid encoding
├── security/        # JWT filter, token fingerprinting, rate limiter
├── event/           # Application events + notification listener
├── exception/       # Global exception handler, custom exceptions
├── scheduler/       # Due-date reminders, token cleanup jobs
└── util/            # HashidService, FileSecurityValidator, ClamAvScanService
```

-----

## Running Locally

### Prerequisites

- Java 21+
- Docker (for MySQL + ClamAV)
- AWS credentials (or LocalStack for local S3)

### 1. Clone and configure

```bash
git clone https://github.com/olafabregas/Reviewflow.git
cd Reviewflow
cp .env.example .env
```

Edit `.env` with your values — see `.env.example` for all required variables.

### 2. Start dependencies

```bash
docker compose up -d mysql clamav
```

### 3. Start the backend

```bash
./mvnw spring-boot:run
```

Flyway migrations run automatically on startup. To seed development data:

```bash
mysql -u root -p reviewflow_dev < src/main/resources/db/seed/seed.sql
```

API: `http://localhost:8081/api/v1`  
Swagger UI: `http://localhost:8081/swagger-ui.html`

-----

## What’s Next

- [ ] React frontend (15 screens across 3 roles)
- [ ] Docker Compose production config
- [ ] VPS deployment
- [ ] Live demo

-----

## License

This project is licensed under the [MIT License](./LICENSE).

-----

## Contributing

This project is currently in active development and not open for contributions.

Feel free to fork it or open an issue if you spot something worth discussing.

---

**Other projects:** [ApexWeather](https://apexweather.vercel.app) · [TechTrainers](https://techtrainers.ca)


