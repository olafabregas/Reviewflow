package com.reviewflow.service;

import com.reviewflow.event.ExtensionDecidedEvent;
import com.reviewflow.event.ExtensionRequestedEvent;
import com.reviewflow.exception.AccessDeniedException;
import com.reviewflow.exception.AlreadyRespondedException;
import com.reviewflow.exception.ExtensionCutoffPassedException;
import com.reviewflow.exception.ExtensionRequestExistsException;
import com.reviewflow.exception.InvalidRequestedDateException;
import com.reviewflow.exception.NotInTeamException;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.Course;
import com.reviewflow.model.entity.CourseInstructor;
import com.reviewflow.model.entity.ExtensionRequest;
import com.reviewflow.model.entity.Submission;
import com.reviewflow.model.entity.Team;
import com.reviewflow.model.entity.TeamMember;
import com.reviewflow.model.entity.TeamMemberStatus;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.model.enums.ExtensionRequestStatus;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.ExtensionRequestRepository;
import com.reviewflow.repository.SubmissionRepository;
import com.reviewflow.repository.TeamMemberRepository;
import com.reviewflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtensionRequestServiceTest {

    @Mock
    private ExtensionRequestRepository extensionRequestRepository;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock
    private CourseInstructorRepository courseInstructorRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private HashidService hashidService;
    @Mock
    private AuditService auditService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ExtensionRequestService extensionRequestService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(extensionRequestService, "extensionCutoffHoursDefault", 48);
    }

    @Test
    void create_individualSuccess_persistsAndPublishes() {
        Long assignmentId = 10L;
        Long actorUserId = 77L;
        Instant dueAt = Instant.now().plusSeconds(72 * 3600);
        Instant requestedDueAt = dueAt.plusSeconds(24 * 3600);

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .title("Essay 1")
                .submissionType(SubmissionType.INDIVIDUAL)
                .course(Course.builder().id(99L).build())
                .dueAt(dueAt)
                .build();

        User actor = User.builder()
                .id(actorUserId)
                .email("student@test.local")
                .firstName("Ada")
                .lastName("Lovelace")
                .role(UserRole.STUDENT)
                .build();

        when(hashidService.decodeOrThrow("A10")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actor));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(99L, actorUserId)).thenReturn(true);
        when(extensionRequestRepository.findTopByAssignment_IdAndStudent_IdAndStatusInOrderByCreatedAtDesc(anyLong(), anyLong(), any()))
                .thenReturn(Optional.empty());
        when(courseInstructorRepository.findByCourse_Id(99L)).thenReturn(List.of(
                CourseInstructor.builder().course(assignment.getCourse()).user(User.builder().id(900L).build()).build()
        ));
        when(extensionRequestRepository.save(any(ExtensionRequest.class))).thenAnswer(invocation -> {
            ExtensionRequest request = invocation.getArgument(0);
            request.setId(555L);
            return request;
        });

        ExtensionRequest saved = extensionRequestService.create("A10", actorUserId, "medical", requestedDueAt);

        assertEquals(555L, saved.getId());
        assertEquals(ExtensionRequestStatus.PENDING, saved.getStatus());
        assertEquals(actorUserId, saved.getStudent().getId());

        ArgumentCaptor<ExtensionRequestedEvent> eventCaptor = ArgumentCaptor.forClass(ExtensionRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ExtensionRequestedEvent event = eventCaptor.getValue();
        assertEquals(555L, event.extensionRequestId());
        assertEquals("Essay 1", event.assignmentTitle());
        assertEquals(List.of(900L), event.instructorUserIds());

        verify(auditService).log(eq(actorUserId), eq("EXTENSION_REQUEST_SUBMITTED"), eq("ExtensionRequest"), eq(555L), anyString(), eq(null));
    }

    @Test
    void create_whenCutoffPassed_throwsExtensionCutoffPassed() {
        Long assignmentId = 10L;
        Long actorUserId = 77L;
        Instant dueAt = Instant.now().plusSeconds(2 * 3600);

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .submissionType(SubmissionType.INDIVIDUAL)
                .course(Course.builder().id(99L).build())
                .dueAt(dueAt)
                .build();

        User actor = User.builder().id(actorUserId).role(UserRole.STUDENT).build();

        when(hashidService.decodeOrThrow("A10")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actor));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(99L, actorUserId)).thenReturn(true);

        assertThrows(ExtensionCutoffPassedException.class,
                () -> extensionRequestService.create("A10", actorUserId, "urgent", dueAt.plusSeconds(3600)));

        verify(extensionRequestRepository, never()).save(any(ExtensionRequest.class));
    }

    @Test
    void create_whenExistingRequest_throwsExtensionRequestExists() {
        Long assignmentId = 10L;
        Long actorUserId = 77L;
        Instant dueAt = Instant.now().plusSeconds(72 * 3600);

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .submissionType(SubmissionType.INDIVIDUAL)
                .course(Course.builder().id(99L).build())
                .dueAt(dueAt)
                .build();

        User actor = User.builder().id(actorUserId).role(UserRole.STUDENT).build();

        when(hashidService.decodeOrThrow("A10")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actor));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(99L, actorUserId)).thenReturn(true);
        when(extensionRequestRepository.findTopByAssignment_IdAndStudent_IdAndStatusInOrderByCreatedAtDesc(anyLong(), anyLong(), any()))
                .thenReturn(Optional.of(ExtensionRequest.builder().id(1L).build()));

        assertThrows(ExtensionRequestExistsException.class,
                () -> extensionRequestService.create("A10", actorUserId, "reason", dueAt.plusSeconds(3600)));
    }

    @Test
    void respond_whenAlreadyResponded_throwsAlreadyResponded() {
        Long requestId = 123L;
        Long actorUserId = 900L;

        Assignment assignment = Assignment.builder()
                .id(10L)
                .course(Course.builder().id(99L).build())
                .build();

        ExtensionRequest request = ExtensionRequest.builder()
                .id(requestId)
                .assignment(assignment)
                .status(ExtensionRequestStatus.DENIED)
                .build();

        User instructor = User.builder().id(actorUserId).role(UserRole.INSTRUCTOR).build();

        when(extensionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(instructor));
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(99L, actorUserId)).thenReturn(true);

        assertThrows(AlreadyRespondedException.class,
                () -> extensionRequestService.respond(requestId, actorUserId, true, "ok"));
    }

    @Test
    void respond_approveIndividual_updatesLateFlagsAndPublishes() {
        Long requestId = 123L;
        Long actorUserId = 900L;
        Instant newDueAt = Instant.now().plusSeconds(3 * 3600);

        Assignment assignment = Assignment.builder()
                .id(10L)
                .title("Essay 2")
                .course(Course.builder().id(99L).build())
                .build();

        User student = User.builder().id(77L).email("student@test.local").build();
        User instructor = User.builder().id(actorUserId).role(UserRole.INSTRUCTOR).build();

        ExtensionRequest request = ExtensionRequest.builder()
                .id(requestId)
                .assignment(assignment)
                .student(student)
                .requestedDueAt(newDueAt)
                .status(ExtensionRequestStatus.PENDING)
                .build();

        Submission sub = Submission.builder()
                .id(501L)
                .uploadedAt(Instant.now().plusSeconds(3600))
                .isLate(true)
                .build();

        when(extensionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(instructor));
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(99L, actorUserId)).thenReturn(true);
        when(submissionRepository.findByStudent_IdAndAssignment_IdOrderByVersionNumberDesc(77L, 10L))
                .thenReturn(List.of(sub));
        when(extensionRequestRepository.save(any(ExtensionRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExtensionRequest updated = extensionRequestService.respond(requestId, actorUserId, true, "approved");

        assertEquals(ExtensionRequestStatus.APPROVED, updated.getStatus());
        assertNotNull(updated.getRespondedAt());
        assertFalse(sub.getIsLate());

        verify(submissionRepository).saveAll(any());

        ArgumentCaptor<ExtensionDecidedEvent> eventCaptor = ArgumentCaptor.forClass(ExtensionDecidedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ExtensionDecidedEvent event = eventCaptor.getValue();
        assertEquals(Boolean.TRUE, event.approved());
        assertEquals(List.of(77L), event.recipientUserIds());
    }

    @Test
    void getByAssignment_nonInstructorDenied() {
        Long actorUserId = 77L;
        Long assignmentId = 10L;

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .course(Course.builder().id(99L).build())
                .build();
        User student = User.builder().id(actorUserId).role(UserRole.STUDENT).build();

        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(student));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(99L, actorUserId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> extensionRequestService.getByAssignment(assignmentId, actorUserId, org.springframework.data.domain.Pageable.unpaged()));
    }

    @Test
    void create_whenActorNotStudent_throwsAccessDenied() {
        Long assignmentId = 10L;
        Long actorUserId = 77L;
        Instant dueAt = Instant.now().plusSeconds(72 * 3600);

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .submissionType(SubmissionType.INDIVIDUAL)
                .course(Course.builder().id(99L).build())
                .dueAt(dueAt)
                .build();
        User actor = User.builder().id(actorUserId).role(UserRole.INSTRUCTOR).build();

        when(hashidService.decodeOrThrow("A10")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actor));

        assertThrows(AccessDeniedException.class,
                () -> extensionRequestService.create("A10", actorUserId, "reason", dueAt.plusSeconds(3600)));
    }

    @Test
    void create_whenNotEnrolled_throwsAccessDenied() {
        Long assignmentId = 10L;
        Long actorUserId = 77L;
        Instant dueAt = Instant.now().plusSeconds(72 * 3600);

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .submissionType(SubmissionType.INDIVIDUAL)
                .course(Course.builder().id(99L).build())
                .dueAt(dueAt)
                .build();
        User actor = User.builder().id(actorUserId).role(UserRole.STUDENT).build();

        when(hashidService.decodeOrThrow("A10")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actor));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(99L, actorUserId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> extensionRequestService.create("A10", actorUserId, "reason", dueAt.plusSeconds(3600)));
    }

    @Test
    void create_whenDueAtMissing_throwsInvalidRequestedDate() {
        Long assignmentId = 10L;
        Long actorUserId = 77L;

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .submissionType(SubmissionType.INDIVIDUAL)
                .course(Course.builder().id(99L).build())
                .dueAt(null)
                .build();
        User actor = User.builder().id(actorUserId).role(UserRole.STUDENT).build();

        when(hashidService.decodeOrThrow("A10")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actor));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(99L, actorUserId)).thenReturn(true);

        assertThrows(InvalidRequestedDateException.class,
                () -> extensionRequestService.create("A10", actorUserId, "reason", Instant.now().plusSeconds(3600)));
    }

    @Test
    void create_whenRequestedDateNotAfterDue_throwsInvalidRequestedDate() {
        Long assignmentId = 10L;
        Long actorUserId = 77L;
        Instant dueAt = Instant.now().plusSeconds(72 * 3600);

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .submissionType(SubmissionType.INDIVIDUAL)
                .course(Course.builder().id(99L).build())
                .dueAt(dueAt)
                .build();
        User actor = User.builder().id(actorUserId).role(UserRole.STUDENT).build();

        when(hashidService.decodeOrThrow("A10")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actor));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(99L, actorUserId)).thenReturn(true);

        assertThrows(InvalidRequestedDateException.class,
                () -> extensionRequestService.create("A10", actorUserId, "reason", dueAt));
    }

    @Test
    void create_teamAssignmentWithoutMembership_throwsNotInTeam() {
        Long assignmentId = 10L;
        Long actorUserId = 77L;
        Instant dueAt = Instant.now().plusSeconds(72 * 3600);

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .submissionType(SubmissionType.TEAM)
                .course(Course.builder().id(99L).build())
                .dueAt(dueAt)
                .build();
        User actor = User.builder().id(actorUserId).role(UserRole.STUDENT).build();

        when(hashidService.decodeOrThrow("A10")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actor));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(99L, actorUserId)).thenReturn(true);
        when(teamMemberRepository.findByAssignment_IdAndUser_IdAndStatus(assignmentId, actorUserId, TeamMemberStatus.ACCEPTED))
                .thenReturn(List.of());

        assertThrows(NotInTeamException.class,
                () -> extensionRequestService.create("A10", actorUserId, "reason", dueAt.plusSeconds(3600)));
    }

    @Test
    void create_teamAssignmentSuccess_withDefaultSubmissionTypeBranch() {
        Long assignmentId = 10L;
        Long actorUserId = 77L;
        Long teamId = 500L;
        Instant dueAt = Instant.now().plusSeconds(72 * 3600);

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .title("Project")
                .submissionType(null)
                .course(Course.builder().id(99L).build())
                .dueAt(dueAt)
                .build();
        User actor = User.builder().id(actorUserId).role(UserRole.STUDENT).email("student@test.local").build();
        Team team = Team.builder().id(teamId).name("Team A").assignment(assignment).build();
        TeamMember member = TeamMember.builder().team(team).assignment(assignment).user(actor).status(TeamMemberStatus.ACCEPTED).build();

        when(hashidService.decodeOrThrow("A10")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actor));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(99L, actorUserId)).thenReturn(true);
        when(extensionRequestRepository.findTopByAssignment_IdAndStudent_IdAndStatusInOrderByCreatedAtDesc(anyLong(), anyLong(), any()))
                .thenReturn(Optional.empty());
        when(courseInstructorRepository.findByCourse_Id(99L)).thenReturn(List.of(CourseInstructor.builder().course(assignment.getCourse()).user(null).build()));
        when(extensionRequestRepository.save(any(ExtensionRequest.class))).thenAnswer(invocation -> {
            ExtensionRequest request = invocation.getArgument(0);
            request.setId(801L);
            return request;
        });

        ExtensionRequest saved = extensionRequestService.create("A10", actorUserId, "reason", dueAt.plusSeconds(3600));

        assertEquals(801L, saved.getId());
        assertEquals(actorUserId, saved.getStudent().getId());
    }

    @Test
    void create_teamAssignmentSuccess_setsTeamOwner() {
        Long assignmentId = 10L;
        Long actorUserId = 77L;
        Long teamId = 500L;
        Instant dueAt = Instant.now().plusSeconds(72 * 3600);

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .title("Project")
                .submissionType(SubmissionType.TEAM)
                .course(Course.builder().id(99L).build())
                .dueAt(dueAt)
                .build();
        User actor = User.builder().id(actorUserId).role(UserRole.STUDENT).email("student@test.local").build();
        Team team = Team.builder().id(teamId).name("Team A").assignment(assignment).build();
        TeamMember member = TeamMember.builder().team(team).assignment(assignment).user(actor).status(TeamMemberStatus.ACCEPTED).build();

        when(hashidService.decodeOrThrow("A10")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actor));
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(99L, actorUserId)).thenReturn(true);
        when(teamMemberRepository.findByAssignment_IdAndUser_IdAndStatus(assignmentId, actorUserId, TeamMemberStatus.ACCEPTED))
                .thenReturn(List.of(member));
        when(extensionRequestRepository.findTopByAssignment_IdAndTeam_IdAndStatusInOrderByCreatedAtDesc(anyLong(), anyLong(), any()))
                .thenReturn(Optional.empty());
        when(courseInstructorRepository.findByCourse_Id(99L)).thenReturn(List.of());
        when(extensionRequestRepository.save(any(ExtensionRequest.class))).thenAnswer(invocation -> {
            ExtensionRequest request = invocation.getArgument(0);
            request.setId(802L);
            return request;
        });

        ExtensionRequest saved = extensionRequestService.create("A10", actorUserId, "reason", dueAt.plusSeconds(3600));

        assertEquals(802L, saved.getId());
        assertEquals(teamId, saved.getTeam().getId());
    }

    @Test
    void respond_whenActorHasNoPermission_throwsAccessDenied() {
        Long requestId = 123L;
        Long actorUserId = 700L;

        Assignment assignment = Assignment.builder()
                .id(10L)
                .course(Course.builder().id(99L).build())
                .build();
        ExtensionRequest request = ExtensionRequest.builder()
                .id(requestId)
                .assignment(assignment)
                .status(ExtensionRequestStatus.PENDING)
                .build();
        User actor = User.builder().id(actorUserId).role(UserRole.STUDENT).build();

        when(extensionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actor));
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(99L, actorUserId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> extensionRequestService.respond(requestId, actorUserId, true, "ok"));
    }

    @Test
    void respond_denyTeamRequest_asAdmin_publishesRecipientsWithoutLateRecalc() {
        Long requestId = 222L;
        Long adminId = 1L;

        Assignment assignment = Assignment.builder()
                .id(10L)
                .title("Project")
                .course(Course.builder().id(99L).build())
                .build();
        Team team = Team.builder().id(500L).assignment(assignment).name("Team A").build();

        ExtensionRequest request = ExtensionRequest.builder()
                .id(requestId)
                .assignment(assignment)
                .team(team)
                .status(ExtensionRequestStatus.PENDING)
                .build();

        User admin = User.builder().id(adminId).role(UserRole.ADMIN).build();
        User acceptedUser = User.builder().id(91L).build();
        TeamMember accepted = TeamMember.builder().team(team).assignment(assignment).user(acceptedUser).status(TeamMemberStatus.ACCEPTED).build();
        TeamMember pending = TeamMember.builder().team(team).assignment(assignment).user(User.builder().id(92L).build()).status(TeamMemberStatus.PENDING).build();
        TeamMember acceptedNullUser = TeamMember.builder().team(team).assignment(assignment).user(null).status(TeamMemberStatus.ACCEPTED).build();

        when(extensionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(extensionRequestRepository.save(any(ExtensionRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(teamMemberRepository.findByTeam_Id(team.getId())).thenReturn(List.of(accepted, pending, acceptedNullUser));

        ExtensionRequest updated = extensionRequestService.respond(requestId, adminId, false, "denied");

        assertEquals(ExtensionRequestStatus.DENIED, updated.getStatus());
        verify(submissionRepository, never()).saveAll(any());

        ArgumentCaptor<ExtensionDecidedEvent> eventCaptor = ArgumentCaptor.forClass(ExtensionDecidedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(List.of(91L), eventCaptor.getValue().recipientUserIds());
        assertEquals(Boolean.FALSE, eventCaptor.getValue().approved());
    }

    @Test
    void getByAssignment_adminCanView_returnsPage() {
        Long actorUserId = 1L;
        Long assignmentId = 10L;
        Pageable pageable = Pageable.unpaged();

        Assignment assignment = Assignment.builder().id(assignmentId).course(Course.builder().id(99L).build()).build();
        User admin = User.builder().id(actorUserId).role(UserRole.ADMIN).build();
        Page<ExtensionRequest> page = new PageImpl<>(List.of(ExtensionRequest.builder().id(1L).assignment(assignment).build()));

        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(admin));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(extensionRequestRepository.findByAssignment_IdOrderByCreatedAtDesc(assignmentId, pageable)).thenReturn(page);

        Page<ExtensionRequest> result = extensionRequestService.getByAssignment(assignmentId, actorUserId, pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getMine_returnsStudentPage() {
        Long actorUserId = 77L;
        Pageable pageable = Pageable.unpaged();
        Page<ExtensionRequest> page = new PageImpl<>(List.of(ExtensionRequest.builder().id(2L).build()));

        when(extensionRequestRepository.findByStudent_IdOrderByCreatedAtDesc(actorUserId, pageable)).thenReturn(page);

        Page<ExtensionRequest> result = extensionRequestService.getMine(actorUserId, pageable);

        assertEquals(1, result.getTotalElements());
    }
}
