package com.reviewflow.repository;

import com.reviewflow.model.entity.InstructorScore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InstructorScoreRepository extends JpaRepository<InstructorScore, Long> {

    Optional<InstructorScore> findByAssignment_IdAndStudent_Id(Long assignmentId, Long studentId);

    Optional<InstructorScore> findByAssignment_IdAndTeam_Id(Long assignmentId, Long teamId);

    Page<InstructorScore> findByAssignment_Id(Long assignmentId, Pageable pageable);

    List<InstructorScore> findByAssignment_IdAndIsPublishedFalse(Long assignmentId);

    boolean existsByAssignment_Id(Long assignmentId);

    boolean existsByAssignment_IdAndIsPublishedTrue(Long assignmentId);

    long countByAssignment_Id(Long assignmentId);

    long countByAssignment_IdAndIsPublishedTrue(Long assignmentId);

    long countByAssignment_IdAndIsPublishedFalse(Long assignmentId);
}