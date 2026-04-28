package com.reviewflow.model.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CourseModulesResponse {

  List<AssignmentModuleResponse> modules;
  List<AssignmentModuleResponse.AssignmentSummary> unmoduled;
}
