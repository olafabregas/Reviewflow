package com.reviewflow.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AssignModuleRequest {

    @Schema(description = "Target module hashid; null removes assignment from module", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String moduleId;
}
