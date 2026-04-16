# ReviewFlow — Module 11: System Admin

> Controller: `SystemAdminController.java`  
> Base path: `/api/v1/system`

---

## 11.1 Module Overview

The System Admin module provides platform operators (`SYSTEM_ADMIN` role) with infrastructure monitoring, cache management, security event auditing, and emergency intervention capabilities. This module is separate from the `ADMIN` role, which handles academic operations (user management, courses, grades).

### Key Distinctions

- **SYSTEM_ADMIN** (platform operator): monitors infrastructure health, manages caches, views security events, can force-logout users and reopen evaluations in exceptional circumstances
- **ADMIN** (academic admin): manages courses, grades, announcements, and academic users
- **STUDENT/INSTRUCTOR**: cannot access any /system/\* endpoints

### Scope

- Real-time system metrics (JVM memory, database connections, instance health)
- Cache statistics and per-cache eviction with automatic throttling
- Security event audit log (login failures, permission denials, system overrides)
- Emergency operations: force user logout, unlock teams, reopen evaluations
- Multi-instance consideration: throttle state is in-process (not shared); Redis upgrade path documented in ARCHITECTURE.md

### SYSTEM_ADMIN Role Requirements

- **Hard limit:** Maximum 5 accounts platform-wide (enforced at DB and application level)
- **Immutable creation:** Created only via Flyway migrations, never via API
- **Cannot self-manage:** One SYSTEM_ADMIN cannot force-logout another SYSTEM_ADMIN
- **Full audit trail:** All actions logged to `audit_log` table as `OVERRIDE` entries

---

## 11.2 Authentication & Authorization

### Permission Model

- **Endpoint protection:** All `/api/v1/system/*` endpoints require `SYSTEM_ADMIN` role
- **No delegation:** `SYSTEM_ADMIN` role cannot be delegated; hierarchy check is explicit (no "or higher" fallback)
- **Deactivated accounts:** If `is_active = false`, returns `403 FORBIDDEN` with code `ACCOUNT_DEACTIVATED` even if role is correct
- **Token validation:** Missing or expired access token returns `401 UNAUTHORIZED`

### Role Examples

```
GET  /api/v1/system/cache/stats        → SYSTEM_ADMIN required (403 if ADMIN)
POST /api/v1/system/cache/evict/{name} → SYSTEM_ADMIN required (403 if INSTRUCTOR)
GET  /api/v1/system/security/events    → SYSTEM_ADMIN required (403 if STUDENT)
```

### Multi-Instance Considerations

- Each deployed instance has its own in-process throttle state (60-second eviction cooldown)
- Both instances could evict the same cache simultaneously if requests coincide
- **Future upgrade:** Redis-backed throttle for cluster-wide coordination (see Decision 18 in DECISIONS.md)

---

## 11.3 API Endpoints Summary

| Endpoint                                   | Method | Purpose                                                       | Auth         | Rate Limit  |
| ------------------------------------------ | ------ | ------------------------------------------------------------- | ------------ | ----------- |
| `/cache/stats`                             | GET    | Retrieve cache hit/miss/size stats for all caches             | SYSTEM_ADMIN | Standard    |
| `/cache/evict/{cacheName}`                 | POST   | Evict a single cache; throttled 60s per cache                 | SYSTEM_ADMIN | 429 if <60s |
| `/config`                                  | GET    | List available caches and configuration metadata              | SYSTEM_ADMIN | Standard    |
| `/security/events`                         | GET    | Last 100 security events (login failures, denials, overrides) | SYSTEM_ADMIN | Standard    |
| `/users/{targetUserId}/force-logout`       | POST   | Terminate all active sessions for a user                      | SYSTEM_ADMIN | Standard    |
| `/teams/{teamId}/unlock`                   | POST   | Unlock a locked team (manual bypass for deadlock scenarios)   | SYSTEM_ADMIN | Standard    |
| `/evaluations/{evaluationId}/reopen`       | POST   | Reopen a published evaluation to draft state                  | SYSTEM_ADMIN | Standard    |

---

## 11.4 Endpoint Specifications

### 11.4.1 GET /api/v1/system/cache/stats

**Purpose:** Retrieve statistics for all registered caches (hits, misses, evictions, current size).

**Auth:** SYSTEM_ADMIN only

**Request:**

```http
GET /api/v1/system/cache/stats HTTP/1.1
Host: localhost:8081
Cookie: access_token=<JWT>
```

**Response — 200 OK:**

```json
{
  "success": true,
  "data": {
    "caches": [
      {
        "name": "adminStats",
        "size": 42,
        "estimatedMemoryBytes": 204800,
        "hits": 1250,
        "misses": 89,
        "hitRate": 0.9334,
        "lastEvictedAt": "2026-03-30T12:05:30Z"
      },
      {
        "name": "unreadCount",
        "size": 156,
        "estimatedMemoryBytes": 512000,
        "hits": 3421,
        "misses": 231,
        "hitRate": 0.9367,
        "lastEvictedAt": "2026-03-30T11:45:15Z"
      },
      {
        "name": "userCourses",
        "size": 287,
        "estimatedMemoryBytes": 1843200,
        "hits": 5834,
        "misses": 412,
        "hitRate": 0.9338,
        "lastEvictedAt": null
      },
      {
        "name": "assignmentDetail",
        "size": 73,
        "estimatedMemoryBytes": 614400,
        "hits": 892,
        "misses": 67,
        "hitRate": 0.9304,
        "lastEvictedAt": "2026-03-30T09:20:00Z"
      },
      {
        "name": "courseGradeGroups",
        "size": 19,
        "estimatedMemoryBytes": 184320,
        "hits": 410,
        "misses": 33,
        "hitRate": 0.9255,
        "lastEvictedAt": "2026-03-30T12:44:00Z"
      }
    ],
    "timestamp": "2026-03-30T14:22:10Z",
    "instanceId": "prod-instance-1"
  },
  "timestamp": "2026-03-30T14:22:10Z"
}
```

**Response — 403 Forbidden (insufficient role):**

```json
{
  "success": false,
  "error": {
    "code": "FORBIDDEN",
    "message": "SYSTEM_ADMIN role required"
  },
  "timestamp": "2026-03-30T14:22:10Z"
}
```

---

### 11.4.2 POST /api/v1/system/cache/evict/{cacheName}

**Purpose:** Evict a single named cache. Throttled to once per 60 seconds per cache.

**Auth:** SYSTEM_ADMIN only

**Path Parameters:**
| Name | Type | Required | Notes |
|------|------|----------|-------|
| `cacheName` | String | ✓ | One of: `adminStats`, `unreadCount`, `userCourses`, `assignmentDetail` |

**Request:**

```http
POST /api/v1/system/cache/adminStats/evict HTTP/1.1
Host: localhost:8081
Cookie: access_token=<JWT>
Content-Type: application/json

{
  "reason": "Stale instructor permissions after role change"
}
```

**Response — 200 OK:**

```json
{
  "success": true,
  "data": {
    "cacheName": "adminStats",
    "evictedAt": "2026-03-30T14:22:15Z",
    "entriesRemoved": 42,
    "nextEvictionEligibleAt": "2026-03-30T14:23:15Z"
  },
  "timestamp": "2026-03-30T14:22:15Z"
}
```

**Response — 400 Bad Request (unknown cache):**

```json
{
  "success": false,
  "error": {
    "code": "UNKNOWN_CACHE",
    "message": "Cache 'invalidCacheName' not found. Valid names: adminStats, unreadCount, userCourses, assignmentDetail"
  },
  "timestamp": "2026-03-30T14:22:15Z"
}
```

**Response — 429 Too Many Requests (throttled):**

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 45

{
  "success": false,
  "error": {
    "code": "EVICTION_TOO_SOON",
    "message": "Cache 'adminStats' evicted 15 seconds ago. Must wait 45 more seconds."
  },
  "timestamp": "2026-03-30T14:22:15Z"
}
```

**Note:** Response includes `Retry-After` header in seconds, indicating safe wait time.

---

### 11.4.3 GET /api/v1/system/config

**Purpose:** List available caches, configuration metadata, and instance info. Used by operators to discover cache names for eviction.

**Auth:** SYSTEM_ADMIN only

**Request:**

```http
GET /api/v1/system/config HTTP/1.1
Host: localhost:8081
Cookie: access_token=<JWT>
```

**Response — 200 OK:**

```json
{
  "success": true,
  "data": {
    "instanceId": "prod-instance-1",
    "appVersion": "1.2.3",
    "javaVersion": "21.0.1",
    "springBootVersion": "3.2.0",
    "cacheNames": [
      {
        "name": "adminStats",
        "type": "Caffeine",
        "maxSize": 1000,
        "ttlMinutes": 30
      },
      {
        "name": "unreadCount",
        "type": "Caffeine",
        "maxSize": 10000,
        "ttlMinutes": 15
      },
      {
        "name": "userCourses",
        "type": "Caffeine",
        "maxSize": 10000,
        "ttlMinutes": 60
      },
      {
        "name": "assignmentDetail",
        "type": "Caffeine",
        "maxSize": 5000,
        "ttlMinutes": 45
      }
    ],
    "metricsEnabled": true,
    "auditLogEnabled": true,
    "clusterMode": false,
    "timestamp": "2026-03-30T14:22:20Z"
  },
  "timestamp": "2026-03-30T14:22:20Z"
}
```

---

### 11.4.4 GET /api/v1/system/security/events

**Purpose:** Retrieve the last 100 security events: login failures, permission denials, admin overrides, and suspicious activities.

**Auth:** SYSTEM_ADMIN only

**Query Parameters:**
| Parameter | Type | Default | Notes |
|-----------|------|---------|-------|
| `page` | Integer | `0` | Zero-indexed page (100 events per page in fixed window) |
| `eventType` | String | (none) | Filter: `LOGIN_FAILURE`, `PERMISSION_DENIED`, `OVERRIDE`, `ACCOUNT_DEACTIVATED` |
| `startDate` | ISO 8601 | (none) | Filter: events on or after this timestamp |

**Request:**

```http
GET /api/v1/system/security/events?eventType=LOGIN_FAILURE&startDate=2026-03-30T00:00:00Z HTTP/1.1
Host: localhost:8081
Cookie: access_token=<JWT>
```

**Response — 200 OK:**

```json
{
  "success": true,
  "data": {
    "events": [
      {
        "id": "ev_0X8F92K1",
        "timestamp": "2026-03-30T14:20:05Z",
        "eventType": "LOGIN_FAILURE",
        "userId": "user_ABC123",
        "userName": "jane.doe@example.com",
        "reason": "Invalid password",
        "resource": null,
        "ipAddress": "192.168.1.105",
        "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64) AppleWebKit/537.36"
      },
      {
        "id": "ev_0X8F8K9L",
        "timestamp": "2026-03-30T14:15:22Z",
        "eventType": "PERMISSION_DENIED",
        "userId": "user_DEF456",
        "userName": "john.smith@example.com",
        "reason": "Insufficient role (INSTRUCTOR < ADMIN required)",
        "resource": "POST /admin/users",
        "ipAddress": "192.168.1.110",
        "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"
      },
      {
        "id": "ev_0X8F7M5N",
        "timestamp": "2026-03-30T14:10:18Z",
        "eventType": "OVERRIDE",
        "userId": "user_SYSADM1",
        "userName": "admin.operator@example.com",
        "reason": "Emergency team unlock (deadlock recovery)",
        "resource": "team_XYZ789",
        "ipAddress": "10.0.1.50",
        "userAgent": "PostmanRuntime/7.32.1"
      }
    ],
    "totalEvents": 247,
    "currentPage": 0,
    "pageSize": 100
  },
  "timestamp": "2026-03-30T14:22:25Z"
}
```

**Immutability Note:** Audit log is append-only and immutable (see ARCHITECTURE.md). Events cannot be deleted or modified.

---

### 11.4.5 POST /api/v1/system/users/{userId}/force-logout

**Purpose:** Terminate all active sessions and refresh tokens for a user (emergency operation for compromised accounts or abuse). Immediately invalidates all cookies.

**Auth:** SYSTEM_ADMIN only

**Path Parameters:**
| Name | Type | Notes |
|------|------|-------|
| `userId` | HashId String | Hashid-encoded user ID (e.g., "ABC123XYZ") |

**Request:**

```http
POST /api/v1/system/users/ABC123XYZ/force-logout HTTP/1.1
Host: localhost:8081
Cookie: access_token=<JWT>
Content-Type: application/json

{
  "reason": "Account compromised; password reset required"
}
```

**Response — 200 OK:**

```json
{
  "success": true,
  "data": {
    "userId": "ABC123XYZ",
    "userName": "jane.doe@example.com",
    "sessionsTerminated": 3,
    "refreshTokensRevoked": 3,
    "loggedOutAt": "2026-03-30T14:22:30Z"
  },
  "timestamp": "2026-03-30T14:22:30Z"
}
```

**Response — 403 Forbidden (attempting to force-logout another SYSTEM_ADMIN):**

```json
{
  "success": false,
  "error": {
    "code": "CANNOT_FORCE_LOGOUT_SYSTEM_ADMIN",
    "message": "Cannot force-logout another SYSTEM_ADMIN account. Manual intervention required."
  },
  "timestamp": "2026-03-30T14:22:30Z"
}
```

**Response — 404 Not Found:**

```json
{
  "success": false,
  "error": {
    "code": "NOT_FOUND",
    "message": "User 'ABC123XYZ' does not exist"
  },
  "timestamp": "2026-03-30T14:22:30Z"
}
```

---

### 11.4.6 POST /api/v1/system/teams/{teamId}/unlock

**Purpose:** Manually unlock a team that is in locked state (deadlock recovery). Used when team is stuck in `is_locked = true` and cannot be unlocked via normal flow.

**Auth:** SYSTEM_ADMIN only

**Path Parameters:**
| Name | Type | Notes |
|------|------|-------|
| `teamId` | HashId String | Hashid-encoded team ID |

**Request:**

```http
POST /api/v1/system/teams/TEAM0XYZ/unlock HTTP/1.1
Host: localhost:8081
Cookie: access_token=<JWT>
Content-Type: application/json

{
  "reason": "Deadlock recovery: team formation window reopened for late joiners"
}
```

**Response — 200 OK:**

```json
{
  "success": true,
  "data": {
    "teamId": "TEAM0XYZ",
    "teamName": "Project Group A",
    "courseId": "COUR01ABC",
    "assignmentId": "ASSGN02XYZ",
    "isLocked": false,
    "unlockedAt": "2026-03-30T14:22:35Z",
    "memberCount": 4
  },
  "timestamp": "2026-03-30T14:22:35Z"
}
```

**Response — 409 Conflict (team not locked):**

```json
{
  "success": false,
  "error": {
    "code": "TEAM_NOT_LOCKED",
    "message": "Team 'TEAM0XYZ' is already unlocked (is_locked=false). No action needed."
  },
  "timestamp": "2026-03-30T14:22:35Z"
}
```

**Response — 404 Not Found:**

```json
{
  "success": false,
  "error": {
    "code": "NOT_FOUND",
    "message": "Team 'TEAM0XYZ' does not exist"
  },
  "timestamp": "2026-03-30T14:22:35Z"
}
```

---

### 11.4.7 POST /api/v1/system/evaluations/{evaluationId}/reopen

**Purpose:** Reopen a published evaluation by setting it back to draft state. Emergency operation to fix grading errors or correct published rubrics. All student visibility reverted.

**Auth:** SYSTEM_ADMIN only

**Path Parameters:**
| Name | Type | Notes |
|------|------|-------|
| `evaluationId` | HashId String | Hashid-encoded evaluation ID |

**Request:**

```http
POST /api/v1/system/evaluations/EVAL0ABC/reopen HTTP/1.1
Host: localhost:8081
Cookie: access_token=<JWT>
Content-Type: application/json

{
  "reason": "Rubric criterion had incorrect point value; reverting for correction"
}
```

**Response — 200 OK:**

```json
{
  "success": true,
  "data": {
    "evaluationId": "EVAL0ABC",
    "assignmentId": "ASSGN02XYZ",
    "courseId": "COUR01ABC",
    "isDraft": true,
    "reopenedAt": "2026-03-30T14:22:40Z",
    "previouslyPublishedAt": "2026-03-29T10:15:00Z",
    "criteria": [
      {
        "id": "crit_001",
        "name": "Code Quality",
        "maxPoints": 20
      },
      {
        "id": "crit_002",
        "name": "Documentation",
        "maxPoints": 10
      }
    ]
  },
  "timestamp": "2026-03-30T14:22:40Z"
}
```

**Response — 409 Conflict (evaluation still in draft):**

```json
{
  "success": false,
  "error": {
    "code": "EVALUATION_NOT_PUBLISHED",
    "message": "Evaluation 'EVAL0ABC' is still in draft state (is_draft=true). Cannot reopen a draft evaluation."
  },
  "timestamp": "2026-03-30T14:22:40Z"
}
```

**Response — 404 Not Found:**

```json
{
  "success": false,
  "error": {
    "code": "NOT_FOUND",
    "message": "Evaluation 'EVAL0ABC' does not exist"
  },
  "timestamp": "2026-03-30T14:22:40Z"
}
```

---

## 11.5 Cache Operations

### Caches Managed by System Admin

ReviewFlow maintains five Caffeine-based caches for performance optimization. All are eligible for manual eviction by `SYSTEM_ADMIN`.

| Cache Name         | Max Entries | TTL    | Purpose                                    |
| ------------------ | ----------- | ------ | ------------------------------------------ |
| `adminStats`       | 1,000       | 30 min | Admin user lists and course counts         |
| `unreadCount`      | 10,000      | 15 min | Unread notification counts per user        |
| `userCourses`      | 10,000      | 60 min | Courses enrolled by each user              |
| `assignmentDetail` | 5,000       | 45 min | Assignment metadata and submission windows |

### Eviction Mechanics

**Per-Cache Eviction:**

- POST `/api/v1/system/cache/evict/{cacheName}` removes all entries from that specific cache
- Safer than bulk eviction — allows targeted fixes without affecting unrelated caches
- Throttled individually: each cache tracks its own last-eviction timestamp

**Throttle Enforcement:**

- After eviction, cache is locked for 60 seconds
- Subsequent eviction attempt within 60 seconds returns `429 Too Many Requests` with `EVICTION_TOO_SOON` code
- `Retry-After` header specifies remaining wait time in seconds
- **Multi-instance note:** Throttle is per-instance; coordinated cluster eviction requires Redis upgrade (see Decision 18)

**Example Throttle Flow:**

```
T=14:22:15  → POST /cache/evict/adminStats → 200 OK (evicted)
T=14:22:45  → POST /cache/evict/adminStats → 429 Too Many Requests (Retry-After: 30)
T=14:23:16  → POST /cache/evict/adminStats → 200 OK (now eligible; 60s elapsed)
```

### Cache Hit Rate Monitoring

Cache statistics returned by `GET /cache/stats` include:

- **Hits:** Successful lookups from cache
- **Misses:** Lookups that fell through to database
- **Hit Rate:** (Hits / (Hits + Misses)) × 100%

Monitor hit rates via dashboard (PRD-08). Sustained **<90% hit rate** on a cache indicates either:

1. Cache misconfiguration (TTL too short, max size too small)
2. Workload spike causing cache churn
3. Manual evictions being called too frequently

---

## 11.6 Real-Time Metrics (WebSocket)

### Subscription Pattern

**Protocol:** STOMP over WebSocket (same infrastructure as notifications, PRD-03)  
**Role:** SYSTEM_ADMIN only  
**Subscription endpoint:** `/queue/system-metrics`  
**Auth:** Requires valid access token (JWT in session cookie)

### Connection Setup

Client connects to WebSocket and performs STOMP handshake:

```
CONNECT
accept-version:1.0,1.1,2.0
heart-beat:0,0

SUBSCRIBE
id:sub-metrics-001
destination:/queue/system-metrics
```

### Metrics Push Schedule

**Scheduled Push:** Every 30 seconds, server pushes metrics snapshot to all subscribed `SYSTEM_ADMIN` clients

**Event-Triggered Push:** Immediately upon event (not scheduled):

- JVM heap usage exceeds threshold (e.g., 85%)
- Database connection pool exhaustion (>95% utilization)
- Cache miss spike detected (>50% miss rate on any cache)
- Evaluation publishing error
- System restart/deployment rollout

### Metrics Payload

```json
{
  "timestamp": "2026-03-30T14:22:30.123Z",
  "instanceId": "prod-instance-1",
  "jvmMemory": {
    "heapUsedMB": 512,
    "heapMaxMB": 1024,
    "heapUsagePercent": 50.0,
    "nonHeapUsedMB": 128
  },
  "database": {
    "activeConnections": 45,
    "maxConnections": 50,
    "connectionPoolPercent": 90.0,
    "queryCountLastSecond": 234,
    "slowQueryCountLastSecond": 2
  },
  "cacheStats": {
    "adminStats": {
      "size": 42,
      "hitRate": 0.9334,
      "missesLastSecond": 2
    },
    "unreadCount": {
      "size": 156,
      "hitRate": 0.9367,
      "missesLastSecond": 1
    },
    "userCourses": {
      "size": 287,
      "hitRate": 0.9338,
      "missesLastSecond": 3
    },
    "assignmentDetail": {
      "size": 73,
      "hitRate": 0.9304,
      "missesLastSecond": 1
    },
    "courseGradeGroups": {
      "size": 19,
      "hitRate": 0.9255,
      "missesLastSecond": 1
    }
  },
  "uptime": {
    "uptimeSeconds": 864000,
    "lastRestartAt": "2026-03-20T08:15:00Z"
  },
  "notifications": {
    "unsentNotificationsInQueue": 12,
    "emailNotificationsFailedLastHour": 0,
    "websocketConnectionsActive": 143
  },
  "alarmState": "HEALTHY",
  "alarmDetails": []
}
```

### Alarm Thresholds

| Metric                       | Threshold         | Severity |
| ---------------------------- | ----------------- | -------- |
| Heap usage                   | >85%              | WARNING  |
| Connection pool              | >95%              | CRITICAL |
| Database query latency (p95) | >2000ms           | WARNING  |
| Cache miss rate              | >50% on any cache | INFO     |
| Email send failures          | >5 in last hour   | WARNING  |

When threshold breached, `alarmState` transitions to `WARNING` or `CRITICAL`, and `alarmDetails` array is populated with descriptions.

### Reconnection Strategy

**Client-side auto-reconnect:**

- On WebSocket close, client waits 5 seconds before attempting reconnection
- Maximum 10 retry attempts before manual intervention required
- Reconnection logic prevents connection storms

**Server-side:** No session state persisted; fresh subscription on each reconnect

**Example Client Flow:**

```
1. Connect → CONNECTED (metrics begin flowing)
2. Network drop → delay 5s
3. Retry → CONNECTED (fresh subscription, metrics resume)
```

---

## 11.7 Security Events Log

**Source:** Immutable append-only `audit_log` table (see ARCHITECTURE.md — Audit Log)

### Event Types

| Type                  | Scenario                                                          | Logged By            | Visible To                                                           |
| --------------------- | ----------------------------------------------------------------- | -------------------- | -------------------------------------------------------------------- |
| `LOGIN_FAILURE`       | Failed authentication (wrong password, invalid email)             | Auth system          | SYSTEM_ADMIN only                                                    |
| `PERMISSION_DENIED`   | Role insufficient or account deactivated                          | Authorization filter | SYSTEM_ADMIN only                                                    |
| `OVERRIDE`            | SYSTEM_ADMIN uses force-logout, team unlock, or evaluation reopen | SYSTEM_ADMIN action  | SYSTEM_ADMIN; name redacted to "System Administrator" for ADMIN role |
| `ACCOUNT_DEACTIVATED` | Admin deactivates a user account                                  | ADMIN                | SYSTEM_ADMIN and ADMIN                                               |

### Event Retention

- **Last 100 events** returned by API paginated query
- **Total retention:** 12 months in database; older events archived to cold storage (see PRD-08)
- **Immutability:** Events cannot be deleted, edited, or reordered

### Event Details Example

```
{
  "id": "ev_0X8F92K1",
  "timestamp": "2026-03-30T14:20:05Z",
  "eventType": "OVERRIDE",
  "userId": "user_SYSADM1",              # SYSTEM_ADMIN who performed action
  "userName": "admin.operator@example.com",
  "reason": "Emergency team unlock (deadlock recovery)",
  "resource": "team_XYZ789",             # ID of affected resource
  "ipAddress": "10.0.1.50",
  "userAgent": "PostmanRuntime/7.32.1"
}
```

---

## 11.8 Error Codes (SYSTEM_ADMIN Specific)

All error codes listed below plus base codes from [GLOBAL_RULES](./00_Global_Rules_and_Reference.md#comprehensive-error-codes-reference).

| Code                               | HTTP                  | Trigger                                                                                                                                                | Recovery                                                                                                                  |
| ---------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------- |
| `UNKNOWN_CACHE`                    | 400 Bad Request       | POST /system/cache/evict with cache name not recognized by Spring Cache Manager (not in list: adminStats, unreadCount, userCourses, assignmentDetail, courseGradeGroups). | Verify cache name. Call GET /system/config to list available caches.                                                      |
| `EVICTION_TOO_SOON`                | 429 Too Many Requests | Cache eviction attempted within 60 seconds of last eviction of the same cache. Anti-DDoS throttle per-cache. Response includes `Retry-After` header.   | Wait 60 seconds since last eviction. Throttle protects against abuse. Retry header indicates safe wait time.              |
| `SYSTEM_ADMIN_LIMIT_EXCEEDED`      | 409 Conflict          | Flyway migration or hypothetical API attempt to create SYSTEM_ADMIN account when 5 accounts already exist. Hard ceiling enforced.                      | Remove/deactivate existing SYSTEM_ADMIN account first. Manage via direct DB migration rollback. Cannot exceed 5 accounts. |
| `CANNOT_FORCE_LOGOUT_SYSTEM_ADMIN` | 403 Forbidden         | SYSTEM_ADMIN attempts POST /system/users/{id}/force-logout on another SYSTEM_ADMIN account (peer protection).                                          | Cannot force-logout peer admins. Only deactivation via manual DB/migration intervention.                                  |
| `TEAM_NOT_LOCKED`                  | 409 Conflict          | POST /system/teams/{id}/unlock called on team with is_locked=false (already unlocked). No-op; team is not in locked state.                             | Verify team lock status. Team is already unlocked; no action needed.                                                      |
| `EVALUATION_NOT_PUBLISHED`         | 409 Conflict          | POST /system/evaluations/{id}/reopen called on evaluation with is_draft=true (already draft). Cannot reopen a draft.                                   | Verify evaluation state. Only published evaluations (is_draft=false) can be reopened.                                     |

**Note:** All base codes (`UNAUTHORIZED`, `FORBIDDEN`, `ACCOUNT_DEACTIVATED`, `NOT_FOUND`, `VALIDATION_ERROR`) still apply.

---

## 11.9 Constraints & Edge Cases

### SYSTEM_ADMIN Account Limits

- **Hard ceiling:** Maximum 5 accounts platform-wide
- **Error:** `SYSTEM_ADMIN_LIMIT_EXCEEDED` (409 Conflict) if limit exceeded
- **Creation:** Flyway migration only; no API endpoint
- **Deletion:** Manual DB or migration rollback only; no API endpoint
- **Audit trail:** All SYSTEM_ADMIN account changes logged to audit_log with version control history

### Force Logout Constraints

- **Cannot self-logout:** SYSTEM_ADMIN can force-logout any user except other SYSTEM_ADMIN accounts
- **Error:** `CANNOT_FORCE_LOGOUT_SYSTEM_ADMIN` (403 Forbidden) if target is peer SYSTEM_ADMIN
- **Effect:** Immediate revocation of all active sessions and refresh tokens
- **No grace period:** User is logged out immediately; existing browser sessions are invalidated

### Team Unlock Constraints

- **Only for locked teams:** Can only unlock team with is_locked=true
- **Error:** `TEAM_NOT_LOCKED` (409 Conflict) if team already unlocked
- **No cascade:** Unlocking team does NOT reopen submissions or extend due dates
- **Audit trail:** Unlock reason logged as OVERRIDE entry in audit_log

### Evaluation Reopen Constraints

- **Only for published evaluations:** Can only reopen evaluation with is_draft=false (published state)
- **Error:** `EVALUATION_NOT_PUBLISHED` (409 Conflict) if evaluation still in draft
- **Student visibility reverted:** Published grades immediately hidden; evaluation returns 404 to students
- **No score wipe:** Existing grades retained in database; only visibility toggled
- **Immediate effect:** Cannot be queued; reopen is synchronous operation

### Cache Eviction Throttle Edge Cases

| Scenario                                    | Action | Result                                                            |
| ------------------------------------------- | ------ | ----------------------------------------------------------------- |
| Evict cache A at T=0s                       | ✓      | Success; A locked for 60s                                         |
| Evict cache B at T=10s                      | ✓      | Success; B locked for 60s (independent throttle)                  |
| Evict cache A at T=15s                      | ✗      | 429; retry after 45s                                              |
| Evict cache A at T=61s                      | ✓      | Success; first eligible attempt                                   |
| Multi-instance: both evict A simultaneously | ⚠️     | Both succeed (no coordination in v1). Future: Redis-backed state. |

---

## 11.10 Related PRDs

- **[PRD-09: System Admin & Platform Operations](../Features/PRD9-SystemAdmin.md)** — Feature requirements, role hierarchy design, decision rationale
- **[PRD-08: Logging & Monitoring](../Features/PRD_08_logging_monitoring.md)** — Structured logging, CloudWatch integration, metrics dashboard

---

## 11.11 Related Decisions

- **[Decision 16: Two-role hierarchy with role layers](../DECISIONS.md#16-two-role-hierarchy-with-role-layers)** — SYSTEM_ADMIN > ADMIN > INSTRUCTOR > STUDENT hierarchy, rationale
- **[Decision 17: Fixed 5-account ceiling for system administration](../DECISIONS.md#17-fixed-5-account-ceiling-for-system-administration)** — Why 5 accounts, enforcement, migration-based creation
- **[Decision 18: Cache eviction throttle with 60-second window](../DECISIONS.md#18-cache-eviction-throttle-with-60-second-window)** — Throttle design, multi-instance considerations, Redis upgrade path
- **[Decision 19: WebSocket push for real-time metrics delivery](../DECISIONS.md#19-websocket-push-for-real-time-metrics-delivery)** — Metrics architecture, STOMP integration, reconnect strategy

---

## 11.12 Testing Checklist

- [ ] GET /cache/stats returns 200 with all five cache metrics
- [ ] POST /cache/evict/{cacheName} evicts entries and returns 200
- [ ] Eviction throttle blocks same cache within 60 seconds (429)
- [ ] Eviction throttle allows different caches simultaneously
- [ ] `Retry-After` header present in 429 response
- [ ] GET /config lists all caches and metadata
- [ ] GET /security/events returns last 100 events with pagination
- [ ] POST /users/{id}/force-logout terminates all sessions (verify token invalid)
- [ ] POST /users/{id}/force-logout blocks when target is SYSTEM_ADMIN (403)
- [ ] POST /teams/{id}/unlock unlocks locked team
- [ ] POST /teams/{id}/unlock rejects already-unlocked team (409)
- [ ] POST /evaluations/{id}/reopen reverts published evaluation to draft
- [ ] POST /evaluations/{id}/reopen rejects draft evaluation (409)
- [ ] WebSocket /queue/system-metrics subscription delivers metrics every 30s
- [ ] Alarm triggers immediate push (event-based delivery)
- [ ] Client auto-reconnects on WebSocket drop
- [ ] Security events audit log is immutable (events not modifiable)
- [ ] SYSTEM_ADMIN operations logged as OVERRIDE entries
- [ ] Insufficient role returns 403 FORBIDDEN
- [ ] Deactivated SYSTEM_ADMIN account returns 403 ACCOUNT_DEACTIVATED
- [ ] Invalid/missing JWT returns 401 UNAUTHORIZED

---

## 11.13 Notes for Implementation

1. **Throttle State Management:** Store `Map<String, Long> lastEvictionTime` (cacheName → epoch millis) in a `@Component` singleton. Thread-safe (use `ConcurrentHashMap`).

2. **Metrics Collection:** Hook into Spring's `MeterRegistry` (registered by Spring Boot Actuator). Extract `CacheMeterBinder` stats for each cache.

3. **WebSocket Subscription:** Reuse existing `StompEventListener` pattern. Create `SystemMetricsEventListener` publishing to `/queue/system-metrics`.

4. **Audit Logging:** All SYSTEM_ADMIN actions automatically logged via `AuditLogService.logOverride(userId, reason, resource, overrideType)`.

5. **Role Check:** Use `@PreAuthorize("hasRole('SYSTEM_ADMIN')")` on all endpoints. Spring Security enforces hierarchy; no manual role comparison needed.

6. **Error Response Formatting:** All errors wrapped in standard envelope (see GLOBAL_RULES). Use `ResponseEntity` with `ErrorResponse` DTO for consistency.

7. **Redis Upgrade Path:** Document in ARCHITECTURE.md. When multi-instance active, throttle state moves to Redis with 60-second TTL per cache key.
