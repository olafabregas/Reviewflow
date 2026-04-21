---
applyTo: "**/*.java"
---

# Subagent 6: Error Handling Cleanup

> Run this SIXTH — after types are strong. Weak types often mask bad error handling.
> A clean type pass makes error boundaries much easier to reason about.

## Objective

Find all error handling code in the ReviewFlow backend.
Remove anything that silently swallows errors, hides failures, or falls back
to defaults that mask real problems.
Keep error handling that serves a real purpose: recovery, logging,
cleanup, or user-facing error reporting.

---

## Step 1 — Inspect

Scan the backend for the following error handling anti-patterns:

### 1a. Empty or Near-Empty Catch Blocks
```java
// BAD — error silently swallowed
try {
    sendEmail(user);
} catch (Exception e) {
    // do nothing
}

// BAD — comment instead of handling
try {
    parseConfig();
} catch (Exception e) {
    // this shouldn't happen
}
```

### 1b. Silent Logging Without Re-throw
```java
// BAD — logs the error but returns as if nothing happened
try {
    submission.validate();
} catch (ValidationException e) {
    log.error("Validation failed", e);
    // caller receives null/empty result with no indication of failure
    return null;
}
```

### 1c. Swallowing Exceptions with Default Fallbacks
```java
// BAD — caller never knows the lookup failed
try {
    return userRepository.findById(id).orElseThrow();
} catch (Exception e) {
    return new User(); // empty user silently returned
}
```

### 1d. Overly Broad Exception Catching
```java
// BAD — catches InterruptedException, OutOfMemoryError, etc.
catch (Exception e) { ... }
catch (Throwable t) { ... }

// GOOD — catch what you can actually handle
catch (ResourceNotFoundException e) { ... }
catch (IOException e) { ... }
```

### 1e. `@SneakyThrows` (Lombok) Used to Hide Checked Exceptions
`@SneakyThrows` has legitimate uses but is often used to avoid
thinking about checked exceptions. Flag every use and verify intent.

### 1f. Exception Translation Without Logging
```java
// BAD — original exception context is lost
catch (SQLException e) {
    throw new RuntimeException("DB error"); // stack trace from SQL is gone
}

// GOOD — preserve cause
catch (SQLException e) {
    throw new DatabaseException("Failed to fetch submission", e);
}
```

### 1g. WebSocket / Async Error Handling Gaps
ReviewFlow uses WebSockets. Check:
- `@MessageMapping` methods — do they handle errors or let them propagate silently?
- `@Async` methods — unchecked exceptions in async methods are silently discarded
  unless an `AsyncUncaughtExceptionHandler` is configured
- Check if `AsyncUncaughtExceptionHandler` is registered in the project

---

## Step 2 — Write Critical Assessment

```
| File | Pattern | Risk | Proposed Fix | Confidence |
|------|---------|------|--------------|------------|
| EmailService.java:78 | Empty catch on SendFailedException | HIGH — email failures invisible to system | Log + throw or publish failure event | HIGH |
| SubmissionService.java:122 | Returns null on exception | HIGH — caller assumes success | Throw domain exception instead | HIGH |
| AsyncNotificationService.java | No AsyncUncaughtExceptionHandler | MEDIUM — async failures lost silently | Register handler in @Configuration | HIGH |
```

---

## Step 3 — Implement HIGH Confidence Fixes Only

**Preferred patterns for ReviewFlow:**

```java
// Pattern 1: Log and rethrow as domain exception
catch (IOException e) {
    log.error("Failed to store submission file for submissionId={}", id, e);
    throw new SubmissionStorageException("File storage failed", e);
}

// Pattern 2: Log and propagate for async methods
@Bean
public AsyncUncaughtExceptionHandler asyncUncaughtExceptionHandler() {
    return (ex, method, params) ->
        log.error("Async method {} failed with params {}", method.getName(), params, ex);
}

// Pattern 3: Legitimate recovery with explicit comment
catch (CacheException e) {
    // Intentional: cache miss is non-critical — fall through to DB lookup
    log.warn("Cache unavailable, falling back to database", e);
}
```

**Never remove error handling that:**
- Is at a true system boundary (HTTP layer, WebSocket endpoint, scheduled job entry point)
- Performs genuine cleanup (close connection, release lock, rollback transaction)
- Provides user-facing error reporting with a meaningful message
- Is part of the global exception handler (`@ControllerAdvice`)

---

## Step 4 — Run All Checks

```bash
mvn compile
mvn test
mvn checkstyle:checkstyle
mvn spotbugs:check
mvn pmd:check
```

Do NOT proceed to Subagent 7 until all checks pass.
