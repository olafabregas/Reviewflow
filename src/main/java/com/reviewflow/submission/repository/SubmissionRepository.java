package com.reviewflow.submission.repository;

import com.reviewflow.shared.domain.Submission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

  List<Submission> findByTeamIdAndAssignmentIdOrderByVersionNumberDesc(
      Long teamId, Long assignmentId);

  @Query(
      "SELECT MAX(s.versionNumber) FROM Submission s WHERE s.team.id = :teamId AND s.assignment.id"
          + " = :assignmentId")
  Optional<Integer> findMaxVersionNumber(
      @Param("teamId") Long teamId, @Param("assignmentId") Long assignmentId);

  @Query(
      "SELECT MAX(s.versionNumber) FROM Submission s WHERE s.student.id = :studentId AND"
          + " s.assignment.id = :assignmentId")
  Optional<Integer> findMaxVersionNumberByStudent(
      @Param("studentId") Long studentId, @Param("assignmentId") Long assignmentId);

  @Query("SELECT COALESCE(SUM(s.fileSizeBytes), 0) FROM Submission s")
  Long sumFileSizeBytes();

  List<Submission> findByTeamIdOrderByVersionNumberDesc(Long teamId);

  List<Submission> findByStudentIdAndAssignmentIdOrderByVersionNumberDesc(
      Long studentId, Long assignmentId);

  List<Submission> findByStudentIdOrderByVersionNumberDesc(Long studentId);

  List<Submission> findByAssignmentId(Long assignmentId);

  List<Submission> findByAssignmentIdOrderByTeamIdAscVersionNumberDesc(Long assignmentId);

  @Query(
      """
      SELECT s
      FROM Submission s
      JOIN FETCH s.team t
      WHERE s.assignment.id = :assignmentId
          AND s.team IS NOT NULL
          AND s.versionNumber = (
                  SELECT MAX(s2.versionNumber)
                  FROM Submission s2
                  WHERE s2.assignment.id = :assignmentId
                      AND s2.team.id = s.team.id
          )
      ORDER BY t.name ASC
      """)
  Page<Submission> findLatestTeamSubmissionsByAssignmentId(
      @Param("assignmentId") Long assignmentId, Pageable pageable);

  @Query(
      """
      SELECT s
      FROM Submission s
      JOIN FETCH s.student st
      WHERE s.assignment.id = :assignmentId
          AND s.student IS NOT NULL
          AND s.versionNumber = (
                  SELECT MAX(s2.versionNumber)
                  FROM Submission s2
                  WHERE s2.assignment.id = :assignmentId
                      AND s2.student.id = s.student.id
          )
      ORDER BY st.lastName ASC, st.firstName ASC
      """)
  Page<Submission> findLatestIndividualSubmissionsByAssignmentId(
      @Param("assignmentId") Long assignmentId, Pageable pageable);

  @Query(
      """
      SELECT s
      FROM Submission s
      WHERE s.assignment.id IN :assignmentIds
          AND s.student.id IN :studentIds
          AND s.versionNumber = (
              SELECT MAX(s2.versionNumber)
              FROM Submission s2
              WHERE s2.assignment.id = s.assignment.id
                  AND s2.student.id = s.student.id
          )
      """)
  List<Submission> findLatestByAssignmentIdsAndStudentIds(
      @Param("assignmentIds") List<Long> assignmentIds,
      @Param("studentIds") List<Long> studentIds);

  @Query(
      """
      SELECT s
      FROM Submission s
      WHERE s.assignment.id IN :assignmentIds
          AND s.team.id IN :teamIds
          AND s.versionNumber = (
              SELECT MAX(s2.versionNumber)
              FROM Submission s2
              WHERE s2.assignment.id = s.assignment.id
                  AND s2.team.id = s.team.id
          )
      """)
  List<Submission> findLatestByAssignmentIdsAndTeamIds(
      @Param("assignmentIds") List<Long> assignmentIds, @Param("teamIds") List<Long> teamIds);

  @Query(
      "SELECT s FROM Submission s WHERE s.team.id IN (SELECT tm.team.id FROM TeamMember tm WHERE"
          + " tm.user.id = :userId) ORDER BY s.uploadedAt DESC")
  org.springframework.data.domain.Page<Submission> findByTeamMemberUserId(
      @Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);

  @Query(
      """
      SELECT s
      FROM Submission s
      WHERE s.assignment.id IN :assignmentIds
          AND s.student.id = :studentId
          AND s.versionNumber = (
              SELECT MAX(s2.versionNumber)
              FROM Submission s2
              WHERE s2.assignment.id = s.assignment.id
                  AND s2.student.id = :studentId
          )
      """)
  List<Submission> findLatestByAssignmentIdsAndStudentId(
      @Param("assignmentIds") List<Long> assignmentIds, @Param("studentId") Long studentId);

  @Query(
      """
      SELECT s
      FROM Submission s
      WHERE s.assignment.id IN :assignmentIds
          AND s.team.id = :teamId
          AND s.versionNumber = (
              SELECT MAX(s2.versionNumber)
              FROM Submission s2
              WHERE s2.assignment.id = s.assignment.id
                  AND s2.team.id = :teamId
          )
      """)
  List<Submission> findLatestByAssignmentIdsAndTeamId(
      @Param("assignmentIds") List<Long> assignmentIds, @Param("teamId") Long teamId);
}
