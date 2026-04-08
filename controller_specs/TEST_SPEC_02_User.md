# TEST_SPEC_02_User.md - User Admin Endpoints Test Specification

**Version:** 1.0  
**Last Updated:** April 7, 2026  
**Scope:** User CRUD, Role Management, Deactivation, Audit Logging  
**Module Reference:** [02_Module_Admin.md](02_Module_Admin.md)

---

## 1. Endpoints Inventory

| #   | Method | Endpoint                       | Description                | Auth Required          |
| --- | ------ | ------------------------------ | -------------------------- | ---------------------- |
| 1   | GET    | `/admin/users`                 | List all users (paginated) | ✅ ADMIN, SYSTEM_ADMIN |
| 2   | GET    | `/admin/users/{id}`            | Get user by ID             | ✅ ADMIN, SYSTEM_ADMIN |
| 3   | POST   | `/admin/users`                 | Create new user            | ✅ ADMIN, SYSTEM_ADMIN |
| 4   | PUT    | `/admin/users/{id}`            | Update user profile        | ✅ ADMIN, SYSTEM_ADMIN |
| 5   | PATCH  | `/admin/users/{id}/role`       | Change user role           | ✅ ADMIN, SYSTEM_ADMIN |
| 6   | PATCH  | `/admin/users/{id}/deactivate` | Deactivate user            | ✅ ADMIN, SYSTEM_ADMIN |

---

## 2. Role Permission Matrix

| Endpoint                           | STUDENT | INSTRUCTOR | ADMIN  | SYSTEM_ADMIN |
| ---------------------------------- | ------- | ---------- | ------ | ------------ |
| GET /admin/users                   | ❌ 403  | ❌ 403     | ✅ 200 | ✅ 200       |
| GET /admin/users/{id}              | ❌ 403  | ❌ 403     | ✅ 200 | ✅ 200       |
| POST /admin/users                  | ❌ 403  | ❌ 403     | ✅ 201 | ✅ 201       |
| PUT /admin/users/{id}              | ❌ 403  | ❌ 403     | ✅ 200 | ✅ 200       |
| PATCH /admin/users/{id}/role       | ❌ 403  | ❌ 403     | ✅ 200 | ✅ 200       |
| PATCH /admin/users/{id}/deactivate | ❌ 403  | ❌ 403     | ✅ 200 | ✅ 200       |

**Permission Rule:** Only ADMIN and SYSTEM_ADMIN roles can access `/admin/users` endpoints.

---

## 3. Real Test Users

```
ADMIN Role:
  - humberadmin@reviewflow.com
  - yorkadmin@reviewflow.com

SYSTEM_ADMIN Role:
  - main_sysadmin@reviewflow.com

INSTRUCTOR Role (Sample):
  - sarah.johnson@university.edu

STUDENT Role (Sample):
  - jane.smith@university.edu

Global Test Password: Test@1234
```

---

## 4. Success Paths

### 4.1 List Users (Paginated)

**Test Case:** `List_Users_DefaultPagination_ADMIN_200`

**Setup:**

- Authenticate as `humberadmin@reviewflow.com` (ADMIN role)
- Token obtained via `/auth/login` with password `Test@1234`

**Request:**

```http
GET /admin/users?page=0&size=20
Authorization: Bearer <admin_token>
```

**Expected Response:** `200 OK`

```json
{
  "content": [
    {
      "id": "uuid1",
      "email": "humberadmin@reviewflow.com",
      "firstName": "Humber",
      "lastName": "Admin",
      "role": "ADMIN",
      "is_active": true,
      "createdAt": "2026-01-01T00:00:00Z",
      "updatedAt": "2026-01-01T00:00:00Z"
    },
    {
      "id": "uuid2",
      "email": "sarah.johnson@university.edu",
      "firstName": "Sarah",
      "lastName": "Johnson",
      "role": "INSTRUCTOR",
      "is_active": true,
      "createdAt": "2026-01-02T00:00:00Z",
      "updatedAt": "2026-01-02T00:00:00Z"
    }
  ],
  "totalElements": 50,
  "totalPages": 3,
  "currentPage": 0,
  "pageSize": 20,
  "hasNext": true,
  "hasPrevious": false
}
```

**Validations:**

- ✅ Status code is 200
- ✅ Content array contains user objects
- ✅ Each user has `id`, `email`, `firstName`, `lastName`, `role`, `is_active`, `createdAt`, `updatedAt`
- ✅ Pagination metadata correct (totalPages = ceil(totalElements / pageSize))
- ✅ `is_active` is boolean (not string)
- ✅ No password fields returned

---

### 4.2 List Users (SYSTEM_ADMIN Visibility)

**Test Case:** `List_Users_SystemAdminSeeAll_SYSTEM_ADMIN_200`

**Setup:**

- Authenticate as `main_sysadmin@reviewflow.com` (SYSTEM_ADMIN role)

**Request:**

```http
GET /admin/users?page=0&size=20
Authorization: Bearer <system_admin_token>
```

**Expected Response:** `200 OK` with same structure as 4.1

**Validations:**

- ✅ SYSTEM_ADMIN can list all users
- ✅ Visibility matches ADMIN role

---

### 4.3 Get User by ID

**Test Case:** `Get_User_ByID_ValidUser_ADMIN_200`

**Setup:**

- Retrieve user ID from list (e.g., jane.smith@university.edu)
- Authenticate as `humberadmin@reviewflow.com` (ADMIN)

**Request:**

```http
GET /admin/users/{id}
Authorization: Bearer <admin_token>
```

**Expected Response:** `200 OK`

```json
{
  "id": "uuid_jane_smith",
  "email": "jane.smith@university.edu",
  "firstName": "Jane",
  "lastName": "Smith",
  "role": "STUDENT",
  "is_active": true,
  "createdAt": "2026-01-05T00:00:00Z",
  "updatedAt": "2026-01-05T00:00:00Z"
}
```

**Validations:**

- ✅ Status code 200
- ✅ User data matches request
- ✅ No sensitive fields returned

---

### 4.4 Create New User (Default Role STUDENT)

**Test Case:** `Create_User_DefaultRoleStudent_ADMIN_201`

**Setup:**

- Authenticate as `humberadmin@reviewflow.com` (ADMIN)
- Generate unique email (e.g., `test.user.001@university.edu`)

**Request:**

```http
POST /admin/users
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "email": "test.user.001@university.edu",
  "firstName": "Test",
  "lastName": "User",
  "password": "Test@1234"
}
```

**Expected Response:** `201 Created`

```json
{
  "id": "uuid_new_user",
  "email": "test.user.001@university.edu",
  "firstName": "Test",
  "lastName": "User",
  "role": "STUDENT",
  "is_active": true,
  "createdAt": "2026-04-07T10:00:00Z",
  "updatedAt": "2026-04-07T10:00:00Z"
}
```

**Validations:**

- ✅ Status 201
- ✅ User ID generated (UUID format)
- ✅ `role` defaults to STUDENT
- ✅ `is_active` defaults to true
- ✅ Created user can authenticate with provided password
- ✅ No password returned in response
- ✅ Timestamps auto-generated

---

### 4.5 Create User with Explicit Role

**Test Case:** `Create_User_ExplicitRoleInstructor_ADMIN_201`

**Setup:**

- Authenticate as `humberadmin@reviewflow.com`
- Generate unique email: `instructor.new.001@university.edu`

**Request:**

```http
POST /admin/users
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "email": "instructor.new.001@university.edu",
  "firstName": "New",
  "lastName": "Instructor",
  "password": "Test@1234",
  "role": "INSTRUCTOR"
}
```

**Expected Response:** `201 Created`

```json
{
  "id": "uuid_new_instructor",
  "email": "instructor.new.001@university.edu",
  "firstName": "New",
  "lastName": "Instructor",
  "role": "INSTRUCTOR",
  "is_active": true,
  "createdAt": "2026-04-07T10:05:00Z",
  "updatedAt": "2026-04-07T10:05:00Z"
}
```

**Validations:**

- ✅ Status 201
- ✅ Role set to INSTRUCTOR as requested
- ✅ User can login with provided password
- ✅ User can perform INSTRUCTOR-level operations

---

### 4.6 Update User Profile (Name & Email Preferences)

**Test Case:** `Update_User_ProfileInfo_ADMIN_200`

**Setup:**

- Authenticate as `humberadmin@reviewflow.com`
- Target user: `jane.smith@university.edu` (existing STUDENT)

**Request:**

```http
PUT /admin/users/{id}
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "firstName": "Jane",
  "lastName": "Smith-Updated",
  "emailPreferences": {
    "receiveNotifications": true,
    "receiveWeeklySummary": false
  }
}
```

**Expected Response:** `200 OK`

```json
{
  "id": "uuid_jane_smith",
  "email": "jane.smith@university.edu",
  "firstName": "Jane",
  "lastName": "Smith-Updated",
  "role": "STUDENT",
  "is_active": true,
  "emailPreferences": {
    "receiveNotifications": true,
    "receiveWeeklySummary": false
  },
  "createdAt": "2026-01-05T00:00:00Z",
  "updatedAt": "2026-04-07T10:10:00Z"
}
```

**Validations:**

- ✅ Status 200
- ✅ `lastName` field updated
- ✅ Email preferences stored
- ✅ `updatedAt` timestamp changed
- ✅ `createdAt` unchanged
- ✅ User can still login with original email

---

### 4.7 Change User Role (STUDENT → INSTRUCTOR)

**Test Case:** `Update_User_RoleChange_StudentToInstructor_ADMIN_200`

**Setup:**

- Authenticate as `humberadmin@reviewflow.com`
- Target: Recently created STUDENT user from test 4.4
- Original token still valid but reflects old role (STUDENT)

**Request:**

```http
PATCH /admin/users/{id}/role
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "role": "INSTRUCTOR"
}
```

**Expected Response:** `200 OK`

```json
{
  "id": "uuid_new_user",
  "email": "test.user.001@university.edu",
  "firstName": "Test",
  "lastName": "User",
  "role": "INSTRUCTOR",
  "is_active": true,
  "createdAt": "2026-04-07T10:00:00Z",
  "updatedAt": "2026-04-07T10:15:00Z"
}
```

**Validations:**

- ✅ Status 200
- ✅ Role changed to INSTRUCTOR
- ✅ `updatedAt` updated
- ✅ Other user properties unchanged
- ✅ User's old token still valid until expiry (will reflect old role)
- ✅ After token refresh, user has INSTRUCTOR permissions

---

### 4.8 Change User Role (INSTRUCTOR → ADMIN)

**Test Case:** `Update_User_RoleChange_InstructorToAdmin_SYSTEM_ADMIN_200`

**Setup:**

- Authenticate as `main_sysadmin@reviewflow.com` (SYSTEM_ADMIN)
- Target: `sarah.johnson@university.edu` (INSTRUCTOR)

**Request:**

```http
PATCH /admin/users/{id}/role
Authorization: Bearer <system_admin_token>
Content-Type: application/json

{
  "role": "ADMIN"
}
```

**Expected Response:** `200 OK` with role changed to ADMIN

**Validations:**

- ✅ SYSTEM_ADMIN can perform role changes
- ✅ Role change from INSTRUCTOR to ADMIN succeeds

---

### 4.9 Deactivate User

**Test Case:** `Deactivate_User_ActiveUser_ADMIN_200`

**Setup:**

- Authenticate as `humberadmin@reviewflow.com`
- Target: User with no active enrollments/assignments (e.g., newly created student)

**Request:**

```http
PATCH /admin/users/{id}/deactivate
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "is_active": false
}
```

**Expected Response:** `200 OK`

```json
{
  "id": "uuid_test_user",
  "email": "test.user.002@university.edu",
  "firstName": "Test",
  "lastName": "Deactivate",
  "role": "STUDENT",
  "is_active": false,
  "deactivatedAt": "2026-04-07T10:20:00Z",
  "updatedAt": "2026-04-07T10:20:00Z"
}
```

**Validations:**

- ✅ Status 200
- ✅ `is_active` set to false
- ✅ `deactivatedAt` timestamp recorded
- ✅ Deactivated user cannot login (next auth attempt returns 403 ACCOUNT_DEACTIVATED)
- ✅ Audit event USER_DEACTIVATED created

---

### 4.10 Reactivate Deactivated User

**Test Case:** `Reactivate_User_DeactivatedUser_ADMIN_200`

**Setup:**

- Deactivated user from test 4.9
- Authenticate as `yorkadmin@reviewflow.com` (different ADMIN)

**Request:**

```http
PATCH /admin/users/{id}/deactivate
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "is_active": true
}
```

**Expected Response:** `200 OK`

```json
{
  "id": "uuid_test_user",
  "email": "test.user.002@university.edu",
  "firstName": "Test",
  "lastName": "Deactivate",
  "role": "STUDENT",
  "is_active": true,
  "reactivatedAt": "2026-04-07T10:25:00Z",
  "updatedAt": "2026-04-07T10:25:00Z"
}
```

**Validations:**

- ✅ Status 200
- ✅ `is_active` set to true
- ✅ `reactivatedAt` timestamp recorded
- ✅ User can login again with original password
- ✅ API calls work normally after login
- ✅ Audit event USER_REACTIVATED created

---

### 4.11 Verify Deactivated User Cannot Access API

**Test Case:** `Deactivated_User_API_Call_403`

**Setup:**

1. Deactivate user from test 4.9
2. User attempts to make any API call (e.g., GET /courses)

**Request:**

```http
GET /courses
Authorization: Bearer <deactivated_user_token>
```

**Expected Response:** `403 Forbidden`

```json
{
  "error": "ACCOUNT_DEACTIVATED",
  "message": "Your account has been deactivated. Contact administrator.",
  "timestamp": "2026-04-07T10:20:30Z"
}
```

**Validations:**

- ✅ Status 403
- ✅ Error code is ACCOUNT_DEACTIVATED
- ✅ Token is still technically valid but rejected at application level
- ✅ User cannot perform any operations until reactivated

---

## 5. Error Cases

### 5.1 Duplicate Email on Create

**Test Case:** `Create_User_DuplicateEmail_ADMIN_409`

**Setup:**

- Authenticate as `humberadmin@reviewflow.com`
- Email already exists: `sarah.johnson@university.edu`

**Request:**

```http
POST /admin/users
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "email": "sarah.johnson@university.edu",
  "firstName": "Duplicate",
  "lastName": "Test",
  "password": "Test@1234"
}
```

**Expected Response:** `409 Conflict`

```json
{
  "error": "DUPLICATE_EMAIL",
  "message": "Email already exists: sarah.johnson@university.edu",
  "timestamp": "2026-04-07T10:30:00Z"
}
```

**Validations:**

- ✅ Status 409
- ✅ Error code is DUPLICATE_EMAIL
- ✅ User not created
- ✅ Original user unchanged

---

### 5.2 Invalid Email Format

**Test Case:** `Create_User_InvalidEmailFormat_ADMIN_400`

**Setup:**

- Authenticate as `humberadmin@reviewflow.com`

**Request:**

```http
POST /admin/users
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "email": "invalid-email-format",
  "firstName": "Test",
  "lastName": "User",
  "password": "Test@1234"
}
```

**Expected Response:** `400 Bad Request`

```json
{
  "error": "INVALID_EMAIL",
  "message": "Email must be in valid format (e.g., user@domain.com)",
  "field": "email",
  "timestamp": "2026-04-07T10:35:00Z"
}
```

**Validations:**

- ✅ Status 400
- ✅ Error code is INVALID_EMAIL
- ✅ Field name specified

---

### 5.3 Invalid Role String

**Test Case:** `Create_User_InvalidRole_ADMIN_400`

**Setup:**

- Authenticate as `humberadmin@reviewflow.com`

**Request:**

```http
POST /admin/users
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "email": "test.user.003@university.edu",
  "firstName": "Test",
  "lastName": "User",
  "password": "Test@1234",
  "role": "SUPERUSER"
}
```

**Expected Response:** `400 Bad Request`

```json
{
  "error": "INVALID_ROLE",
  "message": "Role must be one of: STUDENT, INSTRUCTOR, ADMIN, SYSTEM_ADMIN",
  "field": "role",
  "validRoles": ["STUDENT", "INSTRUCTOR", "ADMIN", "SYSTEM_ADMIN"],
  "timestamp": "2026-04-07T10:40:00Z"
}
```

**Validations:**

- ✅ Status 400
- ✅ Error code is INVALID_ROLE
- ✅ Valid roles listed
- ✅ User not created

---

### 5.4 Missing Authorization Token

**Test Case:** `List_Users_NoToken_401`

**Setup:**

- No authentication

**Request:**

```http
GET /admin/users
```

**Expected Response:** `401 Unauthorized`

```json
{
  "error": "MISSING_TOKEN",
  "message": "Authorization header is missing or invalid",
  "timestamp": "2026-04-07T10:45:00Z"
}
```

**Validations:**

- ✅ Status 401
- ✅ Error code is MISSING_TOKEN
- ✅ Clear message

---

### 5.5 Invalid Token

**Test Case:** `List_Users_InvalidToken_401`

**Setup:**

- Provide malformed or expired token

**Request:**

```http
GET /admin/users
Authorization: Bearer malformed_token_xyz
```

**Expected Response:** `401 Unauthorized`

```json
{
  "error": "INVALID_TOKEN",
  "message": "Token is invalid or expired",
  "timestamp": "2026-04-07T10:50:00Z"
}
```

**Validations:**

- ✅ Status 401
- ✅ Error code is INVALID_TOKEN

---

### 5.6 INSTRUCTOR Attempts Create User

**Test Case:** `Create_User_InstructorRole_403`

**Setup:**

- Authenticate as `sarah.johnson@university.edu` (INSTRUCTOR)

**Request:**

```http
POST /admin/users
Authorization: Bearer <instructor_token>
Content-Type: application/json

{
  "email": "new.user@university.edu",
  "firstName": "New",
  "lastName": "User",
  "password": "Test@1234"
}
```

**Expected Response:** `403 Forbidden`

```json
{
  "error": "INSUFFICIENT_PERMISSIONS",
  "message": "User role INSTRUCTOR does not have permission to create users",
  "requiredRole": ["ADMIN", "SYSTEM_ADMIN"],
  "timestamp": "2026-04-07T10:55:00Z"
}
```

**Validations:**

- ✅ Status 403
- ✅ Error code is INSUFFICIENT_PERMISSIONS
- ✅ Required roles listed
- ✅ User not created

---

### 5.7 STUDENT Attempts List Users

**Test Case:** `List_Users_StudentRole_403`

**Setup:**

- Authenticate as `jane.smith@university.edu` (STUDENT)

**Request:**

```http
GET /admin/users
Authorization: Bearer <student_token>
```

**Expected Response:** `403 Forbidden`

```json
{
  "error": "INSUFFICIENT_PERMISSIONS",
  "message": "User role STUDENT does not have permission to access admin endpoints",
  "requiredRole": ["ADMIN", "SYSTEM_ADMIN"],
  "timestamp": "2026-04-07T11:00:00Z"
}
```

**Validations:**

- ✅ Status 403
- ✅ Error code is INSUFFICIENT_PERMISSIONS
- ✅ Clear message

---

### 5.8 User ID Not Found

**Test Case:** `Get_User_InvalidID_404`

**Setup:**

- Authenticate as `humberadmin@reviewflow.com`
- Use non-existent UUID: `00000000-0000-0000-0000-000000000000`

**Request:**

```http
GET /admin/users/00000000-0000-0000-0000-000000000000
Authorization: Bearer <admin_token>
```

**Expected Response:** `404 Not Found`

```json
{
  "error": "USER_NOT_FOUND",
  "message": "User with ID 00000000-0000-0000-0000-000000000000 not found",
  "timestamp": "2026-04-07T11:05:00Z"
}
```

**Validations:**

- ✅ Status 404
- ✅ Error code is USER_NOT_FOUND
- ✅ ID shown in message

---

### 5.9 Deactivate User with Active Enrollments

**Test Case:** `Deactivate_User_ActiveEnrollments_409`

**Setup:**

- Authenticate as `humberadmin@reviewflow.com`
- Target: User enrolled in active course (e.g., `jane.smith@university.edu`)

**Request:**

```http
PATCH /admin/users/{id}/deactivate
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "is_active": false
}
```

**Expected Response:** `409 Conflict`

```json
{
  "error": "USER_HAS_ACTIVE_ENROLLMENTS",
  "message": "Cannot deactivate user with active course enrollments or pending assignments",
  "details": {
    "activeEnrollments": 2,
    "pendingAssignments": 1,
    "conflictingCourses": ["CS101", "MATH201"]
  },
  "timestamp": "2026-04-07T11:10:00Z"
}
```

**Validations:**

- ✅ Status 409
- ✅ Error code is USER_HAS_ACTIVE_ENROLLMENTS
- ✅ Detail about enrollments and assignments provided
- ✅ User remains active
- ✅ Admin must manually unenroll or have alternative process

---

### 5.10 Invalid Pagination Parameters

**Test Case:** `List_Users_InvalidPageSize_400`

**Setup:**

- Authenticate as `humberadmin@reviewflow.com`
- Request page size of 1000 (exceeds max 500)

**Request:**

```http
GET /admin/users?page=0&size=1000
Authorization: Bearer <admin_token>
```

**Expected Response:** `400 Bad Request`

```json
{
  "error": "INVALID_PAGINATION",
  "message": "Page size must be between 1 and 500",
  "field": "size",
  "provided": 1000,
  "maxAllowed": 500,
  "timestamp": "2026-04-07T11:15:00Z"
}
```

**Validations:**

- ✅ Status 400
- ✅ Error code is INVALID_PAGINATION
- ✅ Max size enforced (500)
- ✅ Page defaults to 0 if missing
- ✅ Size defaults to 20 if missing

---

### 5.11 Missing Required Fields on Create

**Test Case:** `Create_User_MissingEmail_400`

**Setup:**

- Authenticate as `humberadmin@reviewflow.com`

**Request:**

```http
POST /admin/users
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "firstName": "Test",
  "lastName": "User",
  "password": "Test@1234"
}
```

**Expected Response:** `400 Bad Request`

```json
{
  "error": "MISSING_REQUIRED_FIELD",
  "message": "The following required fields are missing: email",
  "missingFields": ["email"],
  "timestamp": "2026-04-07T11:20:00Z"
}
```

**Validations:**

- ✅ Status 400
- ✅ Error lists all missing required fields
- ✅ User not created

---

## 6. Edge Cases

### 6.1 Deactivate User Mid-Session (Active Token)

**Test Case:** `Deactivate_User_MidSession_UserGets403OnNextCall`

**Setup:**

1. User logged in as `jane.smith@university.edu` (STUDENT)
2. User has valid token (not expired)
3. Admin deactivates user in another session

**Step 1 - User Makes Call Before Deactivation:**

```http
GET /courses
Authorization: Bearer <student_token>
```

**Response:** `200 OK` (returns courses list)

**Step 2 - Admin Deactivates User:**

```http
PATCH /admin/users/{jane_id}/deactivate
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "is_active": false
}
```

**Response:** `200 OK` (deactivation succeeds)

**Step 3 - User Makes Next API Call (Token Still Valid):**

```http
GET /courses
Authorization: Bearer <student_token>
```

**Expected Response:** `403 Forbidden`

```json
{
  "error": "ACCOUNT_DEACTIVATED",
  "message": "Your account has been deactivated. Contact administrator.",
  "timestamp": "2026-04-07T11:25:00Z"
}
```

**Validations:**

- ✅ Deactivation effective immediately
- ✅ All subsequent API calls fail with 403 ACCOUNT_DEACTIVATED
- ✅ Message is user-friendly
- ✅ Token verification happens before business logic

---

### 6.2 Role Change Mid-Session (Old Token Reflects Old Role)

**Test Case:** `Change_Role_MidSession_OldTokenStillHasOldRole`

**Setup:**

1. User logged in as `test.user.001@university.edu` (STUDENT)
2. User has valid token with role STUDENT
3. Admin changes role to INSTRUCTOR

**Step 1 - User Makes Call as STUDENT:**

```http
GET /assignments?courseId=MATH201
Authorization: Bearer <student_token_with_student_role>
```

**Response:** `200 OK` (student endpoint, access granted)

**Step 2 - Admin Changes User Role:**

```http
PATCH /admin/users/{id}/role
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "role": "INSTRUCTOR"
}
```

**Response:** `200 OK`

**Step 3 - User Tries INSTRUCTOR-Only Endpoint with Old Token:**

```http
POST /admin/courses
Content-Type: application/json
Authorization: Bearer <student_token_with_student_role>

{
  "name": "New Course"
}
```

**Expected Response:** `403 Forbidden`

```json
{
  "error": "INSUFFICIENT_PERMISSIONS",
  "message": "User role STUDENT does not have permission to create courses",
  "timestamp": "2026-04-07T11:30:00Z"
}
```

**Validations:**

- ✅ Token still valid but reflects old role until expiry
- ✅ Role-based access control enforced per request
- ✅ User must refresh token to get new role
- ✅ After token refresh, user has INSTRUCTOR permissions

**Step 4 - User Refreshes Token:**

```http
POST /auth/refresh
Authorization: Bearer <student_token_with_student_role>
```

**Response:** `200 OK` with new token containing INSTRUCTOR role

**Step 5 - Retry INSTRUCTOR Endpoint with New Token:**

```http
POST /admin/courses
Authorization: Bearer <instructor_token_with_instructor_role>
```

**Expected Response:** `201 Created` (course created)

---

### 6.3 Deactivate → Reactivate → Login Works

**Test Case:** `Deactivate_Reactivate_LoginWorks_ADMIN_200`

**Setup:**

1. Create test user: `test.user.004@university.edu`
2. User logs in successfully
3. Deactivate user
4. Verify login fails
5. Reactivate user
6. Verify login works again

**Step 1 - Create User:**

```http
POST /admin/users
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "email": "test.user.004@university.edu",
  "firstName": "Test",
  "lastName": "DeactivateTest",
  "password": "Test@1234",
  "role": "STUDENT"
}
```

**Response:** `201 Created`

**Step 2 - User Logs In (Succeeds):**

```http
POST /auth/login
Content-Type: application/json

{
  "email": "test.user.004@university.edu",
  "password": "Test@1234"
}
```

**Response:** `200 OK`

```json
{
  "token": "eyJhbGc...",
  "role": "STUDENT",
  "expiresIn": 3600
}
```

**Step 3 - Admin Deactivates User:**

```http
PATCH /admin/users/{id}/deactivate
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "is_active": false
}
```

**Response:** `200 OK`

**Step 4 - User Attempts Login (Fails):**

```http
POST /auth/login
Content-Type: application/json

{
  "email": "test.user.004@university.edu",
  "password": "Test@1234"
}
```

**Expected Response:** `403 Forbidden`

```json
{
  "error": "ACCOUNT_DEACTIVATED",
  "message": "Account is deactivated. Contact administrator.",
  "timestamp": "2026-04-07T11:35:00Z"
}
```

**Step 5 - Admin Reactivates User:**

```http
PATCH /admin/users/{id}/deactivate
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "is_active": true
}
```

**Response:** `200 OK`

**Step 6 - User Logs In (Succeeds Again):**

```http
POST /auth/login
Content-Type: application/json

{
  "email": "test.user.004@university.edu",
  "password": "Test@1234"
}
```

**Expected Response:** `200 OK`

```json
{
  "token": "eyJhbGc...",
  "role": "STUDENT",
  "expiresIn": 3600
}
```

**Validations:**

- ✅ Deactivation prevents login
- ✅ Reactivation restores login capability
- ✅ Password remains correct
- ✅ Role unchanged after deactivate/reactive cycle

---

### 6.4 Bulk User Creation (Pagination Edge Case)

**Test Case:** `Bulk_Create_Users_100Users_VerifyPagination`

**Setup:**

- Authenticate as `main_sysadmin@reviewflow.com`
- Create 100 users in sequence
- Then test pagination with different page sizes

**Step 1 - Create 100 Users:**

```bash
for i in {1..100}:
  POST /admin/users
  Authorization: Bearer <system_admin_token>
  {
    "email": "bulk.user.$i@university.edu",
    "firstName": "Bulk",
    "lastName": "User$i",
    "password": "Test@1234"
  }
```

**Step 2 - Verify Pagination Page 0, Size 20:**

```http
GET /admin/users?page=0&size=20
Authorization: Bearer <system_admin_token>
```

**Response:** `200 OK`

```json
{
  "content": [
    /* 20 users */
  ],
  "totalElements": 150,
  "totalPages": 8,
  "currentPage": 0,
  "pageSize": 20,
  "hasNext": true,
  "hasPrevious": false
}
```

**Step 3 - Request Out-of-Bounds Page:**

```http
GET /admin/users?page=999&size=20
Authorization: Bearer <system_admin_token>
```

**Expected Response:** `200 OK` with empty content

```json
{
  "content": [],
  "totalElements": 150,
  "totalPages": 8,
  "currentPage": 999,
  "pageSize": 20,
  "hasNext": false,
  "hasPrevious": true
}
```

**Step 4 - Max Page Size (500):**

```http
GET /admin/users?page=0&size=500
Authorization: Bearer <system_admin_token>
```

**Response:** `200 OK`

```json
{
  "content": [
    /* 150 users (all) */
  ],
  "totalElements": 150,
  "totalPages": 1,
  "currentPage": 0,
  "pageSize": 500,
  "hasNext": false,
  "hasPrevious": false
}
```

**Validations:**

- ✅ Pagination math correct (totalPages = ceil(totalElements / pageSize))
- ✅ Out-of-bounds pages return empty content, not error
- ✅ `hasNext` and `hasPrevious` flags accurate
- ✅ Max size limit (500) enforced
- ✅ Default size (20) applied when missing
- ✅ All 100 created users appear in results

---

### 6.5 Email Uniqueness Enforced (Across Updates)

**Test Case:** `Update_User_DuplicateEmail_409`

**Setup:**

- User A: `user.a@university.edu`
- User B: `user.b@university.edu`
- Authenticate as `humberadmin@reviewflow.com`
- Attempt to update User B's email to User A's email

**Request:**

```http
PUT /admin/users/{user_b_id}
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "firstName": "User",
  "lastName": "Changed",
  "email": "user.a@university.edu"
}
```

**Expected Response:** `409 Conflict`

```json
{
  "error": "DUPLICATE_EMAIL",
  "message": "Email already exists: user.a@university.edu",
  "timestamp": "2026-04-07T11:40:00Z"
}
```

**Validations:**

- ✅ Status 409
- ✅ Update rejected
- ✅ User B's email unchanged
- ✅ User A's email still belongs to User A

---

### 6.6 Password Never Returned in API Response

**Test Case:** `Verify_No_Password_InResponse`

**Setup:**

- Create, retrieve, and update multiple users
- Inspect all responses

**Endpoints to Test:**

1. POST /admin/users (create)
2. GET /admin/users (list)
3. GET /admin/users/{id} (retrieve)
4. PUT /admin/users/{id} (update)
5. PATCH /admin/users/{id}/role (role change)

**Expected for All:**

- ✅ Response JSON never contains `password` field
- ✅ Response JSON never contains `passwordHash` field
- ✅ Response JSON never contains `passwordSalt` field
- ✅ No sensitive hashing info leaked
- ✅ Password validation can be done via login endpoint only

---

### 6.7 Same Admin Cannot Be Deactivated by Self

**Test Case:** `Cannot_Deactivate_Self_403` (Optional - Depends on Business Rule)

**Setup:**

- Authenticate as `humberadmin@reviewflow.com` (ADMIN)
- Attempt to deactivate same user

**Request:**

```http
PATCH /admin/users/{humberadmin_id}/deactivate
Authorization: Bearer <humberadmin_token>
Content-Type: application/json

{
  "is_active": false
}
```

**Expected Response:** `403 Forbidden` OR `409 Conflict`

```json
{
  "error": "CANNOT_DEACTIVATE_SELF",
  "message": "Administrators cannot deactivate their own account",
  "timestamp": "2026-04-07T11:45:00Z"
}
```

**Validations:**

- ✅ User remains active
- ✅ Clear error message

---

## 7. Audit Logging

### 7.1 Events to Log

All User management actions must generate audit events with the following structure:

```json
{
  "event_type": "string",
  "timestamp": "ISO8601",
  "admin_id": "uuid",
  "admin_email": "string",
  "target_user_id": "uuid",
  "target_user_email": "string",
  "action": "string",
  "details": "object",
  "status": "SUCCESS|FAILURE",
  "ipAddress": "string"
}
```

### 7.2 Required Audit Events

| Event Type        | Trigger                                                       | Details                                             |
| ----------------- | ------------------------------------------------------------- | --------------------------------------------------- |
| USER_CREATED      | POST /admin/users succeeds                                    | email, role, created_by                             |
| USER_UPDATED      | PUT /admin/users/{id} succeeds                                | changed_fields (array), previous_values, new_values |
| USER_ROLE_CHANGED | PATCH /admin/users/{id}/role succeeds                         | old_role, new_role, changed_by                      |
| USER_DEACTIVATED  | PATCH /admin/users/{id}/deactivate (is_active=false) succeeds | deactivated_by, reason (optional)                   |
| USER_REACTIVATED  | PATCH /admin/users/{id}/deactivate (is_active=true) succeeds  | reactivated_by, reason (optional)                   |

### 7.3 Audit Event Examples

**Example 1 - USER_CREATED:**

```json
{
  "event_type": "USER_CREATED",
  "timestamp": "2026-04-07T10:00:15Z",
  "admin_id": "uuid_humber_admin",
  "admin_email": "humberadmin@reviewflow.com",
  "target_user_id": "uuid_new_user",
  "target_user_email": "test.user.001@university.edu",
  "action": "CREATE",
  "details": {
    "email": "test.user.001@university.edu",
    "firstName": "Test",
    "lastName": "User",
    "role": "STUDENT",
    "department": "Engineering"
  },
  "status": "SUCCESS",
  "ipAddress": "192.168.1.100"
}
```

**Example 2 - USER_ROLE_CHANGED:**

```json
{
  "event_type": "USER_ROLE_CHANGED",
  "timestamp": "2026-04-07T10:15:30Z",
  "admin_id": "uuid_humber_admin",
  "admin_email": "humberadmin@reviewflow.com",
  "target_user_id": "uuid_new_user",
  "target_user_email": "test.user.001@university.edu",
  "action": "ROLE_CHANGE",
  "details": {
    "old_role": "STUDENT",
    "new_role": "INSTRUCTOR",
    "effective_immediately": true
  },
  "status": "SUCCESS",
  "ipAddress": "192.168.1.100"
}
```

**Example 3 - USER_DEACTIVATED:**

```json
{
  "event_type": "USER_DEACTIVATED",
  "timestamp": "2026-04-07T10:20:45Z",
  "admin_id": "uuid_york_admin",
  "admin_email": "yorkadmin@reviewflow.com",
  "target_user_id": "uuid_test_user",
  "target_user_email": "test.user.002@university.edu",
  "action": "DEACTIVATE",
  "details": {
    "reason": "End of semester",
    "active_sessions_terminated": 1
  },
  "status": "SUCCESS",
  "ipAddress": "192.168.1.101"
}
```

### 7.4 Audit Event Retrieval

Admins should be able to query audit logs:

```http
GET /admin/audit-logs?event_type=USER_ROLE_CHANGED&startDate=2026-04-01T00:00:00Z
Authorization: Bearer <admin_token>
```

Expected: List of audit events matching criteria

---

## 8. Postman Test Collection Templates

### 8.1 Test: Create Student → List Users → Change Role → Verify in List

**Test Name:** `User_Admin_Complete_Workflow`

**Setup:**

```javascript
// Postman Pre-request Script
// Set admin token from environment or login
if (!pm.environment.get("admin_token")) {
  pm.sendRequest(
    {
      url: pm.environment.get("base_url") + "/auth/login",
      method: "POST",
      header: { "Content-Type": "application/json" },
      body: {
        mode: "raw",
        raw: JSON.stringify({
          email: "humberadmin@reviewflow.com",
          password: "Test@1234",
        }),
      },
    },
    function (err, response) {
      if (!err) {
        pm.environment.set("admin_token", response.json().token);
      }
    },
  );
}
```

**Step 1 - Create Student User:**

```javascript
// Request
POST {{base_url}}/admin/users
Authorization: Bearer {{admin_token}}
Content-Type: application/json

{
  "email": "postman.test.student.{{$timestamp}}@university.edu",
  "firstName": "Postman",
  "lastName": "Student",
  "password": "Test@1234",
  "role": "STUDENT"
}

// Tests
pm.test("Status code is 201", function() {
  pm.expect(pm.response.code).to.equal(201);
});

pm.test("Response has user ID", function() {
  var responseJson = pm.response.json();
  pm.expect(responseJson.id).to.exist;
  pm.environment.set("created_user_id", responseJson.id);
});

pm.test("Role is STUDENT", function() {
  var responseJson = pm.response.json();
  pm.expect(responseJson.role).to.equal("STUDENT");
});

pm.test("No password in response", function() {
  var responseJson = pm.response.json();
  pm.expect(responseJson.password).to.not.exist;
});
```

**Step 2 - List Users:**

```javascript
// Request
GET {{base_url}}/admin/users?page=0&size=20
Authorization: Bearer {{admin_token}}

// Tests
pm.test("Status code is 200", function() {
  pm.expect(pm.response.code).to.equal(200);
});

pm.test("Response is paginated", function() {
  var responseJson = pm.response.json();
  pm.expect(responseJson).to.have.property("content");
  pm.expect(responseJson).to.have.property("totalElements");
  pm.expect(responseJson).to.have.property("totalPages");
});

pm.test("Created student in list", function() {
  var responseJson = pm.response.json();
  var created_user_id = pm.environment.get("created_user_id");
  var found = responseJson.content.some(u => u.id === created_user_id);
  pm.expect(found).to.be.true;
});
```

**Step 3 - Change Role to INSTRUCTOR:**

```javascript
// Request
PATCH {{base_url}}/admin/users/{{created_user_id}}/role
Authorization: Bearer {{admin_token}}
Content-Type: application/json

{
  "role": "INSTRUCTOR"
}

// Tests
pm.test("Status code is 200", function() {
  pm.expect(pm.response.code).to.equal(200);
});

pm.test("Role changed to INSTRUCTOR", function() {
  var responseJson = pm.response.json();
  pm.expect(responseJson.role).to.equal("INSTRUCTOR");
});
```

**Step 4 - Retrieve Updated User and Verify:**

```javascript
// Request
GET {{base_url}}/admin/users/{{created_user_id}}
Authorization: Bearer {{admin_token}}

// Tests
pm.test("Status code is 200", function() {
  pm.expect(pm.response.code).to.equal(200);
});

pm.test("User role persisted as INSTRUCTOR", function() {
  var responseJson = pm.response.json();
  pm.expect(responseJson.role).to.equal("INSTRUCTOR");
});
```

---

### 8.2 Test: Deactivate User → Verify 403 on API Call

**Test Name:** `User_Deactivation_Blocks_Access`

**Step 1 - Create Test User:**

```javascript
POST {{base_url}}/admin/users
Authorization: Bearer {{admin_token}}
Content-Type: application/json

{
  "email": "postman.test.deactivate.{{$timestamp}}@university.edu",
  "firstName": "Postman",
  "lastName": "DeactivateTest",
  "password": "Test@1234"
}

// Tests
pm.test("User created", function() {
  pm.expect(pm.response.code).to.equal(201);
  var userId = pm.response.json().id;
  pm.environment.set("deactivate_user_id", userId);
});
```

**Step 2 - User Logs In (Succeeds):**

```javascript
POST {{base_url}}/auth/login
Content-Type: application/json

{
  "email": "postman.test.deactivate.{{$timestamp}}@university.edu",
  "password": "Test@1234"
}

// Tests
pm.test("Login successful", function() {
  pm.expect(pm.response.code).to.equal(200);
  var userToken = pm.response.json().token;
  pm.environment.set("test_user_token", userToken);
});
```

**Step 3 - User Makes API Call Before Deactivation:**

```javascript
GET {{base_url}}/courses
Authorization: Bearer {{test_user_token}}

// Tests
pm.test("User can access API before deactivation", function() {
  pm.expect(pm.response.code).to.equal(200);
});
```

**Step 4 - Admin Deactivates User:**

```javascript
PATCH {{base_url}}/admin/users/{{deactivate_user_id}}/deactivate
Authorization: Bearer {{admin_token}}
Content-Type: application/json

{
  "is_active": false
}

// Tests
pm.test("User deactivated", function() {
  pm.expect(pm.response.code).to.equal(200);
  pm.expect(pm.response.json().is_active).to.equal(false);
});
```

**Step 5 - User Attempts API Call After Deactivation:**

```javascript
GET {{base_url}}/courses
Authorization: Bearer {{test_user_token}}

// Tests
pm.test("Deactivated user gets 403", function() {
  pm.expect(pm.response.code).to.equal(403);
});

pm.test("Error is ACCOUNT_DEACTIVATED", function() {
  var responseJson = pm.response.json();
  pm.expect(responseJson.error).to.equal("ACCOUNT_DEACTIVATED");
});
```

**Step 6 - Admin Reactivates User:**

```javascript
PATCH {{base_url}}/admin/users/{{deactivate_user_id}}/deactivate
Authorization: Bearer {{admin_token}}
Content-Type: application/json

{
  "is_active": true
}

// Tests
pm.test("User reactivated", function() {
  pm.expect(pm.response.code).to.equal(200);
  pm.expect(pm.response.json().is_active).to.equal(true);
});
```

**Step 7 - User Logs In Again:**

```javascript
POST {{base_url}}/auth/login
Content-Type: application/json

{
  "email": "postman.test.deactivate.{{$timestamp}}@university.edu",
  "password": "Test@1234"
}

// Tests
pm.test("Reactivated user can login", function() {
  pm.expect(pm.response.code).to.equal(200);
});
```

---

### 8.3 Test: Permission Denied Cases

**Test Name:** `User_Admin_Permission_Denied`

**Test 1 - STUDENT Cannot List Users:**

```javascript
// Setup: Get STUDENT token
GET {{base_url}}/admin/users
Authorization: Bearer {{student_token}}

// Tests
pm.test("STUDENT gets 403 on list users", function() {
  pm.expect(pm.response.code).to.equal(403);
});

pm.test("Error is INSUFFICIENT_PERMISSIONS", function() {
  pm.expect(pm.response.json().error).to.equal("INSUFFICIENT_PERMISSIONS");
});
```

**Test 2 - INSTRUCTOR Cannot Create User:**

```javascript
POST {{base_url}}/admin/users
Authorization: Bearer {{instructor_token}}
Content-Type: application/json

{
  "email": "new.user@university.edu",
  "firstName": "New",
  "lastName": "User",
  "password": "Test@1234"
}

// Tests
pm.test("INSTRUCTOR gets 403 on create user", function() {
  pm.expect(pm.response.code).to.equal(403);
});
```

---

## 9. Summary: Test Coverage Matrix

| Category             | Tests       | Status      |
| -------------------- | ----------- | ----------- |
| **Happy Path**       | 11          | ✅ Defined  |
| **Error Cases**      | 11          | ✅ Defined  |
| **Edge Cases**       | 7           | ✅ Defined  |
| **Audit Logging**    | 5 events    | ✅ Defined  |
| **Postman Tests**    | 3 workflows | ✅ Defined  |
| **Total Test Cases** | 37+         | ✅ Complete |

---

## 10. Execution Checklist

- [ ] Database seeded with test users (see Section 3)
- [ ] Admin accounts created and verified
- [ ] SYSTEM_ADMIN account active
- [ ] Test environment configured (base_url, credentials)
- [ ] Postman collection imported
- [ ] Happy path tests executed
- [ ] Error case tests executed
- [ ] Edge case tests executed
- [ ] Audit logs verified
- [ ] All 37+ test cases passed
- [ ] No password fields in responses
- [ ] Role-based access control enforced
- [ ] Pagination tested with edge cases
- [ ] Email uniqueness validated
- [ ] Deactivation/reactivation flow verified

---

## 11. Notes & Known Issues

### 11.1 Password Hashing

- Passwords are never stored in plain text
- Password verification happens in `/auth/login` endpoint
- Admin cannot reset user passwords via `/admin/users` endpoints
- Consider adding `/admin/users/{id}/reset-password` endpoint if needed

### 11.2 Session Handling

- Deactivated users are blocked at authentication middleware level
- Role changes take effect after token refresh
- Active tokens for deactivated users are revoked immediately
- Consider implementing token blacklist for deactivated users

### 11.3 Concurrency Considerations

- Email uniqueness check must happen within same transaction as insert
- Pagination must handle concurrent user creation
- Audit logs should be idempotent (same action logged once)

### 11.4 Future Enhancements

- Bulk user import (CSV)
- User search by name, email, role, department
- Suspend vs. Deactivate distinction
- Password reset via email link
- Admin change audit trail (who changed what, when)
- Rate limiting on admin endpoints

---

**Document Version:** 1.0  
**Last Reviewed:** April 7, 2026  
**Next Review:** After first test execution
