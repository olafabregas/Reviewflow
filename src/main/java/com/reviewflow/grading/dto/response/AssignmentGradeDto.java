package com.reviewflow.grading.dto.response;

import com.reviewflow.shared.domain.SubmissionType;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

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
