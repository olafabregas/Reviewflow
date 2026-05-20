package com.reviewflow.team.repository;

import com.reviewflow.shared.domain.Team;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {

  List<Team> findByAssignmentId(Long assignmentId);

  @Query(
      """
      SELECT DISTINCT t FROM Team t
      LEFT JOIN FETCH t.members m
      LEFT JOIN FETCH m.user
      WHERE t.assignment.id = :assignmentId
      """)
  List<Team> findByAssignmentIdWithMembers(@Param("assignmentId") Long assignmentId);

  @Query(
      """
      SELECT DISTINCT t
      FROM Team t
      JOIN FETCH t.members m
      JOIN FETCH m.user
      WHERE t.assignment.id IN :assignmentIds
          AND m.user.id IN :userIds
      """)
  List<Team> findByAssignmentIdsAndMemberUserIds(
      @Param("assignmentIds") List<Long> assignmentIds, @Param("userIds") List<Long> userIds);

  @Query(
      "SELECT t FROM Team t JOIN t.members m WHERE t.assignment.id = :assignmentId AND m.user.id ="
          + " :userId")
  Optional<Team> findByAssignmentIdAndMembersUserId(
      @Param("assignmentId") Long assignmentId, @Param("userId") Long userId);

  boolean existsByAssignmentIdAndName(Long assignmentId, String name);

  boolean existsByAssignmentIdAndNameAndIdNot(Long assignmentId, String name, Long id);

  @Query(
      """
      SELECT DISTINCT t
      FROM Team t
      JOIN t.members m
      WHERE t.assignment.id IN :assignmentIds
          AND m.user.id = :userId
      """)
  List<Team> findByAssignmentIdsAndMemberUserId(
      @Param("assignmentIds") List<Long> assignmentIds, @Param("userId") Long userId);
}
