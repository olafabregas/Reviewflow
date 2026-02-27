package com.reviewflow.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Profile("local")
public class LocalFileStorageService implements StorageService {

    private final Path basePath;

    public LocalFileStorageService(@Value("${app.storage.base-path:./storage}") String basePath) {
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create storage directory", e);
        }
    }

    @Override
    public String store(String relativePath, MultipartFile file) {
        try {
            String sanitized = sanitizeFileName(file.getOriginalFilename());
            String name = UUID.randomUUID() + "_" + sanitized;
            Path target = basePath.resolve(relativePath).resolve(name).normalize();
            if (!target.startsWith(basePath)) {
                throw new SecurityException("Path traversal not allowed");
            }
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
            return relativePath + "/" + name;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    @Override
    public String store(String relativePath, InputStream inputStream, long contentLength, String contentType) {
        try {
            String name = UUID.randomUUID().toString();
            Path target = basePath.resolve(relativePath).resolve(name).normalize();
            if (!target.startsWith(basePath)) {
                throw new SecurityException("Path traversal not allowed");
            }
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target);
            return relativePath + "/" + name;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    @Override
    public Resource load(String path) {
        try {
            Path target = basePath.resolve(path).normalize();
            if (!target.startsWith(basePath)) {
                throw new SecurityException("Path traversal not allowed");
            }
            Resource r = new UrlResource(target.toUri());
            if (!r.exists() || !r.isReadable()) {
                throw new RuntimeException("File not found or not readable: " + path);
            }
            return r;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load file", e);
        }
    }

    @Override
    public void delete(String path) {
        try {
            Path target = basePath.resolve(path).normalize();
            if (!target.startsWith(basePath)) {
                throw new SecurityException("Path traversal not allowed");
            }
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) return "file";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
