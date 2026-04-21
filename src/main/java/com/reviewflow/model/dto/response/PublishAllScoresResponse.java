package com.reviewflow.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PublishAllScoresResponse {

    @Schema(description = "Number of scores that were published")
    int publishedCount;

    @Schema(description = "Human-readable result message")
    String message;
}
