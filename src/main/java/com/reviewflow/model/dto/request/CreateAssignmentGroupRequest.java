package com.reviewflow.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateAssignmentGroupRequest {

    @Schema(description = "Group name", example = "Projects", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String name;

    @Schema(description = "Weight percentage for the group", example = "40.00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Min(0)
    @Max(100)
    private BigDecimal weight;

    @Schema(description = "Number of lowest grades to drop from this group", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Min(0)
    private Integer dropLowestN;

    @Schema(description = "Display order within the course", example = "1", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Min(0)
    private Integer displayOrder;
}