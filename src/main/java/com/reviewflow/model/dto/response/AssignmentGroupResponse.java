package com.reviewflow.model.dto.response;

import com.reviewflow.model.enums.SubmissionType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
@Builder
public class AssignmentGroupResponse {

    String id;
    String name;
    BigDecimal weight;
    Integer dropLowestN;
    Integer displayOrder;
    Boolean isUncategorized;
    Long assignmentCount;
    List<AssignmentSummary> assignments;
    String weightWarning;

    @Value
    @Builder
    public static class AssignmentSummary {

        String id;
        String title;
        Instant dueAt;
        SubmissionType submissionType;
    }
}
