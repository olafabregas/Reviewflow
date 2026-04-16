package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AssignmentGroupMoveResponse {

    String assignmentId;
    String newGroupId;
    String newGroupName;
}