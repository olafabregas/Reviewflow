package com.reviewflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "discussions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Discussion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assignment_id")
  private Assignment assignment;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String prompt;

  @Column(name = "due_at", nullable = false)
  private Instant dueAt;

  @Column(name = "require_post_before_reading", nullable = false)
  @Builder.Default
  private Boolean requirePostBeforeReading = true;

  @Column(name = "allow_anonymous", nullable = false)
  @Builder.Default
  private Boolean allowAnonymous = false;

  @Column(name = "is_graded", nullable = false)
  @Builder.Default
  private Boolean isGraded = false;

  @Column(name = "is_published", nullable = false)
  @Builder.Default
  private Boolean isPublished = false;

  @Column(name = "published_at")
  private Instant publishedAt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by", nullable = false)
  private User createdBy;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;
}
