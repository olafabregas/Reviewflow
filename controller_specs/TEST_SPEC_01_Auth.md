# TEST_SPEC_01 — Auth Module

**Status:** Complete  
**Version:** 1.0  
**Last Updated:** 2026-04-07  
**Reference Spec:** [01_Module_Auth.md](01_Module_Auth.md) | [00_Global_Rules_and_Reference.md](00_Global_Rules_and_Reference.md)  
**Related PRDs:** [PRD-09 System Admin](../Features/PRD9-SystemAdmin.md) | [PRD-08 Logging & Monitoring](../Features/PRD_08_logging_monitoring.md)

---

## 1. Endpoints Inventory

| Method | Endpoint      | Purpose                        | Auth Required | Public |
| ------ | ------------- | ------------------------------ | ------------- | ------ |
| POST   | /auth/login   | Authenticate and set cookies   | ❌            | ✅     |
| POST   | /auth/refresh | Rotate access token            | ❌ (cookie)   | ❌     |
| POST   | /auth/logout  | Clear session and revoke token | ✅            | ❌     |
| GET    | /auth/me      | Get current user profile       | ✅            | ❌     |

---

## 2. Role Permission Matrix

| Endpoint           | STUDENT | INSTRUCTOR | ADMIN | SYSTEM_ADMIN |
| ------------------ | ------- | ---------- | ----- | ------------ |
| POST /auth/login   | ✅      | ✅         | ✅    | ✅           |
| POST /auth/refresh | ✅      | ✅         | ✅    | ✅           |
| POST /auth/logout  | ✅      | ✅         | ✅    | ✅           |
| GET /auth/me       | ✅      | ✅         | ✅    | ✅           |

**Notes:**

- All roles can authenticate and manage their own session
- Token refresh and logout are automatic (no role checks)
- /auth/me returns only current user's data (no parameter required)

---

## 3. Success Path Tests (Happy Path)

### Prerequisites

All tests use HTTP-only cookies for session management:

- `reviewflow_access` — JWT access token (15 min expiry)
- `reviewflow_refresh` — JWT refresh token (7 day expiry)

Base URL: `http://localhost:8081/api/v1`

### 3.1 SYSTEM_ADMIN Login

**User:** main_sysadmin@reviewflow.com  
**Password:** Test@1234  
**HTTP Method:** POST  
**Endpoint:** `/auth/login`  
**Request Body:**

```json
{
  "email": "main_sysadmin@reviewflow.com",
  "password": "Test@1234"
}
```

**Expected Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "userId": <numeric_id>,
    "firstName": "Main",
    "lastName": "Sysadmin",
    "email": "main_sysadmin@reviewflow.com",
    "role": "SYSTEM_ADMIN"
  },
  "timestamp": "2026-04-07T12:30:45.123Z"
}
```

**Verify:**

- [ ] Status code is 200
- [ ] Response includes userId, firstName, lastName, email, role
- [ ] role = "SYSTEM_ADMIN" (exact match)
- [ ] `reviewflow_access` cookie set (HttpOnly, SameSite=Lax, Path=/, 15 min expiry)
- [ ] `reviewflow_refresh` cookie set (HttpOnly, SameSite=Lax, Path=/, 7 day expiry)
- [ ] Cookies NOT in response body
- [ ] X-Trace-Id header present in response
- [ ] Audit log entry created: eventType=USER_LOGIN, userId={id}, email={email}

---

### 3.2 ADMIN Login

**User:** humberadmin@reviewflow.com  
**Password:** Test@1234

**Expected Response:** `200 OK`, role=ADMIN

**Request Body:**

```json
{
  "email": "humberadmin@reviewflow.com",
  "password": "Test@1234"
}
```

**Verify:**

- [ ] Status code is 200
- [ ] role = "ADMIN"
- [ ] Cookies set with correct expiry times
- [ ] Audit log: USER_LOGIN event with email=humberadmin@reviewflow.com
- [ ] User can subsequently access /admin/\*\* endpoints

---

### 3.3 INSTRUCTOR Login

**User:** sarah.johnson@university.edu  
**Password:** Test@1234

**Expected Response:** `200 OK`, role=INSTRUCTOR

**Request Body:**

```json
{
  "email": "sarah.johnson@university.edu",
  "password": "Test@1234"
}
```

**Verify:**

- [ ] Status code is 200
- [ ] role = "INSTRUCTOR"
- [ ] User can subsequently access /courses, /assignments endpoints
- [ ] Audit log: USER_LOGIN event

---

### 3.4 STUDENT Login

**User:** jane.smith@university.edu  
**Password:** Test@1234

**Expected Response:** `200 OK`, role=STUDENT

**Request Body:**

```json
{
  "email": "jane.smith@university.edu",
  "password": "Test@1234"
}
```

**Verify:**

- [ ] Status code is 200
- [ ] role = "STUDENT"
- [ ] User can submit assignments and form teams
- [ ] Audit log: USER_LOGIN event
- [ ] User cannot access /admin or /system endpoints (tested in 3.5B)

---

### 3.5A GET /auth/me (Authenticated as STUDENT)

**Precondition:** User jane.smith@university.edu logged in, `reviewflow_access` cookie present

**HTTP Method:** GET  
**Endpoint:** `/auth/me`  
**Headers:**

- Cookie: `reviewflow_access=<jwt_token>`

**Expected Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "userId": <numeric_id>,
    "firstName": "Jane",
    "lastName": "Smith",
    "email": "jane.smith@university.edu",
    "role": "STUDENT"
  },
  "timestamp": "2026-04-07T12:35:10.456Z"
}
```

**Verify:**

- [ ] Status code is 200
- [ ] Returns current authenticated user's data
- [ ] Role matches login role
- [ ] Email matches login email
- [ ] No password or sensitive fields in response
- [ ] X-Trace-Id header present

---

### 3.5B GET /auth/me (STUDENT Attempting Access to Admin Endpoint)

**Precondition:** User jane.smith@university.edu logged in

**HTTP Method:** GET  
**Endpoint:** `/admin/users` (use any /admin endpoint)

**Expected Response:** `403 Forbidden`

```json
{
  "success": false,
  "error": {
    "code": "INSUFFICIENT_PERMISSIONS",
    "message": "Your role (STUDENT) does not have permission to access this resource"
  },
  "timestamp": "2026-04-07T12:35:15.789Z"
}
```

**Verify:**

- [ ] Status code is 403 (not 401, not 404)
- [ ] Error code is INSUFFICIENT_PERMISSIONS
- [ ] Message does not expose internal structure
- [ ] /system endpoints also return 403 for STUDENT

---

### 3.6 Token Refresh Flow

**Precondition:** Valid `reviewflow_refresh` cookie from login at t1

**HTTP Method:** POST  
**Endpoint:** `/auth/refresh`  
**Headers:**

- Cookie: `reviewflow_refresh=<jwt_token_from_login>`

**Expected Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "message": "Token refreshed successfully"
  },
  "timestamp": "2026-04-07T12:40:00.123Z"
}
```

**Verify:**

- [ ] Status code is 200
- [ ] New `reviewflow_access` cookie issued (15 min from NOW)
- [ ] New `reviewflow_refresh` cookie issued (7 day from NOW)
- [ ] **OLD** refresh token is **revoked** in refresh_tokens table (is_revoked=true)
- [ ] Can immediately use the new access cookie to call endpoints
- [ ] OLD refresh cookie is unusable now (tested in 4.9)
- [ ] No body cookies leaked, headers only

---

### 3.7 Logout (Happy Path)

**Precondition:** Logged in session with valid `reviewflow_access` and `reviewflow_refresh` cookies

**HTTP Method:** POST  
**Endpoint:** `/auth/logout`  
**Headers:**

- Cookie: `reviewflow_access=<token>; reviewflow_refresh=<token>`

**Expected Response:** `200 OK`

```json
{
  "success": true,
  "data": {
    "message": "Logged out successfully"
  },
  "timestamp": "2026-04-07T12:45:30.321Z"
}
```

**Verify:**

- [ ] Status code is 200
- [ ] Response body includes success message
- [ ] `reviewflow_access` cookie cleared (Set-Cookie with Max-Age=0)
- [ ] `reviewflow_refresh` cookie cleared (Set-Cookie with Max-Age=0)
- [ ] refresh_tokens table: token marked as revoked (is_revoked=true)
- [ ] Immediate /auth/me call returns 401 (token invalidated)
- [ ] Audit log: USER_LOGOUT event created
- [ ] X-Trace-Id header present

---

## 4. Authorization & 4xx Error Tests

### 4.1 401 Unauthorized — Wrong Password

**Setup:** email exists in DB

**HTTP Method:** POST  
**Endpoint:** `/auth/login`

**Request Body:**

```json
{
  "email": "jane.smith@university.edu",
  "password": "WrongPassword123!"
}
```

**Expected Response:** `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "Invalid email or password"
  },
  "timestamp": "2026-04-07T13:00:00.000Z"
}
```

**Verify:**

- [ ] Status code is 401 (not 400, not 403)
- [ ] Error code is INVALID_CREDENTIALS
- [ ] Message is generic — does NOT say "password incorrect"
- [ ] No cookies set
- [ ] Audit log: USER_LOGIN_FAILED event (reason=INVALID_CREDENTIALS)
- [ ] Attempt counter incremented for rate limiting
- [ ] Does NOT reveal email exists or password wrong

---

### 4.2 401 Unauthorized — Non-Existent User

**Setup:** email does NOT exist in DB

**HTTP Method:** POST  
**Endpoint:** `/auth/login`

**Request Body:**

```json
{
  "email": "nonexistent@university.edu",
  "password": "Test@1234"
}
```

**Expected Response:** `401 Unauthorized` (**same as 4.1**)

```json
{
  "success": false,
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "Invalid email or password"
  },
  "timestamp": "2026-04-07T13:05:00.000Z"
}
```

**Verify:**

- [ ] Status code is 401 (identical response to 4.1)
- [ ] Error code is INVALID_CREDENTIALS (identical)
- [ ] Message identical to 4.1 — does NOT say "user not found"
- [ ] No timing difference that reveals email existence (timing attack prevention)
- [ ] Audit log: USER_LOGIN_FAILED event (reason=INVALID_CREDENTIALS)

---

### 4.3 403 Forbidden — Deactivated Account

**Setup:** Test user with is_active=false in users table

**HTTP Method:** POST  
**Endpoint:** `/auth/login`

**Request Body:**

```json
{
  "email": "deactivated_user@university.edu",
  "password": "Test@1234"
}
```

**Expected Response:** `403 Forbidden`

```json
{
  "success": false,
  "error": {
    "code": "ACCOUNT_DEACTIVATED",
    "message": "Your account has been deactivated. Contact admin."
  },
  "timestamp": "2026-04-07T13:10:00.000Z"
}
```

**Verify:**

- [ ] Status code is 403 (not 401)
- [ ] Error code is ACCOUNT_DEACTIVATED (distinct from INVALID_CREDENTIALS)
- [ ] Message is user-friendly
- [ ] No cookies set
- [ ] Audit log: USER_LOGIN_FAILED event (reason=ACCOUNT_DEACTIVATED)
- [ ] Different error code allows frontend to show different UI message

---

### 4.4 400 Bad Request — Missing Email Field

**HTTP Method:** POST  
**Endpoint:** `/auth/login`

**Request Body:**

```json
{
  "password": "Test@1234"
}
```

**Expected Response:** `400 Bad Request`

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "email is required"
  },
  "timestamp": "2026-04-07T13:15:00.000Z"
}
```

**Verify:**

- [ ] Status code is 400
- [ ] Error code is VALIDATION_ERROR
- [ ] Message specifies which field is missing
- [ ] No cookies set
- [ ] No audit log entry (validation error pre-auth)

---

### 4.5 400 Bad Request — Missing Password Field

**HTTP Method:** POST  
**Endpoint:** `/auth/login`

**Request Body:**

```json
{
  "email": "jane.smith@university.edu"
}
```

**Expected Response:** `400 Bad Request`

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "password is required"
  },
  "timestamp": "2026-04-07T13:20:00.000Z"
}
```

**Verify:**

- [ ] Status code is 400
- [ ] Error message specifies "password"
- [ ] No cookies set
- [ ] No audit log entry

---

### 4.6 400 Bad Request — Invalid Email Format

**HTTP Method:** POST  
**Endpoint:** `/auth/login`

**Request Body:**

```json
{
  "email": "not-an-email",
  "password": "Test@1234"
}
```

**Expected Response:** `400 Bad Request`

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "email must be a valid email address"
  },
  "timestamp": "2026-04-07T13:25:00.000Z"
}
```

**Verify:**

- [ ] Status code is 400
- [ ] Validator rejects format before DB lookup (fail-fast)
- [ ] No cookies set
- [ ] No audit log entry (validation error pre-auth)

---

### 4.7 400 Bad Request — Empty Email String

**HTTP Method:** POST  
**Endpoint:** `/auth/login`

**Request Body:**

```json
{
  "email": "",
  "password": "Test@1234"
}
```

**Expected Response:** `400 Bad Request`

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "email is required"
  },
  "timestamp": "2026-04-07T13:30:00.000Z"
}
```

**Verify:**

- [ ] Status code is 400
- [ ] Empty string treated as missing field
- [ ] No cookies set

---

### 4.8 429 Too Many Requests — Rate Limiting

**Setup:** Simulate 6 consecutive failed login attempts from same IP address within 15 minutes

**HTTP Method:** POST  
**Endpoint:** `/auth/login`

**Attempts 1-5 Request Bodies:**

```json
{
  "email": "jane.smith@university.edu",
  "password": "WrongPassword_1" (or "WrongPassword_2", etc.)
}
```

**Each of Attempts 1-5 Response:** `401 Unauthorized` (see 4.1)

**Attempt 6 Request:**

```json
{
  "email": "jane.smith@university.edu",
  "password": "WrongPassword_6"
}
```

**Expected Response: 429 Too Many Requests**

```json
{
  "success": false,
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many login attempts. Please try again later."
  },
  "timestamp": "2026-04-07T13:35:00.000Z"
}
```

**Response Headers:**

```
Retry-After: 840
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 2026-04-07T13:50:00Z
```

**Verify:**

- [ ] Status code is 429 (not 401)
- [ ] Error code is RATE_LIMIT_EXCEEDED
- [ ] Retry-After header present with seconds (840 = 14 min remaining)
- [ ] Rate limit tracked per IP address (verify by using different IPs for different tests)
- [ ] Audit log: RATE_LIMIT_EXCEEDED event (ip, attempt_count, retry_after_seconds)
- [ ] All 6 attempts logged to audit_log
- [ ] After 15 minutes pass, same IP can login again: `200 OK`
- [ ] Successful login resets the rate limit counter

---

### 4.9 401 Unauthorized — GET /auth/me Without Token

**Setup:** No authentication cookie

**HTTP Method:** GET  
**Endpoint:** `/auth/me`  
**Headers:** (empty or no Cookie header)

**Expected Response:** `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Not authenticated"
  },
  "timestamp": "2026-04-07T13:40:00.000Z"
}
```

**Verify:**

- [ ] Status code is 401 (not 500)
- [ ] Error code is UNAUTHORIZED
- [ ] No user data in response
- [ ] No null pointer exception (controller has null check on @AuthenticationPrincipal)

---

### 4.10 401 Unauthorized — GET /auth/me After Logout

**Precondition:**

1. User logs in (receives access + refresh cookies)
2. User calls POST /auth/logout (cookies cleared, token revoked)
3. Attempt to call /auth/me with old cookie

**HTTP Method:** GET  
**Endpoint:** `/auth/me`  
**Headers:**

- Cookie: `reviewflow_access=<expired_or_revoked_token>`

**Expected Response:** `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Not authenticated"
  },
  "timestamp": "2026-04-07T13:45:00.000Z"
}
```

**Verify:**

- [ ] Status code is 401
- [ ] Token is rejected even if JWT signature is valid (because token was revoked in refresh_tokens table)
- [ ] No user data returned
- [ ] Cannot use old token to access any protected endpoint

---

### 4.11 401 Unauthorized — Expired Access Token

**Setup:** Valid access token that has expired (manually set expiry to past time in tests, or wait 15 min in manual testing)

**HTTP Method:** GET  
**Endpoint:** `/auth/me` (or any protected endpoint)

**Headers:**

- Cookie: `reviewflow_access=<expired_jwt>`

**Expected Response:** `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Not authenticated"
  },
  "timestamp": "2026-04-07T13:50:00.000Z"
}
```

**Verify:**

- [ ] Status code is 401
- [ ] JWT expiry validation catches the expired token
- [ ] Does NOT automatically refresh (decision: refresh only on explicit /auth/refresh call)
- [ ] User must call POST /auth/refresh or login again

---

### 4.12 401 Unauthorized — Refresh Token Used After Rotation (Reuse Attack)

**Precondition:**

1. User logs in at t1 → receives refresh_token_v1
2. User calls POST /auth/refresh at t2 → refresh_token_v1 revoked, refresh_token_v2 issued
3. Attempt to reuse refresh_token_v1

**HTTP Method:** POST  
**Endpoint:** `/auth/refresh`

**Headers:**

- Cookie: `reviewflow_refresh=<refresh_token_v1_AFTER_rotation>`

**Expected Response:** `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "INVALID_TOKEN",
    "message": "Refresh token is invalid or has been revoked"
  },
  "timestamp": "2026-04-07T13:55:00.000Z"
}
```

**CRITICAL Security Verify:**

- [ ] Status code is 401
- [ ] Error code is INVALID_TOKEN (or TOKEN_REUSE_ATTACK if specifically implemented)
- [ ] Database check: refresh_token_v1 has is_revoked=true
- [ ] **ALL tokens for affected user are revoked** (security measure against token theft)
- [ ] User must log in again (forced to provide credentials)
- [ ] Audit log: TOKEN_REUSE_ATTACK event with user_id, ip, attempted_token_id
- [ ] Security team can be notified (potential account compromise)

---

## 5. Edge Cases & Boundary Tests

### 5.1 Email Case Insensitivity

**Test 1: Login with uppercase**

```json
{
  "email": "JANE.SMITH@UNIVERSITY.EDU",
  "password": "Test@1234"
}
```

**Expected:** `200 OK`

**Test 2: Login with mixed case**

```json
{
  "email": "Jane.Smith@University.Edu",
  "password": "Test@1234"
}
```

**Expected:** `200 OK`

**Verify:**

- [ ] Email lookup is case-insensitive
- [ ] Returns same user as lowercase version
- [ ] response.email matches database value (lowercase)
- [ ] Role matches regardless of case

---

### 5.2 Password Case Sensitivity

**Original password:** Test@1234

**Test 1: Wrong case**

```json
{
  "email": "jane.smith@university.edu",
  "password": "test@1234"
}
```

**Expected:** `401 INVALID_CREDENTIALS`

**Test 2: Different case**

```json
{
  "email": "jane.smith@university.edu",
  "password": "TEST@1234"
}
```

**Expected:** `401 INVALID_CREDENTIALS`

**Verify:**

- [ ] Password comparison is case-sensitive (BCrypt enforces this)
- [ ] Passwords with wrong case are rejected

---

### 5.3 Concurrent Login Attempts (Same User, Different IPs)

**Setup:** Simultaneously send 3 login requests from 3 different IP addresses (or simulate with threading)

**IP 1:** POST /auth/login (jane.smith@university.edu)  
**IP 2:** POST /auth/login (jane.smith@university.edu)  
**IP 3:** POST /auth/login (jane.smith@university.edu)

**Expected:** All 3 receive `200 OK`

**Verify:**

- [ ] All 3 receive valid cookies (independent sessions)
- [ ] All 3 have independent refresh tokens in DB (3 separate refresh_token rows)
- [ ] Logging out from IP1 does NOT invalidate IP2 or IP3 sessions
- [ ] Each IP can independently call /auth/me
- [ ] Each IP can independently refresh its tokens

---

### 5.4 Concurrent Refresh Requests (Token Rotation Race Condition)

**Setup:** Send 3 simultaneous POST /auth/refresh requests with same refresh_token

**Token Version:** refresh_token_v1 (issued at t0)

**Request 1:** POST /auth/refresh (refresh_token_v1)  
**Request 2:** POST /auth/refresh (refresh_token_v1) — sent simultaneously  
**Request 3:** POST /auth/refresh (refresh_token_v1) — sent simultaneously

**Possible Outcomes:**

**Option A (Serialization):**

- Request 1: `200 OK` → refresh_token_v1 revoked, refresh_token_v2 issued
- Request 2: `401 INVALID_TOKEN` (token already revoked)
- Request 3: `401 INVALID_TOKEN` (token already revoked)

**Option B (First Wins):**

- All 3 serialized; first succeeds, others fail with 401

**Verify:**

- [ ] No race condition creates duplicate tokens
- [ ] Final state: exactly 1 new token issued
- [ ] Defeated requests receive 401
- [ ] No database integrity violation
- [ ] Winning request's token is valid for subsequent calls

---

### 5.5 Role Change Mid-Session

**Setup:**

1. User jane.smith (STUDENT) logs in at t1
2. Access token issued (valid until t1 + 15 min)
3. ADMIN changes jane's role to INSTRUCTOR at t2 (while token is still valid)
4. jane calls /auth/me at t3 (t3 < t1 + 15 min, token still valid)

**Expected:**

- /auth/me returns role=STUDENT (old role, from token principal)

**Then:** 5. jane calls POST /auth/refresh at t4 6. New access token issued 7. jane calls /auth/me at t5

**Expected:**

- /auth/me returns role=INSTRUCTOR (new role, from database lookup on refresh)

**Verify:**

- [ ] Changes are NOT immediately visible (access token is stateless)
- [ ] Changes visible after next refresh (token rotation reflects DB state)
- [ ] Tests eventual consistency window (TTL = 15 min)
- [ ] Audit log shows ROLE_CHANGED event at t2
- [ ] Next login shows new role immediately

---

### 5.6 Account Deactivation Mid-Session

**Setup:**

1. User jane.smith (STUDENT) logs in
2. SYSTEM_ADMIN executes: UPDATE users SET is_active=false WHERE id=jane
3. jane attempts to call GET /auth/me with valid access token

**Expected:** `403 Forbidden`

```json
{
  "success": false,
  "error": {
    "code": "ACCOUNT_DEACTIVATED",
    "message": "Your account has been deactivated. Contact admin."
  },
  "timestamp": "2026-04-07T14:00:00.000Z"
}
```

**Alternative (depending on implementation):**

If implementation checks deactivation on every request (recommended):

- /auth/me: `403 ACCOUNT_DEACTIVATED` (even with valid token)

If implementation only checks on login:

- /auth/me: `200 OK` (token still valid until expiry or refresh)
- Next refreshtoken call: `403 ACCOUNT_DEACTIVATED`

**Verify:**

- [ ] Deactivated users cannot use existing tokens (Option 1 preferred for security)
- [ ] GET /auth/me fails immediately after deactivation
- [ ] User is forced to contact admin
- [ ] Any subsequent endpoint call should fail (consistent policy)
- [ ] Audit log: ACCOUNT_DEACTIVATED event

---

### 5.7 Logout Idempotency

**Setup:** User is logged in

**Request 1:** POST /auth/logout → `200 OK`  
**Request 2:** POST /auth/logout (immediately after) → Expected: `200 OK` (idempotent)

**Verify:**

- [ ] First logout succeeds, refreshtoken revoked
- [ ] Second logout with already-cleared cookie also returns `200`
- [ ] No error thrown for logout-when-already-logged-out
- [ ] Idempotent operation (safe to retry)
- [ ] Two audit logs: both USER_LOGOUT events (second may be optional)

---

### 5.8 Rapid Logout-Then-Login

**Setup:** User logs out, then logs back in within 1 second

**Request 1:** POST /auth/logout → `200 OK`  
**Request 2:** POST /auth/login (same email) → `200 OK`

**Verify:**

- [ ] Second login issues NEW refresh tokens
- [ ] Old tokens are dead (revoked in DB)
- [ ] New tokens are valid
- [ ] No conflicts in refresh_tokens table
- [ ] Audit log shows LOGOUT then LOGIN

---

### 5.9 Multiple Login Sessions (Not Logged Out)

**Setup:** User logs in twice WITHOUT logging out first

**Time t1:** POST /auth/login → refresh_token_v1 issued  
**Time t2:** POST /auth/login → refresh_token_v2 issued

**Verify:**

- [ ] Both tokens exist in refresh_tokens table (not revoked)
- [ ] User can use either refresh_token_v1 or refresh_token_v2
- [ ] Calling refresh with v1 at t3 → v1 revoked, v3 issued
- [ ] Calling refresh with v2 at t4 → v2 revoked, v4 issued
- [ ] User can maintain multiple independent sessions
- [ ] Logout only revokes current token, not all tokens

---

### 5.10 Login After Account Deleted

**Setup:**

1. User account deleted from users table at t0
2. Attempt login at t1

**HTTP Method:** POST  
**Endpoint:** `/auth/login`

**Request Body:**

```json
{
  "email": "deleted.user@university.edu",
  "password": "Test@1234"
}
```

**Expected Response:** `401 Unauthorized` (identical to 4.2 — user not found)

```json
{
  "success": false,
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "Invalid email or password"
  },
  "timestamp": "2026-04-07T14:05:00.000Z"
}
```

**Verify:**

- [ ] Deleted user cannot login
- [ ] Error code is INVALID_CREDENTIALS (same as wrong password — no difference)
- [ ] No audit log for deleted user
- [ ] Soft delete recommendation: set is_active=false instead (preserves audit trail)

---

## 6. Audit Logging Tests

All audit tests verify that the correct events are created in the audit_log table with the expected structure and fields.

### 6.1 Successful Login — USER_LOGIN Event

**Trigger:** POST /auth/login with correct credentials

**Real Test User:** sarah.johnson@university.edu (INSTRUCTOR)

**Database Query After Login:**

```sql
SELECT * FROM audit_log
WHERE action='USER_LOGIN'
  AND actor_id=(SELECT id FROM users WHERE email='sarah.johnson@university.edu')
ORDER BY created_at DESC
LIMIT 1;
```

**Expected audit_log Entry:**

```
id: <auto_increment>
action: USER_LOGIN
entity_type: USER
entity_id: <sarah_user_id>
actor_id: <sarah_user_id>
actor_role: INSTRUCTOR
timestamp: <NOW() at login>
change_set: {
  "email": "sarah.johnson@university.edu",
  "role": "INSTRUCTOR",
  "ip_address": "127.0.0.1" (if captured),
  "session_id": "<jwt_token_id>" (for correlation)
}
reason: null
trace_id: <X-Trace-Id from request>
created_at: <NOW()>
```

**Verify:**

- [ ] Exactly 1 USER_LOGIN entry created
- [ ] actor_id = entity_id = sarah_user_id (user logging in)
- [ ] actor_role = INSTRUCTOR
- [ ] timestamp is near login time (within 1 second)
- [ ] trace_id matches X-Trace-Id from HTTP response
- [ ] change_set includes email and role
- [ ] created_at is NOW() (insertion time)
- [ ] No reason field for successful login

---

### 6.2 Failed Login (Wrong Password) — USER_LOGIN_FAILED Event

**Trigger:** POST /auth/login with correct email, wrong password

**Fake Test User:** marcus.chen@university.edu (STUDENT)  
**Wrong Password:** WrongPassword!

**Database Query After Failed Attempt:**

```sql
SELECT * FROM audit_log
WHERE action='USER_LOGIN_FAILED'
  AND entity_type='USER'
  AND change_set->>'email' = 'marcus.chen@university.edu'
ORDER BY created_at DESC
LIMIT 1;
```

**Expected audit_log Entry:**

```
id: <auto_increment>
action: USER_LOGIN_FAILED
entity_type: USER
entity_id: <marcus_user_id>
actor_id: null (no authenticated actor — login failed)
actor_role: null
timestamp: <NOW() at failed attempt>
change_set: {
  "email": "marcus.chen@university.edu",
  "reason": "INVALID_CREDENTIALS",
  "ip_address": "127.0.0.1",
  "attempt_number": 1
}
reason: "Invalid email or password"
trace_id: <X-Trace-Id from request>
created_at: <NOW()>
```

**Verify:**

- [ ] action = USER_LOGIN_FAILED
- [ ] entity_type = USER, entity_id = marcus_user_id
- [ ] actor_id = null (not yet authenticated)
- [ ] change_set.reason = "INVALID_CREDENTIALS"
- [ ] trace_id matches request
- [ ] Entry created within 1 second of failed attempt
- [ ] **Not** revealing which part of credentials was wrong

---

### 6.3 Failed Login (Non-Existent User) — USER_LOGIN_FAILED Event

**Trigger:** POST /auth/login with non-existent email

**Non-Existent Email:** nonexistent@university.edu  
**Password:** Test@1234

**Expected audit_log Entry (if captured):**

**Option A (Recommended - Privacy):**

- No audit entry (user doesn't exist, nothing to audit)

**Option B (Detailed Logging):**

```
action: USER_LOGIN_FAILED
entity_type: USER
entity_id: null
actor_id: null
actor_role: null
change_set: {
  "email": "nonexistent@university.edu",
  "reason": "USER_NOT_FOUND",
  "ip_address": "127.0.0.1"
}
reason: "User not found"
```

**Verify:**

- [ ] If logged: change_set.reason = "USER_NOT_FOUND" or "INVALID_CREDENTIALS"
- [ ] Does NOT log missing user to expose email existence
- [ ] If not logged: acceptable for privacy reasons
- [ ] IP address captured for rate limiting

---

### 6.4 Failed Login (Deactivated Account) — USER_LOGIN_FAILED Event

**Trigger:** POST /auth/login for deactivated user

**Deactivated User:** deactivated_user@university.edu  
**Password:** Test@1234  
**DB State:** users.is_active=false

**Expected audit_log Entry:**

```
action: USER_LOGIN_FAILED
entity_type: USER
entity_id: <deactivated_user_id>
actor_id: null
actor_role: null
change_set: {
  "email": "deactivated_user@university.edu",
  "reason": "ACCOUNT_DEACTIVATED",
  "ip_address": "127.0.0.1"
}
reason: "Account has been deactivated"
trace_id: <X-Trace-Id>
created_at: <NOW()>
```

**Verify:**

- [ ] action = USER_LOGIN_FAILED
- [ ] change_set.reason = "ACCOUNT_DEACTIVATED" (distinct from INVALID_CREDENTIALS)
- [ ] entity_id = deactivated_user_id (user exists, just inactive)
- [ ] Distinguishes from wrong password in audit

---

### 6.5 Successful Logout — USER_LOGOUT Event

**Trigger:** POST /auth/logout by authenticated user

**User:** michael.torres@university.edu (INSTRUCTOR)

**Database Query After Logout:**

```sql
SELECT * FROM audit_log
WHERE action='USER_LOGOUT'
  AND actor_id=(SELECT id FROM users WHERE email='michael.torres@university.edu')
ORDER BY created_at DESC
LIMIT 1;
```

**Expected audit_log Entry:**

```
id: <auto_increment>
action: USER_LOGOUT
entity_type: USER
entity_id: <michael_user_id>
actor_id: <michael_user_id> (authenticated, logging themselves out)
actor_role: INSTRUCTOR
timestamp: <NOW() at logout>
change_set: {
  "session_end_time": "2026-04-07T14:10:00.123Z",
  "session_duration_seconds": 300
}
reason: null
trace_id: <X-Trace-Id from logout request>
created_at: <NOW()>
```

**Verify:**

- [ ] Exactly 1 USER_LOGOUT entry created
- [ ] actor_id = entity_id = michael_user_id
- [ ] actor_role = INSTRUCTOR
- [ ] change_set optionally includes session duration
- [ ] timestamp is near logout time
- [ ] trace_id matches HTTP response header

---

### 6.6 Rate Limit Exceeded — RATE_LIMIT_EXCEEDED Event

**Trigger:** 6th failed login from same IP within 15 minutes

**IP Address:** 192.168.1.100 (simulated)  
**Email:** jane.smith@university.edu (used for all 6 attempts)  
**Attempts:** 1-5 return 401, 6th returns 429

**Expected audit_log Entry (for 6th attempt):**

```
action: RATE_LIMIT_EXCEEDED
entity_type: null (or LOGIN_ATTEMPT)
entity_id: null
actor_id: null (not authenticated)
actor_role: null
change_set: {
  "ip_address": "192.168.1.100",
  "attempt_count": 6,
  "endpoint": "/auth/login",
  "retry_after_seconds": 840,
  "user_attempted": "jane.smith@university.edu"
}
reason: "Too many login attempts from IP"
trace_id: <X-Trace-Id>
created_at: <NOW()>
```

**Verify:**

- [ ] action = RATE_LIMIT_EXCEEDED
- [ ] change_set.ip_address = 192.168.1.100 (accurate IP)
- [ ] change_set.attempt_count = 6
- [ ] change_set.retry_after_seconds = 840 (14 minutes remaining)
- [ ] Entry created when 6th attempt made
- [ ] Can be used for security analytics (repeated attackers)
- [ ] trace_id correlates with 429 HTTP response

---

### 6.7 Token Reuse Attack — TOKEN_REUSE_ATTACK Audit Event

**Trigger:** Refresh token used AFTER it was already rotated

**Timeline:**

- t1: Login → refresh_token_v1 issued
- t2: POST /auth/refresh with v1 → v1 revoked, v2 issued
- t3: POST /auth/refresh with v1 AGAIN (stolen/reused) → 401 INVALID_TOKEN

**Expected audit_log Entry (at t3):**

```
action: TOKEN_REUSE_ATTACK (or SECURITY_INCIDENT)
entity_type: USER
entity_id: <user_id>
actor_id: null (unauthenticated attack)
actor_role: null
change_set: {
  "event_type": "TOKEN_REUSE_ATTACK",
  "token_id": "<refresh_token_v1_jti>",
  "ip_address": "192.168.1.50",
  "user_email": "jane.smith@university.edu",
  "action_taken": "ALL_TOKENS_REVOKED"
}
reason: "Refresh token reuse detected - possible account compromise"
trace_id: <X-Trace-Id>
created_at: <NOW()>
```

**Database State After Attack:**

```sql
SELECT * FROM refresh_tokens WHERE user_id=<jane_id>;
-- Expected: is_revoked=true for ALL tokens (security measure)
```

**Verify:**

- [ ] action = TOKEN_REUSE_ATTACK (flagged as security incident)
- [ ] entity_id = affected user_id
- [ ] change_set includes original token_id, ip_address, action_taken
- [ ] **ALL tokens for user are revoked** (defensive security)
- [ ] User must re-authenticate (login again)
- [ ] trace_id for incident investigation
- [ ] Entry serves as security alert for admin dashboard

---

## 7. Postman Test Collection (Ready-to-Run)

Base URL: `{{baseUrl}}` = `http://localhost:8081/api/v1`

### 7.1 Happy Path: SYSTEM_ADMIN Login → Access /system Endpoints

**Test Name:** `01_SYSTEM_ADMIN_Login_Access_System`

**Folder:** Auth > HappyPath > SystemAdmin

**Request 1: Login as SYSTEM_ADMIN**

```
POST {{baseUrl}}/auth/login
Content-Type: application/json

{
  "email": "main_sysadmin@reviewflow.com",
  "password": "Test@1234"
}
```

**Expected:** `200 OK`

**Tests (Postman Script):**

```javascript
pm.test("Login successful", function () {
  pm.response.to.have.status(200);
  var json = pm.response.json();
  pm.expect(json.success).to.equal(true);
  pm.expect(json.data.role).to.equal("SYSTEM_ADMIN");
  pm.environment.set("userId", json.data.userId);
  pm.environment.set("accessToken", pm.cookies.get("reviewflow_access"));
});

pm.test("Access cookie set", function () {
  var cookie = pm.cookies.get("reviewflow_access");
  pm.expect(cookie).to.not.be.undefined;
});

pm.test("Audit log USER_LOGIN created", function () {
  // Would require separate DB query or audit API endpoint
  // pm.expect(auditLog).to.contain("USER_LOGIN");
});
```

---

**Request 2: Access /system/cache/stats (SYSTEM_ADMIN should succeed)**

```
GET {{baseUrl}}/system/cache/stats
Cookie: reviewflow_access={{accessToken}}
```

**Expected:** `200 OK`, returns cache statistics

**Tests:**

```javascript
pm.test("SYSTEM_ADMIN can access /system endpoints", function () {
  pm.response.to.have.status(200);
});
```

---

**Request 3: Logout**

```
POST {{baseUrl}}/auth/logout
Cookie: reviewflow_access={{accessToken}}
```

**Expected:** `200 OK`

**Tests:**

```javascript
pm.test("Logout successful", function () {
  pm.response.to.have.status(200);
  var json = pm.response.json();
  pm.expect(json.success).to.equal(true);
});
```

---

### 7.2 Happy Path: ADMIN Login → Verify Cannot Access /system

**Test Name:** `02_ADMIN_Login_403_System`

**Folder:** Auth > HappyPath > Admin

**Request 1: Login as ADMIN**

```
POST {{baseUrl}}/auth/login
Content-Type: application/json

{
  "email": "humberadmin@reviewflow.com",
  "password": "Test@1234"
}
```

**Expected:** `200 OK`, role=ADMIN

---

**Request 2: Try to access /system/cache/stats (should be 403)**

```
GET {{baseUrl}}/system/cache/stats
Cookie: reviewflow_access={{accessToken}}
```

**Expected:** `403 Forbidden`

**Tests:**

```javascript
pm.test("ADMIN cannot access /system endpoints", function () {
  pm.response.to.have.status(403);
  var json = pm.response.json();
  pm.expect(json.error.code).to.equal("INSUFFICIENT_PERMISSIONS");
});
```

---

### 7.3 Happy Path: INSTRUCTOR Login → Create Course (FIX 2)

**Test Name:** `03_INSTRUCTOR_Login_Create_Course`

**Folder:** Auth > HappyPath > Instructor

**Note:** Per decision/fix, INSTRUCTORs can now create courses (PRD-02 update)

**Request 1: Login as INSTRUCTOR**

```
POST {{baseUrl}}/auth/login
Content-Type: application/json

{
  "email": "sarah.johnson@university.edu",
  "password": "Test@1234"
}
```

**Expected:** `200 OK`, role=INSTRUCTOR

**Tests:**

```javascript
pm.test("INSTRUCTOR login successful", function () {
  pm.response.to.have.status(200);
  pm.expect(pm.response.json().data.role).to.equal("INSTRUCTOR");
});
```

---

**Request 2: Create Course (now allowed per Fix 2)**

```
POST {{baseUrl}}/courses
Content-Type: application/json
Cookie: reviewflow_access={{accessToken}}

{
  "code": "CS-401",
  "name": "Advanced Java Programming",
  "term": "Spring-2026",
  "description": "Deep dive into Java 21 features and best practices"
}
```

**Expected:** `201 Created`

**Tests:**

```javascript
pm.test("INSTRUCTOR can create course (Fix 2)", function () {
  pm.response.to.have.status(201);
  var json = pm.response.json();
  pm.expect(json.data.code).to.equal("CS-401");
  pm.expect(json.data.instructorId).to.equal(pm.environment.get("userId"));
});
```

---

### 7.4 Token Rotation Flow

**Test Name:** `04_Token_Rotation_Flow`

**Folder:** Auth > EdgeCases > TokenRotation

**Request 1: Login (get initial refresh_cookie_1)**

```
POST {{baseUrl}}/auth/login
Content-Type: application/json

{
  "email": "jane.smith@university.edu",
  "password": "Test@1234"
}
```

**Expected:** `200 OK`, cookies set

**Tests:**

```javascript
var cookies = pm.cookies.jar.get("{{baseUrl}}");
pm.environment.set("refresh_token_1", pm.cookies.get("reviewflow_refresh"));
pm.environment.set("access_token_1", pm.cookies.get("reviewflow_access"));
```

---

**Request 2: Refresh Tokens (rotates refresh_cookie_1 to refresh_cookie_2)**

```
POST {{baseUrl}}/auth/refresh
Cookie: reviewflow_refresh={{refresh_token_1}}
```

**Expected:** `200 OK`, new cookies issued

**Tests:**

```javascript
pm.test("New tokens issued", function () {
  pm.response.to.have.status(200);
  pm.environment.set("refresh_token_2", pm.cookies.get("reviewflow_refresh"));
  pm.environment.set("access_token_2", pm.cookies.get("reviewflow_access"));

  // Verify they're different from original
  pm.expect(pm.environment.get("refresh_token_2")).to.not.equal(
    pm.environment.get("refresh_token_1"),
  );
});
```

---

**Request 3: Try to reuse OLD refresh_token_1 (should fail - reuse attack)**

```
POST {{baseUrl}}/auth/refresh
Cookie: reviewflow_refresh={{refresh_token_1}}
```

**Expected:** `401 Unauthorized`

**Tests:**

```javascript
pm.test("Reuse attack detected", function () {
  pm.response.to.have.status(401);
  var json = pm.response.json();
  pm.expect(json.error.code).to.equal("INVALID_TOKEN");
});

pm.test("User forced to re-authenticate", function () {
  // Follow-up: try to call /auth/me
  // Expected: 401 (all tokens revoked)
});
```

---

**Request 4: Verify new tokens work**

```
GET {{baseUrl}}/auth/me
Cookie: reviewflow_access={{access_token_2}}
```

**Expected:** `200 OK`, current user

---

### 7.5 Rate Limiting Test (6 Failed Attempts)

**Test Name:** `05_Rate_Limiting_429`

**Folder:** Auth > ErrorCases > RateLimiting

**Note:** Requires sequential requests with delays, or triggered via script

**Pre-request Script (runs before all 6 requests):**

```javascript
// Reset rate limiter (if admin endpoint available)
// pm.sendRequest({
//   url: "{{baseUrl}}/admin/rate-limit/reset",
//   method: "POST",
//   header: { "Authorization": "Bearer {{systemAdminToken}}" }
// });
```

**Requests 1-5: Failed Login (wrong password)**

```
POST {{baseUrl}}/auth/login
Content-Type: application/json

{
  "email": "marcus.chen@university.edu",
  "password": "WrongPassword_{{$randomInt}}"
}
```

**Expected:** `401 Unauthorized` (Requests 1-5)

---

**Request 6: 6th Attempt (rate limited)**

```
POST {{baseUrl}}/auth/login
Content-Type: application/json

{
  "email": "marcus.chen@university.edu",
  "password": "WrongPassword_6"
}
```

**Expected:** `429 Too Many Requests`

**Tests:**

```javascript
pm.test("Rate limit enforced on 6th attempt", function () {
  pm.response.to.have.status(429);
  var json = pm.response.json();
  pm.expect(json.error.code).to.equal("RATE_LIMIT_EXCEEDED");
});

pm.test("Retry-After header present", function () {
  pm.expect(pm.response.headers.get("Retry-After")).to.exist;
  var retryAfter = parseInt(pm.response.headers.get("Retry-After"));
  pm.expect(retryAfter).to.be.below(900); // < 15 min
});
```

---

### 7.6 Security: Account Deactivation Mid-Session

**Test Name:** `06_Account_Deactivation_Mid_Session`

**Folder:** Auth > Security > Deactivation

**Requires Database Access (DBA or integration test setup)**

**Request 1: Login as deactivated_user@university.edu**

```
POST {{baseUrl}}/auth/login
Content-Type: application/json

{
  "email": "deactivated_user@university.edu",
  "password": "Test@1234"
}
```

**Expected:** `403 Forbidden` (ACCOUNT_DEACTIVATED)

**Tests:**

```javascript
pm.test("Deactivated user cannot login", function () {
  pm.response.to.have.status(403);
  var json = pm.response.json();
  pm.expect(json.error.code).to.equal("ACCOUNT_DEACTIVATED");
});
```

---

### 7.7 Error Cases: 401 Unauthorized Tests

**Collection Name:** `007_Error_Cases_401`

**Requests:**

#### 7.7.1 Wrong Password

```
POST {{baseUrl}}/auth/login
{ "email": "jane.smith@university.edu", "password": "WrongPassword" }
```

**Expected:** `401 INVALID_CREDENTIALS`

#### 7.7.2 Non-Existent User

```
POST {{baseUrl}}/auth/login
{ "email": "nonexistent@university.edu", "password": "Test@1234" }
```

**Expected:** `401 INVALID_CREDENTIALS` (same error as wrong password)

#### 7.7.3 No Token on /auth/me

```
GET {{baseUrl}}/auth/me
(no cookie)
```

**Expected:** `401 UNAUTHORIZED`

---

### 7.8 Validation Tests: 400 Bad Request

**Collection Name:** `008_Validation_Errors_400`

#### 7.8.1 Missing Email

```
POST {{baseUrl}}/auth/login
{ "password": "Test@1234" }
```

**Expected:** `400 VALIDATION_ERROR` - "email is required"

#### 7.8.2 Missing Password

```
POST {{baseUrl}}/auth/login
{ "email": "jane.smith@university.edu" }
```

**Expected:** `400 VALIDATION_ERROR` - "password is required"

#### 7.8.3 Invalid Email Format

```
POST {{baseUrl}}/auth/login
{ "email": "not-an-email", "password": "Test@1234" }
```

**Expected:** `400 VALIDATION_ERROR` - "email must be a valid email address"

#### 7.8.4 Empty Email

```
POST {{baseUrl}}/auth/login
{ "email": "", "password": "Test@1234" }
```

**Expected:** `400 VALIDATION_ERROR` - "email is required"

---

## 8. Test Execution Checklist

### Phase 1: Happy Path (All Roles)

- [ ] **3.1** SYSTEM_ADMIN Login → 200 OK, role=SYSTEM_ADMIN
- [ ] **3.2** ADMIN Login → 200 OK, role=ADMIN
- [ ] **3.3** INSTRUCTOR Login → 200 OK, role=INSTRUCTOR
- [ ] **3.4** STUDENT Login → 200 OK, role=STUDENT
- [ ] **3.5A** GET /auth/me → 200 OK, current user data
- [ ] **3.5B** STUDENT accessing /admin endpoint → 403 INSUFFICIENT_PERMISSIONS
- [ ] **3.6** Token Refresh Flow → 200 OK, new tokens issued, old revoked
- [ ] **3.7** Logout → 200 OK, cookies cleared, tokens revoked

### Phase 2: 401 Unauthorized Tests

- [ ] **4.1** Wrong Password → 401 INVALID_CREDENTIALS
- [ ] **4.2** Non-Existent User → 401 INVALID_CREDENTIALS (same as 4.1)
- [ ] **4.9** GET /auth/me without token → 401 UNAUTHORIZED
- [ ] **4.10** GET /auth/me after logout → 401 UNAUTHORIZED
- [ ] **4.11** Expired access token → 401 UNAUTHORIZED

### Phase 3: 403 Forbidden Tests

- [ ] **4.3** Deactivated Account → 403 ACCOUNT_DEACTIVATED
- [ ] **5.6** Account Deactivation Mid-Session → 403 ACCOUNT_DEACTIVATED
- [ ] **3.5B** Role-based access control → 403 INSUFFICIENT_PERMISSIONS

### Phase 4: 400 Bad Request Tests

- [ ] **4.4** Missing email → 400 VALIDATION_ERROR
- [ ] **4.5** Missing password → 400 VALIDATION_ERROR
- [ ] **4.6** Invalid email format → 400 VALIDATION_ERROR
- [ ] **4.7** Empty email string → 400 VALIDATION_ERROR

### Phase 5: 429 Rate Limiting

- [ ] **4.8** 6th failed attempt from same IP → 429 RATE_LIMIT_EXCEEDED
- [ ] **4.8** Retry-After header present and correct
- [ ] **4.8** After 15 min, login succeeds → 200 OK
- [ ] **6.6** Audit log: RATE_LIMIT_EXCEEDED event created

### Phase 6: Edge Cases

- [ ] **5.1** Email case insensitivity → login with UPPERCASE
- [ ] **5.2** Password case sensitivity → wrong case fails
- [ ] **5.3** Concurrent logins (3+ IPs) → all succeed independently
- [ ] **5.4** Concurrent refresh requests → serialization, no duplicates
- [ ] **5.5** Role change mid-session → old role visible until refresh
- [ ] **5.7** Logout idempotency → second logout returns 200
- [ ] **5.8** Rapid logout-then-login → both succeed
- [ ] **5.9** Multiple login sessions → both tokens work independently

### Phase 7: Audit Logging

- [ ] **6.1** Successful login → USER_LOGIN audit event
- [ ] **6.2** Failed login (wrong password) → USER_LOGIN_FAILED event
- [ ] **6.3** Failed login (user not found) → audit handling
- [ ] **6.4** Failed login (deactivated) → USER_LOGIN_FAILED with ACCOUNT_DEACTIVATED
- [ ] **6.5** Logout → USER_LOGOUT audit event
- [ ] **6.6** Rate limit exceeded → RATE_LIMIT_EXCEEDED audit event
- [ ] **6.7** Token reuse attack → TOKEN_REUSE_ATTACK event + all tokens revoked

### Phase 8: Security Tests

- [ ] **4.12** Token reuse attack → 401 INVALID_TOKEN, all tokens revoked
- [ ] **5.6** Account deactivation mid-session → 403 (or appropriate enforcement)
- [ ] **6.7** Stolen token detection → forced re-authentication

### Phase 9: Database Integrity

- [ ] All refresh_tokens entries are immutable (audit trail preserved)
- [ ] No duplicate refresh tokens in DB after rotation
- [ ] audit_log table has correct entries (verified above)
- [ ] No N+1 queries in /auth/me endpoint (profile endpoint)
- [ ] Timestamps are ISO 8601 UTC format

### Phase 10: Response Format Compliance

- [ ] All 200 responses follow: `{ success: true, data: {...}, timestamp: ISO8601 }`
- [ ] All error responses follow: `{ success: false, error: { code, message }, timestamp: ISO8601 }`
- [ ] X-Trace-Id header present in all responses
- [ ] X-Total-Elements and X-Total-Pages NOT present for single-entity endpoints

---

## 9. Known Gaps & TODOs

### To Be Verified (Implementation Details)

- [ ] **Rate Limiting IP Tracking**
  - [ ] Verify IP address is correctly extracted from X-Forwarded-For header (proxy scenario)
  - [ ] Confirm rate limit is per-IP, not globally
  - [ ] Test with VPN/proxy (X-Forwarded-For header)
- [ ] **Token Fingerprinting (PRD-09)**
  - [ ] Confirm token fingerprinting is implemented against stolen token attacks
  - [ ] Verify device fingerprint is included in token claims
  - [ ] Test browser fingerprinting (User-Agent, Accept-Language)
  - [ ] Confirm fingerprint mismatch → 401 INVALID_TOKEN

- [ ] **WebSocket Force-Logout (PRD-09)**
  - [ ] Verify SYSTEM_ADMIN force-logout triggers WebSocket disconnect for affected user
  - [ ] Confirm WebSocket client receives logout event
  - [ ] Test WebSocket reconnection denied after force-logout

- [ ] **Email Verification (Not Mentioned)**
  - [ ] Confirm if email verification is required before login
  - [ ] Verify unverified users receive appropriate error
  - [ ] If required: add test for email verification workflow

- [ ] **Two-Factor Authentication (Not Mentioned)**
  - [ ] Check if 2FA is implemented (not in current spec)
  - [ ] If implemented: add 2FA challenge tests

- [ ] **FORGOT_PASSWORD Endpoint**
  - [ ] Verify FORGOT_PASSWORD endpoint exists or is not required
  - [ ] If exists: add test spec for password reset flow
  - [ ] If not: confirm out of scope for Auth module

- [ ] **Concurrent Logout from Multiple IPs**
  - [ ] Verify logout behavior when user has multiple active sessions
  - [ ] Confirm logout only revokes current session or all sessions (design decision)
  - [ ] Current assumption: logout revokes only current refresh token, not all

- [ ] **Database Connection Failure During Login**
  - [ ] Test login when database is unreachable
  - [ ] Verify 500 error is returned (not masked as 401)
  - [ ] Check CloudWatch logs are generated

- [ ] **Null Check on @AuthenticationPrincipal**
  - [ ] Confirm controller has `if (principal == null) return 401` check
  - [ ] Verify no NullPointerException on /auth/me without token
  - [ ] Use Postman test to verify response

- [ ] **Password Hashing Algorithm Verification**
  - [ ] Confirm BCrypt is used with cost factor ≥ 12
  - [ ] Verify password is never logged or exposed in errors
  - [ ] Test old bcrypt hashes are migrated (if applicable)

---

## 10. Test Data Management

### Test Users Created via Flyway Migrations

```sql
-- SYSTEM_ADMIN accounts (2 total, platform limit)
INSERT INTO users (email, password_hash, first_name, last_name, role, is_active, created_at)
VALUES
  ('admin@reviewflow.com', '$2a$12$...', 'Admin', 'User', 'SYSTEM_ADMIN', true, NOW()),
  ('main_sysadmin@reviewflow.com', '$2a$12$...', 'Main', 'Sysadmin', 'SYSTEM_ADMIN', true, NOW());

-- ADMIN accounts
INSERT INTO users (email, password_hash, first_name, last_name, role, is_active, created_at)
VALUES
  ('humberadmin@reviewflow.com', '$2a$12$...', 'Humber', 'Admin', 'ADMIN', true, NOW()),
  ('yorkadmin@reviewflow.com', '$2a$12$...', 'York', 'Admin', 'ADMIN', true, NOW());

-- INSTRUCTOR accounts
INSERT INTO users (email, password_hash, first_name, last_name, role, is_active, created_at)
VALUES
  ('sarah.johnson@university.edu', '$2a$12$...', 'Sarah', 'Johnson', 'INSTRUCTOR', true, NOW()),
  ('michael.torres@university.edu', '$2a$12$...', 'Michael', 'Torres', 'INSTRUCTOR', true, NOW());

-- STUDENT accounts
INSERT INTO users (email, password_hash, first_name, last_name, role, is_active, created_at)
VALUES
  ('jane.smith@university.edu', '$2a$12$...', 'Jane', 'Smith', 'STUDENT', true, NOW()),
  ('marcus.chen@university.edu', '$2a$12$...', 'Marcus', 'Chen', 'STUDENT', true, NOW());

-- TEST: Deactivated account
INSERT INTO users (email, password_hash, first_name, last_name, role, is_active, created_at)
VALUES
  ('deactivated_user@university.edu', '$2a$12$...', 'Deactivated', 'User', 'STUDENT', false, NOW());

-- Password hash is BCrypt of "Test@1234" with cost factor 12
-- $ 2a $ 12 $ [22 chars salt] [31 chars hash] = 60 chars total
```

### Test Data Cleanup (After Tests)

**Option 1: Revert via Flyway** (Recommended)

- Create `V_NextVersion__cleanup_test_users.sql` (only in dev/test builds)
- Delete test users after test suite completes

**Option 2: Manual Cleanup**

```sql
DELETE FROM refresh_tokens WHERE user_id IN (
  SELECT id FROM users WHERE email IN (
    'jane.smith@university.edu',
    'marcus.chen@university.edu',
    'deactivated_user@university.edu'
  )
);
-- Test data preserved for audit trail
```

---

## 11. Implementation Notes for Developers

### Cookie Security (CRITICAL)

✅ **Must:** HttpOnly flag (prevents JS access)  
✅ **Must:** SameSite=Lax (prevents CSRF)  
✅ **Must:** Path=/ (accessible across all endpoints)  
✅ **Must:** Secure flag (HTTPS only in production)  
✅ **Must:** Max-Age on logout (0 or negative = immediate expiry)

### Token Handling (CRITICAL)

✅ **Must:** JWT signed with HS256 (secret key in environment)  
✅ **Must:** Access token expiry: 15 minutes  
✅ **Must:** Refresh token expiry: 7 days  
✅ **Must:** Token fingerprinting (Device/Browser hash in token claims)  
✅ **Must:** Revoked tokens checked in refresh_tokens table (full audit trail)

### Error Messages (CRITICAL)

✅ **Must NOT:** Reveal if email exists ("user not found" different from wrong password)  
✅ **Must NOT:** Expose password in logs or errors  
✅ **Must NOT:** Expose internal Java exception messages  
✅ **Must:** Use generic error codes (INVALID_CREDENTIALS, ACCOUNT_DEACTIVATED)

### Rate Limiting (CRITICAL)

✅ **Must:** Track per IP address (not per email)  
✅ **Must:** 5 failed attempts threshold (6th is rejected)  
✅ **Must:** 15-minute window  
✅ **Must:** Retry-After header includes seconds  
✅ **Must:** Resets after successful login

### Audit Logging (CRITICAL)

✅ **Must:** USER_LOGIN event on success  
✅ **Must:** USER_LOGIN_FAILED event on failure  
✅ **Must:** USER_LOGOUT event on logout  
✅ **Must:** RATE_LIMIT_EXCEEDED event on rate limit  
✅ **Must:** Include X-Trace-Id for request correlation  
✅ **Must:** Include IP address (if available)  
✅ **Must:** Immutable audit trail (INSERT only, no UPDATE)

---

## 12. References & Related Documents

- 📄 [01_Module_Auth.md](01_Module_Auth.md) — Auth module requirements
- 📄 [00_Global_Rules_and_Reference.md](00_Global_Rules_and_Reference.md) — Global response format
- 📄 [12_Module_Monitoring.md](12_Module_Monitoring.md) — Audit logging table schema
- 📄 [DECISIONS.md](../DECISIONS.md#2-http-only-cookies-over-authorization-header) — Cookie & token decisions
- 📄 [PRD9-SystemAdmin.md](../Features/PRD9-SystemAdmin.md) — Token fingerprinting (PRD-09)
- 📄 [ReviewFlow_Postman_Guide.md](../other/ReviewFlow_Postman_Guide.md) — Postman setup

---

**Document Version:** 1.0  
**Status:** ✅ Complete and Ready for Testing  
**Last Updated:** 2026-04-07  
**Next Review:** Post-Implementation (after Auth module coded)
