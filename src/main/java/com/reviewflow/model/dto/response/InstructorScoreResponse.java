package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class InstructorScoreResponse {

    String id;
    String assignmentId;
    String studentId;
    String teamId;
    BigDecimal score;
    BigDecimal maxScore;
    BigDecimal percent;
    String comment;
    Boolean isPublished;
    Instant enteredAt;
    Instant publishedAt;
}
