package com.reviewflow.repository;

import com.reviewflow.model.entity.RubricCriterion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RubricCriterionRepository extends JpaRepository<RubricCriterion, Long> {

    List<RubricCriterion> findByAssignment_IdOrderByDisplayOrderAsc(Long assignmentId);
}
