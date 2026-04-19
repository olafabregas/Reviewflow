package com.reviewflow.repository;

import com.reviewflow.model.entity.AssignmentModule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentModuleRepository extends JpaRepository<AssignmentModule, Long> {

    @EntityGraph(attributePaths = {"assignments", "assignments.assignmentGroup"})
    List<AssignmentModule> findByCourse_IdOrderByDisplayOrderAsc(Long courseId);

    Optional<AssignmentModule> findByIdAndCourse_Id(Long id, Long courseId);

    boolean existsByCourse_IdAndId(Long courseId, Long moduleId);
}
