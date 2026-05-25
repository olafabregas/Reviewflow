package com.reviewflow.user.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pending_s3_deletions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingS3Deletion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "s3_key", nullable = false, length = 500)
  private String s3Key;

  @Column(name = "entity_type", nullable = false, length = 50)
  private String entityType;

  @Column(nullable = false, length = 100)
  private String reason;

  @Column(name = "retry_count", nullable = false)
  @Builder.Default
  private int retryCount = 0;

  @Column(name = "max_retries", nullable = false)
  @Builder.Default
  private int maxRetries = 5;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "last_attempted_at")
  private Instant lastAttemptedAt;

  @Column(name = "error_message", length = 500)
  private String errorMessage;
}
