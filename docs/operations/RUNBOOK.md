# ReviewFlow Operations Runbook

## Observability Alerting Thresholds (ReviewFlow)

`reviewflow.ratelimit.check_failed` > 10/min
  → Check Redis connectivity (fail-open rate limits now in effect)

`reviewflow.security.login{result=failed}` 3× baseline over 15m
  → Review lockout config + WAF rules

`reviewflow.security.login{result=unknown_user}` spike
  → Credential stuffing investigation

`reviewflow.security.lockout` rate > baseline
  → Security notification

`reviewflow.clamav.malware.detected` ≥ 1
  → Page security; quarantine S3 object immediately

`reviewflow.job.failed` > 0 sustained 5m
  → Check csvWorkerExecutor pool size and job logs

`reviewflow.pdf.generation.failed` > 0 sustained 5m
  → Check pdfExecutor pool and CloudFront signed URL config

Redis health DOWN (`GET /actuator/health/redis`)
  → Ops alert; fail-open rate limits in effect; job creation degraded

## Notes

DO NOT ALERT on WebSocket `db.activeConnections` == 0 before
verifying HikariCP wiring (was stub zero — now wired via HIGH-1).
Establish a 30-day baseline after deploy before setting thresholds.

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
