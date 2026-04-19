package com.reviewflow.repository;

import com.reviewflow.model.entity.AssignmentGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssignmentGroupRepository extends JpaRepository<AssignmentGroup, Long> {

    @EntityGraph(attributePaths = {"assignments"})
    List<AssignmentGroup> findByCourse_IdOrderByDisplayOrderAsc(Long courseId);

    @Query("""
            SELECT DISTINCT ag
            FROM AssignmentGroup ag
            LEFT JOIN FETCH ag.assignments a
            LEFT JOIN FETCH a.assignmentModule
            LEFT JOIN FETCH a.rubricCriteria
            WHERE ag.course.id = :courseId
            ORDER BY ag.displayOrder ASC
            """)
    List<AssignmentGroup> findDetailedByCourseId(@Param("courseId") Long courseId);

    Optional<AssignmentGroup> findByCourse_IdAndIsUncategorizedTrue(Long courseId);

    boolean existsByCourse_IdAndId(Long courseId, Long groupId);
}
