---
applyTo: "**/*.java"
---

# Subagent 4: Circular Dependencies

> Run this FOURTH — structural issues must be resolved before type and logic passes.

## Objective

Map the full dependency graph of the ReviewFlow backend.
Find every circular import or Spring bean circular dependency.
Untangle the ones that matter — prioritising those that affect maintainability,
testability, and correctness.

---

## Step 1 — Inspect

### 1a. Static Import Cycles

Analyse Java `import` statements to build a package-level dependency graph.
Look for cycles at both the class level and the package level.

Focus on cycles involving:
- Domain service classes calling each other (e.g. `UserService` → `ReviewService` → `UserService`)
- Controller importing from another controller's package
- Repository classes with cross-references
- `@Configuration` classes that create beans depending on each other

### 1b. Spring Bean Circular Dependencies

Look for any `@Autowired` / constructor injection patterns where:
- Bean A injects Bean B, and Bean B injects Bean A
- Bean A injects Bean B which injects Bean C which injects Bean A

Spring Boot 2.6+ prohibits circular bean dependencies by default.
If the project was started on an older version, these may exist silently.

Check `application.yml` / `application.properties` for:
```yaml
spring.main.allow-circular-references=true
```
If this flag is set to `true`, that is a red flag — document every cycle it is masking.

### 1c. Flyway Migration Dependencies

Ensure no Java service or repository is imported into a Flyway migration class.
Migrations must be self-contained SQL — never Java domain logic.

---

## Step 2 — Write Critical Assessment

For each cycle found, classify by impact:

| Severity | Definition |
|----------|------------|
| **CRITICAL** | Prevents independent testing of a class; masks a design flaw |
| **HIGH** | Makes the dependency graph hard to reason about; will cause problems at scale |
| **MEDIUM** | Technical cycle but low practical impact; refactor when touching those classes |
| **LOW** | Package-level cycle with no real runtime consequence |

Document each cycle:
```
| Cycle | Severity | Root Cause | Proposed Fix | Confidence |
|-------|----------|-----------|--------------|------------|
| UserService ↔ EmailService | HIGH | UserService calls EmailService.send(); EmailService calls UserService.getEmail() | Extract EmailResolver interface; inject into EmailService instead | HIGH |
| auth ↔ submission (package level) | MEDIUM | AuthUtil imported in SubmissionController | Move AuthUtil to shared package | HIGH |
```

---

## Step 3 — Implement HIGH Confidence Fixes Only

**Preferred resolution strategies (in order of preference):**

1. **Extract a shared neutral module** — move the shared logic that both sides need
   into a new class/package that neither side owns (e.g. `shared/`, `common/`, `util/`)
2. **Introduce an interface** — the upstream class depends on an interface;
   the downstream provides the implementation. Breaks the import cycle cleanly.
3. **Use Spring events** — replace a direct method call with an `ApplicationEvent`
   so the dependency direction is reversed through the event bus
4. **Merge classes** — if two classes are so tightly coupled they form a cycle,
   they may actually belong together

**Never do these to break a cycle:**
- Add `@Lazy` to an `@Autowired` field as a permanent fix (it is a band-aid, not a fix)
- Set `allow-circular-references=true` in properties
- Introduce a new abstract layer just to technically break the cycle without fixing the design

For MEDIUM/LOW cycles:
```java
// TODO [CIRCULAR-DEP-AGENT]: Package-level cycle with [X]. Low impact now
// but should be resolved when this class is next modified.
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

Do NOT proceed to Subagent 5 until all checks pass.
