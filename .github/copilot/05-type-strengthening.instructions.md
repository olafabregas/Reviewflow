---
applyTo: "**/*.java"
---

# Subagent 5: Type Strengthening

> Run this FIFTH — after structure is clean. Type changes are safest on a
> well-organised, dependency-clean codebase.

## Objective

Find every weak type in the ReviewFlow backend — `Object`, raw collections,
missing generics, overly broad return types — and replace them with
strong, precise types. Run type checks after every batch.

---

## Step 1 — Inspect

Scan the backend for the following weak type patterns:

### 1a. Raw / Unparameterised Types
```java
// Bad
List items = new ArrayList();
Map config = new HashMap();

// Good
List<SubmissionDto> items = new ArrayList<>();
Map<String, Object> config = new HashMap<>();
```

### 1b. `Object` Used as a Data Container
```java
// Bad
public Object getResult() { ... }
private Object data;

// Good
public SubmissionResult getResult() { ... }
private SubmissionPayload data;
```

### 1c. Overly Broad Return Types
```java
// Bad — caller has to cast
public Collection getSubmissions() { ... }

// Good
public List<SubmissionDto> getSubmissions() { ... }
```

### 1d. Missing Generics in Spring Types
```java
// Bad
ResponseEntity response = ResponseEntity.ok(dto);
Optional result = repository.findById(id);

// Good
ResponseEntity<SubmissionDto> response = ResponseEntity.ok(dto);
Optional<User> result = repository.findById(id);
```

### 1e. Weak Exception Types
```java
// Bad
catch (Exception e) { ... }  // catches everything including RuntimeException

// Good — only if you truly mean to catch anything:
catch (IOException | SQLException e) { ... }
```

### 1f. Jackson / JSON Deserialisation Using `Object` or `Map<String, Object>`
These are sometimes legitimate — inspect before changing.
A `Map<String, Object>` at a JSON boundary may be intentional for dynamic payloads.

### 1g. JWT Claims Using Raw Types
ReviewFlow uses JWT — check that claim extraction returns typed values:
```java
// Bad
Object userId = claims.get("userId");

// Good
Long userId = claims.get("userId", Long.class);
```

---

## Step 2 — Write Critical Assessment

For each weak type, document:
- File and line
- Current type vs. correct strong type
- How you determined the correct type (inspected callers, return values, related tests)
- Whether the weak type is a legitimate boundary (intentional `Object` at a JSON edge)

```
| File | Element | Current Type | Correct Type | Legitimate Boundary? | Confidence |
|------|---------|--------------|--------------|----------------------|------------|
| SubmissionService.java:47 | getResult() return | Object | SubmissionResult | No | HIGH |
| WebSocketHandler.java:91 | payload field | Object | ChatMessage | No | HIGH |
| DynamicConfigService.java:33 | configMap | Map<String,Object> | Map<String,Object> | YES — dynamic JSON config | SKIP |
```

---

## Step 3 — Implement HIGH Confidence Strengthening Only

**Work in small batches — no more than 10 changes before running checks.**

After each batch:
```bash
mvn compile
```
Fix any compilation errors before continuing.

**Preserve legitimate boundary types:**
- `Object` or `Map<String, Object>` at JSON deserialisation edges where the schema is truly dynamic
- `Object` in generic utility classes where the type parameter is the correct solution but was not feasible at the time
- Add a comment explaining why the weak type is intentional:
```java
// Intentional: payload schema is dynamic per event type — typed via discriminator at handler level
private final Object rawPayload;
```

---
## After Completing This Track

If any MEDIUM or LOW confidence items were identified but not implemented,
create a GitHub issue for each one using the GitHub CLI:

```bash
gh issue create \
  --title "refactor(track-name): <short description of the unresolved item>" \
  --body "## Context
Track [N] ([track name]) identified this during the cleanup pass.

**What was found:**
<what the agent found>

**Why it was not fixed:**
<confidence level and reason>

**Proposed fix:**
<what the agent recommends>

## Acceptance criteria
- [ ] <verifiable outcome>

## Do not
- <anti-pattern to avoid>" \
  --label "tech-debt" \
  --label "refactor"
```
## Step 4 — Run All Checks

```bash
mvn compile
mvn test
mvn checkstyle:checkstyle
mvn spotbugs:check
mvn pmd:check
```

Do NOT proceed to Subagent 6 until all checks pass.
