package com.reviewflow.repository;

import com.reviewflow.model.entity.AssignmentGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentGroupRepository extends JpaRepository<AssignmentGroup, Long> {

    @EntityGraph(attributePaths = {"assignments"})
    List<AssignmentGroup> findByCourse_IdOrderByDisplayOrderAsc(Long courseId);

    Optional<AssignmentGroup> findByCourse_IdAndIsUncategorizedTrue(Long courseId);

    boolean existsByCourse_IdAndId(Long courseId, Long groupId);
}
