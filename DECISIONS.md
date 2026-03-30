# ReviewFlow — Design Decisions

This document captures the reasoning behind key architectural choices in ReviewFlow — not just what was built, but why, and what was accepted in return.

For system flows and diagrams see [ARCHITECTURE.md](./ARCHITECTURE.md).  
For the project overview see [README.md](./README.md).

---

## Contents

1. [Monolith first, microservices boundaries defined](#1-monolith-first-microservices-boundaries-defined)
1. [HTTP-only cookies over Authorization header](#2-http-only-cookies-over-authorization-header)
1. [Token fingerprinting for stolen token rejection](#3-token-fingerprinting-for-stolen-token-rejection)
1. [Hashids over UUIDs for external IDs](#4-hashids-over-uuids-for-external-ids)
1. [Caffeine over Redis at current scale](#5-caffeine-over-redis-at-current-scale)
1. [Event-driven async notifications](#6-event-driven-async-notifications)
1. [ClamAV fail-open locally, fail-closed in production](#7-clamav-fail-open-locally-fail-closed-in-production)
1. [Draft/publish lifecycle for evaluations](#8-draftpublish-lifecycle-for-evaluations)
1. [Versioned submissions — no overwrite](#9-versioned-submissions--no-overwrite)
1. [Submission type stored as explicit state](#10-submission-type-stored-as-explicit-state)
1. [Event-driven email delivery decoupled from WebSocket](#11-event-driven-email-delivery-decoupled-from-websocket)
1. [Announcement draft/publish before broadcast](#12-announcement-draftpublish-before-broadcast)
1. [S3 presigned URLs for direct client access](#13-s3-presigned-urls-for-direct-client-access)
1. [CSV in-memory generation at current scale](#14-csv-in-memory-generation-at-current-scale)
1. [Structured JSON logging to CloudWatch](#15-structured-json-logging-to-cloudwatch)
1. [Two-role hierarchy with role layers](#16-two-role-hierarchy-with-role-layers)
1. [Fixed 5-account ceiling for system administration](#17-fixed-5-account-ceiling-for-system-administration)
1. [Cache eviction throttle with 60-second window](#18-cache-eviction-throttle-with-60-second-window)
1. [WebSocket push for real-time metrics delivery](#19-websocket-push-for-real-time-metrics-delivery)

---

## 1. Monolith first, microservices boundaries defined

**Decision:** Single Spring Boot deployment. Services are logically separated by domain but deployed as one unit.

**Why:**

- Fast iteration — no inter-service networking, no distributed transaction overhead
- Simple operations — one deployment, one database, full ACID guarantees across all operations
- At university scale (hundreds of users, one institution), microservices would be pure overhead

**Accepted tradeoffs:**

- Cannot independently scale file upload throughput, API requests, and notification delivery
- One deployment unit means one failure domain — a crash takes down everything
- WebSocket requires sticky sessions before horizontal scaling is viable

**Mitigation:** Service boundaries are already defined in the codebase. Extraction to independent services is a deployment decision, not a refactoring effort. See [ARCHITECTURE.md — Evolution Path](./ARCHITECTURE.md#12-architecture-evolution-path) for the extraction plan.

---

## 2. HTTP-only cookies over Authorization header

**Decision:** JWT stored in `HttpOnly; Secure; SameSite=Strict` cookies, not in `localStorage` or `Authorization` headers.

**Why:**

- `localStorage` is accessible to JavaScript — any XSS vulnerability exposes the token
- HTTP-only cookies cannot be read by JavaScript under any circumstances — XSS cannot steal them
- `SameSite=Strict` prevents cookies from being sent on cross-origin requests, which eliminates the CSRF concern traditionally associated with cookie-based auth

**Accepted tradeoffs:**

- Mobile clients and non-browser consumers need a different auth flow
- Concurrent refresh requests can create a brief race window — mitigated by refresh token rotation (see Decision 3)
- More complex CORS configuration required — credentials must be explicitly allowed

---

## 3. Token fingerprinting for stolen token rejection

**Decision:** A fingerprint hash derived from the request’s `User-Agent` and IP address is embedded as a claim in the JWT. Every authenticated request recomputes the fingerprint and compares it against the claim — a mismatch returns `401 INVALID_FINGERPRINT`.

**Why:**

- Even with HTTP-only cookies, a cookie can theoretically be extracted via network interception or a browser vulnerability
- Without fingerprinting, a stolen cookie is valid on any device until it expires
- With fingerprinting, a stolen cookie is invalid the moment it is used from a different device or network context

**Accepted tradeoffs:**

- Users on dynamic IP addresses (mobile networks, VPNs) may experience unexpected logouts — fingerprint includes IP, which changes
- Disabled in local/dev profile to avoid friction during development
- User-Agent can be spoofed — this is a deterrent layer, not a cryptographic guarantee

---

## 4. Hashids over UUIDs for external IDs

**Decision:** Internal primary keys are `BIGINT` (fast joins, compact indexes). All external-facing IDs are encoded to 8-character opaque strings using Hashids before leaving the API layer.

**Why:**

- Sequential integers in a public API leak record counts and enable enumeration attacks (`GET /submissions/1`, `/2`, `/3`)
- UUIDs solve the enumeration problem but are 36 characters, random, and not sortable
- Hashids produce short, deterministic, reversible opaque strings — no storage overhead, no schema change, and the salt is the only secret

```
External:  GET /evaluations/k3N9mQ2p
                                ↓ HashidService.decodeOrThrow()
Internal:  SELECT * FROM evaluations WHERE id = 47391
```

**Accepted tradeoffs:**

- The salt must never change after first deployment — changing it invalidates all existing external IDs permanently
- Hashids are reversible if the salt is leaked — they are obfuscation, not encryption
- A separate `HashidService.encode/decode` call is required at every controller boundary

---

## 5. Caffeine over Redis at current scale

**Decision:** In-memory Caffeine cache for all four caches. No Redis.

**Why:**

- At single-node, single-institution scale, Redis adds operational complexity with no performance benefit
- Caffeine benchmarks faster than Redis for single-node use — no network round-trip
- The Spring Cache abstraction means the entire switch to Redis requires changing only `CacheConfig` — no `@Cacheable` or `@CacheEvict` annotations change anywhere

**Accepted tradeoffs:**

- Cache is not shared across nodes — irrelevant until horizontal scaling
- Cache is lost on restart — acceptable given TTLs of 30 seconds to 10 minutes
- Rate limiting is also per-instance — no global rate limit enforcement across nodes

**The line to Redis:** The moment horizontal scaling is needed — either for WebSocket (which requires a shared broker) or for cache consistency — Redis gets added and Caffeine is swapped out. The code is already written for that transition.

---

## 6. Event-driven async notifications

**Decision:** Services publish Spring `ApplicationEvent`s on write operations. `NotificationEventListener` handles DB persistence and WebSocket delivery on a separate thread pool — never on the calling thread.

**Why:**

- A file upload or grade publication should not be slowed down by notification delivery
- Decoupling write operations from notification side-effects makes each service’s responsibility clear
- DB-first persistence means a notification is never lost, even if WebSocket delivery fails

**Accepted tradeoffs:**

- Users may not receive critical updates immediately — WebSocket delivery is best-effort
- System state and user perception can briefly diverge (within the 30-second `unreadCount` cache TTL)
- Debugging is harder — a notification failure is invisible to the thread that triggered it

**The guarantee:** The DB write for the notification always succeeds before the WebSocket push is attempted. If the push fails, the notification is still in the database and the client recovers it on reconnect.

---

## 7. ClamAV fail-open locally, fail-closed in production

**Decision:** If ClamAV is unavailable, file uploads proceed in local/dev profile and are rejected in production.

**Why:**

- Requiring ClamAV to be running for every local development session would create constant friction
- In production, an unavailable ClamAV scanner means the security guarantee cannot be upheld — uploads must be blocked

**Accepted tradeoffs:**

- A developer who forgets to start ClamAV locally will not notice — files that would be rejected in production pass through silently
- This creates a potential gap between local behavior and production behavior
- Mitigated by keeping ClamAV in `docker compose` — `docker compose up -d clamav` is one command

**Profile configuration:**

| Profile | ClamAV unavailable            |
| ------- | ----------------------------- |
| `local` | Upload proceeds (fail-open)   |
| `prod`  | Upload rejected (fail-closed) |

---

## 8. Draft/publish lifecycle for evaluations

**Decision:** Evaluations are created as `is_draft=true` and are completely invisible to students. A draft evaluation returns `404`, not `403`, when requested by a student.

**Why:**

- Returning `403` would tell the student “this evaluation exists but you can’t see it” — revealing information before the instructor is ready to publish
- `404` reveals nothing — from the student’s perspective, the evaluation simply does not exist yet
- Instructors need to be able to save, score, and review evaluations before students can see them — the draft state enables this without time pressure

**Accepted tradeoffs:**

- The distinction between “doesn’t exist” and “exists but draft” is invisible to students — this is intentional
- Once published, an evaluation cannot be unpublished — publication is a permanent transition

---

## 9. Versioned submissions — no overwrite

**Decision:** Every file upload creates a new submission record with an incremented `version_number`. Previous versions are retained in S3 and in the database.

**Why:**

- Overwriting a submission loses the audit history of what was submitted and when
- `isLate` is computed at upload time — version history preserves when each version was submitted relative to the deadline
- Instructors can see the full submission history, not just the latest file
- No destructive writes means no data loss scenarios from accidental re-upload

**Accepted tradeoffs:**

- S3 storage grows with every version — old versions are never automatically deleted
- Requires a `SELECT MAX(version_number)` before every upload to determine the next version
- Students submitting many times accumulate storage costs — acceptable at current scale

---

## 10. Submission type stored as explicit state

**Decision:** The `submission_type` enum (INDIVIDUAL | TEAM) is stored on the `assignments` table as an explicit column, not inferred from team configuration.

**Why:**

- Prevents ambiguity when team formation rules and assignment intent drift apart
- Enables explicit state machine for downstream features (export, notifications, evaluations)
- Audit trail shows exactly when and what submission model was chosen

**Accepted tradeoffs:**

- Adds one column to assignments table and one migration
- Application must guard against type/data mismatch — a team exists but assignment is INDIVIDUAL
- Requires validation in controllers before accepting team or individual submissions

**Mitigation:**

- Application-level guard: all submission endpoints check assignment `submission_type` and accept/reject accordingly
- Audit logging: type changes are logged to audit_log if permitted (blocked once submissions exist)
- Test coverage: both INDIVIDUAL and TEAM paths tested to prevent divergence

See [ARCHITECTURE.md — Dual Ownership Model](./ARCHITECTURE.md#dual-ownership-model) for query pattern details.

---

## 11. Event-driven email delivery decoupled from WebSocket

**Decision:** Email notifications are published as async events on a separate thread pool, completely independent of WebSocket delivery. Email failure never blocks or affects in-app notifications.

**Why:**

- SMTP latency (100-500ms per email) must not block WebSocket push or REST response
- Decoupling write operations from email side-effects: grade publication returns immediately, email ships asynchronously
- Email failures are isolated — a mail server outage does not bring down the application

**Accepted tradeoffs:**

- Users may not receive emails immediately — emails arrive delayed by seconds to minutes
- Retry complexity: failed emails queued and retried with exponential backoff
- Debugging is harder — email failures are invisible to the thread that triggered them

**Mitigation:**

- Dead-letter queue (DLQ) for emails exceeding retry limit
- Admin dashboard exposes undelivered email count and last 100 failures
- User sees "notification queued" status in UI — sets expectations for eventual delivery

See [ARCHITECTURE.md — Email Event Listeners](./ARCHITECTURE.md#email-event-listeners) for architecture diagram.

---

## 12. Announcement draft/publish before broadcast

**Decision:** Announcements are created in DRAFT state and are invisible to students and most instructors. Draft announcements must be explicitly published by creator or admin before students can read them.

**Why:**

- Prevents accidental typos or off-topic broadcasts that reach entire courses
- Allows review window: creator drafts, another instructor reviews, then publishes
- Email and notifications only fire on publish — no premature broadcasts

**Accepted tradeoffs:**

- UI complexity: additional workflow step (draft → review → publish) before announcements go live
- Instructors may forget to publish — requires double-action to broadcast
- History of draft announcements accumulates in database

**Mitigation:**

- Templates: pre-written announcement templates for common scenarios (due date changes, grading delays)
- Scheduling UI: instructors can draft announcements and schedule publication for later
- Publish button is high-visibility: distinct color, prominent placement in instructor dashboard

See [ARCHITECTURE.md — Announcement State Machine](./ARCHITECTURE.md#announcement-state-machine) for state diagram.

---

## 13. S3 presigned URLs for direct client access

**Decision:** Files (submissions, PDFs, avatars) are accessed via presigned S3 URLs returned by the API, not streamed through the backend. Client-side rendering is fully supported.

**Why:**

- Zero backend memory overhead — no file buffering or streaming
- Presigned URL generation is stateless — no session tracking required
- Browser native rendering: PDF viewers, image galleries handle display directly
- S3 handles HTTPS, range requests, and authentication — all built-in

**Accepted tradeoffs:**

- Presigned URLs are longer and less readable than traditional `/api/files/{id}` endpoints
- URLs are reversible if salt is leaked (not encrypted, but time-bounded)
- Longer URLs can cause issues in some contexts (email, logging, shared links)

**Mitigation:**

- Short TTL (15 minutes): URLs are valid only long enough for a study session
- IP fingerprinting: optional extra layer — presigned URLs valid only from originating IP
- Upgrade path: when CloudFront is added, swap to CloudFront signed URLs without code changes

---

## 14. CSV in-memory generation at current scale

**Decision:** Grade export generates CSV in memory using OpenCSV library. No streaming, no async job queue.

**Why:**

- Current scale: hundreds of submissions per assignment fit in process memory (<5MB)
- Simplicity: single controller method, no job infrastructure, response streams directly to browser
- Deterministic: export is atomic — no race conditions between building CSV and delivery

**Accepted tradeoffs:**

- OOM (out of memory) risk if an assignment scales to thousands of submissions
- No streaming progress bar — users wait for entire CSV generation before download starts
- Memory spike during export prevents other requests from using that heap

**Mitigation:**

- Clear max size limits: API rejects exports for assignments with >5000 submissions
- Documented upgrade path: when scale demands, swap to async job queue + S3 upload pattern
- Monitoring: alerts on heap pressure during exports

See [ARCHITECTURE.md — Grade Export Query Optimization](./ARCHITECTURE.md#grade-export-query-optimization) for alternative approaches.

---

## 15. Structured JSON logging to CloudWatch

**Decision:** All application logs are formatted as JSON (via logstash-logback-encoder) and published to AWS CloudWatch Logs. No local file logs in production.

**Why:**

- Centralized searchability: logs are queryable from AWS console
- Container-ready: works in Docker and ECS without local file mounts
- Structured data: log fields (user_id, course_id, action) are queryable JSON — no log parsing required
- Retention policies: CloudWatch handles log rotation and deletion

**Accepted tradeoffs:**

- Hard AWS dependency: logging fails if CloudWatch is unreachable
- Local development requires CloudWatch mock (localstack) or file fallback
- JSON parsing overhead: negligible (<1ms per request)

**Mitigation:**

- Local CloudWatch emulator: `docker-compose up localstack` provides a CloudWatch mock
- Dev profile falls back to JSON-to-console logging, no AWS credentials needed
- Request correlation via `X-Trace-Id` header stitches logs across async flows

---

## 16. Two-role hierarchy with role layers

**Decision:** Introduce a four-tier role hierarchy: SYSTEM_ADMIN > ADMIN > INSTRUCTOR > STUDENT. Spring Security role hierarchy grants permissions down the chain.

**Why:**

- Separates concerns: SYSTEM_ADMIN handles platform infrastructure (metrics, cache, security)
- ADMIN remains for academic management (users, courses, grades)
- Prevents mission creep: program coordinators no longer have view into infrastructure metrics
- Aligns with Blackboard/Canvas/Banner model — familiar to administrators

**Accepted tradeoffs:**

- Increased role complexity: four roles instead of three
- Program coordinators lose access to infrastructure monitoring
- Requires documentation: role boundaries not immediately obvious

**Mitigation:**

- Role documentation: DECISIONS.md and ARCHITECTURE.md document each role's scope
- Runbook: operation manual includes role assignment procedures
- Audit logging: all role changes logged to audit_log

See [ARCHITECTURE.md — Role Hierarchy Diagram](./ARCHITECTURE.md#role-hierarchy-diagram) and [00_Global_Rules_and_Reference.md](./controller_specs/00_Global_Rules_and_Reference.md#role-hierarchy).

---

## 17. Fixed 5-account ceiling for system administration

**Decision:** Hard limit of 5 SYSTEM_ADMIN accounts platform-wide, enforced at both database and application layer. No API endpoint to create or delete — only Flyway migrations.

**Why:**

- Not 1 (single point of failure if credentials compromised or forgotten)
- Not unlimited (security and governance risk)
- 5 provides backup and redundancy without proliferation
- Migration-based creation, not API: prevents privilege escalation bugs

**Accepted tradeoffs:**

- Manual account creation process with database access required
- Exceeding 5 accounts requires direct SQL modification
- Slower onboarding for new system administrators

**Mitigation:**

- Migration-based creation: all accounts logged in version control (audit trail)
- Runbook: documents exact steps to add a SYSTEM_ADMIN via migration
- Count enforcement: application rejects creation via API if limit is exceeded (fail-closed)

---

## 18. Cache eviction throttle with 60-second window

**Decision:** SYSTEM_ADMIN can force-evict caches (individually or all), but throttle prevents more than one eviction per cache per 60 seconds. Throttle state is in-process (not replicated).

**Why:**

- Operational recovery: allows cache refresh without full restart
- DoS prevention: throttle prevents operator from hammering eviction endpoint
- Simple implementation: in-process Map<cacheName, lastEvictionTime>

**Accepted tradeoffs:**

- Throttle state not shared across multi-instance deployments — both instances could evict simultaneously if in the same second
- Operator must wait 60 seconds between evictions — cannot rapid-fire recovery
- Documented limitation: upgrade path is Redis-backed throttle once multi-instance active

**Mitigation:**

- Limitation documented in runbook and ARCHITECTURE.md
- Monitoring: alerts on cache miss patterns (potential stale data trigger)
- Redis upgrade path: when horizontal scaling is needed, throttle state moves to shared Redis

---

## 19. WebSocket push for real-time metrics delivery

**Decision:** System metrics (JVM memory, DB connections, cache stats, uptime) are pushed to subscribed SYSTEM_ADMIN clients via WebSocket every 30 seconds, plus immediately on alarm triggers.

**Why:**

- Lower latency: metrics arrive without polling
- Lower overhead: 30-second cadence is gentle on network vs HTTP polling every 5 seconds
- Real-time event notification: alarms (cache miss spike, connection pool exhaustion) trigger immediate push
- STOMP integration: leverage existing WebSocket infrastructure

**Accepted tradeoffs:**

- Requires persistent connection: SYSTEM_ADMIN dashboard must stay open
- Slight complexity vs stateless REST: requires subscription management
- Multi-instance scaling requires shared metric broker (Redis pub/sub) to avoid silos

**Mitigation:**

- Client reconnect logic: automatic reconnection if WebSocket drops
- STOMP subscription pattern documented in [09_Module_Admin.md](./controller_specs/08_Module_Admin.md)
- Future: Redis pub/sub provides multi-instance metric broadcasting

See [ARCHITECTURE.md — Metrics Push Architecture](./ARCHITECTURE.md#metrics-push-architecture) for complete flow diagram.

---
