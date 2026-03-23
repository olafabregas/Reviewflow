package com.reviewflow.service;

import com.reviewflow.event.SubmissionUploadedEvent;
import com.reviewflow.exception.IndividualSubmissionOnlyException;
import com.reviewflow.exception.TeamSubmissionRequiredException;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.Course;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.monitoring.SecurityMetrics;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.SubmissionRepository;
import com.reviewflow.repository.TeamMemberRepository;
import com.reviewflow.repository.TeamRepository;
import com.reviewflow.repository.UserRepository;
import com.reviewflow.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock
    private CourseInstructorRepository courseInstructorRepository;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileSecurityValidator fileSecurityValidator;
    @Mock
    private AdminStatsService adminStatsService;
    @Mock
    private ClamAvScanService clamAvScanService;
    @Mock
    private RateLimiterService rateLimiterService;
    @Mock
    private SecurityMetrics securityMetrics;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private SubmissionService submissionService;

    @Test
    void upload_teamAssignmentWithoutTeamId_throwsTeamSubmissionRequired() throws Exception {
        Long assignmentId = 10L;
        Long uploaderId = 77L;

        Assignment assignment = Assignment.builder()
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

        MockMultipartFile file = new MockMultipartFile("file", "work.zip", "application/zip", "abc".getBytes());

        assertThrows(TeamSubmissionRequiredException.class,
                () -> submissionService.upload(null, assignmentId, null, file, uploaderId));

        verify(eventPublisher, never()).publishEvent(any(SubmissionUploadedEvent.class));
    }

    @Test
    void upload_individualAssignmentWithTeamId_throwsIndividualSubmissionOnly() throws Exception {
        Long assignmentId = 20L;
        Long uploaderId = 88L;

        Assignment assignment = Assignment.builder()
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

        MockMultipartFile file = new MockMultipartFile("file", "essay.pdf", "application/pdf", "abc".getBytes());

        assertThrows(IndividualSubmissionOnlyException.class,
                () -> submissionService.upload(999L, assignmentId, null, file, uploaderId));

        verify(eventPublisher, never()).publishEvent(any(SubmissionUploadedEvent.class));
    }
}
