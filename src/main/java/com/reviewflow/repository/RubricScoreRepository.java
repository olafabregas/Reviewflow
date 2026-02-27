package com.reviewflow.repository;

import com.reviewflow.model.entity.RubricScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RubricScoreRepository extends JpaRepository<RubricScore, Long> {

    List<RubricScore> findByEvaluation_Id(Long evaluationId);

    Optional<RubricScore> findByEvaluation_IdAndCriterion_Id(Long evaluationId, Long criterionId);
}
