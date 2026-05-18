package com.reviewflow.grading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewflow.assignment.repository.AssignmentRepository;
import com.reviewflow.course.repository.CourseEnrollmentRepository;
import com.reviewflow.course.repository.CourseInstructorRepository;
import com.reviewflow.grading.exception.ImportInProgressException;
import com.reviewflow.grading.job.JobState;
import com.reviewflow.grading.job.JobStatus;
import com.reviewflow.grading.repository.InstructorScoreRepository;
import com.reviewflow.infrastructure.jobs.AsyncJobService;
import com.reviewflow.infrastructure.jobs.SseEmitterRegistry;
import com.reviewflow.infrastructure.config.ValidationConfig;
import com.reviewflow.infrastructure.storage.FileSecurityValidator;
import com.reviewflow.infrastructure.storage.S3Service;
import java.io.InputStream;
import com.reviewflow.shared.domain.Assignment;
import com.reviewflow.shared.domain.Course;
import com.reviewflow.shared.domain.SubmissionType;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.team.repository.TeamRepository;
import com.reviewflow.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

  @Mock private AssignmentRepository assignmentRepository;
  @Mock private UserRepository userRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private CourseInstructorRepository courseInstructorRepository;
  @Mock private CourseEnrollmentRepository courseEnrollmentRepository;
  @Mock private InstructorScoreRepository instructorScoreRepository;
  @Mock private CsvImportCommitService csvImportCommitService;
  @Mock private AsyncJobService asyncJobService;
  @Mock private S3Service s3Service;
  @Mock private SseEmitterRegistry sseEmitterRegistry;
  @Mock private FileSecurityValidator fileSecurityValidator;
  @Mock private HashidService hashidService;
  @Mock private Executor csvWorkerExecutor;
  @Mock private Executor uploadExecutor;

  private CsvImportService csvImportService;

  @BeforeEach
  void setUp() {
    csvImportService =
        new CsvImportService(
            assignmentRepository,
            userRepository,
            teamRepository,
            courseInstructorRepository,
            courseEnrollmentRepository,
            instructorScoreRepository,
            csvImportCommitService,
            asyncJobService,
            s3Service,
            sseEmitterRegistry,
            fileSecurityValidator,
            hashidService,
            new ObjectMapper(),
            csvWorkerExecutor,
            uploadExecutor);

  }

  private void runUploadTasksSynchronously() {
    doAnswer(
            invocation -> {
              Runnable task = invocation.getArgument(0);
              task.run();
              return null;
            })
        .when(uploadExecutor)
        .execute(any(Runnable.class));
  }

  @Test
  void startImport_whenLockNotAcquired_throwsImportInProgress() {
    long assignmentId = 10L;
    long actorId = 77L;
    long courseId = 5L;

    Assignment assignment = instructorGradedAssignment(assignmentId, courseId);
    when(assignmentRepository.findWithDetailsById(assignmentId)).thenReturn(Optional.of(assignment));
    when(userRepository.findById(actorId)).thenReturn(Optional.of(instructorUser(actorId)));
    when(courseInstructorRepository.existsByCourseIdAndUserId(courseId, actorId)).thenReturn(true);
    when(hashidService.encode(courseId)).thenReturn("courseHash");
    when(asyncJobService.acquireImportLock(eq("courseHash"), any())).thenReturn(false);

    MultipartFile file =
        new MockMultipartFile("file", "scores.csv", "text/csv", "student_email,score\n".getBytes());

    assertThatThrownBy(() -> csvImportService.startImport(assignmentId, actorId, file))
        .isInstanceOf(ImportInProgressException.class);

    verify(asyncJobService, never()).saveJob(any());
    verify(s3Service, never())
        .putObject(org.mockito.ArgumentMatchers.anyString(), any(byte[].class), any());
  }

  @Test
  void startImport_whenLockAcquired_returnsJobIdAndSavesState() throws Exception {
    runUploadTasksSynchronously();
    long assignmentId = 10L;
    long actorId = 77L;
    long courseId = 5L;

    Assignment assignment = instructorGradedAssignment(assignmentId, courseId);
    when(assignmentRepository.findWithDetailsById(assignmentId)).thenReturn(Optional.of(assignment));
    when(userRepository.findById(actorId)).thenReturn(Optional.of(instructorUser(actorId)));
    when(courseInstructorRepository.existsByCourseIdAndUserId(courseId, actorId)).thenReturn(true);
    when(hashidService.encode(courseId)).thenReturn("courseHash");
    when(hashidService.encode(assignmentId)).thenReturn("asgHash");
    when(hashidService.encode(actorId)).thenReturn("instHash");
    when(asyncJobService.acquireImportLock(eq("courseHash"), any())).thenReturn(true);
    when(s3Service.putObject(any(String.class), any(InputStream.class), any(Long.class), any(String.class)))
        .thenReturn("https://example.com/imports/job/source.csv");

    MultipartFile file =
        new MockMultipartFile(
            "file",
            "scores.csv",
            "text/csv",
            "student_email,score\nstudent@test.com,90\n".getBytes());

    var response = csvImportService.startImport(assignmentId, actorId, file);

    assertThat(response.getJobId()).isNotBlank();
    assertThat(response.getStatus()).isEqualTo(JobStatus.UPLOADED);
    verify(asyncJobService).saveJob(any(JobState.class));
    verify(fileSecurityValidator).validateMessageAttachment(file, ValidationConfig.MESSAGE);
  }

  private static Assignment instructorGradedAssignment(long assignmentId, long courseId) {
    Course course = Course.builder().id(courseId).code("CS101").build();
    return Assignment.builder()
        .id(assignmentId)
        .course(course)
        .submissionType(SubmissionType.INSTRUCTOR_GRADED)
        .maxScore(new BigDecimal("100"))
        .build();
  }

  private static User instructorUser(long actorId) {
    return User.builder()
        .id(actorId)
        .email("instructor@test.com")
        .role(UserRole.INSTRUCTOR)
        .isActive(true)
        .build();
  }
}
