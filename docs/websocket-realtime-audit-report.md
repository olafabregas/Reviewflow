# WebSocket & Realtime Audit Report

**Date:** 2026-05-18  
**Branch:** audit/websocket  
**Tier:** 2 ΓÇö CRITICAL findings block production deploy  
**Flyway:** V34 (messaging V32)  
**Targets scanned:** 15  
**Files scanned:** 18  

---

## Executive Summary

ReviewFlowΓÇÖs STOMP stack is **well secured at CONNECT**: single-use ws-tickets, JWT bridge explicitly disabled, token-version binding, and principal names set to raw numeric user IDs for `convertAndSendToUser`. Server-side push paths consistently use per-user queues, not `/topic` broadcast.

Gaps are mainly **defense-in-depth and abuse resistance**: no SUBSCRIBE destination checks, no WebSocket/STOMP or ws-ticket rate limits, incomplete transport teardown on force logout, missing broker frame size limits, and unconditional SockJS in all profiles. **No CRITICAL** issues were found in static analysis assuming production `CORS_ALLOWED_ORIGINS` is not set to `*`.

| Severity | Count |
|----------|-------|
| CRITICAL | 0 |
| HIGH | 2 |
| MEDIUM | 4 |
| INFO | 4 |

**Top risks**

1. **WS02** ΓÇö Client SUBSCRIBE frames are not validated against the authenticated principal (IDOR risk if broker accepts foreign `/user/{id}/...` destinations).
2. **WS05 / RL04** ΓÇö `/ws/**` and STOMP CONNECT bypass `RateLimitFilter`; ws-ticket issuance has no dedicated throttle.

---

## Findings

### HIGH

#### [RULE-WS02 | HIGH] WebSocketAuthInterceptor.java:40

**Issue:** Inbound channel interceptor only handles `StompCommand.CONNECT`; SUBSCRIBE (and SEND) frames pass through without destination vs principal checks.

**Context:** After CONNECT, a client may attempt `SUBSCRIBE` to another userΓÇÖs `/user/{foreignUserId}/queue/notifications` or `/queue/messages`. SpringΓÇÖs user-destination resolver helps when clients use `/user/queue/...`, but explicit foreign user segments are a known IDOR class without a SUBSCRIBE interceptor or Spring Security messaging rules.

**Snippet:**

```java
if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
  return message;
}
```

**Fix:** Extend `ChannelInterceptor` (or add `StompBrokerRelay` security) to reject SUBSCRIBE destinations where the resolved user segment Γëá `Principal#getName()`. Prefer `spring-security-messaging` `simpDestMatchers().authenticated()` with user-specific matchers.

**Cross-ref:** `@security-auth-audit` RULE-S12.

---

#### [RULE-WS05 | HIGH] RateLimitFilter.java:45 + AuthController.java:190

**Issue:** No application-level rate limit on WebSocket upgrade (`/ws/**` skipped) or on `GET /api/v1/auth/ws-ticket`; no per-session STOMP frame counter.

**Context:** Attackers can flood CONNECT attempts (ticket grinding) or open many SockJS sessions. HTTP `RateLimitFilter` explicitly skips `/ws/`. ws-ticket endpoint is authenticated but uses only generic API buckets after JWT ΓÇö no `AUTH_WS_TICKET` strategy.

**Snippet:**

```java
if (path.startsWith("/ws/")) {
  return true; // shouldNotFilter
}
```

**Fix:** Add `AUTH_WS_TICKET` (per-user + per-IP) on `wsTicket()`; add CONNECT/subscribe throttle in `WebSocketAuthInterceptor` or a dedicated STOMP interceptor (Bucket4j keyed by principal/session).

**Cross-ref:** `@rate-limit-abuse-audit` RULE-RL04.

---

### MEDIUM

#### [RULE-WS06 | MEDIUM] WebSocketForceDisconnectListener.java:20

**Issue:** Force logout clears in-memory session registry and pushes `/queue/session-revoked` but does not disconnect STOMP/WebSocket transport sessions at the broker.

**Context:** After password change / `ForceLogoutEvent`, stale TCP sessions may remain until the client handles the event or reconnects. Registry cleanup prevents *new* routing mistakes but does not close existing connections.

**Snippet:**

```java
webSocketSessionRegistry.clearUser(userId);
webSocketUserEventService.notifySessionsRevoked(userId);
```

**Fix:** Inject `SimpUserRegistry` / `WebSocketSession` registry and call disconnect on each session id from `getSessionIds(userId)` before or after notify; optionally reject non-CONNECT frames when token version is stale.

---

#### [RULE-WS08 | MEDIUM] WebSocketConfig.java:15

**Issue:** `WebSocketMessageBrokerConfigurer` does not override `configureWebSocketTransport` ΓÇö no `setMessageSizeLimit`, `setSendBufferSizeLimit`, or `setSendTimeLimit`.

**Context:** Large STOMP frames can stress heap independently of HTTP multipart limits (FILE02).

**Fix:** Add transport limits aligned with max message attachment policy, e.g. 256KBΓÇô512KB message size, bounded send buffer/time.

---

#### [RULE-WS10 | MEDIUM] WebSocketConfig.java:41

**Issue:** `.withSockJS()` is registered unconditionally; no prod profile disable or documented threat model.

**Context:** SockJS adds xhr-streaming/jsonp downgrade surface; combined with misconfigured CORS (WS04) increases cross-site risk.

**Fix:** Profile-specific config: native WebSocket only in prod (`websocket.sockjs.enabled=false`), or document accepted transports in PRD/ops runbook.

---

#### [RULE-WS05 | MEDIUM] WebSocketAuthInterceptor.java (inbound channel)

**Issue:** No per-principal or per-session counter for inbound STOMP `SEND`/`SUBSCRIBE` after CONNECT.

**Context:** Presence-only traffic is lower risk, but broker fan-out from malicious SEND (if handlers are added later) is unbounded.

**Fix:** Optional token-bucket in interceptor keyed by `sessionId` + command type; INFO-only acceptable if product confirms client-only SUBSCRIBE with no `@MessageMapping`.

---

### INFO

#### [RULE-WS04 | INFO] WebSocketConfig.java:40

**Issue:** Origins come from `${app.cors.allowed-origins}` via `setAllowedOriginPatterns` ΓÇö safe in code, but **mis-set env** `CORS_ALLOWED_ORIGINS=*` would be CRITICAL at runtime.

**Fix:** Startup validator rejecting `*` when credentials/tickets are used; align with `SecurityConfig` HTTP CORS allowlist.

---

#### [RULE-WS07 | INFO] Message.java + SystemMessagingService.java

**Issue:** No `is_system` / `messageType` on `Message` entity (V32); `SystemMessagingService` only exposes moderation reads ΓÇö no automated system-authored sends yet.

**Context:** When system/broadcast messages are implemented, schema and API must distinguish system origin from human `sender_id`.

**Fix:** Add migration column + DTO flag before implementing system send paths.

---

#### [RULE-WS06 | INFO] WsTicketService.java:33

**Issue:** Tickets stored in local Caffeine cache ΓÇö multi-node deploy: ticket issued on instance A may fail CONNECT on instance B unless sticky sessions or Redis-backed ticket store.

**Fix:** Redis cache for `WsTicketPayload` when horizontally scaling.

---

#### [RULE-WS01 | INFO] WebSocketAuthInterceptor.java:40

**Issue:** Non-CONNECT STOMP frames are not re-checked for principal presence or token version (rely on CONNECT-only auth).

**Fix:** Reject frames with null user on inbound channel; optional periodic token-version check on SUBSCRIBE.

---

## Clean Targets (no violations)

| # | Target | Notes |
|---|--------|-------|
| 1 | `WebSocketConfig.java` | WS04/WS10 findings only; broker prefixes correct |
| 2 | `WebSocketAuthInterceptor.java` | WS01 satisfied: ticket consume, version check, JWT rejected |
| 3 | `WebSocketSessionRegistry.java` | Register/remove/clearUser implemented |
| 4 | `WebSocketPrincipalDetails` (nested) | Raw user id as `getUsername()` for STOMP |
| 6 | `WebSocketPresenceListener.java` | DISCONNECT removes session from registry |
| 7 | `WebSocketUserEventService.java` | `convertAndSendToUser` for session-revoked |
| 8 | `WsTicketService.java` | One-time `remove` on consume; SecureRandom ticket |
| 9 | `AuthController` ws-ticket | Authenticated issuance; deprecated JWT WS token removed |
| 10 | `MessagingService` push | Participant-scoped `pushToRecipient`; Redis pub/sub fan-out |
| 11 | `SystemMessagingService` | Moderation only ΓÇö no sender impersonation |
| 12 | `SimpMessagingTemplate` grep | All uses `convertAndSendToUser`; no `/topic` confidential push |
| 13 | `@MessageMapping` / `@SubscribeMapping` | None in codebase ΓÇö WS09 N/A |
| 15 | `SecurityConfig` | `/ws/**` permitAll + STOMP ticket auth is intentional pattern |

**Law 2 note:** `WebSocketAuthInterceptor` uses `UserRepository` directly on CONNECT ΓÇö acceptable for auth boundary; consider delegating to `UserService` for consistency.

---

## Rule Coverage Matrix

| Rule | Status | Hit count |
|------|--------|-----------|
| WS01 | PASS | 0 |
| WS02 | FAIL | 1 |
| WS03 | PASS | 0 |
| WS04 | PASS* | 1 INFO (*env-dependent) |
| WS05 | FAIL | 2 |
| WS06 | PARTIAL | 2 |
| WS07 | N/A / INFO | 1 |
| WS08 | FAIL | 1 |
| WS09 | PASS | 0 |
| WS10 | FAIL | 1 |

---

## Recommended Action Plan

1. **SUBSCRIBE guard (WS02)** ΓÇö Highest security ROI; block foreign `/user/{id}/` destinations.
2. **Rate limits (WS05 / RL04)** ΓÇö ws-ticket bucket + CONNECT throttle per IP/user.
3. **Force disconnect (WS06)** ΓÇö Close transport sessions on `ForceLogoutEvent`.
4. **Transport limits (WS08)** ΓÇö `configureWebSocketTransport` size/time caps.
5. **Prod SockJS policy (WS10)** ΓÇö Profile toggle or documented acceptance.
6. **Horizontal scale (INFO)** ΓÇö Redis-backed ws-tickets when running multiple app instances.

---

## Scan Errors

None. `WebSocketPrincipalDetails` is implemented as a private inner class in `WebSocketAuthInterceptor.java` (not a standalone file) ΓÇö reviewed as target #4.

---

## Files Reviewed

`WebSocketConfig.java`, `WebSocketAuthInterceptor.java`, `WebSocketSessionRegistry.java`, `WebSocketForceDisconnectListener.java`, `WebSocketPresenceListener.java`, `WebSocketUserEventService.java`, `WsTicketService.java`, `AuthController.java` (ws-ticket), `MessagingService.java`, `SystemMessagingService.java`, `NotificationEventListener.java`, `SystemService.java`, `MessagingRedisSubscriber.java`, `MessagingRedisConfig.java`, `RateLimitFilter.java`, `SecurityConfig.java`, `application.properties`, `application-prod.properties`.
