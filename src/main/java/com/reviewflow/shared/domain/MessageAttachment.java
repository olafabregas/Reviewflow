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
@Table(name = "message_attachments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageAttachment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "message_id", nullable = false)
  private Message message;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "file_size_bytes", nullable = false)
  private Long fileSizeBytes;

  @Column(name = "storage_path", nullable = false, length = 500)
  private String storagePath;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "uploaded_at", nullable = false)
  private Instant uploadedAt;
}
