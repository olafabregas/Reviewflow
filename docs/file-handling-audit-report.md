# File Handling & Storage Audit Report

**Date:** 2026-05-18  
**Scope:** Full scan (15 targets) per `@file-handling-audit`  
**Tier:** 2 — CRITICAL/HIGH block production until resolved or accepted with compensating controls  
**Flyway:** V34

---

## Executive Summary

| Severity | Count |
|----------|------:|
| CRITICAL | 1 |
| HIGH | 2 |
| MEDIUM | 4 |
| INFO | 4 |

**Top risks**

1. **ClamAV scan errors/timeouts allow uploads to proceed** — `scanAndThrow` only rejects `INFECTED`; `ERROR`, `DISABLED`, and timeouts do not block storage in production.
2. **Message attachments never scanned** — PRD-18 uploads use MIME allowlist only; no ClamAV on the messaging path.
3. **Dev size-limit drift** — Spring multipart (25MB), unused `security.file.max-upload-size` (50MB), and service cap (100MB) disagree in the default profile.

**Strengths**

- Production profile forces `clamav.enabled=true` unconditionally.
- Submission keys use `S3KeyBuilder` with hashed IDs and monotonic `v{n}` versions.
- Upload pipeline runs Tika MIME sniff + structural validation (PDF/archives/Office) before S3 write.
- ClamAV runs **synchronously before** storage on submissions (no async preview gap on that path).
- Pre-signed URLs are **GET-only** with 15-minute default TTL.
- `LocalFileStorageService` normalizes paths and blocks traversal.
- Download sets `Content-Disposition: attachment`; `FileUploadTimeoutException` maps to **408**.

---

## Findings

### CRITICAL

#### [RULE-FILE09 | CRITICAL] ClamAvScanService.java:81-98

**Issue:** ClamAV failures and timeouts fail open — uploads proceed when scan returns `ERROR` or times out.

**Context:** In production (`clamav.enabled=true`), a down scanner, network fault, or timeout allows malware through the only malware gate on submissions. Rule FILE09 requires fail-closed on scan error in prod.

**Snippet:**

```java
public void scanAndThrow(Path filePath, long timeoutMs) {
  // ...
  if (result == ClamAvScanResult.INFECTED) {
    throw new MalwareDetectedException(...);
  }
  // ERROR, DISABLED, CLEAN all continue without throw
}
```

**Fix:** In prod profile (or when `clamav.enabled=true`), treat `ERROR` and `TimeoutException` as blocking — throw `ValidationException` / `503` with `MALWARE_SCAN_UNAVAILABLE`. Keep `DISABLED` allow only for non-prod profiles.

---

### HIGH

#### [RULE-FILE09 | HIGH] MessagingService.java (attachment upload path)

**Issue:** Message attachments are not scanned by ClamAV.

**Context:** Attachments are stored in S3 and exposed via `generatePresignedDownloadUrl`. `validateMessageAttachment` uses Tika MIME checks only. No `ClamAvScanService` call in `messaging/` package.

**Fix:** Mirror submission flow: temp file → `fileSecurityValidator` → `clamAvScanService.scanAndThrow` → S3 upload. Document in PRD-18 if intentional exception requires sign-off.

---

#### [RULE-FILE09 | HIGH] UserService.java (avatar upload path)

**Issue:** Avatar uploads skip ClamAV.

**Context:** Avatars use `validateWithConfig` (Tika MIME) but never `ClamAvScanService`. Public-ish avatar URLs (`aws.s3.avatar-url-expiry-minutes` defined but unused) increase impact if malicious image/polyglot is stored.

**Fix:** Add ClamAV scan after Tika validation for avatar bytes, or document compensating control (CDN sanitizer, strict image decode).

---

### MEDIUM

#### [RULE-FILE02 | MEDIUM] application.properties (FILE UPLOAD + FILE SECURITY sections)

**Issue:** Upload size limits disagree within the default (local) profile.

**Context:**

| Setting | Value |
|---------|------:|
| `spring.servlet.multipart.max-file-size` | 25MB |
| `security.file.max-upload-size` | 50MB |
| `file.validation.submission.max-size-bytes` | 100MB |

`SubmissionService` enforces 100MB but Tomcat rejects >25MB first — confusing ops tuning, not a prod bypass. Prod profile aligns multipart (100MB) with service cap.

**Fix:** Align all three in `application.properties` (e.g. 25MB everywhere for local) or document env overrides. Wire `FileSecurityProperties.maxUploadSize` into validation or remove dead property.

---

#### [RULE-FILE02 | MEDIUM] FileSecurityProperties.java:14

**Issue:** `security.file.max-upload-size` is never read by validators or services.

**Context:** Property is bound but unused; submission size enforced only via `file.validation.submission.max-size-bytes`.

**Fix:** Enforce `maxUploadSize` in `FileSecurityValidator` or `SubmissionService.upload` as a single source of truth.

---

#### [RULE-FILE03 | MEDIUM] SubmissionService.java:525-536

**Issue:** Preview presign uses extension-only MIME from `MimeTypeResolver`, not stored/detected bytes.

**Context:** Upload path uses Tika + structural gates. Preview path sets S3 `responseContentType` from filename extension only. A DB filename/extension mismatch could serve wrong `Content-Type` on inline preview (lower risk post-upload validation).

**Fix:** Persist detected MIME on `Submission` at upload time; use that for `generatePresignedPreviewUrl`. Optionally re-sniff from S3 head metadata.

---

#### [RULE-FILE08 | MEDIUM] FileSecurityValidator.java:294-318

**Issue:** Message attachments skip Gate 3 structural validation (PDF/archive/Office).

**Context:** `validateMessageAttachment` checks extension + Tika MIME only. Polyglot PDFs/archives may pass MIME while failing structural checks that submissions receive.

**Fix:** Reuse `validateFromPath` / structural branch for attachment extensions, or narrow allowlist to types that do not need structure checks.

---

### INFO

#### [RULE-FILE01 | INFO] application.properties:165

**Issue:** `clamav.enabled=${CLAMAV_ENABLED:false}` in base profile.

**Context:** Expected for local dev. **Not a prod violation** — `application-prod.properties` sets `clamav.enabled=true` unconditionally (line 119).

---

#### [RULE-FILE05 | INFO] application.properties:189

**Issue:** `aws.s3.avatar-url-expiry-minutes=60` is defined but no Java reference uses it; all presign paths use `aws.s3.presigned-url-expiry-minutes` (15 min).

**Fix:** Use separate expiry in avatar presign helper or remove unused property.

---

#### [RULE-FILE01 | INFO] application-prod.properties:122

**Issue:** Prod sets `clamav.timeout` but code reads `clamav.timeout-ms` — prod override may not apply (defaults to 5000ms).

**Fix:** Rename to `clamav.timeout-ms=${CLAMAV_TIMEOUT_MS:30000}` in prod file.

---

#### [Law 3 | INFO] SubmissionService.java:102

**Issue:** Feature service injects `S3Service` directly instead of routing through a storage façade only.

**Context:** Acceptable pattern today but couples submission domain to infra S3 API (preview size, presign, delete-on-timeout).

---

## Clean Targets

| # | Target | Notes |
|---|--------|-------|
| 1 | `application.properties` | ClamAV off locally OK; presign TTL 15m |
| 2 | `application-prod.properties` | `clamav.enabled=true`; multipart 100/105MB |
| 4 | `S3KeyBuilder.java` | Sanitization, hashed paths, `v{n}` |
| 5 | `S3Service.java` | GET presign only; 15m TTL; inline for preview |
| 6 | `S3FileStorageService.java` | Server-side PUT; keys from caller |
| 7 | `LocalFileStorageService.java` | `normalize()` + `startsWith(basePath)` |
| 8 | `ClamAvScanService.java` | Metrics wired; sync scan API exists (fail-open logic flagged above) |
| 9 | `StorageService.java` | Interface only |
| 10 | `SubmissionService.java` | Keys, versioning, sync scan-before-upload (except fail-open) |
| 11 | `SubmissionController.java` | Download `Content-Disposition: attachment` |
| 12 | Grep `generatePresigned` | All usages are `presignGetObject` |
| 13 | Grep `MultipartFile` (submission) | Validated via `FileSecurityValidator` + Tika |
| 14 | `GlobalExceptionHandler.java` | `FileUploadTimeoutException` → 408 |
| 15 | Grep `clamav` | Prod profile forces enabled |

**Not flagged (by design):** `html` allowed for submission upload but not previewable; download uses `attachment`. Dev ≠ prod multipart caps. `PUT` presign not used.

---

## Rule Coverage

| Rule | Status | Hits |
|------|--------|-----:|
| FILE01 | PASS (prod) / INFO (local) | 2 |
| FILE02 | FAIL | 2 |
| FILE03 | PASS (upload) / PARTIAL (preview) | 1 |
| FILE04 | PASS | 0 |
| FILE05 | PASS / INFO | 1 |
| FILE06 | PASS | 0 |
| FILE07 | PASS | 0 |
| FILE08 | PARTIAL | 1 |
| FILE09 | FAIL | 3 |
| FILE10 | PASS | 0 |
| FILE11 | PASS | 0 |
| FILE12 | PASS | 0 |

---

## Recommended Action Plan

1. **Block prod:** Make ClamAV `ERROR`/timeout fail-closed when `clamav.enabled=true` (`ClamAvScanService.scanAndThrow`).
2. **High:** Add ClamAV to messaging attachments (and avatars if no compensating control).
3. **Align limits:** Single submission max-size source; enforce or remove `FileSecurityProperties.maxUploadSize`.
4. **Harden preview:** Store detected MIME at upload; use for presign `contentType`.
5. **Messaging:** Add structural validation for PDF/zip on attachments.
6. **Ops:** Fix `clamav.timeout-ms` in prod properties; wire or drop avatar presign TTL property.

---

## Scan Metadata

| Metric | Value |
|--------|------:|
| Targets scanned | 15 |
| Java/storage files read | 12 |
| Properties files read | 3 |
| Total findings | 11 |

**Cross-audit:** `@security-auth-audit` RULE-S13 (ClamAV prod), `@async-job-executor-audit` ASYNC05 (`@Async` on `scanAsync` — submissions use sync `scanAndThrow`), `@transaction-boundary-audit` (S3 after DB commit on upload — acceptable; scan is pre-storage).
