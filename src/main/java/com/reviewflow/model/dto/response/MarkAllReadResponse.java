package com.reviewflow.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MarkAllReadResponse {

    @Schema(description = "Human-readable result message")
    String message;

    @Schema(description = "Number of notifications that were marked as read")
    int updatedCount;
}
