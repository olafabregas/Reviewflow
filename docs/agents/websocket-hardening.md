# Agent — WebSocket & Realtime Hardening

**Status:** Final  
**Author:** Roqeeb Olamide Ayorinde  
**Source:** WebSocket & Realtime Audit Report 2026-05-18 (10 findings)  
**Tier:** 2 — HIGH findings block production  
**Migration:** None

---

## How to Invoke This Agent

```
@docs/agents/websocket-hardening.md fix all
```

Other commands:

| Command | What it does |
|---|---|
| `fix all` | Execute HIGH → MEDIUM → Security Validator in order |
| `fix high` | HIGH-1 force disconnect only |
| `fix medium` | MEDIUM-1 and MEDIUM-2 only |
| `fix validator` | SecurityStartupValidator only |
| `verify` | Run full verification checklist, no code changes |
| `status` | Show which tiers are complete and which remain |

---

## Already Covered — Verify Exist, Do Not Re-Implement

Before starting, confirm these exist in the codebase:

| Finding | Covered in |
|---|---|
| SUBSCRIBE destination validation | PRD-Security-Auth-Hardening HIGH-4 |
| WS ticket rate limiting (`AUTH_WS_TICKET`) | PRD-RateLimit-Hardening HIGH-2 |
| CORS prod property mismatch | PRD-Security-Auth-Hardening CRITICAL-1 |

---

## Agent Instructions — Read Before Starting

You are a senior Spring Boot engineer executing a structured WebSocket and realtime hardening pass on ReviewFlow, a **Spring Boot 4.0.x / Java 21** academic submission and grading platform.

**Rules:**
- HIGH-1 must be verified before MEDIUM work begins.
- **Read `WebSocketForceDisconnectListener`, `SubProtocolWebSocketHandler`, and `WebSocketSessionRegistry` before touching any of them.** The hard-disconnect implementation must use whatever raw session access the codebase already provides — do not assume `subProtocolHandler.getWebSocketSessions()` exists.
- The `SecurityStartupValidator` is an addition to PRD-Security-Auth-Hardening P0-1 — implement it here if it was not already implemented there. Check first.
- INFO items are deferred — document in RUNBOOK.md only, no code.

**Files likely touched:**

| File | Path |
|---|---|
| WebSocketForceDisconnectListener | `infrastructure/websocket/WebSocketForceDisconnectListener.java` |
| WebSocketConfig | `infrastructure/websocket/WebSocketConfig.java` |
| SecurityStartupValidator | `infrastructure/config/SecurityStartupValidator.java` (new, if not already exists) |
| application.properties | `src/main/resources/application.properties` |
| application-prod.properties | `src/main/resources/application-prod.properties` |
| RUNBOOK.md | `orchestration/RUNBOOK.md` |

---

## Locked Decisions

| Decision | Choice |
|---|---|
| Force logout WS | Four-step sequence: revoke → notify → hard disconnect → 401 on reconnect |
| SockJS in prod | Property toggle: `websocket.sockjs.enabled=false` in prod |
| CORS `*` validator | Non-optional `@Profile("prod")` startup validator |
| Transport limits | 256KB message size, 512KB send buffer, 10s send timeout — all `@Value` backed |

---

## Execution Protocol for `fix all`

```
STEP 1 — Pre-flight
  Verify "Already Covered" items exist in codebase
  Check if SecurityStartupValidator already exists from PRD-Security-Auth-Hardening
  Read WebSocketForceDisconnectListener fully
  Read WebSocketSessionRegistry + SubProtocolWebSocketHandler to understand raw session access

STEP 2 — Create fix branch
  git checkout -b fix/websocket-hardening

STEP 3 — HIGH-1: Force logout four-step sequence
  Verify before advancing

STEP 4 — MEDIUM-1: Transport size and time limits

STEP 5 — MEDIUM-2: SockJS profile toggle

STEP 6 — Security Startup Validator
  Skip if already implemented in PRD-Security-Auth-Hardening

STEP 7 — Update RUNBOOK.md with INFO deferred items

STEP 8 — Run full verification checklist
```

---

## HIGH-1 · WebSocketForceDisconnectListener — Four-Step Force Disconnect

**File:** `infrastructure/websocket/WebSocketForceDisconnectListener.java`  
**Issue:** Force logout clears the in-memory session registry and pushes `/queue/session-revoked` but does not close transport sessions. A client that ignores the push event can auto-reconnect immediately — it still holds a valid token so the new WebSocket session is accepted.

**The reconnect race — why notify-only is insufficient:**

```
Force logout triggered
  → /queue/session-revoked pushed ✓
  → Client ignores event and auto-reconnects immediately
  → Client still has valid token (not yet expired)
  → New WebSocket session accepted
  → Token expires naturally — minutes or hours later
```

**Correct four-step sequence — order is mandatory:**

```
1. Token revoked in auth store       ← closes the reconnect window FIRST
2. Push /queue/session-revoked       ← well-behaved clients disconnect cleanly
3. Hard close transport sessions     ← malicious/buggy clients are cut off
4. Any reconnect attempt hits 401    ← token already invalid, reconnect fails
```

Step 1 must complete before step 3. Without it, step 3 just delays reconnect by milliseconds.

**Ordering guarantee:** `ForceLogoutEvent` fires in `SystemService.forceLogout()` after `userRepository.incrementTokenVersion(userId)` — which means the token is already invalid in the DB when this listener runs. Both `TokenVersionInvalidatedEvent` and `ForceLogoutEvent` are published in the same service method and handled post-commit via `@TransactionalEventListener(AFTER_COMMIT)`.

**Fix:**

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketForceDisconnectListener {

    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketUserEventService userEventService;
    private final SimpUserRegistry simpUserRegistry;
    private final SubProtocolWebSocketHandler subProtocolHandler;
    // SubProtocolWebSocketHandler manages raw WS sessions
    // VERIFY: check if getWebSocketSessions() exists before writing
    // If not: use WebSocketSessionRegistry which may store raw WebSocketSession objects

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleForceDisconnect(ForceLogoutEvent event) {
        Long userId = event.getUserId();
        String hashedUserId = event.getHashedUserId();

        log.info("Force disconnect initiated userId={}", hashedUserId);

        // Step 1: Token already revoked before this fires (ordering guarantee above)

        // Step 2: Notify — push /queue/session-revoked for well-behaved clients
        userEventService.notifySessionsRevoked(userId);

        // Step 3: Hard disconnect — close all transport sessions for this user
        Set<String> sessionIds = getSessionIds(userId);
        if (sessionIds.isEmpty()) {
            log.debug("No active WS sessions for userId={}", hashedUserId);
        } else {
            log.info("Closing {} WS sessions for userId={}",
                sessionIds.size(), hashedUserId);
            sessionIds.forEach(sessionId -> closeSession(sessionId, hashedUserId));
        }

        // Step 4: Automatic — any reconnect attempt finds revoked token → 401
        // (WebSocketAuthInterceptor on CONNECT + JwtAuthenticationFilter)

        // Clear registry AFTER disconnect attempts
        sessionRegistry.clearUser(userId);
    }

    private Set<String> getSessionIds(Long userId) {
        // SimpUserRegistry tracks STOMP-level sessions by principal name
        // Principal name = raw userId as string (verify from existing implementation)
        SimpUser simpUser = simpUserRegistry.getUser(String.valueOf(userId));
        if (simpUser == null) return Set.of();
        return simpUser.getSessions().stream()
            .map(SimpSession::getId)
            .collect(Collectors.toSet());
    }

    private void closeSession(String sessionId, String hashedUserId) {
        try {
            // VERIFY before writing: check whether SubProtocolWebSocketHandler
            // exposes getWebSocketSessions() or equivalent.
            // If WebSocketSessionRegistry already stores raw WebSocketSession objects,
            // use that instead. Read both classes before finalising this method.
            WebSocketSession rawSession = subProtocolHandler
                .getWebSocketSessions().get(sessionId);
            if (rawSession != null && rawSession.isOpen()) {
                rawSession.close(CloseStatus.POLICY_VIOLATION);
                // CloseStatus 1008 = POLICY_VIOLATION
                // Clients must NOT auto-reconnect on 1008 (unlike 1006 abnormal closure)
                log.debug("WS session closed sessionId={} userId={}",
                    sessionId, hashedUserId);
            }
        } catch (IOException e) {
            // Session may already be closing — log and continue
            log.warn("Error closing WS session sessionId={}: {}",
                sessionId, e.getMessage());
        }
    }
}
```

**Verify:**
```
Force logout via POST /system/force-logout:
  Step 1: Token version incremented + committed (pre-condition)
  Step 2: /queue/session-revoked pushed to target user
  Step 3: All WS sessions closed with CloseStatus 1008 POLICY_VIOLATION
  Step 4: Client reconnects → WebSocketAuthInterceptor → token version check → 401

No active WS sessions: force logout completes without error (empty set handled)
IOException on session close: WARN logged, continues to next session (not fatal)
Client re-establishes session after force-logout: 401 on CONNECT
```

---

## MEDIUM-1 · WebSocketConfig — Transport Size and Time Limits

**File:** `infrastructure/websocket/WebSocketConfig.java`  
**Issue:** `configureWebSocketTransport` not overridden. No message size limit, send buffer limit, or send timeout. Large STOMP frames can exhaust JVM heap independently of HTTP multipart limits.

**Read the file first** — verify whether `configureWebSocketTransport` is already overridden. If it is, ensure all three limits are wired and `@Value`-backed.

```java
// WebSocketConfig.java — add override:

@Value("${websocket.transport.message-size-limit:262144}")   // 256KB default
private int messageSizeLimitBytes;

@Value("${websocket.transport.send-buffer-size-limit:524288}") // 512KB default
private int sendBufferSizeLimitBytes;

@Value("${websocket.transport.send-time-limit:10000}")         // 10 seconds default
private int sendTimeLimitMs;

@Override
public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration
        // Max inbound STOMP frame size
        // ReviewFlow STOMP frames carry notification payloads, message previews (~80 chars),
        // and job status updates — never raw file bytes (those go through HTTP multipart)
        // 256KB is generous for all current use cases
        .setMessageSizeLimit(messageSizeLimitBytes)

        // Max outbound send buffer per session — prevents slow-client memory buildup
        .setSendBufferSizeLimit(sendBufferSizeLimitBytes)

        // Max time (ms) to wait for message send before blocking the message thread
        .setSendTimeLimit(sendTimeLimitMs);
}
```

**Add to `application.properties`:**

```properties
# ─── WebSocket transport limits ───────────────────────────────────────────────
websocket.transport.message-size-limit=${WS_MESSAGE_SIZE_LIMIT:262144}
websocket.transport.send-buffer-size-limit=${WS_SEND_BUFFER_LIMIT:524288}
websocket.transport.send-time-limit=${WS_SEND_TIME_LIMIT:10000}
```

**Verify:**
```
STOMP frame > 256KB → connection closed by server (not heap-exhausted)
Normal notification frame (~1KB) → delivered normally (regression)
Slow client stalled for > 10s → send timeout fires, session closed
All three limits wired via @Value — not hardcoded
```

---

## MEDIUM-2 · WebSocketConfig — SockJS Profile Toggle

**File:** `infrastructure/websocket/WebSocketConfig.java`  
**Issue:** `.withSockJS()` registered unconditionally. In production, SockJS adds xhr-streaming and JSONP downgrade transports that increase CORS attack surface. Native WebSocket is the correct prod transport.

**Read `registerStompEndpoints` first** — verify the current allowed origins config binding and endpoint path before modifying.

```java
@Value("${websocket.sockjs.enabled:true}")
private boolean sockJsEnabled;

@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    StompWebSocketEndpointRegistration endpoint = registry
        .addEndpoint("/ws")
        .setAllowedOriginPatterns(allowedOriginsConfig.split(","));
        // allowedOriginsConfig — verify @Value binding name from existing code

    if (sockJsEnabled) {
        endpoint.withSockJS();
        // Local dev: SockJS for broader browser/tooling compatibility
    }
    // Prod: native WebSocket only — no SockJS xhr/jsonp downgrade paths
}
```

**Add to `application.properties`:**

```properties
# ─── WebSocket SockJS ─────────────────────────────────────────────────────────
websocket.sockjs.enabled=${WS_SOCKJS_ENABLED:true}
```

**Add to `application-prod.properties`:**

```properties
websocket.sockjs.enabled=false
```

**Frontend note (document in RUNBOOK.md):** The React frontend must use `new SockJS(url)` locally and `new WebSocket(url)` in production. This is a one-line client-side change — document before any React WebSocket code is written.

**Verify:**
```
Local profile: SockJS polling transports available at /ws
Prod profile: native WebSocket only — SockJS endpoint returns 404
websocket.sockjs.enabled=false in application-prod.properties
```

---

## Security Startup Validator

**Check first:** Verify whether `SecurityStartupValidator.java` already exists from PRD-Security-Auth-Hardening. If it does, skip this section entirely.

**File:** `infrastructure/config/SecurityStartupValidator.java` (new, if not already present)

**Why non-optional:** A misconfigured `CORS_ALLOWED_ORIGINS=*` in prod silently passes all property checks and only becomes visible when an attacker discovers it. This validator fails fast at startup with a clear error — before any traffic is served.

```java
@Component
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class SecurityStartupValidator {

    @Value("${app.cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${security.token.fingerprinting-enabled:false}")
    private boolean fingerprintingEnabled;

    @PostConstruct
    public void validate() {
        List<String> violations = new ArrayList<>();

        // CORS: must be set, must not be wildcard
        if (corsAllowedOrigins.isBlank()) {
            violations.add("app.cors.allowed-origins must be set in production " +
                "(CORS_ALLOWED_ORIGINS env var is empty)");
        }
        Arrays.stream(corsAllowedOrigins.split(","))
            .map(String::trim)
            .filter(o -> o.equals("*") || o.equals("*://*"))
            .findAny()
            .ifPresent(wildcard -> violations.add(
                "app.cors.allowed-origins must not contain wildcard '*' in production. " +
                "Current value: " + corsAllowedOrigins));

        // Cookie security flag
        if (!cookieSecure) {
            violations.add("app.cookie.secure must be true in production");
        }

        // Token fingerprinting
        if (!fingerprintingEnabled) {
            violations.add(
                "security.token.fingerprinting-enabled must be true in production");
        }

        if (!violations.isEmpty()) {
            String message = "PRODUCTION SECURITY MISCONFIGURATION:\n" +
                String.join("\n", violations.stream()
                    .map(v -> "  - " + v)
                    .toList());
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.info("Security startup validation passed (prod profile)");
    }
}
```

**`@Profile("prod")`** — only executes in production. Local dev with `app.cookie.secure=false` does not trigger this validator. Application does not start if any violation is found — ops cannot accidentally deploy a broken security config.

**Verify:**
```
Prod profile + CORS_ALLOWED_ORIGINS=* → startup failure with clear error message
Prod profile + empty CORS_ALLOWED_ORIGINS → startup failure
Prod profile + wildcard pattern *://* → startup failure
Prod profile + all correct settings → starts normally, logs "Security startup validation passed"
Local profile + any settings → validator not executed (no failure)
```

---

## RUNBOOK.md Additions

Add these entries to `orchestration/RUNBOOK.md`:

```markdown
## WebSocket Scaling Checklist

### WS Tickets — Redis Migration (when horizontally scaling)
WsTicketService stores tickets in Caffeine cache (node-local).
On multi-node: a ticket issued by instance A may fail CONNECT on instance B.

When horizontal scaling is enabled:
  Replace Caffeine with Redis in WsTicketService:
  Key: reviewflow:wsticket:{ticketId}
  TTL: ticket validity window (e.g. 30 seconds)
  Use RedisTemplate.opsForValue().setIfAbsent() + getAndDelete() for one-time use
  Redis infrastructure already in place (PRD-19) — estimated 1-day migration.

### SockJS Client Toggle (for React frontend)
Local dev:  new SockJS(url)   — SockJS client required
Production: new WebSocket(url) — native WebSocket only
Controlled by: websocket.sockjs.enabled (true=local, false=prod)
Must be handled before any React WebSocket code is written.

### Per-STOMP Frame Rate Limit (deferred)
No @MessageMapping exists in the codebase — all messaging goes through REST API.
If @MessageMapping is ever added:
  Add a per-session Bucket4j token bucket to the inbound channel interceptor
  BEFORE the endpoint is used in production.
  No rate limit on STOMP frames is acceptable only while no @MessageMapping exists.

### System Messages Schema (deferred)
When system-authored messages are implemented:
  Add message_type ENUM('USER', 'SYSTEM') to messages table via new migration
  sender_id becomes nullable for SYSTEM type
  Do not implement until @MessageMapping paths exist
```

---

## Verification Checklist

Run in full before opening the PR.

### HIGH — Force Disconnect
```
[ ] Force logout: /queue/session-revoked pushed to target user
[ ] Force logout: all WS sessions closed with CloseStatus 1008 POLICY_VIOLATION
[ ] Force logout: reconnect attempt → 401 (token invalid)
[ ] Force logout: token revocation happens BEFORE transport close (ordering)
[ ] ForceLogoutEvent listener uses @TransactionalEventListener(AFTER_COMMIT)
[ ] No active WS sessions: force logout completes without error
[ ] IOException on session close: WARN logged, continues to next session
```

### MEDIUM — Transport Limits
```
[ ] STOMP frame > 256KB → server closes connection
[ ] Normal ~1KB notification frame → delivered normally (regression)
[ ] Slow client stalled > 10s → send timeout fires, session closed
[ ] messageSizeLimitBytes wired via @Value (not hardcoded)
[ ] sendBufferSizeLimitBytes wired via @Value (not hardcoded)
[ ] sendTimeLimitMs wired via @Value (not hardcoded)
```

### MEDIUM — SockJS Toggle
```
[ ] Local profile: SockJS transports available at /ws
[ ] Prod profile: native WebSocket only, SockJS endpoint returns 404
[ ] websocket.sockjs.enabled=false in application-prod.properties
[ ] WebSocket still functional in prod with native transport
```

### Security Startup Validator
```
[ ] Prod profile + CORS_ALLOWED_ORIGINS=* → startup failure with clear message
[ ] Prod profile + empty CORS → startup failure
[ ] Prod profile + *://* pattern → startup failure
[ ] Prod profile + all correct settings → starts normally
[ ] Local profile → validator not executed
[ ] SecurityStartupValidator annotated @Profile("prod")
```

### Already Covered — Verify in Other PRDs
```
[ ] SUBSCRIBE to /user/{otherId}/queue → rejected (PRD-Security-Auth-Hardening HIGH-4)
[ ] 31st ws-ticket request/15min → 429 (PRD-RateLimit-Hardening HIGH-2)
[ ] CORS: app.cors.allowed-origins set correctly in prod (PRD-Security-Auth-Hardening CRITICAL-1)
```

### Regression
```
[ ] Normal WebSocket CONNECT still works (local + prod)
[ ] Notification delivery still works after transport limit changes
[ ] Force logout does not affect other users' sessions
[ ] RUNBOOK.md updated with all three deferred items
```

---

## Summary of All Changed Files

| File | Change |
|---|---|
| `WebSocketForceDisconnectListener.java` | Four-step sequence: push notify → hard close with `CloseStatus.POLICY_VIOLATION` via `SimpUserRegistry` + `SubProtocolWebSocketHandler`; `@TransactionalEventListener(AFTER_COMMIT)` |
| `WebSocketConfig.java` | `configureWebSocketTransport()` override with `@Value`-backed size/time limits; SockJS conditional on `websocket.sockjs.enabled` property |
| `application.properties` | WS transport limit properties; `websocket.sockjs.enabled=true` (default) |
| `application-prod.properties` | `websocket.sockjs.enabled=false` |
| `SecurityStartupValidator.java` | New — `@Profile("prod")` `@PostConstruct` validator: CORS wildcard check, cookie.secure, fingerprinting |
| `orchestration/RUNBOOK.md` | WS tickets Redis migration path; SockJS client toggle note; per-STOMP rate limit deferred note; system messages schema deferred note |
