package com.reviewflow.model.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.reviewflow.model.enums.SubmissionType;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AssignmentResponse {

    String id;
    String courseId;
    String courseCode;
    String courseName;
    String title;
    String description;
    Instant dueAt;
    SubmissionType submissionType;
    Integer maxTeamSize;
    BigDecimal maxScore;
    String groupId;
    String groupName;
    String moduleId;
    String moduleName;
    Boolean isPublished;
    Instant teamLockAt;
    List<RubricCriterionResponse> rubricCriteria;

    // Student-specific fields (null for instructor/admin views)
    String teamStatus; // HAS_TEAM | NO_TEAM | LOCKED
    String submissionStatus; // NOT_SUBMITTED | SUBMITTED | LATE
    Boolean isLate;

    @Value
    @Builder
    public static class RubricCriterionResponse {

        String id;
        String name;
        String description;
        Integer maxScore;
        Integer displayOrder;
    }
}
