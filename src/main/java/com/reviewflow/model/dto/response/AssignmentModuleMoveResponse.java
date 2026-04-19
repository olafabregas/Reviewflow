package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AssignmentModuleMoveResponse {

    String assignmentId;
    String moduleId;
    String moduleName;
}
