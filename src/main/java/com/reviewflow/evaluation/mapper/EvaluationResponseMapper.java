package com.reviewflow.evaluation.mapper;

import com.reviewflow.evaluation.dto.response.EvaluationResponse;
import com.reviewflow.shared.domain.Evaluation;
import com.reviewflow.shared.domain.RubricScore;
import com.reviewflow.shared.util.HashidService;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EvaluationResponseMapper {

  public static EvaluationResponse toResponse(
      Evaluation ev, List<RubricScore> scores, HashidService hashidService) {
    BigDecimal maxPossible =
        scores.stream()
            .map(
                s ->
                    s.getCriterion() != null && s.getCriterion().getMaxScore() != null
                        ? BigDecimal.valueOf(s.getCriterion().getMaxScore())
                        : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    List<EvaluationResponse.RubricScoreResponse> scoreResponses =
        scores.stream()
            .map(
                s -> {
                  BigDecimal maxScore =
                      s.getCriterion() != null && s.getCriterion().getMaxScore() != null
                          ? BigDecimal.valueOf(s.getCriterion().getMaxScore())
                          : BigDecimal.ZERO;
                  return EvaluationResponse.RubricScoreResponse.builder()
                      .id(hashidService.encode(s.getId()))
                      .criterionId(
                          s.getCriterion() != null
                              ? hashidService.encode(s.getCriterion().getId())
                              : null)
                      .criterionName(s.getCriterion() != null ? s.getCriterion().getName() : null)
                      .maxScore(maxScore)
                      .score(s.getScore())
                      .comment(s.getComment())
                      .build();
                })
            .collect(Collectors.toList());
    return EvaluationResponse.builder()
        .id(hashidService.encode(ev.getId()))
        .submissionId(
            ev.getSubmission() != null ? hashidService.encode(ev.getSubmission().getId()) : null)
        .instructorId(
            ev.getInstructor() != null ? hashidService.encode(ev.getInstructor().getId()) : null)
        .instructorName(
            ev.getInstructor() != null
                ? ev.getInstructor().getFirstName() + " " + ev.getInstructor().getLastName()
                : null)
        .overallComment(ev.getOverallComment())
        .totalScore(ev.getTotalScore())
        .maxPossibleScore(maxPossible)
        .isDraft(ev.getIsDraft())
        .publishedAt(ev.getPublishedAt())
        .createdAt(ev.getCreatedAt())
        .hasPdf(ev.getPdfPath() != null)
        .rubricScores(scoreResponses)
        .build();
  }
}
