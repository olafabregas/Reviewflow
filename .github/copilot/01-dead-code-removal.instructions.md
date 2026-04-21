---
applyTo: "**/*.java"
---

# Subagent 1: Dead Code Removal

> Run this FIRST. Removing dead code before any other track reduces noise and
> prevents other agents from wasting effort analysing code that will be deleted.

## Objective

Find and remove all confirmed-dead Java code in the ReviewFlow backend.
Confirmed-dead means: unreachable, unreferenced, and not required by any
framework convention, dynamic lookup, or external configuration.

---

## Step 1 — Inspect

Scan the entire backend module for:

1. **Unused private methods** — methods declared `private` with no callers in the same class
2. **Unused private fields** — fields declared `private` that are never read or written outside their declaration
3. **Unreferenced classes** — classes with no imports anywhere in the codebase and no framework annotation that implies dynamic registration (e.g. `@Component`, `@Service`, `@Repository`, `@RestController`, `@Configuration`, `@EventListener`)
4. **Unreachable code blocks** — code after `return`, `throw`, or `break` statements
5. **Commented-out code blocks** — large blocks of code wrapped in comments
6. **Unused imports** — imports not referenced in the file body
7. **Empty catch blocks** — `catch` blocks with no body or only a comment
8. **Unused local variables** — variables declared but never read

**Spring Boot / Framework exceptions — do NOT flag these as dead:**
- Any class or method annotated with: `@Bean`, `@Component`, `@Service`, `@Repository`,
  `@RestController`, `@Controller`, `@Configuration`, `@Scheduled`, `@EventListener`,
  `@KafkaListener`, `@RabbitListener`, `@MessageMapping`
- Any method matching standard lifecycle contracts: `init()`, `destroy()`, `afterPropertiesSet()`,
  `configure()`, `run()`, `execute()`
- Any class referenced in `application.yml`, `application.properties`, or any Flyway migration file
- Any class used via reflection, `Class.forName()`, or Spring's `@ConditionalOn*` annotations
- Serialization classes implementing `Serializable` — their fields may be required by the JVM

**Flyway migration files — never touch these.**

---

## Step 2 — Write Critical Assessment

Before removing anything, output a table:

```
| File | Element | Type | Confidence | Reason |
|------|---------|------|------------|--------|
| UserService.java | sendWelcomeEmail() | Unused private method | HIGH | No callers found, no framework annotation |
| AuthController.java | legacyLogin() | Commented-out block | HIGH | 47 lines of commented code, no active reference |
```

---

## Step 3 — Implement HIGH Confidence Removals Only

Remove only items marked HIGH confidence.

For MEDIUM items: add a comment above the element:
```java
// TODO [DEAD-CODE-AGENT]: Possible dead code — verify before removing.
// No callers found but could be referenced dynamically. Confidence: MEDIUM.
```

For LOW items: document in the assessment and stop.

---

## Step 4 — Run All Checks

```bash
mvn compile
mvn test
mvn checkstyle:checkstyle
mvn spotbugs:check
mvn pmd:check
```

If any check fails, revert the last removal and report which element caused the failure.
Do NOT proceed to Subagent 2 until all checks pass.
