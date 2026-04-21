package com.reviewflow.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AutoAssignRequest {

    @Schema(description = "Maximum number of students per team; defaults to 3 when omitted", example = "4")
    private Integer maxTeamSize;
}
