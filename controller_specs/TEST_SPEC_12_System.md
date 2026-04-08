# TEST_SPEC_12_System.md

## System Administration Module Test Specification

**Module**: System Operations & Maintenance  
**Controllers**: SystemController  
**Endpoints**: 7  
**Last Updated**: Post-Architecture-Fix Phase 2  
**Test Coverage**: 45+ test cases

---

## 1. Endpoint Summary

| #   | Method | Endpoint                                           | Description                | Role         |
| --- | ------ | -------------------------------------------------- | -------------------------- | ------------ |
| 1   | GET    | `/api/v1/system/cache/stats`                       | Get cache statistics       | SYSTEM_ADMIN |
| 2   | POST   | `/api/v1/system/cache/evict/{cacheName}`           | Clear specific cache       | SYSTEM_ADMIN |
| 3   | GET    | `/api/v1/system/config`                            | Get system configuration   | SYSTEM_ADMIN |
| 4   | GET    | `/api/v1/system/security/events`                   | Get recent security events | SYSTEM_ADMIN |
| 5   | POST   | `/api/v1/system/users/{targetUserId}/force-logout` | Force user logout          | SYSTEM_ADMIN |
| 6   | POST   | `/api/v1/system/teams/{teamId}/unlock`             | Unlock finalized team      | SYSTEM_ADMIN |
| 7   | POST   | `/api/v1/system/evaluations/{evaluationId}/reopen` | Reopen published grade     | SYSTEM_ADMIN |

---

## 2. Permission Matrix

**SYSTEM_ADMIN**: All endpoints (highest privilege)  
**ADMIN**: Denied (403 Forbidden - these are system ops)  
**INSTRUCTOR**: Denied (403 Forbidden)  
**STUDENT**: Denied (403 Forbidden)

**Note**: SYSTEM_ADMIN can override published resources, locked states, and finalized decisions.

---

## 3. Endpoint Test Cases

### 3.1 Get Cache Statistics

**Endpoint**: `GET /api/v1/system/cache/stats`

**Response Example**:

```json
{
  "caches": [
    {
      "name": "courseCache",
      "size": 245,
      "maxSize": 1000,
      "hitRate": 0.87,
      "missRate": 0.13,
      "evictionCount": 12,
      "ttlSeconds": 300
    },
    {
      "name": "assignmentCache",
      "size": 512,
      "maxSize": 2000,
      "hitRate": 0.92,
      "missRate": 0.08,
      "evictionCount": 5,
      "ttlSeconds": 300
    }
  ],
  "memoryUsage": "2.5GB / 8GB"
}
```

**Test Cases**:

1. ✅ SYSTEM_ADMIN gets cache stats (200 OK)
2. ✅ ADMIN cannot access (403 Forbidden)
3. ✅ Response includes all cache names
4. ✅ Response includes hit/miss rates
5. ✅ Response includes memory usage
6. ✅ Hit rate between 0-1
7. ✅ Cache size <= maxSize
8. ✅ No personal data leakage
9. ✅ Response cached (same result for 1 minute)
10. ✅ Audit event: CACHE_STATS_VIEWED -> timestamp

---

### 3.2 Clear Specific Cache

**Endpoint**: `POST /api/v1/system/cache/evict/{cacheName}`

**Test Cases**:

1. ✅ Clear courseCache (200 OK - "Cache evicted")
2. ✅ Clear assignmentCache (200 OK)
3. ✅ Clear userCache (200 OK)
4. ✅ Non-existent cache name (404 Not Found)
5. ✅ After evict, cache size resets to 0
6. ✅ ADMIN cannot evict (403 Forbidden)
7. ✅ Response includes evicted count
8. ✅ Audit event: CACHE_EVICTED -> cacheName, evictedCount
9. ✅ Timestamp records when evicted
10. ✅ New requests refill cache (hit rate resets)

**Caches Available**:

- courseCache (TTL: 5 minutes)
- assignmentCache (TTL: 5 minutes)
- userCache (TTL: 10 minutes)
- evaluationCache (TTL: 2 minutes)

---

### 3.3 Get System Configuration

**Endpoint**: `GET /api/v1/system/config`

**Response Example**:

```json
{
  "appName": "ReviewFlow",
  "appVersion": "1.0.0",
  "environment": "production",
  "maxUploadSize": "100MB",
  "sessionTimeout": "30 minutes",
  "passwordPolicy": {
    "minLength": 8,
    "requireUppercase": true,
    "requireNumbers": true,
    "requireSpecialChars": true,
    "expiryDays": 90
  },
  "features": {
    "s3StorageEnabled": true,
    "emailNotificationsEnabled": true,
    "auditLoggingEnabled": true,
    "websocketEnabled": true
  },
  "limits": {
    "maxTeamSize": 10,
    "maxAssignmentSize": 500,
    "apiRateLimit": "1000/hour"
  }
}
```

**Test Cases**:

1. ✅ SYSTEM_ADMIN gets config (200 OK)
2. ✅ ADMIN cannot access (403 Forbidden)
3. ✅ Response includes all config sections
4. ✅ No sensitive data exposed (credentials not included)
5. ✅ Version matches package.json
6. ✅ Environment correctly set
7. ✅ Feature flags accurate
8. ✅ Limits are positive integers
9. ✅ Response cached (5 minutes)
10. ✅ Audit event: CONFIG_VIEWED -> timestamp

---

### 3.4 Get Security Events

**Endpoint**: `GET /api/v1/system/security/events?limit=50`

**Response Example**:

```json
{
  "events": [
    {
      "eventId": "sec_001",
      "eventType": "PERMISSION_DENIED",
      "timestamp": "2024-01-15T14:30:00Z",
      "userId": 123,
      "resource": "ASSIGNMENT_DELETE",
      "ipAddress": "192.168.1.100",
      "severity": "WARN"
    },
    {
      "eventType": "LOGIN_FAILED",
      "timestamp": "2024-01-15T14:25:00Z",
      "email": "attacker@external.com",
      "ipAddress": "203.0.113.50",
      "reason": "Invalid credentials",
      "severity": "WARN"
    }
  ],
  "totalCount": 1250
}
```

**Test Cases**:

1. ✅ SYSTEM_ADMIN gets security events (200 OK)
2. ✅ Limit parameter (1-500) (200 OK)
3. ✅ Filter by event type (200 OK)
4. ✅ Filter by severity: INFO, WARN, ERROR (200 OK)
5. ✅ Filter by dateRange (200 OK)
6. ✅ Pagination works
7. ✅ Response sorted by timestamp (newest first)
8. ✅ Includes suspicious activity (failed logins, permission denied)
9. ✅ ADMIN cannot access (403 Forbidden)
10. ✅ Audit event: SECURITY_EVENTS_VIEWED -> timestamp

**Security Event Types**:

- LOGIN_FAILED (multiple attempts from same IP)
- PERMISSION_DENIED (unauthorized resource access)
- SQL_INJECTION_ATTEMPT (detected)
- RATE_LIMIT_EXCEEDED (API quota)
- CONFIG_CHANGED (system setting modified)
- DATA_EXPORT (large data download)

---

### 3.5 Force Logout User

**Endpoint**: `POST /api/v1/system/users/{targetUserId}/force-logout`

**Request Body**:

```json
{
  "reason": "Security incident - suspected account compromise"
}
```

**Test Cases**:

1. ✅ SYSTEM_ADMIN forces logout (200 OK)
2. ✅ Target user invalidated (tokens discarded)
3. ✅ Non-existent user (404 Not Found)
4. ✅ User's sessions terminated immediately
5. ✅ Reason logged in audit trail
6. ✅ User receives notification (email)
7. ✅ Cannot force logout self (400 Bad Request)
8. ✅ ADMIN cannot force logout (403 Forbidden)
9. ✅ Response includes: "User logged out - sessions terminated"
10. ✅ Audit event: USER_FORCE_LOGOUT -> targetUserId, reason

---

### 3.6 Unlock Finalized Team

**Endpoint**: `POST /api/v1/system/teams/{teamId}/unlock`

**Request Body**:

```json
{
  "reason": "Correction: remove incorrect member"
}
```

**Test Cases**:

1. ✅ SYSTEM_ADMIN unlocks team (200 OK)
2. ✅ Team status changes from LOCKED to OPEN (200 OK)
3. ✅ Non-existent team (404 Not Found)
4. ✅ Already unlocked team (400 Bad Request)
5. ✅ Instructor receives notification
6. ✅ Team members notified (the team is reopened)
7. ✅ Reason logged in audit
8. ✅ ADMIN cannot unlock (403 Forbidden)
9. ✅ Response: "Team unlocked successfully"
10. ✅ Audit event: TEAM_UNLOCKED -> teamId, reason

---

### 3.7 Reopen Published Evaluation

**Endpoint**: `POST /api/v1/system/evaluations/{evaluationId}/reopen`

**Request Body**:

```json
{
  "reason": "Grade correction: calculation error identified"
}
```

**Test Cases**:

1. ✅ SYSTEM_ADMIN reopens published (200 OK)
2. ✅ Status changes from PUBLISHED to RETURNED (200 OK)
3. ✅ Non-existent evaluation (404 Not Found)
4. ✅ Draft evaluation cannot reopen (400 Bad Request)
5. ✅ Student notified of reopening
6. ✅ Instructor can now edit scores again
7. ✅ Original publish date preserved (audit history)
8. ✅ Reason logged in audit
9. ✅ ADMIN cannot reopen (403 Forbidden)
10. ✅ INSTRUCTOR cannot reopen (403 Forbidden - SYSTEM_ADMIN only)
11. ✅ Audit event: EVALUATION_REOPENED_BY_SYSTEM_ADMIN -> evaluationId, reason
12. ✅ Response: "Evaluation reopened - instructor can make corrections"

---

## 4. Audit Events (System Operations Only)

| Event                               | Triggered By        | Data Logged             |
| ----------------------------------- | ------------------- | ----------------------- |
| CACHE_STATS_VIEWED                  | GET cache/stats     | timestamp               |
| CACHE_EVICTED                       | POST cache/evict    | cacheName, evictedCount |
| CONFIG_VIEWED                       | GET config          | timestamp               |
| SECURITY_EVENTS_VIEWED              | GET security/events | timestamp, limit        |
| USER_FORCE_LOGOUT                   | POST force-logout   | targetUserId, reason    |
| TEAM_UNLOCKED                       | POST team/unlock    | teamId, reason          |
| EVALUATION_REOPENED_BY_SYSTEM_ADMIN | POST eval/reopen    | evaluationId, reason    |

---

## 5. Real Test Users

| User         | Email                        | Role         |
| ------------ | ---------------------------- | ------------ |
| System Admin | main_sysadmin@reviewflow.com | SYSTEM_ADMIN |

---

## 6. Postman Examples

### Force Logout User

```json
{
  "name": "Force Logout User",
  "request": {
    "method": "POST",
    "url": "{{base_url}}/api/v1/system/users/{{targetUserId}}/force-logout",
    "header": [
      { "key": "Authorization", "value": "Bearer {{system_admin_token}}" }
    ],
    "body": { "mode": "raw", "raw": "{\"reason\": \"Security audit\"}" }
  },
  "tests": ["pm.response.code === 200"]
}
```

### Unlock Team

```json
{
  "name": "Unlock Team",
  "request": {
    "method": "POST",
    "url": "{{base_url}}/api/v1/system/teams/{{teamId}}/unlock",
    "body": { "mode": "raw", "raw": "{\"reason\": \"Correction needed\"}" }
  },
  "tests": ["pm.response.code === 200"]
}
```

---

## 7. Security Considerations

1. **SYSTEM_ADMIN Whitelisting**: Only pre-approved accounts have access
2. **Audit Logging**: All SYSTEM_ADMIN operations logged and monitored
3. **IP Whitelisting**: Access restricted to approved networks
4. **Rate Limiting**: System ops throttled to prevent abuse
5. **Email Notification**: High-impact ops (force logout) send confirmation
6. **Two-Factor Auth**: Optional 2FA for sensitive operations

---

## 8. Error Handling

| Scenario                | Status | Message                                     |
| ----------------------- | ------ | ------------------------------------------- |
| Not SYSTEM_ADMIN        | 403    | "Unauthorized - SYSTEM_ADMIN role required" |
| Non-existent resource   | 404    | "Resource not found"                        |
| Already in target state | 400    | "Team already unlocked"                     |
| Invalid reason (empty)  | 400    | "Reason required"                           |

---

## 9. Performance Requirements

| Operation            | Target Time | Notes                 |
| -------------------- | ----------- | --------------------- |
| Cache stats          | <100ms      | Cached, fast          |
| Cache evict          | <1s         | Memory operation      |
| Config               | <50ms       | Highly cached         |
| Security events (50) | <500ms      | DB query optimization |
| Force logout         | <500ms      | Token invalidation    |

---

## 10. Known Limitations

- Batch operations not supported (one at a time)
- Undo operations require manual correction
- No scheduled maintenance windows (manual only)
- Health check endpoint not included
