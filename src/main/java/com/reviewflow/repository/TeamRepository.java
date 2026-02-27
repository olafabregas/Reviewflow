package com.reviewflow.repository;

import com.reviewflow.model.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByAssignment_Id(Long assignmentId);

    @Query("SELECT t FROM Team t JOIN t.members m WHERE t.assignment.id = :assignmentId AND m.user.id = :userId")
    Optional<Team> findByAssignmentIdAndMembersUserId(@Param("assignmentId") Long assignmentId, @Param("userId") Long userId);
}
