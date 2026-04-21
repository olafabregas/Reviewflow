---
applyTo: "**/*.java"
---

# Subagent 8: Google Java Style Enforcement

> Run this LAST. Style is the final pass — never the first.
> All logic, structure, and type changes must be complete and passing
> before this subagent runs.

## Objective

Enforce the Google Java Style Guide across the entire ReviewFlow backend.
This is a two-phase process:
- **Phase 1 (now):** Report all violations, auto-fix safe formatting issues,
  leave `// TODO [STYLE-AGENT]:` comments on structural violations.
- **Phase 2 (after review):** Switch Checkstyle to strict/fail mode so
  future code is enforced at build time.

Full spec: https://google.github.io/styleguide/javaguide.html

---

## Google Java Style — Key Rules Reference

| Rule | Requirement |
|------|-------------|
| Indentation | 2 spaces. No tabs. Ever. |
| Column limit | 100 characters per line |
| Braces | Always used — even for single-line `if`, `for`, `while` |
| One statement per line | No `if (x) return y;` on one line |
| Import order | Static imports first, then grouped by package, no wildcards |
| Naming — classes | `PascalCase` |
| Naming — methods/variables | `camelCase` |
| Naming — constants | `UPPER_SNAKE_CASE` (static final fields) |
| Naming — packages | All lowercase, no underscores |
| Javadoc | Required on all `public` classes and all `public`/`protected` methods |
| Annotations | One annotation per line, above the declaration |
| No C-style array declarations | `String[] args` not `String args[]` |
| Long literals | `1_000_000L` not `1000000l` — note lowercase `l` is banned |
| No trailing whitespace | |
| Blank lines | One blank line between class members. Two blank lines between top-level types. |

---

## Step 1 — Inspect

Run the Checkstyle report to get the full violation list:
```bash
mvn checkstyle:checkstyle
```
The report is generated at: `target/checkstyle-result.xml` and `target/site/checkstyle.html`

Open the HTML report and group violations by:
1. **Formatting** (indentation, whitespace, line length) — safe to auto-fix
2. **Import ordering** — safe to auto-fix
3. **Naming conventions** — requires careful renaming with all call sites updated
4. **Missing Javadoc** — requires writing real documentation, not generated stubs
5. **Structural** (multi-statement lines, missing braces) — safe to fix mechanically

---

## Step 2 — Write Critical Assessment

Before touching anything, output:
- Total violation count by category
- Top 5 files with the most violations
- Any naming violations that would require renaming public API methods
  (these affect external callers and need extra care)

```
Violation Summary:
- Indentation: 847 violations (safe to auto-fix)
- Line length: 234 violations (safe to fix)
- Import order: 91 violations (safe to auto-fix)
- Missing Javadoc: 156 violations (requires writing real docs)
- Naming conventions: 12 violations (requires careful rename)
- Missing braces: 67 violations (safe to fix)

Top files by violation count:
1. SubmissionService.java — 94 violations
2. ReviewController.java — 78 violations
...

Naming violations requiring manual review:
- getUserinfo() should be getUserInfo() — PUBLIC METHOD, check all callers
```

---

## Step 3 — Fix in This Order

### 3a. Auto-fixable Formatting (Do First)
Fix all of these mechanically — they carry zero logic risk:
- Indentation (2 spaces)
- Trailing whitespace removal
- Import reordering
- Missing braces around single-statement blocks
- Line length — wrap at 100 chars using Google's preferred continuation indent (4 spaces)
- C-style array declarations
- Lowercase `l` on long literals

### 3b. Naming Violations
For each naming violation:
1. Identify all call sites (controllers, tests, other services)
2. Rename using IDE refactor — do NOT do text search/replace
3. Run compile check immediately after each rename:
   ```bash
   mvn compile
   ```

**Special attention — do NOT rename:**
- Spring Security principal method names that match a security contract
- Jackson-annotated fields where the JSON key name is part of the API contract
  (use `@JsonProperty("originalName")` to preserve the JSON key while fixing Java naming)
- JPA entity fields mapped to database columns (use `@Column(name = "original_name")`)

### 3c. Missing Javadoc
Write real Javadoc. Do not generate filler.

**Required format:**
```java
/**
 * Retrieves the submission for the given ID, verifying that the requesting
 * user has permission to view it. Throws {@link SubmissionNotFoundException}
 * if no submission exists, or {@link UnauthorizedException} if the user
 * is not the author or an assigned reviewer.
 *
 * @param submissionId the ID of the submission to retrieve
 * @param requestingUser the authenticated user making the request
 * @return the submission DTO with full review metadata
 */
public SubmissionDto getSubmission(Long submissionId, User requestingUser) { ... }
```

**Banned Javadoc patterns (generate these and delete them):**
```java
// These are AI-generated filler — delete immediately:
/** Gets the id. @return the id */
/** Sets the name. @param name the name */
/** Returns true if valid. */
```

---

## Step 4 — Switch to Strict Mode

Once all violations are resolved and all checks pass:

Update `pom.xml` — change the Checkstyle plugin configuration:
```xml
<!-- BEFORE (report mode — Phase 1) -->
<goal>checkstyle</goal>

<!-- AFTER (strict mode — Phase 2) -->
<goal>check</goal>
```

And set:
```xml
<configuration>
    <failsOnError>true</failsOnError>
    <violationSeverity>warning</violationSeverity>
</configuration>
```

From this point forward, any code that violates Google Java Style
will break the build. This is the desired end state.

---

## Step 5 — Run Final Full Check Suite

```bash
mvn compile
mvn test
mvn checkstyle:check
mvn spotbugs:check
mvn pmd:check
```

All 8 tracks are complete when this runs clean.
Document the final state in a `CLEANUP_REPORT.md` at the project root.
