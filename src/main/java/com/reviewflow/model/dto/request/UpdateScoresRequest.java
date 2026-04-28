package com.reviewflow.model.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class UpdateScoresRequest {

  @NotEmpty private List<ScoreEntry> scores;

  @Data
  public static class ScoreEntry {
    private String criterionId;
    private BigDecimal score;
    private String comment;
  }
}
