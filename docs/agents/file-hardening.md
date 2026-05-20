# Agent — File Handling & Storage Hardening

**Status:** Final  
**Author:** Roqeeb Olamide Ayorinde  
**Source:** File Handling & Storage Audit Report 2026-05-18 (11 findings)  
**Tier:** 2 — CRITICAL/HIGH block production  
**Migration:** None (MIME persistence uses S3 metadata — no schema change)

---

## How to Invoke This Agent

```
@docs/agents/file-hardening.md fix all
```

Other commands:

| Command | What it does |
|---|---|
| `fix all` | Execute CRITICAL → HIGH → MEDIUM → INFO in order |
| `fix critical` | CRITICAL tier + prod timeout property fix only |
| `fix high` | HIGH tier only |
| `fix medium` | MEDIUM tier only |
| `verify` | Run full verification checklist, no code changes |
| `status` | Show which tiers are complete and which remain |

---

## Agent Instructions — Read Before Starting

You are a senior Spring Boot engineer executing a structured file handling and storage hardening pass on ReviewFlow, a **Spring Boot 4.0.x / Java 21** academic submission and grading platform.

**Rules:**
- CRITICAL must be implemented and verified before any HIGH or MEDIUM work begins.
- **Read every file before touching it.** Do not assume method signatures, property keys, or existing logic.
- All changes are in service/config files — no migration, no entity changes.
- The prod timeout property fix (`clamav.timeout-ms`) must land in the same commit as CRITICAL-1 — a 5-second timeout with fail-closed will cause widespread 503s under any load.

**Files likely touched:**

| File | Path |
|---|---|
| ClamAvScanService | `infrastructure/storage/ClamAvScanService.java` |
| MalwareScanUnavailableException | `infrastructure/storage/MalwareScanUnavailableException.java` (new) |
| GlobalExceptionHandler | `com.reviewflow.shared.exception.GlobalExceptionHandler` |
| ReviewFlowMetrics | `infrastructure/monitoring/ReviewFlowMetrics.java` |
| MessagingService | `messaging/service/MessagingService.java` |
| UserService | `user/service/UserService.java` (comment only — no code change) |
| FileSecurityValidator | `infrastructure/storage/FileSecurityValidator.java` |
| FileSecurityProperties | `infrastructure/storage/FileSecurityProperties.java` |
| S3Service | `infrastructure/storage/S3Service.java` |
| SubmissionService | `submission/service/SubmissionService.java` |
| application.properties | `src/main/resources/application.properties` |
| application-prod.properties | `src/main/resources/application-prod.properties` |

---

## Locked Decisions

| Decision | Choice |
|---|---|
| ClamAV fail-closed | `ERROR` + `TimeoutException` → 503 `MALWARE_SCAN_UNAVAILABLE` when `clamav.enabled=true` |
| Message attachments | Add ClamAV scan — same env-driven posture as submissions |
| Avatar ClamAV | Rely on ImageIO re-encode as compensating control — document explicitly, no code change |
| MIME persistence | Option B — S3 `Content-Type` metadata at upload, `HeadObject` on presign |
| Dead property | Remove `security.file.max-upload-size` — single source of truth |
| Size limits | Align all three in local profile — one number per context |

---

## Execution Protocol for `fix all`

```
STEP 1 — Create fix branch
  git checkout -b fix/file-hardening

STEP 2 — CRITICAL + prod timeout fix (must land together)
  CRITICAL-1: ClamAvScanService fail-closed logic
  CRITICAL-1 companion: fix clamav.timeout → clamav.timeout-ms in application-prod.properties
  New MalwareScanUnavailableException class
  New GlobalExceptionHandler entry
  New ReviewFlowMetrics methods
  Verify CRITICAL checklist before advancing

STEP 3 — HIGH tier
  HIGH-1: MessagingService — add ClamAV scan to attachment path
  HIGH-2: UserService — add compensating control comment (no code change)
  Verify HIGH checklist before advancing

STEP 4 — MEDIUM tier
  MEDIUM-1+2: Upload size limit alignment + remove dead property
  MEDIUM-3: Preview presign — use S3 Content-Type metadata
  MEDIUM-4: Message attachments — add Gate 3 structural validation

STEP 5 — INFO cleanup
  INFO-1: Resolve aws.s3.avatar-url-expiry-minutes (wire or remove)
  INFO-2: Confirm no remaining references to clamav.timeout (without -ms)

STEP 6 — Run full verification checklist
  Report pass/fail per item
```

---

## CRITICAL — Fix Before Production

### CRITICAL-1 · ClamAvScanService — Fail-Closed on ERROR and Timeout

**File:** `infrastructure/storage/ClamAvScanService.java` (around line 81–98)  
**Issue:** `scanAndThrow` only throws on `INFECTED`. `ERROR`, `DISABLED`, and `TimeoutException` all allow uploads to proceed silently. In production (`clamav.enabled=true`), a scanner outage or timeout becomes an open door for malware.

**Current behaviour (do not leave in place):**
```java
public void scanAndThrow(Path filePath, long timeoutMs) {
    ClamAvScanResult result = scan(filePath, timeoutMs);
    if (result == ClamAvScanResult.INFECTED) {
        throw new MalwareDetectedException(...);
    }
    // ERROR, DISABLED, CLEAN — all fall through silently
}
```

**Fix — full environment-aware implementation:**

```java
@Value("${clamav.enabled:false}")
private boolean clamavEnabled;

public void scanAndThrow(Path filePath, long timeoutMs) {
    ClamAvScanResult result;

    try {
        result = scan(filePath, timeoutMs);
    } catch (SocketTimeoutException | InterruptedException e) {
        Thread.currentThread().interrupt();
        handleScanError("ClamAV scan timed out", e, filePath);
        return; // only reached if fail-open (non-prod)
    } catch (Exception e) {
        handleScanError("ClamAV scan threw unexpected exception", e, filePath);
        return;
    }

    switch (result) {
        case INFECTED -> {
            metrics.recordMalwareDetected();
            log.warn("Malware detected in file: {}", filePath.getFileName());
            throw new MalwareDetectedException("File rejected: malware detected");
        }
        case ERROR -> handleScanError(
            "ClamAV returned ERROR for file: " + filePath.getFileName(),
            null, filePath);
        case DISABLED -> {
            if (clamavEnabled) {
                handleScanError(
                    "ClamAV is enabled but scanner reports DISABLED", null, filePath);
            }
            log.debug("ClamAV disabled — skipping scan for {}", filePath.getFileName());
        }
        case CLEAN -> {
            metrics.recordScanClean();
            log.debug("ClamAV scan clean: {}", filePath.getFileName());
        }
    }
}

private void handleScanError(String message, Exception cause, Path filePath) {
    metrics.recordScanError();
    if (clamavEnabled) {
        // Production: fail-closed — scanner unavailable means reject upload
        if (cause != null) {
            log.error("{} — rejecting upload (fail-closed in prod)", message, cause);
        } else {
            log.error("{} — rejecting upload (fail-closed in prod)", message);
        }
        throw new MalwareScanUnavailableException(
            "File upload rejected: virus scanner is unavailable. Please try again.");
    } else {
        // Non-production: fail-open — log warning and allow
        log.warn("{} — allowing upload (fail-open in non-prod)", message);
        if (cause != null) log.warn("Scan error cause: {}", cause.getMessage());
    }
}
```

**New exception class:**
```java
public class MalwareScanUnavailableException extends RuntimeException {
    public MalwareScanUnavailableException(String message) {
        super(message);
    }
}
```

**New handler in `GlobalExceptionHandler`:**
```java
@ExceptionHandler(MalwareScanUnavailableException.class)
public ResponseEntity<ApiResponse<Void>> handleMalwareScanUnavailable(
        MalwareScanUnavailableException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header("Retry-After", "60")
        .body(ApiResponse.error("MALWARE_SCAN_UNAVAILABLE", ex.getMessage()));
}
```

**New metrics — add to `ReviewFlowMetrics` (verify none already exist before adding):**
```java
public void recordScanError() {
    registry.counter("reviewflow.clamav.scan.error").increment();
}

public void recordScanClean() {
    registry.counter("reviewflow.clamav.scan.clean").increment();
}

public void recordMalwareDetected() {
    registry.counter("reviewflow.clamav.malware.detected").increment();
}
```

---

### CRITICAL-1 Companion · Fix Prod Timeout Property Key

**File:** `application-prod.properties`  
**Issue:** Prod sets `clamav.timeout` but the code reads `clamav.timeout-ms`. The prod override does not apply — prod uses the 5000ms default instead of the intended 30000ms. With CRITICAL-1 fixed (fail-closed), a 5-second timeout under any scan load will immediately produce 503s.

**Fix — must land in the same commit as CRITICAL-1:**
```properties
# BEFORE (broken — key name mismatch, falls back to 5000ms default):
clamav.timeout=30000

# AFTER (correct key):
clamav.timeout-ms=${CLAMAV_TIMEOUT_MS:30000}
```

**Verify:**
```
application-prod.properties contains clamav.timeout-ms (with -ms)
No remaining reference to clamav.timeout (without -ms) anywhere in properties files
```

**CRITICAL Verification:**
```
clamav.enabled=true, scanner returns ERROR     → 503 MALWARE_SCAN_UNAVAILABLE
clamav.enabled=true, scanner timeout           → 503 MALWARE_SCAN_UNAVAILABLE
clamav.enabled=true, scanner returns DISABLED  → 503 MALWARE_SCAN_UNAVAILABLE
clamav.enabled=false, scanner returns ERROR    → allow upload, WARN log
clamav.enabled=false, scanner returns DISABLED → allow upload, DEBUG log
clamav.enabled=true, scanner returns INFECTED  → 422 MALWARE_DETECTED (existing — not broken)
clamav.enabled=true, scanner returns CLEAN     → proceed normally
reviewflow.clamav.scan.error incremented on ERROR/timeout
reviewflow.clamav.scan.clean incremented on CLEAN
MalwareScanUnavailableException handler → 503 with Retry-After: 60
application-prod.properties: clamav.timeout-ms=30000 (not clamav.timeout)
```

---

## HIGH — Fix Before Production

### HIGH-1 · MessagingService — Add ClamAV Scan to Attachment Path

**File:** `messaging/service/MessagingService.java`  
**Issue:** `validateMessageAttachment` uses Tika MIME only — no ClamAV. Message attachments are stored in S3 and served via presigned URLs to any conversation participant.

**Fix — mirror the submission scan flow: temp file → FileSecurityValidator → ClamAV → S3 upload:**

```java
// MessagingService.sendMessage() — attachment processing:

// Inject ClamAvScanService:
private final ClamAvScanService clamAvScanService;

@Value("${clamav.timeout-ms:5000}")
private long clamAvTimeoutMs;

// In attachment loop:
for (MultipartFile file : attachments) {
    // Gate 1 + 2 (extension + MIME) — already exists
    fileSecurityValidator.validateMessageAttachment(file);

    // Gate 3 — ClamAV (new, env-driven same as submissions)
    Path tempPath = null;
    try {
        tempPath = Files.createTempFile("msg-attach-", "-scan");
        file.transferTo(tempPath);
        clamAvScanService.scanAndThrow(tempPath, clamAvTimeoutMs);
    } finally {
        if (tempPath != null) {
            Files.deleteIfExists(tempPath); // delete in ALL paths — pass/fail/exception
        }
    }

    // S3 upload — only reached after clean scan
    String key = s3KeyBuilder.messageAttachmentKey(...);
    s3Service.putObject(key, file.getInputStream(), file.getSize());
}
```

**Behaviour:** Identical to submissions — fail-closed in prod, fail-open in local (driven by `clamav.enabled`). No change to non-prod developer experience.

**Verify:**
```
local (clamav.enabled=false): attachment upload proceeds without scanner
prod (clamav.enabled=true): clean file → proceeds; infected file → 422; scanner down → 503
Temp file deleted in ALL paths (clean, infected, exception, scanner down)
S3 upload only happens after clean scan result
```

---

### HIGH-2 · UserService — Document Avatar Compensating Control

**File:** `user/service/UserService.java` (avatar upload method)  
**Decision:** ClamAV intentionally NOT added to avatar path. ImageIO re-encode is the compensating control.  
**Required action:** Add the following comment to the avatar upload method. This is not optional — it is the documented compensating control record. Agent 6 must verify it is present.

```java
// SECURITY NOTE: ClamAV is intentionally NOT applied to avatar uploads.
// Compensating controls in place:
//   1. Extension allowlist: JPEG, PNG, WebP only (FileSecurityValidator)
//   2. Tika MIME verification: structural MIME check (FileSecurityValidator)
//   3. ImageIO re-encode: forces full decode → re-encode, destroying
//      any embedded payload that is not valid image data (PRD-02)
// This provides equivalent protection for image-only files without
// requiring ClamAV on a low-risk path. If non-image types are ever
// added to avatar uploads, ClamAV must be added.
// Reviewed: 2026-05-18 | Accepted by: Roqeeb Olamide Ayorinde
```

**Verify:**
```
Avatar upload path: ClamAV NOT added (compensating control accepted)
Comment present in UserService avatar upload method
Comment mentions: JPEG/PNG/WebP restriction, ImageIO re-encode, review date
```

---

## MEDIUM — Fix in This PR

### MEDIUM-1 + MEDIUM-2 · Upload Size Limit Alignment

**Files:** `application.properties`, `FileSecurityProperties.java`  
**Issue:** Three settings in the local profile disagree (25MB / 50MB / 100MB). `security.file.max-upload-size` is bound to `FileSecurityProperties` but never actually read at enforcement time — dead property causing confusion.

**Fix — establish one source of truth per upload type:**

```properties
# application.properties — full aligned config:

# ─── Tomcat multipart ceiling (hard outer limit — affects ALL multipart endpoints) ──
# Spring never sees the bytes if exceeded
spring.servlet.multipart.max-file-size=${MAX_FILE_SIZE:25MB}
spring.servlet.multipart.max-request-size=${MAX_REQUEST_SIZE:130MB}
# 130MB = 5 files × 25MB + overhead (messaging attachments allow 5 per message)

# ─── Per-type service-layer caps (enforced by FileSecurityValidator) ─────────────
file.validation.submission.max-size-bytes=${SUBMISSION_MAX_BYTES:104857600}       # 100MB
file.validation.material.max-size-bytes=${MATERIAL_MAX_BYTES:524288000}           # 500MB (video)
file.validation.message-attachment.max-size-bytes=${MESSAGE_MAX_BYTES:26214400}   # 25MB
file.validation.avatar.max-size-bytes=${AVATAR_MAX_BYTES:5242880}                 # 5MB

# ─── REMOVE this line entirely ───────────────────────────────────────────────────
# security.file.max-upload-size=50MB    ← DELETE — never read, causes confusion
```

**In `FileSecurityProperties.java`:** Remove the `maxUploadSize` field entirely. Replace any references with the per-type properties above bound individually.

**The rule:** Tomcat ceiling is the hard outer limit. Per-type limits are enforced at service layer inside `FileSecurityValidator`. Tomcat must never be lower than the highest per-type limit that matters.

**Verify:**
```
POST submission with 101MB file  → Tomcat allows (< 130MB) but FileSecurityValidator rejects (> 100MB)
POST message attachment with 26MB → validator rejects (> 25MB)
POST avatar with 6MB file         → validator rejects (> 5MB)
security.file.max-upload-size not present anywhere in application.properties
FileSecurityProperties.maxUploadSize field removed
```

---

### MEDIUM-3 · Preview Presign — Use S3 Content-Type Metadata

**Files:** `S3Service.java`, `SubmissionService.java`  
**Issue:** Preview presign sets `responseContentType` from filename extension via `MimeTypeResolver`. If the stored filename has the wrong extension, the browser receives the wrong `Content-Type`.

#### Step 1 — Set correct `Content-Type` on S3 PUT at upload time

**Read `S3Service.putObject()` signature before modifying — add `detectedMimeType` parameter:**

```java
// S3Service.putObject() — updated signature:
public String putObject(String key, InputStream inputStream,
                        long contentLength, String detectedMimeType) {
    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .contentType(detectedMimeType) // detected by Tika at validation time
        .contentLength(contentLength)
        .build();
    s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
    return key;
}
```

**Pass detected MIME from `SubmissionService.upload()`:**

```java
// SubmissionService.upload():
// FileSecurityValidator.validate() already runs Tika internally.
// If validate() does not return the detected type, add:
String detectedMime = fileSecurityValidator.detectMimeType(file);
// OR refactor validate() to return ValidationResult { boolean valid, String detectedMimeType }

// Pass to S3:
s3Service.putObject(key, file.getInputStream(), file.getSize(), detectedMime);
```

> If `FileSecurityValidator.validate()` already runs Tika internally but does not expose the detected type, add a `detectMimeType(MultipartFile)` method that returns the Tika-detected string. Read the class before deciding.

#### Step 2 — Add `headObject()` to `S3Service`

```java
public HeadObjectResponse headObject(String key) {
    return s3Client.headObject(HeadObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .build());
}
```

#### Step 3 — Use HeadObject on presign to get stored Content-Type

```java
// SubmissionService.generatePresignedPreviewUrl():

// BEFORE — extension-based MIME:
String contentType = mimeTypeResolver.fromExtension(submission.getFileName());

// AFTER — stored S3 Content-Type:
HeadObjectResponse head = s3Service.headObject(submission.getS3Key());
String contentType = head.contentType();
// Defensive fallback — if HeadObject returns null, fall back to extension:
if (contentType == null || contentType.isBlank()) {
    contentType = mimeTypeResolver.fromExtension(submission.getFileName());
    log.warn("S3 HeadObject returned no Content-Type for key={}, " +
             "falling back to extension", submission.getS3Key());
}

String presignedUrl = s3Service.generatePresignedPreviewUrl(
    submission.getS3Key(), contentType);
```

> `HeadObject` is a lightweight S3 call (metadata only, no data transfer) — adds ~20–50ms to presign. This is acceptable on a presign path called once per preview open.

**Verify:**
```
Upload submission.pdf → S3 object Content-Type = application/pdf
Preview presign → responseContentType = application/pdf (not from extension)
Upload file.pdf renamed to file.docx → Content-Type still = application/pdf (from Tika, not extension)
Browser receives correct Content-Type for inline preview
HeadObject returns null → falls back to extension resolver, WARN log emitted
```

---

### MEDIUM-4 · Message Attachments — Add Gate 3 Structural Validation

**File:** `infrastructure/storage/FileSecurityValidator.java` (around line 294–318)  
**Issue:** `validateMessageAttachment` checks extension and Tika MIME (Gates 1 and 2) but skips Gate 3 structural validation that submissions receive. Polyglot PDFs and ZIP files can pass MIME inspection while failing structural checks.

**Read `validateStructure()` first — verify the method exists on the submission path and confirm its signature before calling it.**

```java
// FileSecurityValidator.validateMessageAttachment() — updated:

// Extensions requiring structural validation:
private static final Set<String> STRUCTURALLY_VALIDATED_EXTENSIONS = Set.of(
    ".pdf", ".zip", ".docx", ".doc", ".pptx", ".ppt", ".xlsx", ".xls"
);

public void validateMessageAttachment(MultipartFile file) {
    // Gate 1: Extension allowlist (already exists)
    validateExtension(file, MESSAGE_ATTACHMENT_ALLOWED_EXTENSIONS);

    // Gate 2: Tika MIME verification (already exists)
    String detectedMime = validateMimeType(file, MESSAGE_ATTACHMENT_ALLOWED_MIMES);

    // Gate 3: Structural validation (NEW — mirrors submission path)
    String extension = getExtension(file.getOriginalFilename()).toLowerCase();
    if (STRUCTURALLY_VALIDATED_EXTENSIONS.contains(extension)) {
        validateStructure(file, extension, detectedMime);
    }
    // Images (JPEG, PNG, WebP, GIF) — Gate 3 skipped.
    // Tika structural MIME check is sufficient for image types in messages.
}
```

**Verify:**
```
Message attachment — valid PDF               → passes all 3 gates
Message attachment — polyglot PDF            → Gate 3 rejects
Message attachment — JPEG                    → passes Gates 1+2, Gate 3 skipped
Message attachment — ZIP with valid structure → passes; ZIP bomb → Gate 3 rejects
Existing submission structural validation     → unchanged
```

---

## INFO — Same PR

### INFO-1 · Resolve Avatar Presign Property

**File:** `application.properties`  
**Property:** `aws.s3.avatar-url-expiry-minutes=60`

Read `UserService` avatar presign path before deciding:

- If avatars use a dedicated presign method with its own TTL and 60 minutes is appropriate (avatars are low sensitivity, longer TTL reduces presign load) → **wire the property** into `UserService.generateAvatarPresignedUrl()`.
- If avatars use the same 15-minute global presign TTL as everything else → **remove the property** entirely.

Whichever is chosen, there must be no dangling unread property after this PR.

### INFO-2 · Confirm No Remaining `clamav.timeout` References

```
Grep all properties files for clamav.timeout (without -ms)
Result must be zero matches
```

---

## Verification Checklist

Run in full before opening the PR.

### CRITICAL
```
[ ] ClamAV INFECTED → 422 MALWARE_DETECTED (existing — verify not broken)
[ ] ClamAV ERROR, clamav.enabled=true → 503 MALWARE_SCAN_UNAVAILABLE
[ ] ClamAV timeout, clamav.enabled=true → 503 MALWARE_SCAN_UNAVAILABLE
[ ] ClamAV DISABLED, clamav.enabled=true → 503 MALWARE_SCAN_UNAVAILABLE
[ ] ClamAV ERROR, clamav.enabled=false → allow + WARN log
[ ] ClamAV DISABLED, clamav.enabled=false → allow + DEBUG log
[ ] reviewflow.clamav.scan.error incremented on ERROR/timeout
[ ] reviewflow.clamav.scan.clean incremented on CLEAN
[ ] reviewflow.clamav.malware.detected incremented on INFECTED
[ ] MalwareScanUnavailableException handler → 503 with Retry-After: 60
[ ] application-prod.properties: clamav.timeout-ms=30000 (not clamav.timeout)
```

### HIGH
```
[ ] Message attachment upload: ClamAV called when clamav.enabled=true
[ ] Message attachment upload: temp file deleted in all paths (pass/fail/exception)
[ ] Message attachment: infected file → 422 MALWARE_DETECTED
[ ] Message attachment: scanner down (prod) → 503 MALWARE_SCAN_UNAVAILABLE
[ ] Avatar upload path: ClamAV NOT added (compensating control accepted)
[ ] UserService avatar upload method: compensating control comment present
[ ] Comment mentions: JPEG/PNG/WebP restriction, ImageIO re-encode, review date
```

### MEDIUM
```
[ ] security.file.max-upload-size property removed from application.properties
[ ] FileSecurityProperties.maxUploadSize field removed
[ ] Per-type size properties present: submission, material, message-attachment, avatar
[ ] Tomcat multipart ceiling (130MB) consistent with per-type limits
[ ] SubmissionService.upload(): detected MIME passed to S3 putObject as Content-Type
[ ] S3Service.putObject(): Content-Type parameter added
[ ] S3Service.headObject(): method added
[ ] Preview presign: uses HeadObject Content-Type (not extension-based resolver)
[ ] Preview presign: falls back to extension if HeadObject returns null/blank
[ ] Message attachment Gate 3: structural validation applied to PDF/ZIP/Office types
[ ] Message attachment Gate 3: NOT applied to image types (JPEG, PNG, WebP, GIF)
[ ] Existing submission structural validation: unchanged
```

### INFO
```
[ ] aws.s3.avatar-url-expiry-minutes: wired to UserService OR removed — no dangling property
[ ] No remaining references to clamav.timeout (without -ms) in any properties file
```

### Regression
```
[ ] Submission upload still works end-to-end (clean file, prod profile)
[ ] Submission upload: infected file still returns 422 (not 503)
[ ] Preview URL: existing submissions get correct Content-Type (HeadObject fallback tested)
[ ] Avatar upload: EXIF strip still applied (PRD-02 re-encode unchanged)
[ ] Local dev (clamav.enabled=false): all upload paths work without scanner running
[ ] Postman: no new failures on submission, avatar, or messaging attachment endpoints
```

---

## Summary of All Changed Files

| File | Change |
|---|---|
| `ClamAvScanService.java` | Fail-closed on `ERROR`/`DISABLED`/timeout when `clamav.enabled=true` |
| `MalwareScanUnavailableException.java` | New exception class |
| `GlobalExceptionHandler.java` | Add handler → 503 with `Retry-After: 60` |
| `ReviewFlowMetrics.java` | Add `recordScanError()`, `recordScanClean()`, `recordMalwareDetected()` |
| `MessagingService.java` | Add ClamAV scan before S3 upload on attachment path |
| `UserService.java` | Add compensating control comment (no code change) |
| `FileSecurityValidator.java` | Add Gate 3 structural validation to `validateMessageAttachment()` |
| `FileSecurityProperties.java` | Remove dead `maxUploadSize` field |
| `S3Service.java` | Add `Content-Type` param to `putObject()`, add `headObject()` method |
| `SubmissionService.java` | Pass detected MIME to `putObject()`, use `headObject()` for preview presign |
| `application.properties` | Remove dead property, add per-type size limits, align Tomcat ceiling |
| `application-prod.properties` | Fix `clamav.timeout` → `clamav.timeout-ms` |
