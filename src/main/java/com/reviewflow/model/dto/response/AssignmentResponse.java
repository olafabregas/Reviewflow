package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class AssignmentResponse {

    Long id;
    Long courseId;
    String title;
    String description;
    Instant dueAt;
    Integer maxTeamSize;
    Boolean isPublished;
    Instant teamLockAt;
    List<RubricCriterionResponse> rubricCriteria;

    @Value
    @Builder
    public static class RubricCriterionResponse {
        Long id;
        String name;
        String description;
        Integer maxScore;
        Integer displayOrder;
    }
}
