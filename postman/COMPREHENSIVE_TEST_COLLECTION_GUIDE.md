# ReviewFlow Comprehensive Test Collection v2 - Complete Guide

## Overview

**ReviewFlow_Comprehensive_v2.json** is a 524+ comprehensive test collection covering all 93 REST API endpoints across 15 ReviewFlow backend modules.

### Quick Stats

- **Total Tests**: 524 (excluding setup)
- **Setup Requests**: 4 (role-based logins)
- **Execution Model**: Sequential DAG (15 items, strict dependency order)
- **Test Coverage**: Happy Path + Error Cases + Edge Cases + Security
- **Data Scale**: Seeded baseline (41 users, 12 courses, 50 teams) + dynamic creation (1000+ records) + bulk operations (500-1500 scale)
- **Cleanup**: Automated atomicity guarantee - fails if any dynamic record remains
- **Execution Time**: ~19 hours sequential (staggered across session)
- **Dual Mode**: Postman UI (manual) + Newman CLI (automated)

---

## Module Breakdown

| #   | Module        | Tests | Focus Areas                                                |
| --- | ------------- | ----- | ---------------------------------------------------------- |
| 01  | AUTH          | 30    | Login, tokens, session management, multi-role              |
| 02  | USERS         | 37    | CRUD, bulk operations, role-based access                   |
| 03  | COURSES       | 50    | Course mgmt, enrollment, permissions, archiving            |
| 04  | ASSIGNMENTS   | 60    | Creation, rubrics, publishing, bulk operations             |
| 05  | TEAMS         | 48    | Team CRUD, membership, bulk operations                     |
| 06  | SUBMISSIONS   | 37    | File uploads, versioning, tracking                         |
| 07  | EVALUATIONS   | 52    | Grading, bulk publish, PDF generation                      |
| 08  | EXTENSIONS    | 25    | Extension requests, approvals, deadlines                   |
| 09  | ANNOUNCEMENTS | 30    | Creation, distribution, notifications                      |
| 10  | NOTIFICATIONS | 25    | Delivery, read status, user preferences                    |
| 11  | EXPORT        | 30    | CSV/JSON/PDF export, bulk operations, performance          |
| 12  | PREVIEW       | 25    | File preview, rendering, access control                    |
| 13  | ADMINSTATS    | 30    | Dashboard, analytics, performance metrics                  |
| 14  | SYSTEMADMIN   | 30    | Health checks, backup, cache management                    |
| 15  | CLEANUP       | 15    | **CRITICAL**: Atomicity verification, seed data validation |

**Total**: 524 tests

---

## Test Strategy

### Each Module Includes:

1. **Happy Path** (50-60%): Normal operations with valid data
2. **Error Path** (25-35%): Invalid inputs, boundary conditions, error scenarios
3. **Edge Cases** (10-15%): Concurrency, race conditions, extreme values
4. **Security** (5-10%): Authorization, data protection, injection prevention

### Data Model

#### Seeded Data (Immutable Baseline - Verified After Tests)

- **Users**: 41 total (2 SYSTEM_ADMIN, 2 ADMIN, 5 INSTRUCTOR, 32 STUDENT)
- **Courses**: 12 (IDs 1-12, codes: CS401-STATS101)
- **Teams**: 50+ pre-assigned
- **Password**: `Test@1234` (all users)

#### Dynamic Data (Created During Tests - Deleted in Cleanup)

- **Users**: Up to 50 bulk-created
- **Courses**: Up to 25 bulk-created
- **Assignments**: Up to 200+ created
- **Submissions**: 500+ file uploads
- **Files**: 1500+ in S3 storage
- **Tracking**: All dynamic IDs stored in environment arrays for cleanup

---

## Environment Setup

### Required Files

1. **ReviewFlow_Test_Environments.postman_environment.json**
   - 150+ variables for all users, courses, bulk configs
   - Dynamic tracking arrays (empty initially, populated during tests)
   - Performance thresholds and cleanup flags
2. **ReviewFlow_Comprehensive_v2.json**
   - Master collection with 15 modules + setup folder
   - Pre-request scripts (global auth functions)
   - Collection-level test scripts (performance tracking)

### Environment Variables - Key Categories

```
BASE CONFIGURATION:
  baseUrl: http://localhost:8081/api/v1
  universalPassword: Test@1234

AUTH TOKENS (populated at runtime):
  systemAdminToken, adminToken, instructorToken, studentToken
  currentToken, currentRole

USER IDS (seeded):
  allSystemAdminIds: "36,46"
  allAdminIds: "47,48"
  allInstructorIds: "1,2,3,4,5"
  allStudentIds: "6,7,8,...,42"

COURSE IDS (seeded):
  course_1_id through course_12_id (values 1-12)

BULK OPERATION CONFIG:
  bulkUserCount: 50
  bulkEnrollCount: 100
  bulkGradeCount: 500
  bulkFileUploadCount: 1500

DYNAMIC TRACKING (empty arrays, populated during tests):
  dynamicUserIds: "[]"
  dynamicCourseIds: "[]"
  dynamicAssignmentIds: "[]"
  ... (all entity types)

PERFORMANCE THRESHOLDS (warnings if exceeded):
  perf_auth_ms: 300
  perf_single_ms: 500
  perf_list_ms: 800
  perf_bulk_100_sec: 3
  perf_publish_500_sec: 10
  ... (all SLAs)
```

---

## Execution Guide

### Pre-Requisites

1. **Backend Running**: `http://localhost:8081/api/v1` (default)
2. **Database Ready**: MySQL 8.0 with seeded data
3. **Postman Installed**: Latest version recommended
4. **Environment Imported**: ReviewFlow_Test_Environments.postman_environment.json

### Option 1: Manual Execution (Postman UI)

#### Setup

```
1. Import environment: ReviewFlow_Test_Environments.postman_environment.json
2. Import collection: ReviewFlow_Comprehensive_v2.json
3. Select environment from dropdown
4. Go to 00_SETUP folder
5. Run all 4 login requests manually (or use "Run Collection" on 00_SETUP)
```

#### Execute Items in Order

```
Postman UI > Run Collection > Select ReviewFlow_Comprehensive_v2
  - Ensure sequential execution enabled
  - Check "Persist responses" for debugging
  - Set delay between requests to 100ms (optional)
  - Hit "Run" button
  - Monitor requests tab for failures
```

#### Expected Flow

```
00_SETUP (login all roles) →
01_AUTH (30 tests) →
02_USERS (37 tests) →
03_COURSES (50 tests) →
04_ASSIGNMENTS (60 tests) →
05_TEAMS (48 tests) →
06_SUBMISSIONS (37 tests) →
07_EVALUATIONS (52 tests) →
08_EXTENSIONS (25 tests) →
09_ANNOUNCEMENTS (30 tests) →
10_NOTIFICATIONS (25 tests) →
11_EXPORT (30 tests) →
12_PREVIEW (25 tests) →
13_ADMINSTATS (30 tests) →
14_SYSTEMADMIN (30 tests) →
15_CLEANUP (15 CRITICAL tests with atomicity verification)
```

### Option 2: Automated Execution (Newman CLI)

#### Install Newman

```bash
npm install -g newman
npm install -g newman-reporter-html
```

#### Run Collection

```bash
newman run ReviewFlow_Comprehensive_v2.json \
  -e ReviewFlow_Test_Environments.postman_environment.json \
  --reporters cli,json,html \
  --reporter-html-export ./results/collection-report.html \
  --reporter-json-export ./results/collection-report.json \
  --delay-request 100 \
  --timeout-request 10000 \
  --bail
```

#### Output Files Generated

```
./results/collection-report.html    # Visual report with summaries
./results/collection-report.json    # Machine-readable for CI/CD
console output                       # Real-time test progress
```

---

## Performance Baselines

### Expected Response Times (SLAs - Warnings Only, Not Hard Failures)

| Operation                      | Target | Warning Threshold |
| ------------------------------ | ------ | ----------------- |
| Login (AUTH)                   | <300ms | >300ms            |
| Single endpoint GET            | <500ms | >500ms            |
| List endpoint (10-100 records) | <800ms | >800ms            |
| Bulk enroll (100 students)     | <3sec  | >3sec             |
| Bulk publish (500 grades)      | <10sec | >10sec            |
| CSV export (500 records)       | <5sec  | >5sec             |
| PDF generation (100 PDFs)      | <15sec | >15sec            |

### Performance Tracking

- Each test logs response time to environment variable
- Post-test collection script calculates p50, p95, p99 latencies
- Summary appended to JSON report for trend analysis
- **Warnings logged** if thresholds exceeded (doesn't fail collection)
- Use for capacity planning and SLA validation

---

## Cleanup Atomicity Guarantee

### Critical Design: Item 15 (CLEANUP)

**Item 15 ALWAYS EXECUTES**, regardless of prior test failures. This ensures:

```
1. PRECONDITION: Prior items may have created up to 1000+ dynamic records

2. CLEANUP SEQUENCE (runs ALWAYS):
   - Step 01: Log cleanup start time
   - Step 02: Delete all dynamic users
   - Step 03: Delete all dynamic courses
   - Step 04: Delete all dynamic assignments
   - Step 05: Delete all dynamic teams
   - Step 06: Delete all dynamic submissions
   - Step 07: Delete all S3 files (1500+ scale)
   - Step 08: VERIFY seed users = 41 (FAIL if != 41)
   - Step 09: VERIFY seed courses >= 12 (FAIL if < 12)
   - Step 10: VERIFY seed teams >= 50 (FAIL if < 50)
   - Step 11: Clear application cache
   - Step 12: Final atomicity check (FAIL if any dynamic record remains)
   - Step 13: Audit log completion
   - Step 14: Atomicity guarantee validation
   - Step 15: Summary report

3. POSTCONDITION:
   - If all steps pass: DB is CLEAN for next collection run
   - If any step fails: Collection marked FAILED, alert developer
   - Seed data VERIFIED to exact count (cannot be corrupted)
```

### Failure Handling

**Bulk Operation Partial Failure** (e.g., 450/500 grades published):

- Test records actual count: `pm.expect(publishedCount).to.equal(500)`
- Test FAILS if count < expected
- Cleanup STILL RUNS and deletes those 450 records
- Next collection run starts with clean DB

**Cleanup Step Failure**:

- If DELETE returns non-200: Test fails, logs error, moves to next step
- If VERIFY returns wrong seed data count: Collection FAILS
- Summary report shows which steps failed
- Developer can investigate DB state before retry

### Why This Matters

- **Isolation**: Each collection run is fully independent (no orphaned data)
- **Reproducibility**: Same test run twice produces same results
- **Safety**: Seed data count is checked, preventing DB corruption
- **Debugging**: Cleanup logs show exactly what was deleted and verified

---

## Troubleshooting

### Issue: "Status 401 Unauthorized" in tests after 01_AUTH

**Cause**: Auth tokens not cached properly

```
Solution:
1. Check 00_SETUP completed successfully
2. Verify environment shows systemAdminToken populated (>20 chars)
3. Check Collection > Pre-request script has proper token setup
4. Re-run 00_SETUP logins manually
```

### Issue: Bulk Operation Timeout (>10s)

**Cause**: Performance degradation or large payload

```
Solution:
1. Check Newman timeout setting: --timeout-request 15000 (15sec)
2. Monitor backend CPU/memory during bulk ops
3. Check S3 upload speed (1500 files = multiple seconds)
4. Review performance warnings in JSON report
5. Consider splitting bulk to 500 units instead of 1500
```

### Issue: "Atomicity verification failed - seed data count mismatch"

**Cause**: Cleanup didn't delete all dynamic records OR extra seeded data

```
Solution:
1. Stop backend immediately - DO NOT run collection again
2. Connect to database: SELECT COUNT(*) FROM users WHERE is_seeded = 0;
3. If orphaned records exist, manually delete them
4. Verify seed data is intact: SELECT COUNT(*) FROM users WHERE is_seeded = 1;
5. If seed count != 41, restore from backup
6. Re-run collection after cleanup complete
```

### Issue: "S3 file cleanup failed"

**Cause**: Presigned URL expired or S3 permissions

```
Solution:
1. Check S3 bucket connectivity: aws s3 ls
2. Verify IAM role has DeleteObject permission
3. Check file expiry settings (should be 1 hour)
4. Manually delete s3://bucket/test-files/* before retry
5. Consider partial failure handling in cleanup step
```

### Issue: "Email uniqueness violation" in bulk user creation

**Cause**: Duplicate email from prior collection run

```
Solution:
1. Cleanup must have failed in prior run
2. Query DB: SELECT COUNT(*) FROM users WHERE email LIKE 'bulk_user_%@%'
3. DELETE FROM users WHERE email LIKE 'bulk_user_%@%' AND is_seeded = 0;
4. Re-run collection
```

### Issue: Notifications not being sent during tests

**Cause**: Email service misconfigured or disabled

```
Solution:
1. Tests expect 200 status (not email delivery)
2. Check backend notification config (real vs mock)
3. Verify email service credentials if using real SMTP
4. Tests pass if HTTP 200, regardless of email destination
```

---

## Files Summary

### Primary Collection Files

```
ReviewFlow_Comprehensive_v2.json (2-3 MB)
  ├── 00_SETUP (4 login requests)
  ├── 01_AUTH (30 tests)
  ├── 02_USERS (37 tests)
  ├── 03_COURSES (50 tests)
  ├── 04_ASSIGNMENTS (60 tests)
  ├── 05_TEAMS (48 tests)
  ├── 06_SUBMISSIONS (37 tests)
  ├── 07_EVALUATIONS (52 tests)
  ├── 08_EXTENSIONS (25 tests)
  ├── 09_ANNOUNCEMENTS (30 tests)
  ├── 10_NOTIFICATIONS (25 tests)
  ├── 11_EXPORT (30 tests)
  ├── 12_PREVIEW (25 tests)
  ├── 13_ADMINSTATS (30 tests)
  ├── 14_SYSTEMADMIN (30 tests)
  └── 15_CLEANUP (15 tests - CRITICAL)

ReviewFlow_Test_Environments.postman_environment.json (35-40 KB)
  └── 150+ variables (users, courses, tracking, thresholds)
```

### Output Files (Generated at Runtime)

```
./results/collection-report.html   # HTML report (interactive)
./results/collection-report.json   # JSON metrics (CI/CD)
```

---

## Git Configuration

### .gitignore (add if not present)

```
ReviewFlow_Test_Environments.postman_environment.json  # Has passwords
results/                                               # Generated reports
*.log                                                  # Execution logs
```

### Commit Collection

```bash
git add ReviewFlow_Comprehensive_v2.json
git commit -m "feat: comprehensive 524-test collection with atomicity cleanup"
git push origin main
```

### Environment - Keep Local Only

```bash
git rm --cached ReviewFlow_Test_Environments.postman_environment.json
git commit -m "security: remove environment from repo (contains passwords)"
# Team members create their own from template
```

---

## Advanced Usage

### CI/CD Integration

**GitHub Actions Example**:

```yaml
name: ReviewFlow API Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-node@v2
      - run: npm install -g newman newman-reporter-html
      - run: |
          newman run Backend/postman/ReviewFlow_Comprehensive_v2.json \
            -e Backend/postman/ReviewFlow_Test_Environments.postman_environment.json \
            --reporters cli,json,html \
            --reporter-html-export results/report.html \
            --bail
      - uses: actions/upload-artifact@v2
        with:
          name: test-results
          path: results/
```

### Custom Report Generation

```bash
# Generate HTML report manually after run
newman run ReviewFlow_Comprehensive_v2.json \
  -e ReviewFlow_Test_Environments.postman_environment.json \
  --reporter html \
  --reporter-html-export my-report.html
```

---

## Performance Optimization Tips

1. **Pre-warm cache**: Run 00_SETUP on backend before collection
2. **Disable logging**: Set backend log level to WARN during bulk ops
3. **Parallel DB queries**: Connection pool >= 50 for bulk operations
4. **S3 batch upload**: Use multipart upload for 1500+ files
5. **Cache cleanup**: Run 15_CLEANUP to clear redis/memcached
6. **Monitor**: Watch CPU/memory during 07_EVALUATIONS (500 PDFs)

---

## Summary

| Aspect          | Details                                                      |
| --------------- | ------------------------------------------------------------ |
| **Tests**       | 524 comprehensive (Happy + Error + Edge + Security)          |
| **Coverage**    | All 93 endpoints across 15 modules                           |
| **Execution**   | Sequential DAG, 15-item strict dependencies                  |
| **Data Scale**  | 41 users, 12 courses, 50 teams (seeded) + 1000+ dynamic      |
| **Performance** | SLA baselines tracked (auth <300ms, bulk <3-10sec)           |
| **Atomicity**   | Item 15 CRITICAL - runs always, fails if seed data corrupted |
| **Modes**       | Postman UI (manual) + Newman CLI (automated)                 |
| **Time**        | ~19 hours sequential (staggered across session)              |
| **Status**      | ✅ Production-ready, fully documented                        |

---

## Support & Questions

For issues or questions about the test collection:

1. Review [Troubleshooting](#troubleshooting) section above
2. Check collection-report.json for detailed error logs
3. Inspect backend logs for API-side errors
4. Verify environment variables match your deployment

**Version**: v2.0 (December 2024)
**Last Updated**: Comprehensive v2 release
**Status**: PRODUCTION READY
