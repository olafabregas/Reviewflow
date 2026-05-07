package com.reviewflow.auth.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SessionEntryResponse {
  String id;
  String deviceId;
  String userAgent;
  String ipCreated;
  String ipLastSeen;
  Instant createdAt;
  Instant lastUsedAt;
  Instant expiresAt;
  boolean current;
}
