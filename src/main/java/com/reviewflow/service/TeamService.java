package com.reviewflow.service;

import com.reviewflow.event.TeamInviteEvent;
import com.reviewflow.event.TeamLockedEvent;
import com.reviewflow.exception.AccessDeniedException;
import com.reviewflow.exception.BusinessRuleException;
import com.reviewflow.exception.DuplicateResourceException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.model.entity.*;
import com.reviewflow.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AssignmentRepository assignmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AdminStatsService adminStatsService;

    @Transactional
    public Team createTeam(Long assignmentId, String teamName, Long creatorId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));
        
        // Check if assignment is published
        if (!Boolean.TRUE.equals(assignment.getIsPublished())) {
            throw new ResourceNotFoundException("Assignment", assignmentId);
        }
        
        // Check if team formation is closed
        if (assignment.getTeamLockAt() != null && assignment.getTeamLockAt().isBefore(Instant.now())) {
            throw new DuplicateResourceException("Team formation is closed for this assignment", "TEAM_FORMATION_CLOSED");
        }
        
        // Check if user is enrolled
        if (!courseEnrollmentRepository.existsByCourse_IdAndUser_Id(assignment.getCourse().getId(), creatorId)) {
            throw new AccessDeniedException("You do not have access to this course");
        }
        
        // Check if user already has a team
        if (teamMemberRepository.existsByAssignment_IdAndUser_IdAndStatus(assignmentId, creatorId, TeamMemberStatus.ACCEPTED)) {
            throw new BusinessRuleException("You are already a member of a team for this assignment", "ALREADY_IN_TEAM");
        }
        
        // Check for duplicate team name
        if (teamRepository.existsByAssignment_IdAndName(assignmentId, teamName)) {
            throw new DuplicateResourceException("Team name already exists for this assignment", "TEAM_NAME_EXISTS");
        }
        
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", creatorId));
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
        adminStatsService.evictStats();
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
    
    public Team getTeamByIdWithAccessControl(Long id, Long userId, UserRole role) {
        Team team = getTeamById(id);
        
        // ADMIN can access any team
        if (role == UserRole.ADMIN) {
            return team;
        }
        
        // INSTRUCTOR can access if team belongs to their course
        if (role == UserRole.INSTRUCTOR) {
            Long courseId = team.getAssignment().getCourse().getId();
            if (courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, userId)) {
                return team;
            }
        }
        
        // STUDENT can access only if they are a member
        if (role == UserRole.STUDENT) {
            boolean isMember = team.getMembers().stream()
                    .anyMatch(m -> m.getUser().getId().equals(userId));
            if (isMember) {
                return team;
            }
        }
        
        throw new AccessDeniedException("You do not have access to this team");
    }

    @Transactional
    public Team renameTeam(Long teamId, String newName, Long userId) {
        Team team = getTeamById(teamId);
        
        if (Boolean.TRUE.equals(team.getIsLocked())) {
            throw new AccessDeniedException("Team is locked");
        }
        
        if (team.getCreatedBy() == null || !team.getCreatedBy().getId().equals(userId)) {
            throw new AccessDeniedException("Only the team creator can rename the team");
        }
        
        // Check for duplicate team name
        if (teamRepository.existsByAssignment_IdAndNameAndIdNot(team.getAssignment().getId(), newName, teamId)) {
            throw new DuplicateResourceException("Team name already exists for this assignment", "TEAM_NAME_EXISTS");
        }
        
        team.setName(newName);
        return teamRepository.save(team);
    }

    @Transactional
    public TeamMember inviteMember(Long teamId, String inviteeEmail, Long inviterId) {
        Team team = getTeamById(teamId);
        Assignment assignment = team.getAssignment();
        
        // Check if team is locked
        if (Boolean.TRUE.equals(team.getIsLocked())
                || (assignment.getTeamLockAt() != null && assignment.getTeamLockAt().isBefore(Instant.now()))) {
            throw new AccessDeniedException("Team is locked");
        }
        
        // Check if requester is the team creator
        if (team.getCreatedBy() == null || !team.getCreatedBy().getId().equals(inviterId)) {
            throw new AccessDeniedException("Only the team creator can invite members");
        }
        
        // Check if team is full
        long acceptedCount = team.getMembers().stream()
                .filter(m -> m.getStatus() == TeamMemberStatus.ACCEPTED).count();
        if (acceptedCount >= assignment.getMaxTeamSize()) {
            throw new BusinessRuleException("Team has reached maximum size of " + assignment.getMaxTeamSize(), "TEAM_FULL");
        }
        
        // Find invitee by email
        User invitee = userRepository.findByEmail(inviteeEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User with email " + inviteeEmail + " not found"));
        
        // Check if inviter is trying to invite themselves
        if (invitee.getId().equals(inviterId)) {
            throw new BusinessRuleException("You cannot invite yourself", "INVALID_REQUEST");
        }
        
        // Check if invitee is enrolled
        if (!courseEnrollmentRepository.existsByCourse_IdAndUser_Id(assignment.getCourse().getId(), invitee.getId())) {
            throw new BusinessRuleException("Invitee is not enrolled in this course", "NOT_ENROLLED");
        }
        
        // Check if invitee already has a team (any status ACCEPTED)
        if (teamMemberRepository.existsByAssignment_IdAndUser_IdAndStatus(assignment.getId(), invitee.getId(), TeamMemberStatus.ACCEPTED)) {
            throw new BusinessRuleException("Invitee already has a team for this assignment", "ALREADY_IN_TEAM");
        }
        
        // Check for duplicate pending invite
        if (teamMemberRepository.existsByTeam_IdAndUser_IdAndStatus(teamId, invitee.getId(), TeamMemberStatus.PENDING)) {
            throw new BusinessRuleException("Invite already sent to this user", "INVITE_ALREADY_SENT");
        }
        
        User inviter = userRepository.findById(inviterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", inviterId));
        
        TeamMember member = TeamMember.builder()
                .team(team)
                .user(invitee)
                .assignment(assignment)
                .joinedAt(Instant.now())
                .invitedBy(inviter)
                .status(TeamMemberStatus.PENDING)
                .build();
        TeamMember saved = teamMemberRepository.save(member);
        
        eventPublisher.publishEvent(new TeamInviteEvent(
                invitee.getId(),
                team.getId(),
                team.getName(),
                inviter.getFirstName(),
                assignment.getId(),
                assignment.getTitle()
        ));
        
        return saved;
    }

    @Transactional
    public TeamMember respondToInvite(Long teamMemberId, boolean accept, Long userId) {
        TeamMember member = teamMemberRepository.findById(teamMemberId)
                .orElseThrow(() -> new ResourceNotFoundException("TeamMember", teamMemberId));
        
        if (!member.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("This invite does not belong to you");
        }
        
        if (member.getStatus() != TeamMemberStatus.PENDING) {
            throw new BusinessRuleException("Invite already responded to", "ALREADY_RESPONDED");
        }
        
        Assignment assignment = member.getAssignment();
        
        // Check if team is locked
        if (Boolean.TRUE.equals(member.getTeam().getIsLocked())
                || (assignment.getTeamLockAt() != null && assignment.getTeamLockAt().isBefore(Instant.now()))) {
            throw new AccessDeniedException("Team is locked");
        }
        
        if (accept) {
            // Check if user already belongs to another team
            if (teamMemberRepository.existsByAssignment_IdAndUser_IdAndStatus(assignment.getId(), userId, TeamMemberStatus.ACCEPTED)) {
                throw new BusinessRuleException("You are already a member of a team for this assignment", "ALREADY_IN_TEAM");
            }
            
            // Check if team is now full
            long acceptedCount = member.getTeam().getMembers().stream()
                    .filter(m -> m.getStatus() == TeamMemberStatus.ACCEPTED).count();
            if (acceptedCount >= assignment.getMaxTeamSize()) {
                throw new BusinessRuleException("Team has reached maximum size of " + assignment.getMaxTeamSize(), "TEAM_FULL");
            }
            
            member.setStatus(TeamMemberStatus.ACCEPTED);
            
            // Decline all other pending invites for same assignment
            List<TeamMember> otherPending = teamMemberRepository
                    .findByAssignment_IdAndUser_IdAndStatus(assignment.getId(), userId, TeamMemberStatus.PENDING);
            otherPending.stream()
                    .filter(m -> !m.getId().equals(teamMemberId))
                    .forEach(m -> {
                        m.setStatus(TeamMemberStatus.DECLINED);
                        teamMemberRepository.save(m);
                    });
        } else {
            member.setStatus(TeamMemberStatus.DECLINED);
        }
        return teamMemberRepository.save(member);
    }

    @Transactional
    public void removeMember(Long teamId, Long memberUserId, Long requesterId) {
        Team team = getTeamById(teamId);
        
        if (Boolean.TRUE.equals(team.getIsLocked())) {
            throw new AccessDeniedException("Team is locked");
        }
        
        // Check if requester is team creator or instructor
        boolean isCreator = team.getCreatedBy() != null && team.getCreatedBy().getId().equals(requesterId);
        boolean isInstructor = courseInstructorRepository.existsByCourse_IdAndUser_Id(
                team.getAssignment().getCourse().getId(), requesterId);
        
        if (!isCreator && !isInstructor) {
            throw new AccessDeniedException("Only the team creator or instructor can remove members");
        }
        
        // Cannot remove the team creator
        if (team.getCreatedBy() != null && team.getCreatedBy().getId().equals(memberUserId)) {
            throw new BusinessRuleException("Cannot remove the team creator", "CANNOT_REMOVE_CREATOR");
        }
        
        TeamMember member = teamMemberRepository.findByTeam_IdAndUser_Id(teamId, memberUserId)
                .orElseThrow(() -> new ResourceNotFoundException("TeamMember not found"));
        teamMemberRepository.delete(member);
    }

    @Transactional
    public Team lockTeam(Long teamId) {
        Team team = getTeamById(teamId);
        
        if (Boolean.TRUE.equals(team.getIsLocked())) {
            throw new BusinessRuleException("Team is already locked", "ALREADY_LOCKED");
        }
        
        team.setIsLocked(true);
        Team saved = teamRepository.save(team);
        
        List<Long> memberIds = team.getMembers().stream()
                .filter(m -> m.getStatus() == TeamMemberStatus.ACCEPTED)
                .map(m -> m.getUser().getId())
                .toList();
        
        eventPublisher.publishEvent(new TeamLockedEvent(
                memberIds,
                team.getId(),
                team.getName(),
                team.getAssignment().getId(),
                team.getAssignment().getTitle()
        ));
        
        return saved;
    }

    @Transactional
    public List<Team> autoAssignTeams(Long assignmentId, Long instructorId, int maxTeamSize) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));
        
        if (!courseInstructorRepository.existsByCourse_IdAndUser_Id(assignment.getCourse().getId(), instructorId)) {
            throw new AccessDeniedException("Not instructor of this course");
        }
        
        // Get students enrolled but not yet in a team for this assignment
        List<Long> enrolledUserIds = courseEnrollmentRepository.findByCourse_Id(assignment.getCourse().getId())
                .stream().map(e -> e.getUser().getId()).toList();
        List<Long> unassigned = enrolledUserIds.stream()
                .filter(uid -> !teamMemberRepository.existsByAssignment_IdAndUser_IdAndStatus(assignmentId, uid, TeamMemberStatus.ACCEPTED))
                .toList();
        
        if (unassigned.isEmpty()) {
            throw new BusinessRuleException("All students already have teams", "ALL_ASSIGNED");
        }
        
        List<Team> createdTeams = new ArrayList<>();
        int teamNumber = teamRepository.findByAssignment_Id(assignmentId).size() + 1;
        List<Long> batch = new ArrayList<>();
        for (Long uid : unassigned) {
            batch.add(uid);
            if (batch.size() == maxTeamSize) {
                createdTeams.add(createAutoTeam(assignment, batch, "Auto Team " + teamNumber++));
                batch = new ArrayList<>();
            }
        }
        if (!batch.isEmpty()) {
            createdTeams.add(createAutoTeam(assignment, batch, "Auto Team " + teamNumber));
        }
        return createdTeams;
    }

    private Team createAutoTeam(Assignment assignment, List<Long> userIds, String name) {
        User firstUser = userRepository.findById(userIds.get(0))
                .orElseThrow(() -> new ResourceNotFoundException("User", userIds.get(0)));
        Team team = Team.builder()
                .assignment(assignment)
                .name(name)
                .isLocked(false)
                .createdBy(firstUser)
                .createdAt(Instant.now())
                .build();
        team = teamRepository.save(team);
        for (Long uid : userIds) {
            User u = userRepository.findById(uid).orElseThrow(() -> new ResourceNotFoundException("User", uid));
            TeamMember m = TeamMember.builder()
                    .team(team)
                    .user(u)
                    .assignment(assignment)
                    .joinedAt(Instant.now())
                    .status(TeamMemberStatus.ACCEPTED)
                    .build();
            teamMemberRepository.save(m);
        }
        return team;
    }

    public List<TeamMember> getPendingInvitesForUser(Long userId) {
        return teamMemberRepository.findByUser_IdAndStatus(userId, TeamMemberStatus.PENDING);
    }
}
