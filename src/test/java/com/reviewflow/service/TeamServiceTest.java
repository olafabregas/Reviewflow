package com.reviewflow.service;

import com.reviewflow.exception.AccessDeniedException;
import com.reviewflow.exception.BusinessRuleException;
import com.reviewflow.exception.DuplicateResourceException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.exception.TeamNotAllowedException;
import com.reviewflow.event.TeamInviteEvent;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.Course;
import com.reviewflow.model.entity.Team;
import com.reviewflow.model.entity.TeamMember;
import com.reviewflow.model.entity.TeamMemberStatus;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.TeamMemberRepository;
import com.reviewflow.repository.TeamRepository;
import com.reviewflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock
    private CourseInstructorRepository courseInstructorRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AdminStatsService adminStatsService;

    @InjectMocks
    private TeamService teamService;

    private Assignment teamAssignment(Long assignmentId, Long courseId) {
        return Assignment.builder()
                .id(assignmentId)
                .submissionType(SubmissionType.TEAM)
                .isPublished(true)
                .maxTeamSize(3)
                .course(Course.builder().id(courseId).build())
                .build();
    }

    private User user(Long id, String email, UserRole role) {
        return User.builder()
                .id(id)
                .email(email)
                .passwordHash("x")
                .firstName("First" + id)
                .lastName("Last" + id)
                .role(role)
                .isActive(true)
                .build();
    }

    @Test
    void createTeam_individualAssignment_throwsTeamNotAllowed() {
        Long assignmentId = 10L;
        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .submissionType(SubmissionType.INDIVIDUAL)
                .build();

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThrows(TeamNotAllowedException.class, () ->
                teamService.createTeam(assignmentId, "Solo Team", 1L));
    }

        @Test
    void createTeam_assignmentNotPublished_throwsResourceNotFound() {
        Long assignmentId = 11L;
        Assignment assignment = Assignment.builder()
            .id(assignmentId)
            .submissionType(SubmissionType.TEAM)
            .isPublished(false)
            .build();

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThrows(ResourceNotFoundException.class, () ->
            teamService.createTeam(assignmentId, "Team A", 1L));
        }

        @Test
        void createTeam_teamLockClosed_throwsDuplicateResource() {
        Long assignmentId = 12L;
        Assignment assignment = teamAssignment(assignmentId, 2L);
        assignment.setTeamLockAt(Instant.now().minusSeconds(60));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThrows(DuplicateResourceException.class, () ->
            teamService.createTeam(assignmentId, "Team A", 1L));
        }

        @Test
        void createTeam_creatorNotEnrolled_throwsAccessDenied() {
        Long assignmentId = 13L;
        Long creatorId = 20L;
        Assignment assignment = teamAssignment(assignmentId, 3L);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(3L, creatorId)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
            teamService.createTeam(assignmentId, "Team A", creatorId));
        }

        @Test
        void createTeam_creatorAlreadyInTeam_throwsBusinessRule() {
        Long assignmentId = 14L;
        Long creatorId = 21L;
        Assignment assignment = teamAssignment(assignmentId, 4L);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(4L, creatorId)).thenReturn(true);
        when(teamMemberRepository.existsByAssignment_IdAndUser_IdAndStatus(assignmentId, creatorId, TeamMemberStatus.ACCEPTED))
            .thenReturn(true);

        assertThrows(BusinessRuleException.class, () ->
            teamService.createTeam(assignmentId, "Team A", creatorId));
        }

        @Test
        void createTeam_duplicateName_throwsDuplicateResource() {
        Long assignmentId = 15L;
        Long creatorId = 22L;
        Assignment assignment = teamAssignment(assignmentId, 5L);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(5L, creatorId)).thenReturn(true);
        when(teamMemberRepository.existsByAssignment_IdAndUser_IdAndStatus(assignmentId, creatorId, TeamMemberStatus.ACCEPTED))
            .thenReturn(false);
        when(teamRepository.existsByAssignment_IdAndName(assignmentId, "Team A")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () ->
            teamService.createTeam(assignmentId, "Team A", creatorId));
        }

        @Test
        void createTeam_happyPath_savesTeamAndCreatorMembership() {
        Long assignmentId = 16L;
        Long creatorId = 23L;
        Assignment assignment = teamAssignment(assignmentId, 6L);
        User creator = user(creatorId, "creator@test.local", UserRole.STUDENT);

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(6L, creatorId)).thenReturn(true);
        when(teamMemberRepository.existsByAssignment_IdAndUser_IdAndStatus(assignmentId, creatorId, TeamMemberStatus.ACCEPTED))
            .thenReturn(false);
        when(teamRepository.existsByAssignment_IdAndName(assignmentId, "Alpha")).thenReturn(false);
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team t = invocation.getArgument(0);
            t.setId(501L);
            return t;
        });

        Team created = teamService.createTeam(assignmentId, "Alpha", creatorId);

        assertEquals(501L, created.getId());
        assertEquals("Alpha", created.getName());
        assertEquals(assignmentId, created.getAssignment().getId());

        ArgumentCaptor<TeamMember> memberCaptor = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).save(memberCaptor.capture());
        assertEquals(TeamMemberStatus.ACCEPTED, memberCaptor.getValue().getStatus());
        assertEquals(creatorId, memberCaptor.getValue().getUser().getId());
        verify(adminStatsService, times(1)).evictStats();
        }

        @Test
        void inviteMember_individualAssignment_throwsTeamNotAllowed() {
        Long teamId = 21L;
        Team team = Team.builder()
            .id(teamId)
            .assignment(Assignment.builder()
                .id(31L)
                .submissionType(SubmissionType.INDIVIDUAL)
                .build())
            .build();
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        assertThrows(TeamNotAllowedException.class, () ->
            teamService.inviteMember(teamId, "invitee@test.local", 1L));
        }

        @Test
        void inviteMember_teamLocked_throwsAccessDenied() {
        Long teamId = 22L;
        Team team = Team.builder()
            .id(teamId)
            .isLocked(true)
            .assignment(teamAssignment(32L, 99L))
            .build();
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        assertThrows(AccessDeniedException.class, () ->
            teamService.inviteMember(teamId, "invitee@test.local", 1L));
        }

        @Test
        void inviteMember_inviterNotCreator_throwsAccessDenied() {
        Long teamId = 23L;
        Assignment assignment = teamAssignment(33L, 99L);
        Team team = Team.builder()
            .id(teamId)
            .isLocked(false)
            .assignment(assignment)
            .createdBy(user(300L, "creator@test.local", UserRole.STUDENT))
            .members(List.of())
            .build();
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        assertThrows(AccessDeniedException.class, () ->
            teamService.inviteMember(teamId, "invitee@test.local", 301L));
        }

        @Test
        void inviteMember_teamAtMaxSize_throwsBusinessRule() {
        Long teamId = 24L;
        Long inviterId = 302L;
        Assignment assignment = teamAssignment(34L, 99L);
        assignment.setMaxTeamSize(1);
        TeamMember accepted = TeamMember.builder().status(TeamMemberStatus.ACCEPTED).build();
        Team team = Team.builder()
            .id(teamId)
            .assignment(assignment)
            .createdBy(user(inviterId, "creator@test.local", UserRole.STUDENT))
            .members(List.of(accepted))
            .build();
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        assertThrows(BusinessRuleException.class, () ->
            teamService.inviteMember(teamId, "invitee@test.local", inviterId));
        }

        @Test
        void inviteMember_inviteeNotFound_throwsResourceNotFound() {
        Long teamId = 25L;
        Long inviterId = 303L;
        Assignment assignment = teamAssignment(35L, 99L);
        Team team = Team.builder()
            .id(teamId)
            .assignment(assignment)
            .createdBy(user(inviterId, "creator@test.local", UserRole.STUDENT))
            .members(List.of())
            .build();

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(userRepository.findByEmail("missing@test.local")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            teamService.inviteMember(teamId, "missing@test.local", inviterId));
        }

        @Test
        void inviteMember_inviteeNotEnrolled_throwsBusinessRule() {
        Long teamId = 26L;
        Long inviterId = 304L;
        Assignment assignment = teamAssignment(36L, 100L);
        Team team = Team.builder()
            .id(teamId)
            .assignment(assignment)
            .createdBy(user(inviterId, "creator@test.local", UserRole.STUDENT))
            .members(List.of())
            .build();
        User invitee = user(401L, "invitee@test.local", UserRole.STUDENT);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(userRepository.findByEmail("invitee@test.local")).thenReturn(Optional.of(invitee));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(100L, 401L)).thenReturn(false);

        assertThrows(BusinessRuleException.class, () ->
            teamService.inviteMember(teamId, "invitee@test.local", inviterId));
        }

        @Test
        void inviteMember_inviteeAlreadyInTeam_throwsBusinessRule() {
        Long teamId = 27L;
        Long inviterId = 305L;
        Assignment assignment = teamAssignment(37L, 101L);
        Team team = Team.builder()
            .id(teamId)
            .assignment(assignment)
            .createdBy(user(inviterId, "creator@test.local", UserRole.STUDENT))
            .members(List.of())
            .build();
        User invitee = user(402L, "invitee@test.local", UserRole.STUDENT);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(userRepository.findByEmail("invitee@test.local")).thenReturn(Optional.of(invitee));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(101L, 402L)).thenReturn(true);
        when(teamMemberRepository.existsByAssignment_IdAndUser_IdAndStatus(37L, 402L, TeamMemberStatus.ACCEPTED))
            .thenReturn(true);

        assertThrows(BusinessRuleException.class, () ->
            teamService.inviteMember(teamId, "invitee@test.local", inviterId));
        }

        @Test
        void inviteMember_duplicatePendingInvite_throwsBusinessRule() {
        Long teamId = 28L;
        Long inviterId = 306L;
        Assignment assignment = teamAssignment(38L, 102L);
        Team team = Team.builder()
            .id(teamId)
            .assignment(assignment)
            .createdBy(user(inviterId, "creator@test.local", UserRole.STUDENT))
            .members(List.of())
            .build();
        User invitee = user(403L, "invitee@test.local", UserRole.STUDENT);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(userRepository.findByEmail("invitee@test.local")).thenReturn(Optional.of(invitee));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(102L, 403L)).thenReturn(true);
        when(teamMemberRepository.existsByAssignment_IdAndUser_IdAndStatus(38L, 403L, TeamMemberStatus.ACCEPTED))
            .thenReturn(false);
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndStatus(teamId, 403L, TeamMemberStatus.PENDING))
            .thenReturn(true);

        assertThrows(BusinessRuleException.class, () ->
            teamService.inviteMember(teamId, "invitee@test.local", inviterId));
        }

        @Test
        void inviteMember_happyPath_savesPendingMemberAndPublishesEvent() {
        Long teamId = 29L;
        Long inviterId = 307L;
        Assignment assignment = teamAssignment(39L, 103L);
        assignment.setTitle("PRD Team Assignment");

        User inviter = user(inviterId, "creator@test.local", UserRole.STUDENT);
        User invitee = user(404L, "invitee@test.local", UserRole.STUDENT);
        TeamMember accepted = TeamMember.builder()
            .status(TeamMemberStatus.ACCEPTED)
            .build();
        Team team = Team.builder()
            .id(teamId)
            .name("Alpha Team")
            .assignment(assignment)
            .createdBy(inviter)
            .isLocked(false)
            .members(List.of(accepted))
            .build();

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(userRepository.findByEmail("invitee@test.local")).thenReturn(Optional.of(invitee));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(103L, 404L)).thenReturn(true);
        when(teamMemberRepository.existsByAssignment_IdAndUser_IdAndStatus(39L, 404L, TeamMemberStatus.ACCEPTED))
            .thenReturn(false);
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndStatus(teamId, 404L, TeamMemberStatus.PENDING))
            .thenReturn(false);
        when(userRepository.findById(inviterId)).thenReturn(Optional.of(inviter));
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(invocation -> {
            TeamMember m = invocation.getArgument(0);
            m.setId(901L);
            return m;
        });

        TeamMember saved = teamService.inviteMember(teamId, "invitee@test.local", inviterId);

        assertEquals(901L, saved.getId());
        assertEquals(TeamMemberStatus.PENDING, saved.getStatus());
        assertEquals(invitee.getId(), saved.getUser().getId());

        ArgumentCaptor<TeamInviteEvent> eventCaptor = ArgumentCaptor.forClass(TeamInviteEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        TeamInviteEvent event = eventCaptor.getValue();
        assertEquals(404L, event.inviteeUserId());
        assertEquals(teamId, event.teamId());
        assertEquals("Alpha Team", event.teamName());
        assertEquals("First307", event.invitedByFirstName());
        assertEquals(39L, event.assignmentId());
        assertEquals("PRD Team Assignment", event.assignmentTitle());
    }

    @Test
    void inviteMember_teamLockAtExpired_throwsAccessDenied() {
        Long teamId = 30L;
        Assignment assignment = teamAssignment(40L, 104L);
        assignment.setTeamLockAt(Instant.now().minusSeconds(60));
        Team team = Team.builder()
                .id(teamId)
                .isLocked(false)
                .assignment(assignment)
                .createdBy(user(308L, "creator@test.local", UserRole.STUDENT))
                .members(List.of())
                .build();
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        assertThrows(AccessDeniedException.class, () ->
                teamService.inviteMember(teamId, "invitee@test.local", 308L));
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void inviteMember_inviteeIsInviter_throwsBusinessRule() {
        Long teamId = 31L;
        Long inviterId = 309L;
        Assignment assignment = teamAssignment(41L, 105L);
        Team team = Team.builder()
                .id(teamId)
                .assignment(assignment)
                .createdBy(user(inviterId, "creator@test.local", UserRole.STUDENT))
                .members(List.of())
                .build();
        User invitee = user(inviterId, "creator@test.local", UserRole.STUDENT);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(userRepository.findByEmail("creator@test.local")).thenReturn(Optional.of(invitee));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                teamService.inviteMember(teamId, "creator@test.local", inviterId));
        assertEquals("INVALID_REQUEST", ex.getCode());
    }
}
