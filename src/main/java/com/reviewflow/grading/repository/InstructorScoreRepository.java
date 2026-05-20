package com.reviewflow.grading.repository;

import com.reviewflow.shared.domain.InstructorScore;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstructorScoreRepository extends JpaRepository<InstructorScore, Long> {

  Optional<InstructorScore> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

  Optional<InstructorScore> findByAssignmentIdAndTeamId(Long assignmentId, Long teamId);

  Page<InstructorScore> findByAssignmentId(Long assignmentId, Pageable pageable);

  List<InstructorScore> findByAssignmentIdInAndStudentIdAndIsPublishedTrue(
      List<Long> assignmentIds, Long studentId);

  @Query(
      """
      SELECT s FROM InstructorScore s
      WHERE s.assignment.id IN :assignmentIds
          AND s.student.id IN :studentIds
          AND s.isPublished = true
      """)
  List<InstructorScore> findPublishedByAssignmentIdsAndStudentIds(
      @Param("assignmentIds") List<Long> assignmentIds,
      @Param("studentIds") Collection<Long> studentIds);

  List<InstructorScore> findByAssignmentIdInAndTeamIdAndIsPublishedTrue(
      List<Long> assignmentIds, Long teamId);

  List<InstructorScore> findByAssignmentIdInAndIsPublishedTrue(List<Long> assignmentIds);

  List<InstructorScore> findByAssignmentIdAndIsPublishedFalse(Long assignmentId);

  boolean existsByAssignmentId(Long assignmentId);

  boolean existsByAssignmentIdAndIsPublishedTrue(Long assignmentId);

  long countByAssignmentId(Long assignmentId);

  long countByAssignmentIdAndIsPublishedTrue(Long assignmentId);

  long countByAssignmentIdAndIsPublishedFalse(Long assignmentId);
}
