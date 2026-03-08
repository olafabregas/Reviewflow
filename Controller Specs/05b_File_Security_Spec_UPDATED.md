# ReviewFlow — File Security Specification
> Applies to: `POST /submissions` — `SubmissionController.java`
> Validation class: `FileSecurityValidator.java`
> Library dependencies: Apache Tika (MIME detection), OpenPDF / iText (PDF structural validation), Apache Commons Compress (archive structural validation)

---

## Overview

Every file submitted goes through a **three-gate validation pipeline** executed synchronously at upload time, before anything is written to storage. If any gate fails the request is rejected immediately with a `400 Bad Request` and the file is discarded — nothing is persisted.

**Scope:** Top-level file only. Recursive scanning inside archives is explicitly out of scope for performance reasons. At scale (100k+ submissions), synchronous deep scanning would saturate the thread pool and stall the HTTP layer. The denylist at Gate 1 and MIME detection at Gate 2 provide the primary security guarantee. Structural validation at Gate 3 confirms the file is parseable as the format it claims to be.

```
Incoming file
      │
      ▼
┌─────────────────────────────────────┐
│ GATE 1 — Denylist + Allowlist       │  < 1ms — pure string check
│ Is the extension explicitly blocked? │
│ Is the extension in the allowlist?   │
└─────────────────────────────────────┘
      │ PASS
      ▼
┌─────────────────────────────────────┐
│ GATE 2 — MIME Type Detection        │  ~5–20ms — Tika reads file bytes
│ Does the real MIME type match        │
│ what the extension claims to be?     │
└─────────────────────────────────────┘
      │ PASS
      ▼
┌─────────────────────────────────────┐
│ GATE 3 — Structural Validation      │  ~10–50ms — format-specific parsing
│ Can the file be opened and parsed    │  (binary formats only)
│ as the format it claims to be?       │
└─────────────────────────────────────┘
      │ PASS
      ▼
   Write to storage
```

---

## Gate 1 — Denylist and Allowlist

Gate 1 runs two checks in order. The denylist is checked first — a match here is a hard rejection regardless of anything else, even if the extension is also in the allowlist.

### 1A — Explicit Denylist (hard block)

These extensions are **always rejected** regardless of context, course, or role. There is no configuration that can override this list.

```java
private static final Set<String> BLOCKED_EXTENSIONS = Set.of(

    // Windows executables & installers
    "exe", "msi", "com", "scr", "pif",

    // Windows scripts
    "bat", "cmd", "ps1", "vbs", "wsf", "hta",

    // Unix scripts
    "sh", "bash", "zsh", "csh", "ksh",

    // Compiled / binary / native libraries
    "dll", "so", "dylib", "sys", "drv", "obj",

    // Java executables — relevant given the Spring Boot stack
    "jar", "war", "ear", "class",

    // macOS executables & installers
    "app", "dmg", "pkg", "command",

    // Mobile
    "apk", "ipa", "xap",

    // Macro-enabled Office formats — blocked entirely at extension level
    "xlsm", "xltm", "xlam", "xls",
    "docm", "dotm", "doc",
    "pptm", "potm", "ppam", "pps", "ppt",

    // Dangerous Windows file types
    "lnk",    // Windows shortcut — can point to arbitrary executables
    "reg",    // Windows registry modification file
    "inf",    // Device setup / autorun file
    "url",    // Internet shortcut

    // Disk & binary images
    "iso", "img", "bin", "raw",

    // Other
    "torrent",
    "cab"     // Windows cabinet archive — can contain installers
);
```

> **Why block legacy Office formats (xls, doc, ppt)?**
> The legacy binary Office formats (pre-2007) have a higher macro and exploit surface than their modern OOXML counterparts. Students should be submitting `.xlsx`, `.docx`, `.pptx`. If they cannot export to the modern format, they can export to `.pdf`.

### 1B — Allowlist

If the extension passes the denylist check, it must also be present in the allowlist. Anything not explicitly allowed is rejected.

```java
private static final Set<String> ALLOWED_EXTENSIONS = Set.of(

    // Archives (preferred submission format)
    "zip", "rar", "gz", "tar", "7z",

    // Documents
    "pdf", "docx", "xlsx", "pptx", "txt",

    // Data & Config
    "csv", "json", "xml", "yaml", "yml", "toml", "sql",

    // Markup & Web
    "html", "css", "md",

    // JVM Languages
    "java", "kt", "scala",

    // Systems & Low-level
    "c", "cpp", "cs", "go", "rs",

    // Scripting
    "py", "rb", "php", "swift", "js", "ts", "r",

    // Notebooks
    "ipynb"
);
```

### Gate 1 Error Responses

| Condition | Error Code | Message |
|-----------|------------|---------|
| Extension is on the denylist | `FILE_TYPE_BLOCKED` | `"File type .exe is not permitted on this platform"` |
| Extension not on allowlist | `INVALID_FILE_TYPE` | `"File type .xyz is not allowed. See documentation for accepted formats"` |

> `FILE_TYPE_BLOCKED` and `INVALID_FILE_TYPE` are intentionally different error codes so security logs can distinguish a student submitting an unsupported format from a deliberate attempt to upload a blocked executable.

### Gate 1 Checklist

- [ ] Denylist check runs before allowlist check
- [ ] Denylist match returns `400 FILE_TYPE_BLOCKED` immediately — no further processing
- [ ] Extension comparison is case-insensitive (`Project.ZIP` = `project.zip`)
- [ ] Extension is extracted from the original filename — not from `Content-Type` header
- [ ] Files with no extension are rejected with `INVALID_FILE_TYPE`
- [ ] Files with multiple extensions (e.g. `project.java.exe`) use the last segment only — `exe` — and are caught by the denylist
- [ ] `FILE_TYPE_BLOCKED` attempts are logged at `WARN` level with `userId`, `fileName`, `ipAddress`, and `timestamp`

---

## Gate 2 — MIME Type Detection (Apache Tika)

Gate 2 reads the actual bytes of the file using Apache Tika and detects the real MIME type, independent of the filename or the client-supplied `Content-Type` header. This catches renamed files — a `.exe` renamed to `.zip` has ZIP magic bytes prepended by the attacker but Tika will detect the real content.

### How Tika works

Every binary file format has a **magic number** — a fixed byte sequence at the start of the file that identifies the format:

```
ZIP       50 4B 03 04       →  application/zip
PDF       25 50 44 46 2D    →  application/pdf
RAR       52 61 72 21 1A 07 →  application/vnd.rar
7Z        37 7A BC AF 27 1C →  application/x-7z-compressed
GZ        1F 8B             →  application/gzip
EXE       4D 5A             →  application/x-msdownload  ← caught here
```

Tika reads these bytes and returns the real MIME type. A student who renames `malware.exe` to `project.zip` passes Gate 1 (zip is allowed) but fails Gate 2 — Tika sees `4D 5A` (MZ header) and returns `application/x-msdownload`, which does not match the expected MIME for `.zip`.

### Extension-to-MIME Map

```java
private static final Map<String, String> EXTENSION_TO_MIME = Map.ofEntries(

    // Archives
    entry("zip",   "application/zip"),
    entry("rar",   "application/vnd.rar"),
    entry("gz",    "application/gzip"),
    entry("tar",   "application/x-tar"),
    entry("7z",    "application/x-7z-compressed"),

    // Documents
    entry("pdf",   "application/pdf"),
    entry("docx",  "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    entry("xlsx",  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    entry("pptx",  "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    entry("txt",   "text/plain"),

    // Data & Config
    entry("csv",   "text/csv"),
    entry("json",  "application/json"),
    entry("xml",   "application/xml"),
    entry("yaml",  "text/yaml"),
    entry("yml",   "text/yaml"),
    entry("toml",  "application/toml"),
    entry("sql",   "text/x-sql"),

    // Markup & Web
    entry("html",  "text/html"),
    entry("css",   "text/css"),
    entry("md",    "text/markdown"),

    // JVM Languages
    entry("java",  "text/x-java-source"),
    entry("kt",    "text/x-kotlin"),
    entry("scala", "text/x-scala"),

    // Systems & Low-level
    entry("c",     "text/x-csrc"),
    entry("cpp",   "text/x-c++src"),
    entry("cs",    "text/x-csharp"),
    entry("go",    "text/x-go"),
    entry("rs",    "text/x-rust"),

    // Scripting
    entry("py",    "text/x-python"),
    entry("rb",    "text/x-ruby"),
    entry("php",   "text/x-php"),
    entry("swift", "text/x-swift"),
    entry("js",    "text/javascript"),
    entry("ts",    "text/typescript"),
    entry("r",     "text/x-r"),

    // Notebooks
    entry("ipynb", "application/x-ipynb+json")
);
```

### MIME Matching Rules

Not all MIME matching is strict equality. Text-based source files (`.py`, `.js`, `.java` etc.) are all fundamentally plain text — Tika may return `text/plain` for any of them because there are no magic bytes to distinguish them. The validation rule is therefore format-dependent:

| Format group | Validation rule | Rationale |
|---|---|---|
| Archives (zip, rar, gz, tar, 7z) | **Strict** — exact MIME match required | Binary formats with distinct magic bytes — spoofing is the primary risk |
| PDF | **Strict** — exact MIME match required | Binary format with distinct magic bytes |
| Office (docx, xlsx, pptx) | **Strict** — exact MIME match required | Binary ZIP-based formats — Tika inspects internal structure to distinguish from plain ZIP |
| Text-based source files | **Loose** — Tika must return any `text/*` MIME | No magic bytes — confirming it is genuinely text (not a binary disguised as code) is sufficient |
| JSON, XML, YAML, CSV | **Loose** — Tika must return any `text/*` or `application/json` / `application/xml` | Structured text formats |
| Notebooks (.ipynb) | **Loose** — Tika must return `text/*` or `application/json` | ipynb is a JSON file internally |

### Gate 2 Error Responses

| Condition | Error Code | Message |
|-----------|------------|---------|
| Tika detects executable content | `INVALID_MIME_TYPE` | `"File content does not match its extension. Executable content is not permitted"` |
| Tika MIME does not match expected | `INVALID_MIME_TYPE` | `"File content does not match its extension"` |

### Gate 2 Checklist

- [ ] Tika runs on the raw file bytes — NOT on the client-supplied `Content-Type` header
- [ ] If Tika detects any executable MIME type (`application/x-msdownload`, `application/x-executable`, `application/x-elf`, `application/x-mach-binary`) the file is rejected even if the extension passed Gate 1
- [ ] Strict MIME matching applied to: `zip`, `rar`, `gz`, `tar`, `7z`, `pdf`, `docx`, `xlsx`, `pptx`
- [ ] Loose MIME matching (any `text/*`) applied to all source code and markup extensions
- [ ] MIME mismatch is logged at `WARN` level with `userId`, `detectedMime`, `claimedExtension`, `ipAddress`
- [ ] Tika is initialised as a singleton — `new Tika()` is NOT instantiated per request

---

## Gate 3 — Structural Validation

Gate 3 applies to **binary formats only** — it attempts to open and parse the file using a format-specific library. A file can have correct magic bytes (passing Tika) but be internally malformed or deliberately crafted to cause issues downstream when an instructor opens it. Gate 3 confirms the file is genuinely parseable.

> Gate 3 does NOT apply to text-based files (source code, CSV, JSON etc.). Those are plain text — there is no meaningful structure to validate beyond what Gate 2 already confirmed.

### 3A — Archive Validation (Apache Commons Compress)

```java
// Validates the archive can be opened and its entries read
// Does NOT decompress entries — reads headers only (performant)
// Does NOT recurse into nested archives
private void validateArchive(InputStream stream, String extension) {
    try (ArchiveInputStream ais = new ArchiveStreamFactory()
            .createArchiveInputStream(extension, stream)) {
        ArchiveEntry entry = ais.getNextEntry();
        if (entry == null) {
            throw new InvalidFileException("Archive is empty or malformed");
        }
        // Walk entry headers only — do not decompress content
        int entryCount = 0;
        while (ais.getNextEntry() != null && entryCount < 100) {
            entryCount++;
        }
    } catch (ArchiveException | IOException e) {
        throw new InvalidFileException("File is not a valid archive");
    }
}
```

> **Why Apache Commons Compress over Java's built-in ZipInputStream?**
> Commons Compress supports ZIP, RAR, TAR, GZ, and 7Z through a unified API. Java's built-in `ZipInputStream` only handles ZIP and has weaker error handling for malformed files. Since your allowlist includes all five archive types, Commons Compress is the right choice here.

**Archive-specific checks**

- [ ] ZIP — open with Commons Compress `ZipArchiveInputStream`, read at least one entry header
- [ ] RAR — open with Commons Compress `RarArchiveInputStream`, read at least one entry header
- [ ] TAR / GZ — open with `TarArchiveInputStream` wrapped in `GzipCompressorInputStream`, read at least one entry header
- [ ] 7Z — open with `SevenZFile`, read at least one entry header
- [ ] **Zip bomb protection** — track uncompressed size as entry headers are read. If cumulative uncompressed size exceeds **500MB**, reject with `ARCHIVE_TOO_LARGE`
- [ ] **Depth protection** — Gate 3 does NOT recurse. Nested archives are not opened. This is by design.
- [ ] **Entry count limit** — if an archive contains more than **1000 entries**, reject with `ARCHIVE_TOO_LARGE` (zip bombs can use millions of tiny files)

### 3B — PDF Validation (OpenPDF / iText)

```java
private void validatePdf(InputStream stream) {
    try {
        PdfReader reader = new PdfReader(stream);
        // Confirm the PDF has at least one page and a valid cross-reference table
        if (reader.getNumberOfPages() < 1) {
            throw new InvalidFileException("PDF has no pages");
        }
        reader.close();
    } catch (IOException e) {
        throw new InvalidFileException("File is not a valid PDF document");
    }
}
```

- [ ] PDF opens without IOException
- [ ] PDF has at least 1 page
- [ ] Encrypted / password-protected PDFs are rejected — `{ code: "PDF_ENCRYPTED", message: "Password-protected PDFs are not accepted. Please remove the password before submitting" }`

### 3C — Office File Validation (DOCX / XLSX / PPTX)

DOCX, XLSX, and PPTX are ZIP archives internally (OOXML format). Gate 3 opens the ZIP and confirms the required internal parts are present. This validates the file is a genuine Office document and not a plain ZIP file with a `.docx` extension.

```java
private void validateOfficeFile(InputStream stream, String extension) {
    try (ZipInputStream zis = new ZipInputStream(stream)) {
        Set<String> requiredParts = switch (extension) {
            case "docx" -> Set.of("[Content_Types].xml", "word/document.xml");
            case "xlsx" -> Set.of("[Content_Types].xml", "xl/workbook.xml");
            case "pptx" -> Set.of("[Content_Types].xml", "ppt/presentation.xml");
            default     -> Set.of("[Content_Types].xml");
        };
        Set<String> foundParts = new HashSet<>();
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            foundParts.add(entry.getName());
        }
        if (!foundParts.containsAll(requiredParts)) {
            throw new InvalidFileException(
                "File is not a valid " + extension.toUpperCase() + " document"
            );
        }
    } catch (IOException e) {
        throw new InvalidFileException("File structure is invalid");
    }
}
```

- [ ] DOCX must contain `[Content_Types].xml` and `word/document.xml`
- [ ] XLSX must contain `[Content_Types].xml` and `xl/workbook.xml`
- [ ] PPTX must contain `[Content_Types].xml` and `ppt/presentation.xml`
- [ ] Any Office file that fails ZIP parsing is rejected with `INVALID_FILE_TYPE`
- [ ] Macro-enabled formats (`xlsm`, `docm`, `pptm` etc.) are already blocked at Gate 1 — no additional check needed here

### Gate 3 Error Responses

| Condition | Error Code | Message |
|-----------|------------|---------|
| Archive is empty or malformed | `INVALID_FILE_STRUCTURE` | `"The archive file is corrupted or empty"` |
| Uncompressed size exceeds 500MB | `ARCHIVE_TOO_LARGE` | `"Archive uncompressed content exceeds the maximum allowed size of 500MB"` |
| Archive entry count exceeds 1000 | `ARCHIVE_TOO_LARGE` | `"Archive contains too many files"` |
| PDF cannot be parsed | `INVALID_FILE_STRUCTURE` | `"The PDF file is corrupted or cannot be read"` |
| PDF is password protected | `PDF_ENCRYPTED` | `"Password-protected PDFs are not accepted. Please remove the password before submitting"` |
| Office file missing required parts | `INVALID_FILE_STRUCTURE` | `"The file does not appear to be a valid DOCX/XLSX/PPTX document"` |

---

## Complete Validation Flow

```java
public void validate(MultipartFile file) {

    String filename  = file.getOriginalFilename();
    String extension = extractExtension(filename).toLowerCase();

    // ── GATE 1A — Denylist ───────────────────────────────────────────
    if (BLOCKED_EXTENSIONS.contains(extension)) {
        log.warn("BLOCKED_FILE_TYPE user={} file={} ip={}", userId, filename, ip);
        throw new BlockedFileTypeException(extension);
        // → 400 FILE_TYPE_BLOCKED
    }

    // ── GATE 1B — Allowlist ──────────────────────────────────────────
    if (!ALLOWED_EXTENSIONS.contains(extension)) {
        throw new InvalidFileTypeException(extension);
        // → 400 INVALID_FILE_TYPE
    }

    // ── GATE 2 — MIME Detection (Tika) ───────────────────────────────
    String detectedMime = tika.detect(file.getInputStream(), filename);

    if (isExecutableMime(detectedMime)) {
        log.warn("EXECUTABLE_MIME_DETECTED user={} file={} mime={} ip={}",
            userId, filename, detectedMime, ip);
        throw new InvalidMimeTypeException(detectedMime);
        // → 400 INVALID_MIME_TYPE
    }

    if (!isMimeAcceptable(detectedMime, extension)) {
        log.warn("MIME_MISMATCH user={} file={} expected={} detected={} ip={}",
            userId, filename, EXTENSION_TO_MIME.get(extension), detectedMime, ip);
        throw new InvalidMimeTypeException(detectedMime);
        // → 400 INVALID_MIME_TYPE
    }

    // ── GATE 3 — Structural Validation ───────────────────────────────
    switch (extension) {
        case "zip", "rar", "tar", "gz", "7z"
                        -> validateArchive(file.getInputStream(), extension);
        case "pdf"      -> validatePdf(file.getInputStream());
        case "docx", "xlsx", "pptx"
                        -> validateOfficeFile(file.getInputStream(), extension);
        default         -> { /* text-based files — no structural check needed */ }
    }

    // ── ALL GATES PASSED — proceed to storage ────────────────────────
}
```

---

## New Error Codes to Add to `00_Global_Rules_and_Reference.md`

| Code | HTTP | When |
|------|------|------|
| `FILE_TYPE_BLOCKED` | 400 | Extension is on the explicit denylist (exe, jar, sh etc.) |
| `INVALID_FILE_TYPE` | 400 | Extension not in allowlist |
| `INVALID_MIME_TYPE` | 400 | File content does not match its extension |
| `INVALID_FILE_STRUCTURE` | 400 | File cannot be parsed as the format it claims to be |
| `ARCHIVE_TOO_LARGE` | 400 | Archive uncompressed size or entry count exceeds limits |
| `PDF_ENCRYPTED` | 400 | PDF is password protected |

---

## What This Does and Does Not Protect Against

| Threat | Protected | Gate |
|--------|-----------|------|
| `.exe` submitted directly | ✅ | Gate 1 — denylist |
| `.exe` renamed to `.zip` | ✅ | Gate 2 — Tika detects EXE magic bytes |
| Macro-enabled Office file (`.xlsm`, `.docm`) | ✅ | Gate 1 — denylist |
| Completely unknown/unsupported file type | ✅ | Gate 1 — not in allowlist |
| Corrupted / malformed ZIP | ✅ | Gate 3 — structural validation |
| Password-protected PDF | ✅ | Gate 3 — structural validation |
| Fake DOCX (plain ZIP with `.docx` extension) | ✅ | Gate 3 — missing required internal parts |
| Zip bomb (compressed) | ✅ | Gate 3 — uncompressed size and entry count limits |
| `.exe` inside a `.zip` | ❌ | Out of scope — recursive scanning excluded for performance |
| Malicious content inside a valid PDF | ❌ | Out of scope — requires antivirus (ClamAV) integration |
| JavaScript exploit inside a valid `.html` file | ❌ | Out of scope — content-level scanning not in scope |

> The last three rows are a known, accepted limitation. They are documented here so the team is not surprised and so a future decision to add ClamAV async scanning has a clear hook point in the architecture.


---

# Additional Production Hardening Rules

The following sections strengthen operational safety, performance stability, and security guarantees of the upload pipeline. These rules do **not change the three‑gate architecture**, but clarify how it must behave in production environments.

---

## Filename Normalization and Validation

Before any validation step, the system **must normalize and sanitize the uploaded filename**.

Rules:

1. Filenames must be normalized using **Unicode NFKC normalization** to prevent homoglyph attacks.
2. Leading and trailing whitespace must be removed.
3. Filenames containing **control characters** or **null bytes (`\0`)** must be rejected.
4. Filenames containing **path traversal patterns** (`../`, `..\\`, or absolute paths) must be rejected.
5. Filenames containing **Windows alternate data streams** (example: `file.txt:evil.exe`) must be rejected.
6. Filenames beginning with a dot (example: `.env`, `.gitignore`) must be rejected unless explicitly allowed.
7. Filenames ending with a trailing dot must be rejected.

After normalization, the **file extension must be extracted from the sanitized filename** and used for extension validation.

---

## Temporary File Handling

### Input Stream Handling

Uploaded files must be written **once** to a temporary file on disk before validation.

All validation stages must operate on this same temporary file:

- MIME detection (Apache Tika)
- Archive inspection
- PDF structural validation
- Office structure validation

This prevents:

- repeated stream reads
- unnecessary buffering
- memory pressure under high concurrency

Example flow:

Upload → Temporary File → Validation Pipeline → Storage

Temporary files must be securely deleted after validation completes.

---

## HTTP Layer Upload Limits

### Request Size Enforcement

To protect application resources, file upload size limits must be enforced **before the request reaches the application layer**.

Recommended enforcement points:

- reverse proxy or API gateway
- application multipart configuration

Example policy:

- Maximum request size: **100 MB**
- Maximum archive uncompressed size: **500 MB**

Rejecting oversized uploads at the network boundary prevents unnecessary disk and CPU consumption.

---

## MIME Detection Timeout Protection

### Content Detection Safety

MIME detection is performed using **Apache Tika**.

To prevent malformed or adversarial files from causing excessive CPU usage or thread blocking:

1. Files exceeding the maximum allowed upload size must be rejected **before MIME detection**.
2. MIME detection must run within a **bounded execution time**.
3. Detection should run in a **bounded thread pool**.

If MIME detection fails or times out, the file must be rejected.

---

## Archive Validation Safety Rules

Archive validation inspects **entry metadata only** without decompression.

The system must track:

- total number of archive entries
- total declared uncompressed size

Validation rules:

1. If the number of entries exceeds **1000**, the archive must be rejected.
2. If the total declared uncompressed size exceeds **500 MB**, the archive must be rejected.
3. If an entry reports **unknown, negative, or invalid size**, the archive must be rejected.

Rejecting entries with unknown sizes prevents bypassing decompression limits using malformed archive metadata.

---

## Configurable Security Limits

Security limits must not be hardcoded and should instead be externally configurable.

Recommended configurable properties:

- maximum archive entry count
- maximum archive uncompressed size
- maximum upload size
- MIME detection timeout

Example configuration:

```
security.archive.maxEntries = 1000
security.archive.maxUncompressedSize = 500MB
security.upload.maxSize = 100MB
security.mimeDetection.timeoutMs = 2000
```

External configuration allows operational tuning without requiring application redeployment.

---

## Security Logging

All validation failures must produce structured security log events.

Example event codes:

- FILE_TYPE_BLOCKED
- INVALID_FILE_TYPE
- MIME_MISMATCH
- EXECUTABLE_MIME_DETECTED
- ARCHIVE_LIMIT_EXCEEDED

Each log entry should include:

- timestamp
- filename
- detected MIME type
- user identifier (if available)

To prevent log amplification attacks, logging systems should support **rate limiting or sampling** when repeated violations occur.

---

## PDF Validation Safety

PDF validation uses **OpenPDF** to verify document structure by parsing the cross‑reference table.

Safety rules:

1. PDF size must not exceed the configured upload limit.
2. All parsing exceptions must be handled safely.
3. Invalid or malformed PDFs must be rejected.

Malformed or incomplete PDFs must not be accepted by the system.

---
