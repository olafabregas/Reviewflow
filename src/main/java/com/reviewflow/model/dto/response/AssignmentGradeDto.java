package com.reviewflow.model.dto.response;

import com.reviewflow.model.enums.SubmissionType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class AssignmentGradeDto {

    String id;
    String title;
    String moduleId;
    String moduleName;
    BigDecimal score;
    BigDecimal maxScore;
    BigDecimal percent;
    Boolean isDropped;
    String dropReason;
    Boolean isPublished;
    String status;
    Boolean isLate;
    Instant submittedAt;
    Instant evaluatedAt;
    SubmissionType submissionType;
}
