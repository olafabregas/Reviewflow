package com.reviewflow.model.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GroupGradeDto {

  String id;
  String name;
  BigDecimal weight;
  Integer dropLowestN;
  BigDecimal groupScorePercent;
  String status;
  List<AssignmentGradeDto> assignments;
}
