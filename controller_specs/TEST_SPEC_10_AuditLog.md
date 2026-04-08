# TEST_SPEC_10_AuditLog.md

## Audit Log Module Test Specification

**Module**: Audit & Compliance  
**Controllers**: AdminAuditController  
**Endpoints**: 1  
**Last Updated**: Post-Architecture-Fix Phase 2  
**Test Coverage**: 25+ test cases

---

## 1. Endpoint Summary

| #   | Method | Endpoint                   | Description             | Role                |
| --- | ------ | -------------------------- | ----------------------- | ------------------- |
| 1   | GET    | `/api/v1/admin/audit-logs` | List audit trail events | ADMIN, SYSTEM_ADMIN |

---

## 2. Permission Matrix

**SYSTEM_ADMIN**: Access all audit logs  
**ADMIN**: Access all audit logs  
**INSTRUCTOR**: Denied (403 Forbidden)  
**STUDENT**: Denied (403 Forbidden)

---

## 3. Endpoint Test Cases

### 3.1 List Audit Logs

**Endpoint**: `GET /api/v1/admin/audit-logs?page=1&size=50&eventType=USER_CREATED&userId=123&startDate=2024-01-01`

**Response Example**:

```json
{
  "data": [
    {
      "id": "audit_001",
      "eventType": "USER_CREATED",
      "userId": 123,
      "userName": "john.doe@university.edu",
      "resourceType": "USER",
      "resourceId": 456,
      "action": "CREATE",
      "timestamp": "2024-01-15T10:30:00Z",
      "ipAddress": "192.168.1.100",
      "userAgent": "Mozilla/5.0...",
      "details": { "email": "jane.smith@university.edu", "role": "STUDENT" },
      "status": "SUCCESS"
    }
  ],
  "totalCount": 1250,
  "page": 1,
  "size": 50
}
```

**Test Cases**:

1. ✅ Admin lists audit logs (200 OK)
2. ✅ Pagination: page, size (50-500 items)
3. ✅ Filter by eventType (200 OK)
4. ✅ Filter by userId (200 OK)
5. ✅ Filter by resourceType (200 OK)
6. ✅ Filter by dateRange: startDate/endDate (200 OK)
7. ✅ Filter by status: SUCCESS, FAILED, BLOCKED (200 OK)
8. ✅ Sort by timestamp descending (newest first)
9. ✅ Empty result (200 OK, empty array)
10. ✅ Student cannot access (403 Forbidden)
11. ✅ Instructor cannot access (403 Forbidden)
12. ✅ SYSTEM_ADMIN can access (200 OK)
13. ✅ Response includes all event fields
14. ✅ Sensitive data masked (passwords, tokens redacted)
15. ✅ IP address included (for security analysis)
16. ✅ Time range query (first 3 months of year)
17. ✅ Multiple filters combined (eventType + userId + date range)
18. ✅ Search by resource (courseId, assignmentId)
19. ✅ Pagination edge cases (page=0, size=-1)
20. ✅ Large dataset (1000+ records, performance OK)

---

## 4. Audit Event Types

| Event Type           | Resource   | Typical Details                     | Severity |
| -------------------- | ---------- | ----------------------------------- | -------- |
| USER_CREATED         | USER       | email, role, institution            | INFO     |
| USER_UPDATED         | USER       | field_changed, old_value, new_value | INFO     |
| USER_DEACTIVATED     | USER       | reason                              | WARN     |
| USER_DELETED         | USER       | user_id                             | ERROR    |
| AUTH_LOGIN_SUCCESS   | AUTH       | ip_address, user_agent              | INFO     |
| AUTH_LOGIN_FAILED    | AUTH       | ip_address, reason                  | WARN     |
| COURSE_CREATED       | COURSE     | title, institution                  | INFO     |
| ASSIGNMENT_PUBLISHED | ASSIGNMENT | student_count                       | INFO     |
| ASSIGNMENT_DELETED   | ASSIGNMENT | submission_count                    | ERROR    |
| SUBMISSION_CREATED   | SUBMISSION | file_count, is_late                 | INFO     |
| EVALUATION_PUBLISHED | EVALUATION | grade, publisher_id                 | INFO     |
| TEAM_LOCKED          | TEAM       | member_count                        | INFO     |
| FILE_UPLOADED        | FILE       | file_size, virus_status             | INFO     |
| PERMISSION_DENIED    | SECURITY   | user_id, resource, reason           | WARN     |
| CONFIG_CHANGED       | SYSTEM     | setting, old_value, new_value       | ERROR    |
| DATA_EXPORT          | COMPLIANCE | export_type, record_count           | INFO     |

---

## 5. Real Test Users

| User         | Email                        | Role         |
| ------------ | ---------------------------- | ------------ |
| Admin        | humberadmin@reviewflow.com   | ADMIN        |
| System Admin | main_sysadmin@reviewflow.com | SYSTEM_ADMIN |
| Instructor   | sarah.johnson@university.edu | INSTRUCTOR   |
| Student      | jane.smith@university.edu    | STUDENT      |

---

## 6. Query Examples (Postman)

### Query 1: All Failed Logins Last 7 Days

```json
{
  "name": "Failed Login Attempts",
  "request": {
    "method": "GET",
    "url": "{{base_url}}/api/v1/admin/audit-logs?eventType=AUTH_LOGIN_FAILED&status=FAILED&days=7",
    "header": [{ "key": "Authorization", "value": "Bearer {{admin_token}}" }]
  }
}
```

### Query 2: User Activity for Specific ID

```json
{
  "name": "User Activity Trail",
  "request": {
    "method": "GET",
    "url": "{{base_url}}/api/v1/admin/audit-logs?userId=123&startDate=2024-01-01&endDate=2024-12-31"
  }
}
```

---

## 7. Compliance Features

1. **Immutable Logs**: Audit entries cannot be updated or deleted
2. **User Identification**: All actions linked to authenticated user
3. **Timestamp Accuracy**: Server time, not client time
4. **IP Tracking**: Network location for all actions
5. **Data Retention**: Logs retained for minimum 1 year
6. **Export Support**: CSV/JSON export for compliance reports
7. **Search Indexing**: Fast queries on large datasets

---

## 8. Error Handling

| Scenario         | Status | Resolution                  |
| ---------------- | ------ | --------------------------- |
| Not admin        | 403    | Login as ADMIN/SYSTEM_ADMIN |
| Invalid filter   | 400    | Use valid eventType values  |
| Date parse error | 400    | Use ISO 8601 format         |
| DB connection    | 500    | Retry or contact support    |

---

## 9. Performance & Caching

| Operation      | Cache TTL                        |
| -------------- | -------------------------------- |
| Audit log list | 1 minute (low-frequency changes) |

---

## 10. Security Considerations

1. **Access Control**: Only ADMIN/SYSTEM_ADMIN can view
2. **Data Masking**: Passwords, tokens removed from details
3. **Rate Limiting**: Heavy queries throttled
4. **Logging Logs**: Access to audits is itself audited
5. **Query Validation**: All inputs sanitized

---

## 11. Known Limitations

- Audit logs cannot be deleted (design feature)
- Historical log archival manual (to external storage)
- Real-time streaming not supported
- Bulk export limited to 10,000 records
