package com.reviewflow.repository;

import com.reviewflow.model.entity.RubricScore;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RubricScoreRepository extends JpaRepository<RubricScore, Long> {

  List<RubricScore> findByEvaluationId(Long evaluationId);

  Optional<RubricScore> findByEvaluationIdAndCriterionId(Long evaluationId, Long criterionId);

  boolean existsByCriterionId(Long criterionId);
}
