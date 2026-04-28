
## Objective

Find and remove legacy code paths, deprecated APIs, and every AI artifact
in the ReviewFlow backend — stubs, placeholder logic, and comments that
narrate edit history instead of explaining intent.

If a comment is worth keeping, rewrite it so a new engineer understands
**WHY the code exists**, not what it does.

---

## Step 1A — Inspect: Deprecated Code

Scan for:

### Legacy / Deprecated Java APIs

```java
// Examples of deprecated patterns to flag:
new Date()                          // use Instant / LocalDateTime
System.currentTimeMillis()          // use Clock or Instant.now()
StringBuffer                        // use StringBuilder unless thread-safety needed
@SuppressWarnings("deprecation")    // flag every usage — why is deprecated code still used?
```

### Spring Boot Deprecated Patterns

* `WebSecurityConfigurerAdapter` — deprecated since Spring Security 5.7; use `SecurityFilterChain` beans
* `spring.datasource.initialization-mode` — replaced by `spring.sql.init.mode`
* `HttpMethod.resolve()` — deprecated; use `HttpMethod.valueOf()`
* `MockMvcBuilders.standaloneSetup()` patterns that bypass Spring context

### Flyway Deprecated Patterns

* `flyway.locations` in old format — verify against current Flyway version in pom.xml
* Java-based migration classes — ensure they follow current Flyway `JavaMigration` interface

### Dead Feature Flags / Conditionals

```java
if (false) { ... }
if (FEATURE_ENABLED && false) { ... }
```

### Fallback Code Paths That No Longer Apply

* Fallback implementations written “in case X isn’t ready” — is X ready now?
* V1 API handlers kept “for backward compatibility” — are any active clients still using them?
* One-time migration or bootstrap logic that is now inert

---

## Step 1B — Inspect: AI Slop

Scan for every AI artifact left in the codebase:

### Placeholder / Stub Methods

```java
public void processReview(Review review) {
    // TODO: implement this
}

public List<Submission> getSubmissions() {
    return Collections.emptyList(); // placeholder
}
```

### Narrative Edit-History Comments

```java
// Updated by AI on 2024-03-15
// Refactored to use service layer
```

Rewrite or delete. Only keep comments that explain **WHY**.

### Commented-Out Implementations

```java
// public Optional<User> findUser(String email) {
//     return userRepository.findByEmail(email);
// }
```

Delete all commented-out code.

### Useless Javadoc

```java
/**
 * Gets the user.
 * @return the user
 */
```

Rewrite only if it adds real context.

### Over-Commented Tutorial Code

Remove comments that explain syntax or obvious behavior.

---

## Step 2 — Write Critical Assessment

```
| File | Element | Category | Action | Confidence |
|------|---------|----------|--------|------------|
| SecurityConfig.java | WebSecurityConfigurerAdapter | Deprecated Spring API | Migrate to SecurityFilterChain | HIGH |
| ReviewService.java:203 | processReview() stub | AI placeholder | Implement or delete | HIGH |
| UserRepository.java | // Updated by AI comment | Edit history comment | Delete comment | HIGH |
| SubmissionController.java | V1 legacy endpoint | Dead feature path | Verify usage before removal | MEDIUM |
```

---

## Step 2.5 — Produce PRD Before Implementation (MANDATORY)

Before making any code changes, generate a **Product Requirements Document (PRD)** based on findings.

This PRD is a **decision artifact** — not optional documentation.

### PRD Structure

#### 1. Scope & Objective

* Define what parts of the backend are being cleaned
* Explain why this cleanup matters now (tie to real risks)

#### 2. Key Findings

* Deprecated APIs
* AI-generated placeholders
* Dead code paths
* Comment quality issues
* Identify systemic patterns (not just isolated issues)

#### 3. Risk Analysis

For each category:

* What could break?
* What dependencies exist?
* What assumptions are being made?

If unclear → mark as NOT HIGH confidence.

#### 4. Action Plan (STRICTLY ORDERED)

Execution order must be explicit:

1. Safe deletions
2. Deprecated API migrations
3. Verified refactors
4. Deferred items

Each item must include:

* File / component
* Action
* Reason
* Confidence level

#### 5. Actions NOT Taken

* List skipped items
* Include reasoning (unclear usage, external dependency, etc.)

#### 6. Expected Impact

* Technical debt reduction
* Maintainability improvements
* Performance or architectural implications

---

### Enforcement Rules

* NO implementation before PRD completion
* PRD must align with assessment table
* Any non-HIGH confidence item is automatically excluded from implementation

---

## Step 3 — Implement HIGH Confidence Removals and Fixes

Only proceed after PRD is complete.

### Rules

* Remove or refactor ONLY HIGH confidence items
* Do NOT guess or assume usage
* Maintain backward compatibility unless explicitly justified

### Stub Handling

If not planned:

```java
// delete entirely
```

If planned:

```java
throw new UnsupportedOperationException(
    "Not yet implemented — tracked in issue #ID"
);
```

### Deprecated APIs

* Migrate properly — no suppression shortcuts

### Comments

* Rewrite only if they explain WHY
* Otherwise delete

---

## Step 4 — Run All Checks

```bash
mvn compile
mvn test
mvn checkstyle:checkstyle
mvn spotbugs:check
mvn pmd:check
```

Do NOT proceed to Subagent 8 until all checks pass.
