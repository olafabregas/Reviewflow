package com.reviewflow.infrastructure.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "security.file")
public class FileSecurityProperties {

  private int maxArchiveEntries = 1000;
  private long maxArchiveUncompressedSize = 500L * 1024 * 1024;
  private long mimeDetectionTimeoutMs = 2000;
}
