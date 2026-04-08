# TEST_SPEC_09_AdminStats.md

## Admin Statistics Module Test Specification

**Module**: Administrative Statistics  
**Controllers**: AdminStatsController  
**Endpoints**: 1  
**Last Updated**: Post-Architecture-Fix Phase 2  
**Test Coverage**: 20+ test cases

---

## 1. Endpoint Summary

| #   | Method | Endpoint              | Description                | Role                |
| --- | ------ | --------------------- | -------------------------- | ------------------- |
| 1   | GET    | `/api/v1/admin/stats` | Get system-wide statistics | ADMIN, SYSTEM_ADMIN |

---

## 2. Permission Matrix

**SYSTEM_ADMIN**: Full access (all stats)  
**ADMIN**: Full access (all stats)  
**INSTRUCTOR**: Denied (403 Forbidden)  
**STUDENT**: Denied (403 Forbidden)

---

## 3. Endpoint Test Cases

### 3.1 Get System Statistics

**Endpoint**: `GET /api/v1/admin/stats?period=30d`

**Response Example**:

```json
{
  "totalUsers": 1250,
  "totalCourses": 85,
  "totalAssignments": 450,
  "totalSubmissions": 3200,
  "averageGrade": 78.5,
  "submissionRate": 94.2,
  "systemUptime": "99.8%",
  "activeUsers": 340,
  "newUsers": 125,
  "period": "30d"
}
```

**Test Cases**:

1. ✅ Admin gets stats (200 OK)
2. ✅ Response includes all metric fields
3. ✅ Student denied (403 Forbidden)
4. ✅ Instructor denied (403 Forbidden)
5. ✅ Period filter: 7d, 30d, 90d, all (200 OK)
6. ✅ Invalid period (400 Bad Request)
7. ✅ Metrics are non-negative
8. ✅ Percentages in range 0-100
9. ✅ SYSTEM_ADMIN can access (200 OK)
10. ✅ Audit event: STATS_VIEWED -> admin_id, period
11. ✅ Response cached (same results for multiple calls)
12. ✅ Calculations accurate (spot-check math)
13. ✅ No personal data leakage
14. ✅ Large number formatting (thousands, millions)
15. ✅ Uptime calculation correct (100 - down_minutes / total_minutes \* 100)

---

## 4. Metric Definitions

| Metric           | Definition                     | Calculation                                                 |
| ---------------- | ------------------------------ | ----------------------------------------------------------- |
| totalUsers       | All registered users           | COUNT(users)                                                |
| totalCourses     | All courses created            | COUNT(courses)                                              |
| totalAssignments | All assignments                | COUNT(assignments)                                          |
| totalSubmissions | All submissions (all states)   | COUNT(submissions)                                          |
| averageGrade     | Mean of all final grades       | AVG(final_grade) where published=true                       |
| submissionRate   | % of students with submissions | (COUNT(students with submissions) / COUNT(students)) \* 100 |
| systemUptime     | % of time system available     | (total_time - downtime) / total_time \* 100                 |
| activeUsers      | Users logged in past 7 days    | COUNT(users with last_login >= now()-7d)                    |
| newUsers         | Users created in period        | COUNT(created_at >= period_start)                           |

---

## 5. Real Test Users

| User         | Email                        | Role         |
| ------------ | ---------------------------- | ------------ |
| Admin        | humberadmin@reviewflow.com   | ADMIN        |
| System Admin | main_sysadmin@reviewflow.com | SYSTEM_ADMIN |
| Instructor   | sarah.johnson@university.edu | INSTRUCTOR   |
| Student      | jane.smith@university.edu    | STUDENT      |

---

## 6. Postman Test

```json
{
  "name": "Get System Statistics",
  "request": {
    "method": "GET",
    "url": "{{base_url}}/api/v1/admin/stats?period=30d",
    "header": [{ "key": "Authorization", "value": "Bearer {{admin_token}}" }]
  },
  "tests": [
    "pm.response.code === 200",
    "pm.response.json().totalUsers >= 0",
    "pm.response.json().averageGrade >= 0 && pm.response.json().averageGrade <= 100"
  ]
}
```

---

## 7. Error Handling

| Scenario               | Status |
| ---------------------- | ------ |
| Not ADMIN/SYSTEM_ADMIN | 403    |
| Invalid period         | 400    |
| DB connection error    | 500    |

---

## 8. Performance & Caching

| Operation      | Cache TTL                     |
| -------------- | ----------------------------- |
| Stats endpoint | 5 minutes (heavy computation) |

---

## 9. Known Limitations

- Per-institution stats not supported (system-wide only)
- Historical trend data not available
- Real-time stats (returns last cached)
