# ReviewFlow Postman — Quick Start

## Canonical harness (recommended)

```
Backend:     http://localhost:8081/api/v1
Environment: ReviewFlow_Test_Environments.postman_environment.json
Collection:  reviewflow-tests.postman_collection.json
Staging:     .generation/{feature}.postman_collection.json
```

See **[CANONICAL_COLLECTION.md](./CANONICAL_COLLECTION.md)** for the full staged → merge workflow.

### 30-second setup

1. Import `ReviewFlow_Test_Environments.postman_environment.json`
2. Import `reviewflow-tests.postman_collection.json`
3. Select **ReviewFlow Test Environments** in the top-right dropdown
4. Run **`00_SETUP`** first (HTTP-only cookie auth), then feature folders

### Regenerate from controllers

```text
@postman-test-suite-audit scan all
cd postman && npm run merge
```

### Newman

```bash
cd postman
npm test
```

---

## Legacy comprehensive collection

Use until `reviewflow-tests` reaches full route coverage (~98+ routes).

```
Files:
  - ReviewFlow_Comprehensive_v2.json (524 tests)
  - ReviewFlow_Test_Environments.postman_environment.json (150+ vars)
```

### Legacy 30-second setup

1. Import environment (same file as above)
2. Import `ReviewFlow_Comprehensive_v2.json`
3. Select environment → Run collection (sequential, proceed on errors)

> **Note:** Legacy login tests expect `token` in JSON body. The live API uses **cookie-based auth** and the `ApiResponse` envelope — prefer `reviewflow-tests` for new work.

## What Happens (legacy)

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
❌ Authorization check failures (check cookies / 00_SETUP ran)
❌ Email service unavailable (check backend config)
```

## Run via CLI (Automated)

```bash
# Canonical
cd postman && npm test

# Legacy
npm run test:legacy
```

Install Newman once:

```bash
npm install -g newman newman-reporter-html
```

Legacy example:

```bash
newman run ReviewFlow_Comprehensive_v2.json \
  -e ReviewFlow_Test_Environments.postman_environment.json \
  --env-var baseUrl=http://localhost:8081/api/v1
```
