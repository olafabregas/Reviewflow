package com.reviewflow.repository;

import com.reviewflow.model.entity.AssignmentGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentGroupRepository extends JpaRepository<AssignmentGroup, Long> {

  @EntityGraph(attributePaths = {"assignments"})
  List<AssignmentGroup> findByCourseIdOrderByDisplayOrderAsc(Long courseId);

  @Query(
      """
      SELECT DISTINCT ag
      FROM AssignmentGroup ag
      LEFT JOIN FETCH ag.assignments a
      LEFT JOIN FETCH a.assignmentModule
      LEFT JOIN FETCH a.rubricCriteria
      WHERE ag.course.id = :courseId
      ORDER BY ag.displayOrder ASC
      """)
  List<AssignmentGroup> findDetailedByCourseId(@Param("courseId") Long courseId);

  Optional<AssignmentGroup> findByCourseIdAndIsUncategorizedTrue(Long courseId);

  boolean existsByCourseIdAndId(Long courseId, Long groupId);
}
