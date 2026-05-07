package com.reviewflow.auth.dto.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {
  private Long id;
  private String deviceId;
  private String userAgent;
  private String ipAddress;
  private Instant createdAt;
  private Instant lastActiveAt;
  private boolean isCurrent;
}
