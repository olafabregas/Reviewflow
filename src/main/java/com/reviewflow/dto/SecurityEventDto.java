package com.reviewflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
