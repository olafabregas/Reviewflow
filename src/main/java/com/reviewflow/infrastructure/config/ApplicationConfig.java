package com.reviewflow.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

  /**
   * Configure ObjectMapper bean for JSON serialization/deserialization Required by services like
   * AuditService for metadata handling
   */
  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
