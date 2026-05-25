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
