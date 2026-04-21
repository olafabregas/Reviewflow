package com.reviewflow.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserActivationResponse {

    @Schema(description = "Human-readable result message")
    String message;

    @Schema(description = "New active state of the user account")
    boolean isActive;
}
