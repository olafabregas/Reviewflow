package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class CourseModulesResponse {

    List<AssignmentModuleResponse> modules;
    List<AssignmentModuleResponse.AssignmentSummary> unmoduled;
}
