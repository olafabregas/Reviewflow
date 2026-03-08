package com.reviewflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "security.file")
public class FileSecurityProperties {
    
    /**
     * Maximum number of entries allowed in an archive file
     */
    private int maxArchiveEntries = 1000;
    
    /**
     * Maximum uncompressed size of archive content in bytes (default: 500MB)
     */
    private long maxArchiveUncompressedSize = 500L * 1024 * 1024;
    
    /**
     * Maximum upload file size in bytes (default: 100MB)
     */
    private long maxUploadSize = 100L * 1024 * 1024;
    
    /**
     * MIME detection timeout in milliseconds
     */
    private long mimeDetectionTimeoutMs = 2000;
}
