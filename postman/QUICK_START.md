# ReviewFlow Comprehensive Test Collection - Quick Start

## What You Need

```
Backend Live:           http://localhost:8081/api/v1
Database:              MySQL 8.0 with seeded data ready
Postman:               Latest version
Files:
  - ReviewFlow_Comprehensive_v2.json (524 tests)
  - ReviewFlow_Test_Environments.postman_environment.json (150+ vars)
```

## 30-Second Setup

### 1. Import Environment

```
Postman > File > Import > ReviewFlow_Test_Environments.postman_environment.json
```

### 2. Import Collection

```
Postman > File > Import > ReviewFlow_Comprehensive_v2.json
```

### 3. Select Environment

```
Top-right dropdown: Switch to "ReviewFlow_Test_Environments"
```

### 4. Run Collection

```
Postman > ReviewFlow_Comprehensive_v2 > Run Collection
☑  Sequential execution (default)
☑  Proceed on errors (allows cleanup to run)
► Click "Run" button
```

## What Happens

1. **00_SETUP**: Logs in all 4 roles (2 min)
2. **01_AUTH → 07_EVALUATIONS**: Tests 294 core functions (8 hours)
3. **08-14**: Tests extensions, admin, export features (5 hours)
4. **15_CLEANUP**: Deletes 1000+ records, verifies seed data intact (5 min)

✅ **Total**: 524 tests, ~13 hours, all modules covered

## Expected Results

### Pass Criteria

```
✅ 95%+ tests pass (some edge cases may be environment-specific)
✅ Cleanup atomicity verified (seed data count correct)
✅ Performance warnings logged (but don't fail tests)
✅ HTML report generated (optional with Newman)
```

### Typical Failure Points

```
⚠️  S3 file upload timeout (1500 files = 2-3 min)
⚠️  PDF generation slow (500 PDFs = 5-10 min)
⚠️  Bulk operations >3sec (warn, don't fail)
❌ Authorization check failures (check tokens cached)
❌ Email service unavailable (check backend config)
```

## Run via CLI (Automated)

```bash
# Install
npm install -g newman newman-reporter-html

# Run
newman run ReviewFlow_Comprehensive_v2.json \
  -e ReviewFlow_Test_Environments.postman_environment.json \
  --reporters cli,json,html \
  --reporter-html-export results.html \
  --timeout-request 10000 \
  --delay-request 100

# View results
open results.html  # macOS
start results.html # Windows
```

## Understand the Output

### JSON Report Fields

```json
{
  "stats": {
    "requests": 524,
    "passed": 500,
    "failed": 24
  },
  "timing": {
    "total_ms": 47000,
    "p50_ms": 85,
    "p95_ms": 450,
    "p99_ms": 2100
  }
}
```

### HTML Report Shows

```
✓ Summary: Requests run, Passed, Failed
✓ Performance: Response time trends
✓ Failed requests: Why they failed
✓ Test scripts: What was asserted
```

## Stop Early?

**Pause Collection**: Postman UI > Pause button (stops after current request)

**Restart from Item 8**:

```
1. Right-click "08_EXTENSIONS"
2. Select "Run folder"
3. Continue from there
```

**Skip to Cleanup (10 min validation)**:

```
1. Right-click "15_CLEANUP" folder
2. Select "Run folder"
3. Validates seed data is intact
```

## Common Issues

### "401 Unauthorized" everywhere

```
→ 00_SETUP failed to log in
→ Check backend is running on http://localhost:8081
→ Check database has seeded users (admin@reviewflow.com)
```

### "Connection refused"

```
→ Backend not running
→ Start backend: mvn spring-boot:run (from Backend folder)
→ Wait 30sec for startup
```

### "Collection runs but nothing is tested"

```
→ Environment not selected
→ Top-right: Confirm "ReviewFlow_Test_Environments" is active (not "No environment")
```

### "Cleanup fails - atomicity error"

```
→ Prior tests didn't all run
→ Manual intervention required:
  DELETE FROM users WHERE email LIKE 'bulk_%@%' AND created_after > now();
  (contact DevOps if unsure)
```

## Next Steps After Running

1. **Review Results**
   - Check HTML report for visual summary
   - Look for failed tests (usually edge cases, not failures)
   - Note performance warnings (good for capacity planning)

2. **Performance Analysis**
   - Export JSON report to analytics tool
   - Track p95 latency across runs
   - Alert if publish grades takes >10sec

3. **Archive Results**
   - Save results/ folder to project history
   - Use as baseline for regression testing
   - Compare across sprints

## Run on Your Schedule

**Daily**: Quick smoke test (just 00_SETUP + 01_AUTH)

```bash
newman run ReviewFlow_Comprehensive_v2.json \
  -e ReviewFlow_Test_Environments.postman_environment.json \
  --folder "00_SETUP,01_AUTH"
```

**Weekly**: Full suite (all 524 tests)

```bash
newman run ReviewFlow_Comprehensive_v2.json \
  -e ReviewFlow_Test_Environments.postman_environment.json
```

**On Deploy**: Small subset (auth + users + courses)

```bash
newman run ReviewFlow_Comprehensive_v2.json \
  -e ReviewFlow_Test_Environments.postman_environment.json \
  --folder "00_SETUP,01_AUTH,02_USERS,03_COURSES"
```

## Get Help

**Issues?**

1. Check COMPREHENSIVE_TEST_COLLECTION_GUIDE.md (full troubleshooting)
2. Review backend logs: `tail -f reviewflow.log`
3. Check database: `SELECT COUNT(*) FROM users;` (should be 41)
4. Verify S3 connection: `aws s3 ls`

**Questions?**

- File an issue: Backend: Issues tab
- Review conversation history for implementation decisions
- Check adjacent PRD files (Features/ folder) for context

---

**Status**: ✅ Ready to run  
**Time**: ~13 hours for full suite  
**Difficulty**: Easy (just click Run)  
**Last Updated**: December 2024
