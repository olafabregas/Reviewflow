package com.reviewflow.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface StorageService {

    String store(String relativePath, MultipartFile file);

    String store(String relativePath, InputStream inputStream, long contentLength, String contentType);

    Resource load(String path);

    void delete(String path);
}
