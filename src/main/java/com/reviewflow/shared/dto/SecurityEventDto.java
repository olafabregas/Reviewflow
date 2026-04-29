package com.reviewflow.shared.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityEventDto {

  @JsonProperty("action")
  private String action;

  @JsonProperty("targetType")
  private String targetType;

  @JsonProperty("targetId")
  private Long targetId;

  @JsonProperty("createdAt")
  private Instant createdAt;
}
