package com.reviewflow.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class MoveAssignmentGroupRequest {

    @Schema(description = "Target assignment group hashid", example = "grpTarget123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String groupId;
}
