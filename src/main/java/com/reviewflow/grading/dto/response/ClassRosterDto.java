package com.reviewflow.grading.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ClassRosterDto {

  String courseCode;
  ClassStatsDto classStats;
  List<StudentStandingDto> students;
  Integer page;
  Integer size;
  Integer totalElements;
  Integer totalPages;

  @Value
  @Builder
  public static class StudentStandingDto {

    String studentId;
    String name;
    String email;
    BigDecimal currentStanding;
    Boolean atRisk;
  }
}
