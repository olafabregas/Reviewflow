package com.reviewflow.shared.domain;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "notifications",
    indexes = {
      @Index(name = "idx_notifications_user_id", columnList = "user_id"),
      @Index(name = "idx_notifications_is_read", columnList = "is_read"),
      @Index(name = "idx_notifications_created_at", columnList = "created_at")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private NotificationType type;

  @Column(nullable = false, length = 150)
  @Builder.Default
  private String title = "";

  @Column(columnDefinition = "TEXT")
  private String message;

  @Column(name = "is_read", nullable = false)
  @Builder.Default
  private Boolean isRead = false;

  @Column(name = "action_url", length = 500)
  private String actionUrl;

  // Optional: ID of the resource this notification points to (team, submission, etc.)
  // Used for action URL rewriting with hashed IDs: "/teams/{id}" becomes "/teams/Xm2pNqR4"
  @Column(name = "target_id")
  private Long targetId;

  /** When set with DISCUSSION_REMINDER, participates in uk_notification_dedup (see V34). */
  @Column(name = "date_bucket")
  private LocalDate dateBucket;

  @Column(name = "dedup_key", insertable = false, updatable = false, length = 255)
  private String dedupKey;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private Instant createdAt;
}
