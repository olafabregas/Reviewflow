package com.reviewflow.submission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reviewflow.submission.event.SubmissionUploadedEvent;
import com.reviewflow.shared.exception.AccessDeniedException;
import com.reviewflow.shared.exception.DuplicateResourceException;
import com.reviewflow.submission.exception.IndividualSubmissionOnlyException;
import com.reviewflow.shared.exception.RateLimitException;
import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.team.exception.TeamSubmissionRequiredException;
import com.reviewflow.shared.exception.ValidationException;
import com.reviewflow.shared.domain.Assignment;
import com.reviewflow.shared.domain.Course;
import com.reviewflow.shared.domain.CourseInstructor;
import com.reviewflow.shared.domain.ExtensionRequest;
import com.reviewflow.shared.domain.Submission;
import com.reviewflow.shared.domain.Team;
import com.reviewflow.shared.domain.TeamMember;
import com.reviewflow.shared.domain.TeamMemberStatus;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.domain.ExtensionRequestStatus;
import com.reviewflow.shared.domain.SubmissionType;
import com.reviewflow.infrastructure.monitoring.SecurityMetrics;
import com.reviewflow.assignment.repository.AssignmentRepository;
import com.reviewflow.course.repository.CourseEnrollmentRepository;
import com.reviewflow.course.repository.CourseInstructorRepository;
import com.reviewflow.extension.repository.ExtensionRequestRepository;
import com.reviewflow.admin.service.AdminStatsService;
import com.reviewflow.admin.service.AuditService;
import com.reviewflow.infrastructure.storage.ClamAvScanService;
import com.reviewflow.infrastructure.storage.FileSecurityValidator;
import com.reviewflow.infrastructure.security.RateLimiterService;
import com.reviewflow.submission.repository.SubmissionRepository;
import com.reviewflow.team.repository.TeamMemberRepository;
import com.reviewflow.team.repository.TeamRepository;
import com.reviewflow.user.repository.UserRepository;
import com.reviewflow.infrastructure.storage.StorageService;
import com.reviewflow.shared.util.HashidService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

  @Mock private SubmissionRepository submissionRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private TeamMemberRepository teamMemberRepository;
  @Mock private CourseEnrollmentRepository courseEnrollmentRepository;
  @Mock private CourseInstructorRepository courseInstructorRepository;
  @Mock private AssignmentRepository assignmentRepository;
  @Mock private ExtensionRequestRepository extensionRequestRepository;
  @Mock private StorageService storageService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private UserRepository userRepository;
  @Mock private FileSecurityValidator fileSecurityValidator;
  @Mock private AdminStatsService adminStatsService;
  @Mock private ClamAvScanService clamAvScanService;
  @Mock private RateLimiterService rateLimiterService;
  @Mock private SecurityMetrics securityMetrics;
  @Mock private AuditService auditService;
  @Mock private HashidService hashidService;

  @InjectMocks private SubmissionService submissionService;

  @BeforeEach
  void setUp() throws Exception {
    ReflectionTestUtils.setField(submissionService, "submissionMaxFileSizeBytes", 104857600L);
    lenient().when(rateLimiterService.isUploadBlockRateLimited(anyString())).thenReturn(false);
    lenient()
        .doNothing()
        .when(fileSecurityValidator)
        .validateFromPath(any(), anyLong(), anyString());
    lenient().doNothing().when(clamAvScanService).scanAndThrow(any(), anyLong());
    lenient().when(hashidService.encode(anyLong())).thenAnswer(inv -> "H" + inv.getArgument(0));
    lenient()
        .when(
            extensionRequestRepository
                .findTopByAssignmentIdAndStudentIdAndStatusOrderByRespondedAtDesc(
                    anyLong(), anyLong(), eq(ExtensionRequestStatus.APPROVED)))
        .thenReturn(Optional.empty());
    lenient()
        .when(
            extensionRequestRepository
                .findTopByAssignmentIdAndTeamIdAndStatusOrderByRespondedAtDesc(
                    anyLong(), anyLong(), eq(ExtensionRequestStatus.APPROVED)))
        .thenReturn(Optional.empty());
  }

  @Test
  void upload_whenRateLimited_throwsRateLimitException() {
    Long uploaderId = 77L;
    when(rateLimiterService.isUploadBlockRateLimited("user_" + uploaderId)).thenReturn(true);
    when(rateLimiterService.getUploadBlockRetryAfterSeconds("user_" + uploaderId)).thenReturn(42L);

    assertThrows(
        RateLimitException.class,
        () -> submissionService.upload(null, 10L, null, validFile("essay.pdf"), uploaderId));

    verify(securityMetrics).recordUploadBlockRateLimited();
    verify(assignmentRepository, never()).findById(anyLong());
  }

  @Test
  void upload_whenFileMissing_throwsValidation() {
    assertThrows(
        ValidationException.class, () -> submissionService.upload(null, 10L, null, null, 77L));
  }

  @Test
  void upload_whenFileTooLarge_throwsValidation() {
    ReflectionTestUtils.setField(submissionService, "submissionMaxFileSizeBytes", 1L);

    assertThrows(
        ValidationException.class,
        () -> submissionService.upload(null, 10L, null, validFile("essay.pdf"), 77L));
  }

  @Test
  void upload_whenChangeNoteTooLong_throwsValidation() {
    Long assignmentId = 10L;
    Long uploaderId = 77L;

    String longNote = "x".repeat(501);
    assertThrows(
        ValidationException.class,
        () ->
            submissionService.upload(
                null, assignmentId, longNote, validFile("essay.pdf"), uploaderId));
  }

  @Test
  void upload_whenAssignmentMissing_throwsResourceNotFound() {
    when(assignmentRepository.findById(10L)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> submissionService.upload(null, 10L, null, validFile("essay.pdf"), 77L));
  }

  @Test
  void upload_whenUploaderMissing_throwsResourceNotFound() {
    Long assignmentId = 10L;
    Long uploaderId = 77L;
    when(assignmentRepository.findById(assignmentId))
        .thenReturn(
            Optional.of(individualAssignment(assignmentId, 99L, Instant.now().plusSeconds(7200))));
    when(userRepository.findById(uploaderId)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            submissionService.upload(null, assignmentId, null, validFile("essay.pdf"), uploaderId));
  }

  @Test
  void upload_whenIndividualAndNotEnrolled_throwsAccessDenied() {
    Long assignmentId = 10L;
    Long uploaderId = 77L;
    Assignment assignment =
        individualAssignment(assignmentId, 99L, Instant.now().plusSeconds(7200));
    User uploader = user(uploaderId, "Alice", "Lee");

    when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
    when(userRepository.findById(uploaderId)).thenReturn(Optional.of(uploader));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(99L, uploaderId)).thenReturn(false);

    assertThrows(
        AccessDeniedException.class,
        () ->
            submissionService.upload(null, assignmentId, null, validFile("essay.pdf"), uploaderId));
  }

  @Test
  void upload_teamAssignmentWithoutTeamId_throwsTeamSubmissionRequired() throws Exception {
    Long assignmentId = 10L;
    Long uploaderId = 77L;

    Assignment assignment =
        Assignment.builder()
            .id(assignmentId)
            .submissionType(SubmissionType.TEAM)
            .course(Course.builder().id(99L).build())
            .dueAt(Instant.now().plusSeconds(7200))
            .build();
    User uploader = User.builder().id(uploaderId).role(UserRole.STUDENT).build();

    when(rateLimiterService.isUploadBlockRateLimited("user_" + uploaderId)).thenReturn(false);
    doNothing().when(fileSecurityValidator).validateFromPath(any(), anyLong(), anyString());
    doNothing().when(clamAvScanService).scanAndThrow(any(), anyLong());
    when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
    when(userRepository.findById(uploaderId)).thenReturn(Optional.of(uploader));

    MockMultipartFile file =
        new MockMultipartFile("file", "work.zip", "application/zip", "abc".getBytes());

    assertThrows(
        TeamSubmissionRequiredException.class,
        () -> submissionService.upload(null, assignmentId, null, file, uploaderId));

    verify(eventPublisher, never()).publishEvent(any(SubmissionUploadedEvent.class));
  }

  @Test
  void upload_individualAssignmentWithTeamId_throwsIndividualSubmissionOnly() throws Exception {
    Long assignmentId = 20L;
    Long uploaderId = 88L;

    Assignment assignment =
        Assignment.builder()
            .id(assignmentId)
            .submissionType(SubmissionType.INDIVIDUAL)
            .course(Course.builder().id(100L).build())
            .dueAt(Instant.now().plusSeconds(7200))
            .build();
    User uploader = User.builder().id(uploaderId).role(UserRole.STUDENT).build();

    when(rateLimiterService.isUploadBlockRateLimited("user_" + uploaderId)).thenReturn(false);
    doNothing().when(fileSecurityValidator).validateFromPath(any(), anyLong(), anyString());
    doNothing().when(clamAvScanService).scanAndThrow(any(), anyLong());
    when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
    when(userRepository.findById(uploaderId)).thenReturn(Optional.of(uploader));

    MockMultipartFile file =
        new MockMultipartFile("file", "essay.pdf", "application/pdf", "abc".getBytes());

    assertThrows(
        IndividualSubmissionOnlyException.class,
        () -> submissionService.upload(999L, assignmentId, null, file, uploaderId));

    verify(eventPublisher, never()).publishEvent(any(SubmissionUploadedEvent.class));
  }

  @Test
  void upload_whenTeamMemberNotAccepted_throwsAccessDenied() {
    Long assignmentId = 10L;
    Long teamId = 500L;
    Long uploaderId = 77L;
    Assignment assignment = teamAssignment(assignmentId, 99L, Instant.now().plusSeconds(7200));
    Team team = Team.builder().id(teamId).name("Team A").assignment(assignment).build();
    User uploader = user(uploaderId, "Alice", "Lee");
    TeamMember pendingMember =
        TeamMember.builder()
            .team(team)
            .user(uploader)
            .assignment(assignment)
            .status(TeamMemberStatus.PENDING)
            .build();

    when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
    when(userRepository.findById(uploaderId)).thenReturn(Optional.of(uploader));
    when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
    when(teamMemberRepository.findByTeamIdAndUserId(teamId, uploaderId))
        .thenReturn(Optional.of(pendingMember));

    assertThrows(
        AccessDeniedException.class,
        () ->
            submissionService.upload(
                teamId, assignmentId, null, validFile("team.zip"), uploaderId));
  }

  @Test
  void upload_whenUploadAlreadyInProgress_throwsDuplicateResourceException() {
    Long assignmentId = 10L;
    Long uploaderId = 77L;
    Assignment assignment =
        individualAssignment(assignmentId, 99L, Instant.now().plusSeconds(7200));
    User uploader = user(uploaderId, "Alice", "Lee");

    when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
    when(userRepository.findById(uploaderId)).thenReturn(Optional.of(uploader));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(99L, uploaderId)).thenReturn(true);

    @SuppressWarnings("unchecked")
    Map<String, Boolean> uploadLocks =
        (Map<String, Boolean>) ReflectionTestUtils.getField(submissionService, "uploadLocks");
    assertNotNull(uploadLocks);
    uploadLocks.put("student_" + uploaderId + "_assignment_" + assignmentId, Boolean.TRUE);

    assertThrows(
        DuplicateResourceException.class,
        () ->
            submissionService.upload(null, assignmentId, null, validFile("essay.pdf"), uploaderId));
  }

  @Test
  void upload_whenSaveFails_releasesLock() {
    Long assignmentId = 10L;
    Long uploaderId = 77L;
    Assignment assignment =
        individualAssignment(assignmentId, 99L, Instant.now().plusSeconds(7200));
    User uploader = user(uploaderId, "Alice", "Lee");

    when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
    when(userRepository.findById(uploaderId)).thenReturn(Optional.of(uploader));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(99L, uploaderId)).thenReturn(true);
    when(submissionRepository.findMaxVersionNumberByStudent(uploaderId, assignmentId))
        .thenReturn(Optional.of(0));
    when(storageService.store(anyString(), any(), eq(3L), anyString())).thenReturn("stored/path");
    when(submissionRepository.save(any(Submission.class)))
        .thenThrow(new RuntimeException("db down"));

    assertThrows(
        RuntimeException.class,
        () ->
            submissionService.upload(null, assignmentId, null, validFile("essay.pdf"), uploaderId));

    @SuppressWarnings("unchecked")
    Map<String, Boolean> uploadLocks =
        (Map<String, Boolean>) ReflectionTestUtils.getField(submissionService, "uploadLocks");
    assertNotNull(uploadLocks);
    assertFalse(uploadLocks.containsKey("student_" + uploaderId + "_assignment_" + assignmentId));
  }

  @Test
  void upload_individualSuccess_persistsAndPublishes() {
    Long assignmentId = 10L;
    Long uploaderId = 77L;
    Assignment assignment =
        individualAssignment(assignmentId, 99L, Instant.now().minusSeconds(120));
    assignment.setTitle("Essay 1");
    User uploader = user(uploaderId, "Alice", "Lee");
    User instructor = user(900L, "Prof", "Kim");
    CourseInstructor ci =
        CourseInstructor.builder().course(assignment.getCourse()).user(instructor).build();

    when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
    when(userRepository.findById(uploaderId)).thenReturn(Optional.of(uploader));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(99L, uploaderId)).thenReturn(true);
    when(submissionRepository.findMaxVersionNumberByStudent(uploaderId, assignmentId))
        .thenReturn(Optional.of(1));
    when(storageService.store(anyString(), any(), eq(3L), anyString()))
        .thenReturn("submissions/H10/H77/v2/essay.pdf");
    when(courseInstructorRepository.findByCourseId(99L)).thenReturn(List.of(ci));
    when(submissionRepository.save(any(Submission.class)))
        .thenAnswer(
            inv -> {
              Submission s = inv.getArgument(0);
              s.setId(501L);
              return s;
            });

    Submission saved =
        submissionService.upload(null, assignmentId, "rev 2", validFile("essay.pdf"), uploaderId);

    assertEquals(501L, saved.getId());
    assertEquals(2, saved.getVersionNumber());
    verify(storageService)
        .store(eq("submissions/H10/H77/v2/essay.pdf"), any(), eq(3L), eq("application/pdf"));

    @SuppressWarnings("unchecked")
    org.mockito.ArgumentCaptor<SubmissionUploadedEvent> eventCaptor =
        org.mockito.ArgumentCaptor.forClass(SubmissionUploadedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    SubmissionUploadedEvent event = eventCaptor.getValue();
    assertEquals(List.of(77L, 900L), event.recipientUserIds());
    assertEquals(SubmissionType.INDIVIDUAL, event.submissionType());
    assertEquals(77L, event.studentId());
    assertEquals(null, event.teamId());
    assertEquals(501L, event.submissionId());
    verify(adminStatsService).evictStats();
    verify(auditService, times(1))
        .log(
            eq(uploaderId),
            eq("SUBMISSION_UPLOADED"),
            eq("SUBMISSION"),
            eq(501L),
            anyString(),
            eq(null));
  }

  @Test
  void upload_teamSuccess_persistsAndPublishesToAcceptedPeers() {
    Long assignmentId = 10L;
    Long teamId = 500L;
    Long uploaderId = 77L;
    Assignment assignment = teamAssignment(assignmentId, 99L, Instant.now().plusSeconds(7200));
    assignment.setTitle("Project 1");
    Team team = Team.builder().id(teamId).name("Team A").assignment(assignment).build();
    User uploader = user(uploaderId, "Alice", "Lee");
    User peerAccepted = user(88L, "Bob", "Ng");
    User peerPending = user(99L, "Cat", "Jo");

    TeamMember uploaderMember =
        TeamMember.builder()
            .team(team)
            .user(uploader)
            .assignment(assignment)
            .status(TeamMemberStatus.ACCEPTED)
            .build();
    TeamMember peerAcceptedMember =
        TeamMember.builder()
            .team(team)
            .user(peerAccepted)
            .assignment(assignment)
            .status(TeamMemberStatus.ACCEPTED)
            .build();
    TeamMember peerPendingMember =
        TeamMember.builder()
            .team(team)
            .user(peerPending)
            .assignment(assignment)
            .status(TeamMemberStatus.PENDING)
            .build();

    when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
    when(userRepository.findById(uploaderId)).thenReturn(Optional.of(uploader));
    when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
    when(teamMemberRepository.findByTeamIdAndUserId(teamId, uploaderId))
        .thenReturn(Optional.of(uploaderMember));
    when(submissionRepository.findMaxVersionNumber(teamId, assignmentId))
        .thenReturn(Optional.of(0));
    when(storageService.store(anyString(), any(), eq(3L), anyString()))
        .thenReturn("submissions/H10/H500/v1/team.zip");
    when(teamMemberRepository.findByTeamId(teamId))
        .thenReturn(
            new ArrayList<>(List.of(uploaderMember, peerAcceptedMember, peerPendingMember)));
    when(submissionRepository.save(any(Submission.class)))
        .thenAnswer(
            inv -> {
              Submission s = inv.getArgument(0);
              s.setId(777L);
              return s;
            });

    Submission saved =
        submissionService.upload(teamId, assignmentId, null, validFile("team.zip"), uploaderId);

    assertEquals(777L, saved.getId());
    assertEquals(1, saved.getVersionNumber());
    verify(storageService)
        .store(eq("submissions/H10/H500/v1/team.zip"), any(), eq(3L), eq("application/pdf"));

    @SuppressWarnings("unchecked")
    org.mockito.ArgumentCaptor<SubmissionUploadedEvent> eventCaptor =
        org.mockito.ArgumentCaptor.forClass(SubmissionUploadedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    SubmissionUploadedEvent event = eventCaptor.getValue();
    assertEquals(List.of(88L), event.recipientUserIds());
    assertEquals(SubmissionType.TEAM, event.submissionType());
    assertEquals(teamId, event.teamId());
    assertEquals(null, event.studentId());
    assertEquals(777L, event.submissionId());
  }

  @Test
  void upload_whenValidatorThrowsRuntime_recordsSecurityAndRateLimit() throws Exception {
    Long assignmentId = 10L;
    Long uploaderId = 77L;
    doThrow(new ValidationException("bad", "FILE_VALIDATION_ERROR"))
        .when(fileSecurityValidator)
        .validateFromPath(any(), anyLong(), anyString());

    assertThrows(
        ValidationException.class,
        () ->
            submissionService.upload(null, assignmentId, null, validFile("essay.pdf"), uploaderId));

    verify(securityMetrics).recordFileBlocked();
    verify(rateLimiterService).recordBlockedUpload("user_" + uploaderId);
  }

  @Test
  void upload_individualWithApprovedExtension_marksNotLateAgainstExtendedDueAt() {
    Long assignmentId = 10L;
    Long uploaderId = 77L;
    Instant originalDueAt = Instant.now().minusSeconds(600);
    Instant extendedDueAt = Instant.now().plusSeconds(3600);

    Assignment assignment = individualAssignment(assignmentId, 99L, originalDueAt);
    User uploader = user(uploaderId, "Alice", "Lee");

    when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
    when(userRepository.findById(uploaderId)).thenReturn(Optional.of(uploader));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(99L, uploaderId)).thenReturn(true);
    when(submissionRepository.findMaxVersionNumberByStudent(uploaderId, assignmentId))
        .thenReturn(Optional.of(0));
    when(storageService.store(anyString(), any(), eq(3L), anyString()))
        .thenReturn("submissions/H10/H77/v1/essay.pdf");
    when(extensionRequestRepository
            .findTopByAssignmentIdAndStudentIdAndStatusOrderByRespondedAtDesc(
                assignmentId, uploaderId, ExtensionRequestStatus.APPROVED))
        .thenReturn(Optional.of(ExtensionRequest.builder().requestedDueAt(extendedDueAt).build()));
    when(submissionRepository.save(any(Submission.class)))
        .thenAnswer(
            inv -> {
              Submission s = inv.getArgument(0);
              s.setId(601L);
              return s;
            });

    Submission saved =
        submissionService.upload(null, assignmentId, null, validFile("essay.pdf"), uploaderId);

    assertEquals(601L, saved.getId());
    assertFalse(saved.getIsLate());
  }

  @Test
  void upload_teamWithApprovedExtension_marksNotLateAgainstExtendedDueAt() {
    Long assignmentId = 10L;
    Long teamId = 500L;
    Long uploaderId = 77L;
    Instant originalDueAt = Instant.now().minusSeconds(600);
    Instant extendedDueAt = Instant.now().plusSeconds(3600);

    Assignment assignment = teamAssignment(assignmentId, 99L, originalDueAt);
    Team team = Team.builder().id(teamId).name("Team A").assignment(assignment).build();
    User uploader = user(uploaderId, "Alice", "Lee");
    TeamMember uploaderMember =
        TeamMember.builder()
            .team(team)
            .user(uploader)
            .assignment(assignment)
            .status(TeamMemberStatus.ACCEPTED)
            .build();

    when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
    when(userRepository.findById(uploaderId)).thenReturn(Optional.of(uploader));
    when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
    when(teamMemberRepository.findByTeamIdAndUserId(teamId, uploaderId))
        .thenReturn(Optional.of(uploaderMember));
    when(submissionRepository.findMaxVersionNumber(teamId, assignmentId))
        .thenReturn(Optional.of(0));
    when(storageService.store(anyString(), any(), eq(3L), anyString()))
        .thenReturn("submissions/H10/H500/v1/team.zip");
    when(extensionRequestRepository.findTopByAssignmentIdAndTeamIdAndStatusOrderByRespondedAtDesc(
            assignmentId, teamId, ExtensionRequestStatus.APPROVED))
        .thenReturn(Optional.of(ExtensionRequest.builder().requestedDueAt(extendedDueAt).build()));
    when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of(uploaderMember));
    when(submissionRepository.save(any(Submission.class)))
        .thenAnswer(
            inv -> {
              Submission s = inv.getArgument(0);
              s.setId(602L);
              return s;
            });

    Submission saved =
        submissionService.upload(teamId, assignmentId, null, validFile("team.zip"), uploaderId);

    assertEquals(602L, saved.getId());
    assertFalse(saved.getIsLate());
  }

  private MockMultipartFile validFile(String name) {
    return new MockMultipartFile("file", name, "application/pdf", "abc".getBytes());
  }

  private Assignment individualAssignment(Long assignmentId, Long courseId, Instant dueAt) {
    return Assignment.builder()
        .id(assignmentId)
        .submissionType(SubmissionType.INDIVIDUAL)
        .course(Course.builder().id(courseId).build())
        .dueAt(dueAt)
        .build();
  }

  private Assignment teamAssignment(Long assignmentId, Long courseId, Instant dueAt) {
    return Assignment.builder()
        .id(assignmentId)
        .submissionType(SubmissionType.TEAM)
        .course(Course.builder().id(courseId).build())
        .dueAt(dueAt)
        .build();
  }

  private User user(Long id, String firstName, String lastName) {
    return User.builder()
        .id(id)
        .firstName(firstName)
        .lastName(lastName)
        .role(UserRole.STUDENT)
        .build();
  }
}
