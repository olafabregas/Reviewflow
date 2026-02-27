package com.reviewflow.service;

import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.model.entity.*;
import com.reviewflow.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AssignmentRepository assignmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public Team createTeam(Long assignmentId, String teamName, Long creatorId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));
        if (assignment.getTeamLockAt() != null && assignment.getTeamLockAt().isBefore(Instant.now())) {
            throw new IllegalStateException("Team formation is locked");
        }
        if (!courseEnrollmentRepository.existsByCourse_IdAndUser_Id(assignment.getCourse().getId(), creatorId)) {
            throw new IllegalArgumentException("User is not enrolled in this course");
        }
        if (teamRepository.findByAssignmentIdAndMembersUserId(assignmentId, creatorId).isPresent()) {
            throw new IllegalStateException("User already has a team for this assignment");
        }
        User creator = userRepository.findById(creatorId).orElseThrow(() -> new ResourceNotFoundException("User", creatorId));
        Team team = Team.builder()
                .assignment(assignment)
                .name(teamName)
                .isLocked(false)
                .createdBy(creator)
                .createdAt(Instant.now())
                .build();
        team = teamRepository.save(team);
        TeamMember member = TeamMember.builder()
                .team(team)
                .user(creator)
                .assignment(assignment)
                .joinedAt(Instant.now())
                .status(TeamMemberStatus.ACCEPTED)
                .build();
        teamMemberRepository.save(member);
        return team;
    }

    public List<Team> listTeamsForAssignment(Long assignmentId, Long userId, UserRole role) {
        if (role == UserRole.ADMIN || role == UserRole.INSTRUCTOR) {
            return teamRepository.findByAssignment_Id(assignmentId);
        }
        return teamRepository.findByAssignmentIdAndMembersUserId(assignmentId, userId)
                .map(List::of)
                .orElse(List.of());
    }

    public Team getTeamById(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team", id));
    }
}
