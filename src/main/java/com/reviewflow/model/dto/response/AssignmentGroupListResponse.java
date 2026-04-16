package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class AssignmentGroupListResponse {

    List<AssignmentGroupResponse> groups;
    BigDecimal totalConfiguredWeight;
    String weightWarning;
}