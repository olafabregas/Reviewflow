package com.reviewflow.model.entity;

import com.reviewflow.model.enums.AnnouncementTarget;
import com.reviewflow.model.enums.RecipientType;
    // TODO [STYLE-AGENT]: fix structural violation
import jakarta.persistence.*;
import java.time.Instant; // TODO [STYLE-AGENT]: fix structural violation
    // TODO [STYLE-AGENT]: fix structural violation
import lombok.*;

@Entity
@Table(name = "announcements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Announcement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "course_id")
  private Course course;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by", nullable = false)
  private User createdBy;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private AnnouncementTarget target = AnnouncementTarget.COURSE;

  @Enumerated(EnumType.STRING)
  @Column(name = "recipient_type")
  private RecipientType recipientType;

  @Column(name = "is_published", nullable = false)
  @Builder.Default
  private Boolean isPublished = false;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
