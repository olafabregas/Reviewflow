# ReviewFlow — Design Decisions

This document captures the reasoning behind key architectural choices in ReviewFlow — not just what was built, but why, and what was accepted in return.

For system flows and diagrams see [ARCHITECTURE.md](./ARCHITECTURE.md).  
For the project overview see [README.md](./README.md).

-----

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

-----

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

-----

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

-----

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

-----

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

-----

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

-----

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

-----

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

|Profile|ClamAV unavailable           |
|-------|-----------------------------|
|`local`|Upload proceeds (fail-open)  |
|`prod` |Upload rejected (fail-closed)|

-----

## 8. Draft/publish lifecycle for evaluations

**Decision:** Evaluations are created as `is_draft=true` and are completely invisible to students. A draft evaluation returns `404`, not `403`, when requested by a student.

**Why:**

- Returning `403` would tell the student “this evaluation exists but you can’t see it” — revealing information before the instructor is ready to publish
- `404` reveals nothing — from the student’s perspective, the evaluation simply does not exist yet
- Instructors need to be able to save, score, and review evaluations before students can see them — the draft state enables this without time pressure

**Accepted tradeoffs:**

- The distinction between “doesn’t exist” and “exists but draft” is invisible to students — this is intentional
- Once published, an evaluation cannot be unpublished — publication is a permanent transition

-----

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