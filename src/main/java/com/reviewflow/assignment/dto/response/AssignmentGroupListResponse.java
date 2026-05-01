package com.reviewflow.assignment.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AssignmentGroupListResponse {

  List<AssignmentGroupResponse> groups;
  BigDecimal totalConfiguredWeight;
  String weightWarning;
}
