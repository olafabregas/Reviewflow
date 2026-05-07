# ReviewFlow — Module 1: Auth

> Controller: `AuthController.java`
> Base path: `/api/v1/auth`
>
> **Related PRDs:** [PRD-09 System Admin](../Features/PRD9-SystemAdmin.md)
> **Related Decisions:** [Decision 2: HTTP-only Cookies](../DECISIONS.md#2-http-only-cookies-over-authorization-header) | [Decision 3: Token Fingerprinting](../DECISIONS.md#3-token-fingerprinting-for-stolen-token-rejection)

---

## 1.1 POST /auth/login

### Must Have

- [ ] Endpoint exists and accepts `POST`
- [ ] Accepts `Content-Type: application/json`
- [ ] Reads `email` and `password` from request body
- [ ] Validates email is a valid email format
- [ ] Validates password is not blank
- [ ] Authenticates against BCrypt hashed password in DB
- [ ] On success: sets `reviewflow_access` HTTP-only cookie (15 min expiry)
- [ ] On success: sets `reviewflow_refresh` HTTP-only cookie (7 day expiry)
- [ ] Both cookies are `HttpOnly`, `SameSite=Lax`, `Path=/`
- [ ] JWT token is NOT returned in response body — cookies only
- [ ] Response body returns `{ userId, firstName, lastName, email, role }`

### Responses

- [ ] `200 OK` — credentials valid, cookies set, user data returned
- [ ] `400 Bad Request` — email missing or invalid format
- [ ] `400 Bad Request` — password missing or blank
- [ ] `401 Unauthorized` — email not found in DB
- [ ] `401 Unauthorized` — email found but password does not match
- [ ] `401` error code must be `INVALID_CREDENTIALS` — never reveal if email exists or not
- [ ] `403 Forbidden` — account exists but `is_active = false`
- [ ] `403` error code must be `ACCOUNT_DEACTIVATED` with message "Your account has been deactivated. Contact admin."
- [ ] `429 Too Many Requests` — more than 5 failed attempts from same IP in 15 minutes
- [ ] `429` response includes `Retry-After` header with seconds until retry allowed

### Edge Cases

- [ ] Login with correct email, wrong password → `401 INVALID_CREDENTIALS`
- [ ] Login with email that doesn't exist → `401 INVALID_CREDENTIALS` (same message as wrong password — never reveal email existence)
- [ ] Login with deactivated account → `403 ACCOUNT_DEACTIVATED`
- [ ] Login with valid credentials but malformed JSON body → `400`
- [ ] Login with empty string email → `400`
- [ ] Login with empty string password → `400`
- [ ] Login with email containing spaces → `400`
- [ ] 6th failed attempt from same IP within 15 min → `429`
- [ ] After rate limit expires, login works again → `200`
- [ ] Login logs `USER_LOGIN` event to `audit_log` on success
- [ ] Failed login logs `USER_LOGIN_FAILED` event to `audit_log`

---

## 1.2 POST /auth/refresh

### Must Have

- [ ] Endpoint exists and accepts `POST`
- [ ] Reads `reviewflow_refresh` cookie from request
- [ ] Looks up refresh token in `refresh_tokens` table
- [ ] Validates token is not revoked (`is_revoked = false`)
- [ ] Validates token is not expired (`expires_at > NOW()`)
- [ ] Issues new `reviewflow_access` cookie on success
- [ ] Rotates refresh token — old token revoked, new token issued
- [ ] New `reviewflow_refresh` cookie set with new token

### Responses

- [ ] `200 OK` — new access cookie issued, refresh token rotated
- [ ] `401 Unauthorized` — refresh cookie is missing
- [ ] `401 Unauthorized` — token not found in DB
- [ ] `401 Unauthorized` — token found but `is_revoked = true`
- [ ] `401 Unauthorized` — token found but expired

### Edge Cases

- [ ] Valid refresh token → new access token issued → `200`
- [ ] Missing refresh cookie → `401`
- [ ] Revoked refresh token → `401`
- [ ] Expired refresh token → `401`
- [ ] **CRITICAL:** Refresh token used AFTER it was rotated (reuse attack) → revoke ALL tokens for that user → `401`
- [ ] After all tokens revoked, user must log in again → `401 on /me`

---

## 1.3 POST /auth/logout

### Must Have

- [ ] Endpoint exists and accepts `POST`
- [ ] Requires valid access token cookie
- [ ] Reads `reviewflow_refresh` cookie from request
- [ ] Looks up refresh token in DB and marks `is_revoked = true`
- [ ] Clears `reviewflow_access` cookie by setting `Max-Age=0`
- [ ] Clears `reviewflow_refresh` cookie by setting `Max-Age=0`
- [ ] Returns `{ message: "Logged out successfully" }`
- [ ] Logs `USER_LOGOUT` to `audit_log`

### Responses

- [ ] `200 OK` — tokens revoked, cookies cleared
- [ ] `401 Unauthorized` — no valid access token

### Edge Cases

- [ ] Logout with valid session → `200`, cookies cleared
- [ ] Logout when refresh cookie is missing → still returns `200` (logout is idempotent)
- [ ] Call `/auth/me` immediately after logout → `401`
- [ ] Reuse refresh token after logout → `401 INVALID_CREDENTIALS`
- [ ] Call logout twice (already logged out) → `200` (idempotent)

---

## 1.4 GET /auth/me

### Must Have

- [ ] Endpoint exists and accepts `GET`
- [ ] Requires valid access token cookie
- [ ] Returns `{ userId, firstName, lastName, email, role }` for current user
- [ ] Uses `@AuthenticationPrincipal` to get current user
- [ ] **CRITICAL:** Has null check on `@AuthenticationPrincipal` — returns `401` not `500` when null

### Responses

- [ ] `200 OK` — returns current user profile
- [ ] `401 Unauthorized` — no valid access token cookie → `{ success: false, error: { code: "UNAUTHORIZED", message: "Not authenticated" } }`

### Edge Cases

- [ ] Authenticated request → `200` with correct user data
- [ ] No cookie present → `401` (not `500`)
- [ ] Expired access token → `401` (not `500`)
- [ ] After logout → `401` (not `500`)
- [ ] Token belongs to deactivated user → `401`

---

## 1.5 Auth hardening (cookies, sessions, WS ticket, step-up, reset)

**Controllers:** `AuthController`, `SessionController`, `StepUpController`, `PasswordResetController` — base path `/api/v1/auth` (password reset under `/api/v1/auth/password-reset/*`).

### Cookies (P0)

- [ ] Access cookie `SameSite=Strict`, path `/`; refresh cookie path `/api/v1/auth` (only sent to login/refresh/logout under that prefix).
- [ ] `X-Device-Id` optional on login; server may bind generated device id on session.

### Sessions (P1)

- [ ] `GET /auth/sessions` — list own sessions (authenticated).
- [ ] `DELETE /auth/sessions/{id}` — revoke one session (authenticated).
- [ ] `POST /auth/sessions/logout-all` — revoke all refresh tokens for current user (authenticated).

### WebSocket ticket (P1)

- [ ] `GET /auth/ws-ticket` — returns short-lived single-use ticket; STOMP `CONNECT` uses `X-Auth-Ticket` (not Bearer JWT).
- [ ] `GET /auth/token` — `410 Gone` with `ENDPOINT_REMOVED` during deprecation window.

### Step-up (P2)

- [ ] `POST /auth/step-up` — body `{ "password" }`; on success sets new access cookie with `stepUpAt` claim; returns `{ stepUpAt }` (epoch seconds). Rate limited per user.
- [ ] Protected high-risk routes return `403` with `STEP_UP_REQUIRED` and `details` map (`stepUpEndpoint`, `maxAgeSeconds`) when step-up claim missing or stale.

### Password reset (P2)

- [ ] `POST /auth/password-reset/request` — body `{ "email" }`; always `200` neutral message; rate limits per IP and per email.
- [ ] `POST /auth/password-reset/confirm` — body `{ "token", "newPassword" }`; on success bumps `token_version`, revokes refresh tokens, returns success message.

### P3 (operability)

- [ ] `auth.token-version.store=caffeine|redis` — Redis for multi-node token version cache.
- [ ] JWT signed with `kid` header; `jwt.keys[]` optional multi-key config; `jwt.allow-legacy-tokens-without-kid` for migration.
- [ ] Per-role access TTL overrides: `auth.session.policy.ADMIN.access-ttl-minutes`, `auth.session.policy.SYSTEM_ADMIN.access-ttl-minutes` (0 = use default JWT access TTL).

### Frontend contract (summary)

- [ ] Send `X-Device-Id` on login when stable device id exists.
- [ ] Call refresh only against `/api/v1/auth/refresh` (refresh cookie path scoped).
- [ ] Obtain WS ticket via `GET /api/v1/auth/ws-ticket`, pass as `X-Auth-Ticket` on STOMP connect.
- [ ] On `403 STEP_UP_REQUIRED`, call `POST /api/v1/auth/step-up` then retry the original mutation with fresh cookies.
