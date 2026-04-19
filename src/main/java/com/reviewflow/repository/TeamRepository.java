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

    boolean existsByAssignment_IdAndName(Long assignmentId, String name);

    boolean existsByAssignment_IdAndNameAndIdNot(Long assignmentId, String name, Long id);

    @Query("""
            SELECT DISTINCT t
            FROM Team t
            JOIN t.members m
            WHERE t.assignment.id IN :assignmentIds
                AND m.user.id = :userId
            """)
    List<Team> findByAssignmentIdsAndMemberUserId(
            @Param("assignmentIds") List<Long> assignmentIds,
            @Param("userId") Long userId
    );
}
