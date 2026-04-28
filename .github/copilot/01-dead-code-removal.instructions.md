---


---

## Objective

Find and remove all confirmed-dead Java code in the ReviewFlow backend.

**Confirmed-dead means:**

* Unreachable
* Unreferenced
* Not required by framework conventions
* Not used via reflection, configuration, or external systems

If there is any uncertainty → it is NOT dead.

---

## Step 1 — Inspect

Scan the entire backend module for:

1. **Unused private methods**
2. **Unused private fields**
3. **Unreferenced classes**
4. **Unreachable code blocks**
5. **Commented-out code blocks**
6. **Unused imports**
7. **Empty catch blocks**
8. **Unused local variables**

---

### Framework Exceptions (DO NOT TOUCH)

Do NOT classify as dead if ANY of the following apply:

* Spring annotations:
  `@Bean`, `@Component`, `@Service`, `@Repository`, `@RestController`,
  `@Controller`, `@Configuration`, `@Scheduled`, `@EventListener`,
  `@KafkaListener`, `@RabbitListener`, `@MessageMapping`

* Lifecycle methods:
  `init()`, `destroy()`, `afterPropertiesSet()`, `configure()`, `run()`, `execute()`

* Referenced in:

  * `application.yml` / `application.properties`
  * Flyway migrations

* Reflection / dynamic usage:

  * `Class.forName()`
  * `@ConditionalOn*`
  * Indirect wiring via configuration

* Serialization:

  * Classes implementing `Serializable`

* External/API usage:

  * Controllers or endpoints that may still be consumed externally

---

### HARD RULE

> “No references found” is NOT sufficient proof of dead code.

You must consider:

* Reflection
* External clients
* Config-driven wiring

---

## Step 2 — Write Critical Assessment (WITH PROOF)

Before removing anything, output:

```
| File | Element | Type | Confidence | Proof of Deadness | Reason |
|------|---------|------|------------|-------------------|--------|
| UserService.java | sendWelcomeEmail() | Private method | HIGH | No callers + not injected + not referenced in config | Safe to remove |
| AuthController.java | legacyLogin() | Endpoint | MEDIUM | No internal calls but externally exposed | Needs verification |
```

---

### Proof of Deadness Requirements

For HIGH confidence, you MUST demonstrate:

* No callers in codebase
* Not injected or wired by Spring
* Not referenced in configuration
* Not part of public API surface
* Not used via reflection

If ANY of these are uncertain → downgrade to MEDIUM.

---

## Step 2.5 — Produce PRD Before Implementation (MANDATORY)

Before making changes, generate a **Product Requirements Document (PRD)**.

This PRD is a **safety gate** — not documentation.

---

### PRD Structure

#### 1. Scope & Objective

* What parts of the system are being analyzed
* Why dead code removal is needed now (e.g., reducing hidden bugs, improving clarity)

#### 2. Key Findings

* Types of dead code discovered:

  * Private unused logic
  * Unreachable blocks
  * Legacy endpoints
  * Commented-out code
* Identify patterns (e.g., “abandoned features”, “partial implementations”)

#### 3. Risk Analysis

For each category:

* What could break?
* Could this be externally used?
* Could this be dynamically referenced?

If unclear → NOT HIGH confidence.

---

#### 4. Action Plan (STRICTLY ORDERED)

1. Safe removals (purely internal, proven dead)
2. Structural removals (classes, modules)
3. Deferred items (uncertain usage)

Each item must include:

* File / element
* Action
* Proof of deadness
* Confidence

---

#### 5. Actions NOT Taken

* All MEDIUM / LOW confidence items
* Explicit reasoning (e.g., “possible external dependency”)

---

#### 6. Expected Impact

* Reduced codebase size
* Improved readability
* Lower cognitive load
* Potential risk areas identified

---

### Enforcement Rules

* NO deletion before PRD completion
* PRD must align with assessment table
* Only HIGH confidence items may proceed

---

## Step 3 — Implement HIGH Confidence Removals ONLY

### Rules

* Remove ONLY items with proven deadness
* Do NOT assume
* Do NOT remove public-facing code without explicit proof

---

### Handling MEDIUM Confidence

```java
// TODO [DEAD-CODE-AGENT]:
// Possible dead code — no internal references found.
// May be used externally or via reflection. DO NOT REMOVE without verification.
```

---

### Handling LOW Confidence

* Do nothing
* Document only

---

## Step 4 — Run All Checks

```bash
mvn compile
mvn test
mvn checkstyle:checkstyle
mvn spotbugs:check
mvn pmd:check
```

If any check fails:

* Revert the last removal
* Identify the exact element causing failure

---

## Final Rule

> If you cannot prove it is dead, it is not dead.

Do NOT proceed to Subagent 2 until all checks pass.
