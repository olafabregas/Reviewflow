package com.reviewflow.evaluation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.reviewflow.evaluation.dto.EvaluationPdfContext;
import com.reviewflow.shared.domain.Assignment;
import com.reviewflow.shared.domain.Evaluation;
import com.reviewflow.shared.domain.RubricCriterion;
import com.reviewflow.shared.domain.RubricScore;
import com.reviewflow.shared.domain.Submission;
import com.reviewflow.shared.domain.SubmissionType;
import com.reviewflow.shared.domain.Team;
import com.reviewflow.shared.domain.User;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluationServicePdfContextTest {

  @Test
  void toPdfContext_teamSubmission_usesTeamLabel() {
    Evaluation evaluation = buildEvaluation(SubmissionType.TEAM, "Team Alpha", null);
    List<RubricScore> scores =
        List.of(
            RubricScore.builder()
                .criterion(RubricCriterion.builder().name("Design").maxScore(10).build())
                .score(BigDecimal.valueOf(7))
                .comment("Solid")
                .build());

    EvaluationPdfContext context = EvaluationService.toPdfContext(evaluation, scores);

    assertEquals("Team: Team Alpha", context.submitterLabelLine());
    assertEquals("Design", context.rubricRows().get(0).criterionName());
    assertEquals(7, context.rubricRows().get(0).score().intValue());
  }

  @Test
  void toPdfContext_individualSubmission_usesStudentLabelWithoutTeam() {
    User student =
        User.builder()
            .firstName("Ada")
            .lastName("Lovelace")
            .email("ada@example.com")
            .build();
    Evaluation evaluation = buildEvaluation(SubmissionType.INDIVIDUAL, null, student);

    EvaluationPdfContext context = EvaluationService.toPdfContext(evaluation, List.of());

    assertEquals("Student: Ada Lovelace (ada@example.com)", context.submitterLabelLine());
  }

  private static Evaluation buildEvaluation(
      SubmissionType submissionType, String teamName, User student) {
    Assignment assignment =
        Assignment.builder().title("Capstone").submissionType(submissionType).build();
    Submission.SubmissionBuilder submissionBuilder =
        Submission.builder().assignment(assignment).versionNumber(3);
    if (teamName != null) {
      submissionBuilder.team(Team.builder().name(teamName).build());
    }
    if (student != null) {
      submissionBuilder.student(student);
    }
    User instructor = User.builder().firstName("Grace").lastName("Hopper").email("gh@example.com").build();
    return Evaluation.builder()
        .id(99L)
        .submission(submissionBuilder.build())
        .instructor(instructor)
        .totalScore(BigDecimal.TEN)
        .build();
  }
}
