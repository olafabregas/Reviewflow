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
@Table(name = "discussion_posts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscussionPost {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "discussion_id", nullable = false)
  private Discussion discussion;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_post_id")
  private DiscussionPost parentPost;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_id", nullable = false)
  private User author;

  @Column(columnDefinition = "TEXT")
  private String content;

  @Column(name = "word_count", nullable = false)
  @Builder.Default
  private Integer wordCount = 0;

  @Column(name = "is_pinned", nullable = false)
  @Builder.Default
  private Boolean isPinned = false;

  @Column(name = "is_deleted", nullable = false)
  @Builder.Default
  private Boolean isDeleted = false;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;
}
