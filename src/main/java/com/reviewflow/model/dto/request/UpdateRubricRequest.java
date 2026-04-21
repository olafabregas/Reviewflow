package com.reviewflow.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class UpdateRubricRequest {

    @Schema(description = "Updated name of the rubric criterion", example = "Code Quality")
    private String name;

    @Schema(description = "Updated description for the rubric criterion", example = "Evaluates readability and maintainability")
    private String description;

    @Schema(description = "Updated maximum score for this criterion", example = "25")
    private Integer maxScore;

    @Schema(description = "Updated display order for sorting within the rubric", example = "2")
    private Integer displayOrder;
}
