package com.reviewflow.shared.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {

  private Long id;
  private Long actorId;
  private String actorEmail;
  private String action;
  private String entityType;
  private Long entityId;
  private String metadata;
  private String ipAddress;
  private Instant createdAt;
}
