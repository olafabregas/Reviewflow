# ReviewFlow — Copilot Agent Instructions

## Project Context

ReviewFlow is a monorepo with a mixed stack:
- **Backend:** Java 21 + Spring Boot + Maven (single module)
- **Database migrations:** Flyway
- **Testing:** JUnit via Surefire, JaCoCo for coverage
- **Frontend:** (separate stack within the monorepo)

## Agent Behaviour — Non-Negotiable Rules

These rules apply to EVERY subagent in this project without exception.

### 1. Inspect Before Acting
Read the relevant files in full before proposing any change.
Never assume structure — verify it.

### 2. Write a Critical Assessment First
Before touching code, output a short written assessment:
- What you found
- What the real risk of changing it is
- Your confidence level (HIGH / MEDIUM / LOW) per change

### 3. Rank by Confidence. Implement Only HIGH Confidence Changes.
- **HIGH:** Provably safe, no ambiguity, no downstream risk
- **MEDIUM:** Flag it, explain it, do NOT implement it — leave a `// TODO [AGENT]:` comment
- **LOW:** Document it and stop. Never touch low-confidence items.

### 4. Run All Checks After Every Track
After completing each subagent track, run the full check suite in order:
```bash
mvn compile
mvn test
mvn checkstyle:check
mvn spotbugs:check
mvn pmd:check
```
If ANY check fails, stop, report the failure, and do NOT proceed to the next track.

### 5. Never Break Working Code to Make It Pretty
Style and cleanliness are secondary to correctness.
If a cleanup introduces a test failure, revert it.

### 6. One Track at a Time
Do not run multiple subagents in parallel.
Complete a track fully (inspect → assess → implement → check) before starting the next.

### 7. Preserve Intent Over Form
If code looks messy but clearly serves a specific purpose,
document the purpose and leave the code alone.

---

## Subagent Execution Order

Run in this sequence to minimise risk of conflicts between tracks:

| Order | Track | Why This Order |
|-------|-------|---------------|
| 1 | Dead Code Removal | Removes noise before analysis |
| 2 | Type Consolidation | Easier to see type drift after dead code is gone |
| 3 | Deduplication | Cleaner surface for DRY analysis |
| 4 | Circular Dependencies | Structural fix before type changes |
| 5 | Type Strengthening | Safer after structure is clean |
| 6 | Error Handling Cleanup | Logic pass after types are solid |
| 7 | Deprecated / AI Slop | Final sweep |
| 8 | Google Java Style | Style enforcement last — never first |

---
## After Completing each Track

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


## Check Suite Reference

### Phase 1 (During Cleanup — Warnings Mode)
Checkstyle runs in report mode only. Build does not fail on style violations.
```bash
mvn checkstyle:checkstyle   # generates report, does not fail build
```

### Phase 2 (After Cleanup — Strict Mode)
Once all 8 tracks are complete and the codebase is clean:
```bash
mvn checkstyle:check        # NOW fails build on violations
```
Update `pom.xml` to switch `checkstyle-maven-plugin` goal from `checkstyle` to `check`
and set `<failsOnError>true</failsOnError>` when ready to enforce strictly.

---

## Google Java Style — Key Rules to Enforce

- 2-space indentation (not 4, not tabs)
- Column limit: 100 characters
- One statement per line
- Braces always used (even for single-line if/for/while)
- Imports: no wildcards, ordered by Google convention
- Naming: `camelCase` for methods/variables, `PascalCase` for classes, `UPPER_SNAKE_CASE` for constants
- Javadoc required on all public methods and classes
- No trailing whitespace

Full spec: https://google.github.io/styleguide/javaguide.html

