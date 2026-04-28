package com.reviewflow.model.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

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
