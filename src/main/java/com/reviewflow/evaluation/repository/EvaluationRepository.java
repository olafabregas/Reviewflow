package com.reviewflow.evaluation.repository;

import com.reviewflow.shared.domain.Evaluation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

  @Query(
      """
      SELECT e
      FROM Evaluation e
      JOIN FETCH e.submission s
      JOIN FETCH s.assignment a
      LEFT JOIN FETCH s.team
      LEFT JOIN FETCH s.student
      JOIN FETCH e.instructor
      WHERE e.id = :id
      """)
  Optional<Evaluation> findByIdWithPdfRelations(@Param("id") Long id);

  Optional<Evaluation> findBySubmissionId(Long submissionId);

  List<Evaluation> findByInstructorIdAndSubmissionAssignmentCourseId(
      Long instructorId, Long courseId);

  List<Evaluation> findBySubmissionAssignmentId(Long assignmentId);

  @Query(
      """
      SELECT e
      FROM Evaluation e
      JOIN FETCH e.submission s 
      WHERE s.assignment.id = :assignmentId
          AND e.isDraft = false
      """)
  List<Evaluation> findPublishedByAssignmentIdWithSubmission(
      @Param("assignmentId") Long assignmentId);

  @Query(
      """
      SELECT e
      FROM Evaluation e
      JOIN FETCH e.submission s
      WHERE s.id IN :submissionIds
          AND e.isDraft = false
      """)
  List<Evaluation> findPublishedBySubmissionIds(@Param("submissionIds") List<Long> submissionIds);

  @Query(
      """
      SELECT e
      FROM Evaluation e
      JOIN FETCH e.submission s
      WHERE s.id IN :submissionIds
          AND e.isDraft = false
          AND e.publishedAt IS NOT NULL
      """)
  List<Evaluation> findPublishedFinalBySubmissionIds(
      @Param("submissionIds") List<Long> submissionIds);

  @org.springframework.data.jpa.repository.Query(
      "SELECT e FROM Evaluation e WHERE e.isDraft = false AND e.submission.team.id IN (SELECT"
          + " tm.team.id FROM TeamMember tm WHERE tm.user.id = :userId) ORDER BY e.publishedAt"
          + " DESC")
  org.springframework.data.domain.Page<Evaluation> findPublishedByTeamMemberUserId(
      @org.springframework.data.repository.query.Param("userId") Long userId,
      org.springframework.data.domain.Pageable pageable);
}
