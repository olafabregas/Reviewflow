package com.reviewflow.grading.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GradeOverviewDto {

  String courseCode;
  String courseName;
  BigDecimal currentStanding;
  String currentStandingLetter;
  String weightWarning;
  String statusMessage;
  Instant lastUpdated;
  List<GroupGradeDto> groups;
}
