package com.reviewflow.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateInstructorScoreRequest {

    @Schema(description = "Student hashid for individual assignments", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String studentId;

    @Schema(description = "Team hashid for team assignments", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String teamId;

    @Schema(description = "Score to assign", example = "84.00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal score;

    @Schema(description = "Optional instructor comment", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String comment;
}
