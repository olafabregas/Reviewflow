package com.reviewflow.grading.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GradebookEntryResponse {
  String teamId;
  String teamName;
  List<String> memberNames;
  Integer latestVersion;
  Instant submittedAt;
  Boolean isLate;
  BigDecimal totalScore;
  String evaluationStatus; // NOT_STARTED | DRAFT | PUBLISHED
}
