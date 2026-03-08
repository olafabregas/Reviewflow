package com.reviewflow.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.lowagie.text.pdf.PdfReader;
import com.reviewflow.config.FileSecurityProperties;
import com.reviewflow.exception.ArchiveTooLargeException;
import com.reviewflow.exception.BlockedFileTypeException;
import com.reviewflow.exception.InvalidFileStructureException;
import com.reviewflow.exception.InvalidFileTypeException;
import com.reviewflow.exception.InvalidMimeTypeException;
import com.reviewflow.exception.PdfEncryptedException;
import com.reviewflow.monitoring.SecurityMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Three-gate file security validation pipeline:
 * Gate 1: Denylist + Allowlist
 * Gate 2: MIME Type Detection (Apache Tika)
 * Gate 3: Structural Validation (format-specific)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileSecurityValidator {

    private final FileSecurityProperties securityProperties;
    private final SecurityMetrics securityMetrics;
    private final Tika tika = new Tika();

    // ═══════════════════════════════════════════════════════════════════════════
    // GATE 1: Denylist and Allowlist
    // ═══════════════════════════════════════════════════════════════════════════

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            // Windows executables & installers
            "exe", "msi", "com", "scr", "pif",
            // Windows scripts
            "bat", "cmd", "ps1", "vbs", "wsf", "hta",
            // Unix scripts
            "sh", "bash", "zsh", "csh", "ksh",
            // Compiled / binary / native libraries
            "dll", "so", "dylib", "sys", "drv", "obj",
            // Java executables
            "jar", "war", "ear", "class",
            // macOS executables & installers
            "app", "dmg", "pkg", "command",
            // Mobile
            "apk", "ipa", "xap",
            // Macro-enabled Office formats
            "xlsm", "xltm", "xlam", "xls",
            "docm", "dotm", "doc",
            "pptm", "potm", "ppam", "pps", "ppt",
            // Dangerous Windows file types
            "lnk", "reg", "inf", "url",
            // Disk & binary images
            "iso", "img", "bin", "raw",
            // Other
            "torrent", "cab"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            // Archives
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

    // ═══════════════════════════════════════════════════════════════════════════
    // GATE 2: MIME Type Detection
    // ═══════════════════════════════════════════════════════════════════════════

    private static final Map<String, String> EXTENSION_TO_MIME = Map.ofEntries(
            // Archives
            Map.entry("zip", "application/zip"),
            Map.entry("rar", "application/vnd.rar"),
            Map.entry("gz", "application/gzip"),
            Map.entry("tar", "application/x-tar"),
            Map.entry("7z", "application/x-7z-compressed"),
            // Documents
            Map.entry("pdf", "application/pdf"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("txt", "text/plain"),
            // Data & Config
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"),
            Map.entry("yaml", "text/yaml"),
            Map.entry("yml", "text/yaml"),
            Map.entry("toml", "application/toml"),
            Map.entry("sql", "text/x-sql"),
            // Markup & Web
            Map.entry("html", "text/html"),
            Map.entry("css", "text/css"),
            Map.entry("md", "text/markdown"),
            // JVM Languages
            Map.entry("java", "text/x-java-source"),
            Map.entry("kt", "text/x-kotlin"),
            Map.entry("scala", "text/x-scala"),
            // Systems & Low-level
            Map.entry("c", "text/x-csrc"),
            Map.entry("cpp", "text/x-c++src"),
            Map.entry("cs", "text/x-csharp"),
            Map.entry("go", "text/x-go"),
            Map.entry("rs", "text/x-rust"),
            // Scripting
            Map.entry("py", "text/x-python"),
            Map.entry("rb", "text/x-ruby"),
            Map.entry("php", "text/x-php"),
            Map.entry("swift", "text/x-swift"),
            Map.entry("js", "text/javascript"),
            Map.entry("ts", "text/typescript"),
            Map.entry("r", "text/x-r"),
            // Notebooks
            Map.entry("ipynb", "application/x-ipynb+json")
    );

    private static final Set<String> STRICT_MIME_EXTENSIONS = Set.of(
            "zip", "rar", "gz", "tar", "7z", "pdf", "docx", "xlsx", "pptx"
    );

    private static final Set<String> EXECUTABLE_MIME_TYPES = Set.of(
            "application/x-msdownload",
            "application/x-executable",
            "application/x-elf",
            "application/x-mach-binary",
            "application/x-dosexec",
            "application/x-sharedlib"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Main Validation Entry Point
    // ═══════════════════════════════════════════════════════════════════════════

    public void validate(MultipartFile file, Long userId, String ipAddress) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new InvalidFileTypeException("");
        }

        // Normalize and sanitize filename
        filename = normalizeFilename(filename);
        String extension = extractExtension(filename);

        // ── GATE 1A — Denylist ───────────────────────────────────────────
        if (BLOCKED_EXTENSIONS.contains(extension)) {
            securityMetrics.recordFileBlocked();
            log.warn("BLOCKED_FILE_TYPE user={} file={} ip={}", userId, filename, ipAddress);
            throw new BlockedFileTypeException(extension);
        }

        // ── GATE 1B — Allowlist ──────────────────────────────────────────
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidFileTypeException(extension);
        }

        // Write to temporary file for validation (prevents multiple stream reads)
        Path tempFile = Files.createTempFile("upload-", "-" + filename);
        try {
            file.transferTo(tempFile.toFile());

            // ── GATE 2 — MIME Detection (Tika) ───────────────────────────────
            String detectedMime;
            try (InputStream is = Files.newInputStream(tempFile)) {
                detectedMime = tika.detect(is, filename);
            }

            if (isExecutableMime(detectedMime)) {
                securityMetrics.recordFileExecutable();
                log.warn("EXECUTABLE_MIME_DETECTED user={} file={} mime={} ip={}",
                        userId, filename, detectedMime, ipAddress);
                throw new InvalidMimeTypeException("File content does not match its extension. Executable content is not permitted");
            }

            if (!isMimeAcceptable(detectedMime, extension)) {
                securityMetrics.recordFileMimeMismatch();
                log.warn("MIME_MISMATCH user={} file={} expected={} detected={} ip={}",
                        userId, filename, EXTENSION_TO_MIME.get(extension), detectedMime, ipAddress);
                throw new InvalidMimeTypeException(detectedMime, extension);
            }

            // ── GATE 3 — Structural Validation ───────────────────────────────
            try (InputStream is = Files.newInputStream(tempFile)) {
                switch (extension) {
                    case "zip", "rar", "tar", "gz", "7z" -> validateArchive(is, extension);
                    case "pdf" -> validatePdf(is);
                    case "docx", "xlsx", "pptx" -> validateOfficeFile(is, extension);
                    default -> { /* text-based files — no structural check needed */ }
                }
            }

        } finally {
            // Always delete temporary file
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("Failed to delete temporary file: {}", tempFile, e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Filename Normalization & Validation
    // ═══════════════════════════════════════════════════════════════════════════

    private String normalizeFilename(String filename) {
        // Unicode NFKC normalization
        filename = Normalizer.normalize(filename, Normalizer.Form.NFKC);

        // Remove leading/trailing whitespace
        filename = filename.trim();

        // Check for control characters or null bytes
        if (filename.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new InvalidFileTypeException("");
        }

        // Check for path traversal patterns
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new InvalidFileTypeException("");
        }

        // Check for Windows alternate data streams
        if (filename.contains(":")) {
            throw new InvalidFileTypeException("");
        }

        // Reject files starting with dot (unless explicitly allowed)
        if (filename.startsWith(".")) {
            throw new InvalidFileTypeException("");
        }

        // Reject trailing dot
        if (filename.endsWith(".")) {
            throw new InvalidFileTypeException("");
        }

        return filename;
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        // Handle .tar.gz specifically
        if (filename.toLowerCase().endsWith(".tar.gz")) {
            return "gz";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GATE 2: MIME Validation Logic
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean isExecutableMime(String mimeType) {
        return EXECUTABLE_MIME_TYPES.contains(mimeType);
    }

    private boolean isMimeAcceptable(String detectedMime, String extension) {
        // Strict matching for binary formats
        if (STRICT_MIME_EXTENSIONS.contains(extension)) {
            String expectedMime = EXTENSION_TO_MIME.get(extension);
            return expectedMime != null && expectedMime.equals(detectedMime);
        }

        // Loose matching for text-based files
        return detectedMime.startsWith("text/") ||
                detectedMime.equals("application/json") ||
                detectedMime.equals("application/xml");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GATE 3: Structural Validation
    // ═══════════════════════════════════════════════════════════════════════════

    private void validateArchive(InputStream stream, String extension) {
        try {
            switch (extension) {
                case "7z" -> validate7z(stream);
                case "zip" -> validateZip(stream);
                case "rar" -> validateRar(stream);
                case "tar" -> validateTar(stream);
                case "gz" -> validateGz(stream);
                default -> throw new InvalidFileStructureException("Unknown archive format");
            }
        } catch (InvalidFileStructureException | ArchiveTooLargeException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Archive validation failed", e);
            throw new InvalidFileStructureException("The archive file is corrupted or empty");
        }
    }

    private void validate7z(InputStream stream) throws IOException {
        // 7z requires file-based access
        Path tempFile = Files.createTempFile("7z-validation-", ".7z");
        try {
            Files.copy(stream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try (SevenZFile sevenZFile = new SevenZFile(tempFile.toFile())) {
                int entryCount = 0;

                while (sevenZFile.getNextEntry() != null && entryCount < securityProperties.getMaxArchiveEntries()) {
                    entryCount++;
                }

                if (entryCount == 0) {
                    throw new InvalidFileStructureException("Archive is empty or malformed");
                }

                if (entryCount >= securityProperties.getMaxArchiveEntries()) {
                    throw new ArchiveTooLargeException("Archive contains too many files");
                }
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void validateZip(InputStream stream) throws IOException {
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(stream)) {
            validateArchiveEntries(zis);
        }
    }

    private void validateRar(InputStream stream) throws Exception {
        try (ArchiveInputStream ais = new ArchiveStreamFactory().createArchiveInputStream("rar", stream)) {
            validateArchiveEntries(ais);
        }
    }

    private void validateTar(InputStream stream) throws IOException {
        try (TarArchiveInputStream tis = new TarArchiveInputStream(stream)) {
            validateArchiveEntries(tis);
        }
    }

    private void validateGz(InputStream stream) throws IOException {
        // GZ is typically a single compressed file or tar.gz
        try (GzipCompressorInputStream gis = new GzipCompressorInputStream(stream)) {
            // Try to read as tar archive
            try (TarArchiveInputStream tis = new TarArchiveInputStream(gis)) {
                validateArchiveEntries(tis);
            } catch (Exception e) {
                // Not a tar archive, just a compressed file - validate it can be read
                byte[] buffer = new byte[8192];
                while (gis.read(buffer) != -1) {
                    // Just reading to validate structure
                }
            }
        }
    }

    private void validateArchiveEntries(ArchiveInputStream ais) throws IOException {
        int entryCount = 0;
        long totalUncompressedSize = 0;

        ArchiveEntry entry;
        while ((entry = ais.getNextEntry()) != null && entryCount < securityProperties.getMaxArchiveEntries()) {
            entryCount++;

            long size = entry.getSize();
            if (size == -1 || size < 0) {
                throw new InvalidFileStructureException("Archive contains entries with invalid size");
            }

            totalUncompressedSize += size;

            if (totalUncompressedSize > securityProperties.getMaxArchiveUncompressedSize()) {
                throw new ArchiveTooLargeException(
                        "Archive uncompressed content exceeds the maximum allowed size of " +
                                (securityProperties.getMaxArchiveUncompressedSize() / (1024 * 1024)) + "MB"
                );
            }
        }

        if (entryCount == 0) {
            throw new InvalidFileStructureException("Archive is empty or malformed");
        }

        if (entryCount >= securityProperties.getMaxArchiveEntries()) {
            throw new ArchiveTooLargeException("Archive contains too many files");
        }
    }

    private void validatePdf(InputStream stream) {
        try (PdfReader reader = new PdfReader(stream)) {
            // Check if encrypted
            if (reader.isEncrypted()) {
                throw new PdfEncryptedException();
            }

            // Confirm at least one page
            if (reader.getNumberOfPages() < 1) {
                throw new InvalidFileStructureException("PDF has no pages");
            }
        } catch (PdfEncryptedException e) {
            throw e;
        } catch (Exception e) {
            log.debug("PDF validation failed", e);
            throw new InvalidFileStructureException("The PDF file is corrupted or cannot be read");
        }
    }

    private void validateOfficeFile(InputStream stream, String extension) {
        try (ZipInputStream zis = new ZipInputStream(stream)) {
            Set<String> requiredParts = switch (extension) {
                case "docx" -> Set.of("[Content_Types].xml", "word/document.xml");
                case "xlsx" -> Set.of("[Content_Types].xml", "xl/workbook.xml");
                case "pptx" -> Set.of("[Content_Types].xml", "ppt/presentation.xml");
                default -> Set.of("[Content_Types].xml");
            };

            Set<String> foundParts = new HashSet<>();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                foundParts.add(entry.getName());
            }

            if (!foundParts.containsAll(requiredParts)) {
                throw new InvalidFileStructureException(
                        "The file does not appear to be a valid " + extension.toUpperCase() + " document"
                );
            }
        } catch (InvalidFileStructureException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Office file validation failed", e);
            throw new InvalidFileStructureException("File structure is invalid");
        }
    }
}
