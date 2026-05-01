package com.reviewflow.grading.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

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
