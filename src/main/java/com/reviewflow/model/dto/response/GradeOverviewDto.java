package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
