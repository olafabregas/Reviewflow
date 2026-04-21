---
applyTo: "**/*.java"
---

# Subagent 3: Deduplication

> Run this THIRD — after dead code and type duplicates are resolved.
> DRY analysis on a clean codebase is far more accurate.

## Objective

Find repeated logic and copy-pasted code blocks across the ReviewFlow backend.
Consolidate only where doing so genuinely reduces complexity without obscuring intent.
Do NOT merge code that merely looks similar but serves different purposes.

---

## Step 1 — Inspect

Scan the backend module for:

1. **Copy-pasted method bodies** — identical or near-identical blocks of logic
   appearing in multiple classes or methods
2. **Repeated validation logic** — the same field validation rules written by hand
   in multiple places rather than centralised into a validator or annotation
3. **Repeated mapping logic** — manual field-by-field mapping between entity ↔ DTO
   repeated across multiple service methods instead of a mapper class/method
4. **Repeated exception handling patterns** — the same try/catch shape with the
   same error translation logic copy-pasted across service methods
5. **Repeated query construction** — JPA or native SQL queries built the same way
   in multiple repository or service methods
6. **Repeated Spring Security checks** — manual `authentication.getName()` or
   role checks repeated in multiple controllers instead of centralised via
   `@PreAuthorize` or a security helper
7. **Repeated response building** — `ResponseEntity.ok(...)` wrapper construction
   repeated with the same shape in many controller methods

---

## Step 2 — Write Critical Assessment

For each duplication found, document:
- The duplicated pattern and where it appears
- Why it is duplicated (copied intentionally? drifted over time?)
- Whether the duplicates are truly identical in intent or just similar in form
- The correct consolidation target (utility method, base class, helper, annotation)
- Confidence level

**Critical distinction — do NOT consolidate if:**
- Two methods look the same but will diverge as features evolve
- The duplication is in test code that benefits from being explicit and self-contained
- The shared logic would require a new abstraction layer that adds more complexity
  than it removes
- The methods are in different bounded domains where coupling them is architecturally wrong

```
| Pattern | Locations | Identical Intent? | Proposed Fix | Confidence |
|---------|-----------|-------------------|--------------|------------|
| Manual user lookup + null check | UserService, ReviewService, SubmissionService | YES | Extract to UserHelper.getOrThrow(id) | HIGH |
| ResponseEntity.ok() wrapping | All 6 controllers | YES | BaseController.ok(T body) helper | MEDIUM — check if controllers extend anything |
```

---

## Step 3 — Implement HIGH Confidence Consolidations Only

For each HIGH confidence consolidation:
1. Extract the shared logic into the most appropriate home:
   - **Utility/helper class** for stateless logic
   - **Base class** only if inheritance is already used and appropriate
   - **Shared service** if the logic requires Spring beans
   - **Custom annotation + AOP** if the pattern is a cross-cutting concern
2. Replace all call sites with the new shared implementation
3. Ensure the extracted code has a Javadoc comment explaining WHY it was centralised

For MEDIUM items:
```java
// TODO [DEDUP-AGENT]: Repeated pattern detected in [X other locations].
// Consolidation possible but requires verifying intent is identical. Confidence: MEDIUM.
```

---

## Step 4 — Run All Checks

```bash
mvn compile
mvn test
mvn checkstyle:checkstyle
mvn spotbugs:check
mvn pmd:check
```

Do NOT proceed to Subagent 4 until all checks pass.
