package com.reviewflow.repository;

import com.reviewflow.model.entity.RubricCriterion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RubricCriterionRepository extends JpaRepository<RubricCriterion, Long> {

  List<RubricCriterion> findByAssignmentIdOrderByDisplayOrderAsc(Long assignmentId);
}
