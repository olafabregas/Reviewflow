package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class ClassRosterDto {

    String courseCode;
    ClassStatsDto classStats;
    List<StudentStandingDto> students;

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
