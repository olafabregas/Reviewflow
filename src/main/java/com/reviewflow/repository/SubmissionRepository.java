package com.reviewflow.repository;

import com.reviewflow.model.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByTeam_IdAndAssignment_IdOrderByVersionNumberDesc(Long teamId, Long assignmentId);

    @Query("SELECT MAX(s.versionNumber) FROM Submission s WHERE s.team.id = :teamId AND s.assignment.id = :assignmentId")
    Optional<Integer> findMaxVersionNumber(@Param("teamId") Long teamId, @Param("assignmentId") Long assignmentId);

    @Query("SELECT COALESCE(SUM(s.fileSizeBytes), 0) FROM Submission s")
    Long sumFileSizeBytes();
}
