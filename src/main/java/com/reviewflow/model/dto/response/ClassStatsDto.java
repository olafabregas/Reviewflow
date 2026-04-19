package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class ClassStatsDto {

    Integer enrolledCount;
    Integer withGrades;
    BigDecimal average;
    BigDecimal highest;
    BigDecimal lowest;
    BigDecimal median;
    Integer atRiskCount;
    BigDecimal atRiskThreshold;
}
