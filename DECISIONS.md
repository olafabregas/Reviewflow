# ReviewFlow — Design Decisions

This document captures the reasoning behind key architectural choices — not just what was built, but why, and what was accepted in return.

For system flows see [ARCHITECTURE.md](./ARCHITECTURE.md).
For the project overview see [README.md](./README.md).

---

## Contents

1. [Monolith first, microservices boundaries defined](#1-monolith-first-microservices-boundaries-defined)
2. [HTTP-only cookies over Authorization header](#2-http-only-cookies-over-authorization-header)
3. [Token fingerprinting for stolen token rejection](#3-token-fingerprinting-for-stolen-token-rejection)
4. [Hashids over UUIDs for external IDs](#4-hashids-over-uuids-for-external-ids)
5. [Caffeine for app caches, Redis for distributed state](#5-caffeine-for-app-caches-redis-for-distributed-state)
6. [ClamAV removed from validation pipeline](#6-clamav-removed-from-validation-pipeline)
7. [Event-driven async notifications](#7-event-driven-async-notifications)
8. [Draft/publish lifecycle for evaluations](#8-draftpublish-lifecycle-for-evaluations)
9. [Versioned submissions — no overwrite](#9-versioned-submissions--no-overwrite)
10. [Submission type stored as explicit state](#10-submission-type-stored-as-explicit-state)
11. [Event-driven email delivery decoupled from WebSocket](#11-event-driven-email-delivery-decoupled-from-websocket)
12. [Resend SMTP relay over AWS SES and Java SDK](#12-resend-smtp-relay-over-aws-ses-and-java-sdk)
13. [Announcement draft/publish before broadcast](#13-announcement-draftpublish-before-broadcast)
14. [Avatar URLs stored as fixed URLs — no presign at read time](#14-avatar-urls-stored-as-fixed-urls--no-presign-at-read-time)
15. [CSV in-memory generation at current scale](#15-csv-in-memory-generation-at-current-scale)
16. [Structured JSON logging to CloudWatch](#16-structured-json-logging-to-cloudwatch)
17. [Four-role hierarchy — SYSTEM_ADMIN separate from ADMIN](#17-four-role-hierarchy--system_admin-separate-from-admin)
18. [Fixed 5-account ceiling for SYSTEM_ADMIN](#18-fixed-5-account-ceiling-for-system_admin)
19. [Cache eviction throttle with 60-second window](#19-cache-eviction-throttle-with-60-second-window)
20. [WebSocket push for real-time metrics delivery](#20-websocket-push-for-real-time-metrics-delivery)
21. [Terraform over CDK and CloudFormation](#21-terraform-over-cdk-and-cloudformation)
22. [Multi-stage Dockerfile — builder + runtime stages](#22-multi-stage-dockerfile--builder--runtime-stages)
23. [GitHub Actions over other CI/CD platforms](#23-github-actions-over-other-cicd-platforms)
24. [Docker image to ECR over JAR-direct-SSH deployment](#24-docker-image-to-ecr-over-jar-direct-ssh-deployment)
25. [INDIVIDUAL as default submission_type](#25-individual-as-default-submission_type)

---

## 1. Monolith first, microservices boundaries defined

**Decision:** Single Spring Boot deployment. Services are logically separated by domain package but deployed as one unit.

**Why:**
- Fast iteration — no inter-service networking, no distributed transaction overhead
- Full ACID guarantees across all operations — grade publish, team formation, submission upload all share one DB connection
- At university scale (hundreds of users, single institution), microservices would be pure operational overhead

**Accepted tradeoffs:**
- Cannot independently scale file upload throughput vs API requests vs notification delivery
- One failure domain — a crash takes down everything
- WebSocket requires sticky sessions or Redis pub/sub before horizontal scaling

**Mitigation:** Service boundaries are already defined by package structure. Extraction is a deployment decision, not a refactor.

---

## 2. HTTP-only cookies over Authorization header

**Decision:** JWT stored in `HttpOnly; Secure; SameSite=Strict` cookies, not in `localStorage` or `Authorization` headers.

**Why:**
- `localStorage` is accessible to JavaScript — any XSS vulnerability exposes the token entirely
- HTTP-only cookies cannot be read by JavaScript under any circumstances
- `SameSite=Strict` prevents cookies from being sent on cross-origin requests, eliminating the CSRF concern traditionally associated with cookie auth

**Accepted tradeoffs:**
- Mobile clients and non-browser consumers need a different auth flow
- More complex CORS configuration — credentials must be explicitly allowed
- Concurrent refresh requests require careful rotation logic

---

## 3. Token fingerprinting for stolen token rejection

**Decision:** A fingerprint hash derived from `User-Agent` and IP is embedded as a JWT claim. Every authenticated request recomputes and compares — mismatch returns `401 INVALID_FINGERPRINT`.

**Why:**
- Even with HTTP-only cookies, a cookie could theoretically be extracted via network interception
- Without fingerprinting, a stolen cookie is valid on any device until expiry
- With fingerprinting, a stolen cookie is immediately invalid on any other device or network

**Accepted tradeoffs:**
- Users on dynamic IPs (mobile, VPN) may experience unexpected logouts
- Disabled in local/dev profile to avoid friction
- User-Agent can be spoofed — this is a deterrent layer, not cryptographic proof

---

## 4. Hashids over UUIDs for external IDs

**Decision:** Internal PKs are `BIGINT`. All external-facing IDs are encoded to 8-character opaque strings using Hashids.

**Why:**
- Sequential integers leak record counts and enable enumeration attacks
- UUIDs are 36 characters, random, and not reversible
- Hashids are short, deterministic, reversible — no storage overhead, no schema change

**Critical rule:** The Hashids salt must never change after first deployment. Changing it invalidates every external ID ever issued — all links, bookmarks, and API integrations break permanently.

**Accepted tradeoffs:**
- Salt must be protected as a secret
- Hashids are obfuscation, not encryption — reversible if salt leaks
- Extra `HashidService.encode/decode` call at every controller boundary

---

## 5. Caffeine for app caches, Redis for distributed state

**Decision:** Two separate caching layers with distinct responsibilities. Caffeine owns Spring `@Cacheable` app caches. Redis owns distributed/shared state.

**What Caffeine owns (per-JVM, in-process):**
All 13 Spring `@Cacheable` caches — adminStats, unreadCount, userCourses, assignmentDetail, courseGradeGroups, courseModules, gradeOverview, classStatistics, courseMaterials, courseDiscussions, discussionParticipation, csvImports, oauthState.

**What Redis owns (distributed, always-on):**
Rate limiting (Bucket4j), grade aggregate blobs, CSV job state, import locks, OAuth CSRF state, token version store (conditional), messaging pub/sub (optional).

**The gray area — grade overviews use both:**
Caffeine wraps the method via `@Cacheable`. Redis stores the computed grade blob inside that method via `GradeAggregateService`. This is not L1/L2 — they serve different purposes on different call paths.

**Why not Redis for everything:**
Caffeine is faster for single-node reads (no network round-trip). The Spring Cache abstraction means swapping Caffeine for Redis on app caches is a `CacheConfig` change — no annotation changes anywhere.

**Why Redis was added:**
Rate limiting must be shared across instances — per-instance in-memory buckets would allow the same IP to be rate-limited separately on each node. Grade aggregates are expensive to recompute — storing them in Redis means any instance can serve the cached result. CSV job state must survive instance restarts.

**Previous decision (outdated):** DECISIONS.md previously stated "no Redis for caches." This was accurate when written but Redis was later added for the above concerns without migrating `CacheConfig` to `RedisCacheManager`. The mental model is: Caffeine = Spring Cache layer, Redis = shared infrastructure state.

---

## 6. ClamAV removed from validation pipeline

**Decision:** ClamAV was removed entirely from the file validation pipeline. The pipeline is now 3 stages: extension whitelist → MIME verification → size limit.

**Why:**
- ClamAV requires a dedicated service running alongside the application — a Docker container, socket connection, and scan latency added to every file upload
- On a t3.micro with 2GB RAM, running ClamAV alongside Spring Boot and MySQL created memory pressure
- ReviewFlow's users are authenticated and known — the threat model for a university platform is materially different from a public file hosting service
- MIME verification already catches the most common attack vectors (MIME spoofing, renamed executables)

**Accepted tradeoffs:**
- No virus scanning — known risk, documented and accepted
- A malicious authenticated user could upload malware that another user downloads
- Mitigation: pre-signed URL downloads mean files are served directly from S3 to the browser — the application server is never in the download path

**Re-enablement path:** ClamAV infrastructure code and profile-based configuration were retained. To re-enable: add `clamav` service to Docker Compose and set `clamav.enabled=true` in the prod profile. The pipeline will activate immediately with fail-closed behaviour.

---

## 7. Event-driven async notifications

**Decision:** Services publish Spring `ApplicationEvent`s. `NotificationEventListener` handles DB persistence and WebSocket delivery on a separate thread pool — never on the calling thread.

**Why:**
- A file upload should not be slowed down by notification delivery
- DB-first persistence means a notification is never lost even if WebSocket delivery fails
- Decoupling write operations from notification side-effects makes each service's responsibility clear

**The guarantee:** DB write for the notification always succeeds before WebSocket push is attempted. If the push fails, the notification exists in the database and the client recovers it on reconnect.

**Accepted tradeoffs:**
- Users may not receive notifications immediately — WebSocket is best-effort
- System state and user perception can briefly diverge (within 30-second `unreadCount` TTL)
- Debugging is harder — notification failures are invisible to the triggering thread

---

## 8. Draft/publish lifecycle for evaluations

**Decision:** Evaluations are created as `is_draft=true` and return `404` (not `403`) to students until published.

**Why:**
- `403` would tell the student "this evaluation exists but you can't see it" — revealing information before the instructor is ready
- `404` reveals nothing — from the student's perspective, the evaluation does not exist yet
- Instructors need time to save, score, and review before students see anything

**Publication is permanent** — once published, an evaluation cannot be unpublished. The SYSTEM_ADMIN reopen flow creates a new evaluation revision with audit snapshot rather than reverting visibility.

---

## 9. Versioned submissions — no overwrite

**Decision:** Every file upload creates a new submission record with an incremented `version_number`. Previous versions are retained.

**Why:**
- Overwriting loses the audit history of what was submitted and when
- `isLate` is computed at upload time — version history preserves the late status of each version
- No destructive writes means no data loss from accidental re-upload

**Accepted tradeoffs:**
- S3 storage grows with every version
- Requires `SELECT MAX(version_number)` before every upload
- Old versions are never automatically deleted (S3 lifecycle transitions to STANDARD_IA after 90 days)

---

## 10. Submission type stored as explicit state

**Decision:** `submission_type` ENUM (INDIVIDUAL | TEAM | INSTRUCTOR_GRADED) stored on `assignments`, not inferred from team configuration.

**Why:**
- Prevents ambiguity when team configuration and assignment intent drift apart
- Enables explicit state machine for downstream features
- Immutability after submissions/teams exist prevents orphaned data

**Default is INDIVIDUAL** — safer failure mode. An instructor forgetting to set the type means students submit directly rather than hitting broken team formation. See Decision 25.

---

## 11. Event-driven email delivery decoupled from WebSocket

**Decision:** Email runs on a completely separate thread pool (`emailTaskExecutor`) from WebSocket notification delivery (`notificationTaskExecutor`).

**Why:**
- SMTP latency (100–500ms) must not block WebSocket push or REST response
- An SMTP outage must not affect in-app notifications
- Fire-and-forget: email failures are logged but never propagate to the calling thread

**The guarantee:** DB notification is written first. If email fails, the user still has the in-app notification via WebSocket and DB.

---

## 12. Resend SMTP relay over AWS SES and Java SDK

**Decision:** Resend SMTP relay via `JavaMailSender`. Not AWS SES. Not the Resend Java SDK.

**Why over AWS SES:**
- `reviewflowlms.com` DNS is already verified at Resend — SPF, DKIM, DMARC all configured
- SES requires production access request (24–48hr AWS review), additional IAM setup, and separate DNS records
- Resend is operational immediately — SES was strictly more work for equivalent outcome

**Why SMTP relay over Resend SDK:**
- The SDK would require rewriting `EmailEventListener` and all 10 `MimeMessage` constructions — high risk, tested code
- SMTP relay keeps the entire existing stack intact — `JavaMailSender` sends through Resend's endpoint exactly as it would through any other SMTP server
- Spring Mail abstraction is preserved — switching providers in the future is a config change

**Accepted tradeoffs:**
- SMTP relay does not provide per-send delivery status in code — Resend dashboard only
- ReviewFlow's fire-and-forget architecture means per-send status provides no architectural benefit at this stage

---

## 13. Announcement draft/publish before broadcast

**Decision:** Announcements are `DRAFT` until explicitly published. No notifications fire until publish.

**Why:**
- Prevents accidental broadcasts before content is ready
- Allows review before sending to entire course

**Semantics:** Students enrolled at publish time receive notifications. Late enrollees can read published announcements but receive no notification.

---

## 14. Avatar URLs stored as fixed URLs — no presign at read time

**Decision:** Avatar URLs are built once on upload (`https://{bucket}.s3.{region}.amazonaws.com/avatars/{hashedUserId}/avatar.{ext}?v={epochMillis}`), persisted in `users.avatar_url`, and returned directly from the DB on every read. No S3 SDK calls at read time.

**Why:**
- A roster endpoint returning 800 members would require 800 presign SDK calls per request — unacceptable latency
- Fixed URLs with `?v=` cache-busting achieve cache freshness without per-request SDK calls
- `?v=` is only updated on avatar upload/replace — not on every API read

**Infrastructure caveat:** This works only if the `avatars/` S3 prefix is publicly readable OR `S3_PUBLIC_BASE_URL` points at CloudFront serving those objects. If the bucket is fully private with `blockPublicAccess: true` and no CDN path, the API returns URLs correctly but browsers receive `403` on image load. Verify bucket/CDN policy before deploying the frontend.

**Upgrade path:** If avatars move to fully private objects, the options are: CloudFront signed cookies, public prefix for avatars only, or batch presign with caching. Either is additive — no application logic changes.

---

## 15. CSV in-memory generation at current scale

**Decision:** Grade export generates CSV in memory. No streaming, no async job.

**Why:**
- Current scale: hundreds of submissions per assignment fit easily in process memory (<5MB)
- Single controller method, no job infrastructure, response streams directly to browser
- Atomic — no race conditions between building CSV and delivery

**Accepted tradeoffs:**
- OOM risk if assignments scale to thousands of submissions
- No progress bar — users wait for full CSV before download starts

**Scale limit:** API rejects exports for assignments with >5,000 submissions. Upgrade path: async job → S3 upload → download link pattern.

---

## 16. Structured JSON logging to CloudWatch

**Decision:** All application logs formatted as JSON via logstash-logback-encoder, shipped to AWS CloudWatch Logs via CloudWatch Agent reading `/var/log/reviewflow/`.

**Why:**
- Log fields (`traceId`, `userId`, `courseId`, `action`) are queryable JSON — no log parsing required
- `X-Trace-Id` header on every response enables instant CloudWatch Insights correlation
- CloudWatch handles retention, rotation, and alerting natively

**Accepted tradeoffs:**
- Hard AWS dependency — logging degrades if CloudWatch is unreachable (logs still written to file)
- JSON encoding overhead — negligible (<1ms per request)

---

## 17. Four-role hierarchy — SYSTEM_ADMIN separate from ADMIN

**Decision:** Four roles: `SYSTEM_ADMIN > ADMIN > INSTRUCTOR > STUDENT`. Academic admins and platform operators are separate.

**Why:**
- An academic admin managing courses and users has no business seeing JVM metrics, cache statistics, or security events
- Mixing responsibilities creates a security risk (over-privileged academic staff) and a UX problem
- Aligns with Blackboard/Canvas model — familiar to university administrators

**SYSTEM_ADMIN exclusive:** actuator endpoints, system dashboard, force logout, team unlock, evaluation reopen, cache management. Max 5 accounts, DB seed only — no API creation path prevents privilege escalation.

---

## 18. Fixed 5-account ceiling for SYSTEM_ADMIN

**Decision:** Hard limit of 5 SYSTEM_ADMIN accounts, enforced at both DB and application layer.

**Why:**
- Not 1 — single point of failure if credentials are compromised or forgotten
- Not unlimited — security and governance risk
- 5 provides backup and redundancy without proliferation
- Migration-based creation only — logged in version control, no API endpoint to exploit

---

## 19. Cache eviction throttle with 60-second window

**Decision:** SYSTEM_ADMIN can force-evict caches, but a 60-second gate prevents more than one eviction per cache per minute.

**Why:**
- Operational recovery without a full restart
- Prevents operator from hammering the eviction endpoint and causing thundering herd

**Limitation:** Throttle state is in-process. Multi-instance deployments could evict simultaneously. Upgrade path: Redis-backed throttle when horizontal scaling is active.

---

## 20. WebSocket push for real-time metrics delivery

**Decision:** System metrics pushed to subscribed SYSTEM_ADMIN clients via WebSocket every 30 seconds plus immediately on alarm triggers.

**Why:**
- Lower latency and overhead than polling
- Leverages existing WebSocket infrastructure
- Immediate push on alarm triggers (cache miss spike, connection pool exhaustion) enables fast incident response

**Limitation:** Multi-instance requires Redis pub/sub to avoid metric silos — already wired, default off.

---

## 21. Terraform over CDK and CloudFormation

**Decision:** Terraform (HCL) for all AWS infrastructure as code.

**Why:**
- Cloud-agnostic — mental model transfers if ReviewFlow moves to GCP or Azure
- Largest provider ecosystem, most mature module system
- CloudFormation is AWS-only with verbose syntax
- CDK adds an abstraction layer that can obscure what is actually being provisioned
- Declarative HCL is easier to review and audit than imperative CDK code

**State management:** S3 backend (`reviewflow-terraform-state-ca`) + DynamoDB locking (`reviewflow-terraform-locks`) — safe for concurrent applies and state versioning.

---

## 22. Multi-stage Dockerfile — builder + runtime stages

**Decision:** Two-stage Dockerfile. Stage 1: full JDK 21 + Maven compiles the JAR. Stage 2: JRE-only runtime copies the JAR in.

**Why:**
- Image size: ~600MB (single-stage) → ~250MB (multi-stage) — 60% reduction
- Source code never appears in the deployed image — reduced attack surface
- Maven toolchain not present in production — smaller dependency footprint
- Layer caching: Maven dependency layer changes rarely — cached between builds

---

## 23. GitHub Actions over other CI/CD platforms

**Decision:** GitHub Actions for all CI/CD pipelines.

**Why:**
- Code already lives in GitHub — zero additional integration
- 2,000 free minutes/month on Linux runners is sufficient for ReviewFlow's build cadence
- Native GitHub integration: PR comments for JaCoCo coverage, GitHub Environments for manual approval gates, GitHub Secrets for credential management
- No additional platform to manage, authenticate, or pay for

---

## 24. Docker image to ECR over JAR-direct-SSH deployment

**Decision:** Build Docker image → push to ECR → pull to EC2 on deploy. Not SSH + SCP of a JAR file.

**Why:**
- Every deploy is a tagged image — rollback means pulling the previous tag, not recovering a lost JAR
- ECR provides image versioning, scanning on push (CVE detection), and lifecycle policies
- Same image runs on staging and production — no "works on my machine" between environments
- Migration path to ECS or Kubernetes requires zero pipeline changes — same image, different target

**Accepted tradeoffs:**
- Slightly slower deploys (~2–3 min to push image vs ~30s SCP)
- ECR storage costs — free tier 500MB/month, image is ~250MB
- Image build requires Docker on the GitHub Actions runner

---

## 25. INDIVIDUAL as default submission_type

**Decision:** `submission_type` defaults to `INDIVIDUAL` when not explicitly set.

**Why:**
- Safer failure mode: an instructor who forgets to set the type gets students submitting directly — functional, if not ideal
- If it defaulted to `TEAM`, students would hit team formation errors with no way to submit — a worse failure
- Academic records principle: a student should always be able to submit. A broken default should not prevent submission.
