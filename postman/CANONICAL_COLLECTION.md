# ReviewFlow — Canonical Postman harness

## Layout

```
postman/
  .generation/                              ← per-feature staging (agent output)
    auth.postman_collection.json
    course.postman_collection.json
    ...
  scripts/
    merge-staging-collections.mjs           ← merges staging → canonical
  reviewflow-tests.postman_collection.json  ← **import this** (Newman + Postman)
  ReviewFlow_Test_Environments.postman_environment.json
  ReviewFlow_Comprehensive_v2.json          ← legacy (kept until unified coverage complete)
  package.json                              ← npm run merge | npm test
```

## Workflow

### 1. Generate staging (Cursor agent)

```
@postman-test-suite-audit scan all
```

Writes or updates `postman/.generation/{feature}.postman_collection.json` for each feature (15 folders), then merges.

Or one feature at a time:

```
@postman-test-suite-audit scan auth
cd postman && npm run merge
```

### 2. Merge (CLI)

```bash
cd postman
npm run merge
```

Preserves the existing **`00_SETUP`** folder in `reviewflow-tests.postman_collection.json` and appends feature folders in fixed order.

### 3. Run tests

**Postman GUI**

1. Import `ReviewFlow_Test_Environments.postman_environment.json`
2. Import `reviewflow-tests.postman_collection.json`
3. Select environment → Run collection (enable cookie jar)

**Newman**

```bash
cd postman
npm install -g newman   # once
npm test
```

Legacy comprehensive run:

```bash
npm run test:legacy
```

## Endpoint folder contract

Each route in staging must use five subfolders:

| Subfolder | Purpose |
|---|---|
| Happy path | Valid auth + body → 2xx, `success: true` |
| Auth failure | Missing/invalid session → 401 |
| Validation failure | `@Valid` violations → 400 |
| Role access | Wrong role → 403 `FORBIDDEN` |
| Domain edge cases | Business rules, not-found Hashids, deadlines |

Tests assert the standard envelope (`controller_specs/00_Global_Rules_and_Reference.md`).

## Auth model

ReviewFlow uses **HTTP-only cookies**, not Bearer tokens in responses. The unified collection uses:

- `00_SETUP` logins with envelope assertions
- Postman **cookie jar** for subsequent requests
- Environment variables for emails/passwords and Hashid placeholders (`courseId`, etc.)

Do not assert `pm.response.json().token` on login — use `success` + `data`.

## Coverage tracking

```
@postman-test-suite-audit coverage
```

Statuses: `COVERED`, `PARTIAL`, `MISSING`, `STALE`.

## Related docs

- `.cursor/rules/postman-test-suite-audit.mdc` — generator rules
- `postman/POSTMAN_98_ROUTE_VERIFICATION_REPORT.md` — legacy route inventory
- `postman/.generation/README.md` — staging directory notes
