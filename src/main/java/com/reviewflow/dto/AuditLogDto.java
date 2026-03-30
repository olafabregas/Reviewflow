package com.reviewflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
