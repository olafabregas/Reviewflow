package com.reviewflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reviewflow.model.entity.Submission;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByTeam_IdAndAssignment_IdOrderByVersionNumberDesc(Long teamId, Long assignmentId);

    @Query("SELECT MAX(s.versionNumber) FROM Submission s WHERE s.team.id = :teamId AND s.assignment.id = :assignmentId")
    Optional<Integer> findMaxVersionNumber(@Param("teamId") Long teamId, @Param("assignmentId") Long assignmentId);

    @Query("SELECT MAX(s.versionNumber) FROM Submission s WHERE s.student.id = :studentId AND s.assignment.id = :assignmentId")
    Optional<Integer> findMaxVersionNumberByStudent(@Param("studentId") Long studentId, @Param("assignmentId") Long assignmentId);

    @Query("SELECT COALESCE(SUM(s.fileSizeBytes), 0) FROM Submission s")
    Long sumFileSizeBytes();

    List<Submission> findByTeam_IdOrderByVersionNumberDesc(Long teamId);

    List<Submission> findByStudent_IdAndAssignment_IdOrderByVersionNumberDesc(Long studentId, Long assignmentId);

    List<Submission> findByStudent_IdOrderByVersionNumberDesc(Long studentId);

    List<Submission> findByAssignment_Id(Long assignmentId);

    List<Submission> findByAssignment_IdOrderByTeam_IdAscVersionNumberDesc(Long assignmentId);

    @Query("SELECT s FROM Submission s WHERE s.team.id IN (SELECT tm.team.id FROM TeamMember tm WHERE tm.user.id = :userId) ORDER BY s.uploadedAt DESC")
    org.springframework.data.domain.Page<Submission> findByTeamMemberUserId(@Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);
}
