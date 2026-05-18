# Postman staging (`postman/.generation/`)

Per-feature Postman Collection v2.1 fragments are written here during `@postman-test-suite-audit scan all` or `scan [feature]`.

## Files

| Pattern | Produced by |
|---|---|
| `{feature}.postman_collection.json` | Generator agent (`auth`, `course`, `grading`, …) |

**Feature order** (merge sequence): auth → user → course → assignment → team → submission → evaluation → grading → extension → notification → announcement → messaging → discussion → admin → system

## Merge into canonical collection

```bash
cd postman
npm run merge
```

Writes **`../reviewflow-tests.postman_collection.json`** (repo import target for Postman and Newman).

## Endpoint folder shape

Each route folder must contain these five subfolders:

1. `Happy path`
2. `Auth failure`
3. `Validation failure`
4. `Role access`
5. `Domain edge cases`

See `.cursor/rules/postman-test-suite-audit.mdc` and `postman/CANONICAL_COLLECTION.md`.

## Legacy collections

Files such as `ReviewFlow_Comprehensive_v2.json` are **not** deleted automatically. Use them for reference until `reviewflow-tests` reaches full coverage.
