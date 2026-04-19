package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

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
