---
applyTo: "**/*.java"
---

# Subagent 2: Type Consolidation

> Run this SECOND — after dead code is removed.
> Type drift is easier to spot on a clean surface.

## Objective

Find all type definitions (DTOs, enums, records, interfaces, exception classes)
that are duplicated or silently diverged across the codebase.
Consolidate them into a single source of truth where safe to do so.

---

## Step 1 — Inspect

Scan the entire backend module for:

1. **Duplicate DTOs / Records** — classes with identical or near-identical field sets
   defined in multiple packages (e.g. `UserDto` in `auth` and `UserDto` in `profile`)
2. **Diverged DTOs** — classes that started as copies but have quietly gained or lost
   fields over time, causing subtle inconsistency in API contracts
3. **Duplicate enums** — the same logical enum (e.g. `Status`, `Role`, `ErrorCode`)
   defined in multiple places with the same or overlapping values
4. **Duplicate exception classes** — custom exceptions that wrap the same error type
   in different packages
5. **Interface duplication** — interfaces that describe the same contract but are
   declared separately, causing classes to implement one and not the other

**Special attention — check these patterns in ReviewFlow:**
- Request/Response DTOs near controller and service layers — are the same shape
  defined twice?
- JWT / auth-related types — token payloads, claim wrappers, principal objects
- Peer review / submission domain types — are assignment, submission, and review
  types consistent across the domain?

---

## Step 2 — Write Critical Assessment

For each duplicate or diverged type, document:
- Both (or all) locations where it exists
- Whether the fields are identical, overlapping, or diverged
- Which version is the "real" one (most complete, most referenced, in the correct package)
- Confidence that merging is safe

```
| Type Name | Locations | State | Target Location | Confidence |
|-----------|-----------|-------|-----------------|------------|
| UserDto | auth/UserDto.java, profile/UserDto.java | Diverged — profile version has extra `avatarUrl` field | shared/dto/UserDto.java | HIGH |
| Role | auth/Role.java, model/Role.java | Identical | auth/Role.java (already used by Spring Security) | HIGH |
```

---

## Step 3 — Implement HIGH Confidence Consolidations Only

For each HIGH confidence consolidation:
1. Move the canonical type to the correct shared package (e.g. `shared/dto`, `common/types`)
2. Update all imports across the codebase
3. Delete the redundant duplicate
4. Verify no serialization, Jackson, or OpenAPI annotations are broken by the move

For MEDIUM items:
```java
// TODO [TYPE-AGENT]: Possible duplicate of UserDto in auth package.
// Fields have diverged — manual review required before consolidating.
```

Do NOT merge types that:
- Serve genuinely different layers (e.g. a JPA entity and a DTO that happen to have the same fields)
- Have different Jackson serialisation annotations that affect API output
- Are part of an external API contract (response types sent to clients)

---

## Step 4 — Run All Checks

```bash
mvn compile
mvn test
mvn checkstyle:checkstyle
mvn spotbugs:check
mvn pmd:check
```

Do NOT proceed to Subagent 3 until all checks pass.
