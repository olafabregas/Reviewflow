package com.reviewflow.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateInstructorScoreRequest {

    @Schema(description = "Updated score value", example = "87.00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal score;

    @Schema(description = "Updated comment", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String comment;
}
