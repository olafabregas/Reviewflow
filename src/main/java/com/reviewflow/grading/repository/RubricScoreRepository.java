package com.reviewflow.grading.repository;

import com.reviewflow.shared.domain.RubricScore;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RubricScoreRepository extends JpaRepository<RubricScore, Long> {

  @Query(
      """
      SELECT rs
      FROM RubricScore rs
      JOIN FETCH rs.criterion
      WHERE rs.evaluation.id = :evaluationId
      """)
  List<RubricScore> findByEvaluationIdWithCriterion(@Param("evaluationId") Long evaluationId);

  List<RubricScore> findByEvaluationId(Long evaluationId);

  Optional<RubricScore> findByEvaluationIdAndCriterionId(Long evaluationId, Long criterionId);

  boolean existsByCriterionId(Long criterionId);
}
