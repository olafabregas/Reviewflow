package com.reviewflow.team.repository;

import com.reviewflow.shared.domain.TeamMember;
import com.reviewflow.shared.domain.TeamMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

  List<TeamMember> findByTeamId(Long teamId);

  Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);

  List<TeamMember> findByUserIdAndStatus(Long userId, TeamMemberStatus status);

  boolean existsByAssignmentIdAndUserId(Long assignmentId, Long userId);

  boolean existsByAssignmentIdAndUserIdAndStatus(
      Long assignmentId, Long userId, TeamMemberStatus status);

  boolean existsByTeamIdAndUserIdAndStatus(Long teamId, Long userId, TeamMemberStatus status);

  List<TeamMember> findByAssignmentIdAndUserIdAndStatus(
      Long assignmentId, Long userId, TeamMemberStatus status);

  @Query(
      "SELECT tm FROM TeamMember tm WHERE tm.assignment.id = :assignmentId AND tm.user.id ="
          + " :userId")
  List<TeamMember> findByAssignmentIdAndUserId(
      @Param("assignmentId") Long assignmentId, @Param("userId") Long userId);

  @Query(
      """
      SELECT tm
      FROM TeamMember tm
      JOIN FETCH tm.user u
      WHERE tm.team.id IN :teamIds
        AND tm.status = :status
      """)
  List<TeamMember> findByTeamIdsAndStatusWithUser(
      @Param("teamIds") List<Long> teamIds, @Param("status") TeamMemberStatus status);

  long countByUserId(Long userId);
}
