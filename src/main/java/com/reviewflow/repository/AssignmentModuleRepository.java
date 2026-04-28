package com.reviewflow.repository;

import com.reviewflow.model.entity.AssignmentModule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentModuleRepository extends JpaRepository<AssignmentModule, Long> {

  @EntityGraph(attributePaths = {"assignments", "assignments.assignmentGroup"})
  List<AssignmentModule> findByCourseIdOrderByDisplayOrderAsc(Long courseId);

  Optional<AssignmentModule> findByIdAndCourseId(Long id, Long courseId);

  boolean existsByCourseIdAndId(Long courseId, Long moduleId);
}
