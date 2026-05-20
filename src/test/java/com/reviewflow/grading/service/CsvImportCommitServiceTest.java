package com.reviewflow.grading.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewflow.assignment.repository.AssignmentRepository;
import com.reviewflow.grading.job.JobState;
import com.reviewflow.grading.job.JobStatus;
import com.reviewflow.grading.job.ValidatedRow;
import com.reviewflow.grading.repository.InstructorScoreRepository;
import com.reviewflow.infrastructure.jobs.AsyncJobService;
import com.reviewflow.infrastructure.storage.S3Service;
import com.reviewflow.shared.domain.Assignment;
import com.reviewflow.shared.domain.Course;
import com.reviewflow.shared.domain.InstructorScore;
import com.reviewflow.shared.domain.SubmissionType;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CsvImportCommitServiceTest {

  @Mock private AsyncJobService asyncJobService;
  @Mock private S3Service s3Service;
  @Mock private AssignmentRepository assignmentRepository;
  @Mock private UserRepository userRepository;
  @Mock private InstructorScoreRepository instructorScoreRepository;
  @Mock private InstructorScoreService instructorScoreService;
  @Mock private HashidService hashidService;

  private CsvImportCommitService commitService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    commitService =
        new CsvImportCommitService(
            asyncJobService,
            s3Service,
            objectMapper,
            assignmentRepository,
            userRepository,
            instructorScoreRepository,
            instructorScoreService,
            hashidService);
  }

  @Test
  void commitJob_individualMode_usesSingleEmailLookupAndSaveAll() throws Exception {
    String jobId = "job-1";
    JobState state =
        new JobState(
            jobId,
            JobStatus.VALIDATION_PASSED,
            "a1",
            "u1",
            "c1",
            2,
            2,
            0,
            2,
            "src",
            "validated",
            null,
            null,
            Instant.now(),
            Instant.now());
    when(asyncJobService.getJob(jobId)).thenReturn(Optional.of(state));
    when(hashidService.decodeOrThrow("a1")).thenReturn(10L);
    when(hashidService.decodeOrThrow("u1")).thenReturn(20L);

    List<ValidatedRow> rows =
        List.of(
            new ValidatedRow("s1@test.com", null, BigDecimal.TEN, "c1"),
            new ValidatedRow("s2@test.com", null, BigDecimal.ONE, "c2"));
    when(s3Service.getObject("validated"))
        .thenReturn(objectMapper.writeValueAsBytes(rows));

    Course course = Course.builder().id(99L).build();
    Assignment assignment =
        Assignment.builder()
            .id(10L)
            .course(course)
            .submissionType(SubmissionType.INSTRUCTOR_GRADED)
            .maxScore(BigDecimal.valueOf(100))
            .build();
    when(assignmentRepository.findWithDetailsById(10L)).thenReturn(Optional.of(assignment));

    User u1 = User.builder().id(1L).email("s1@test.com").build();
    User u2 = User.builder().id(2L).email("s2@test.com").build();
    when(userRepository.findByEmailIn(any())).thenReturn(List.of(u1, u2));

    InstructorScore score1 = InstructorScore.builder().assignment(assignment).student(u1).build();
    InstructorScore score2 = InstructorScore.builder().assignment(assignment).student(u2).build();
    when(instructorScoreService.buildScoresForCsvImport(eq(10L), eq(20L), eq(rows), any()))
        .thenReturn(List.of(score1, score2));

    commitService.commitJob(jobId);

    verify(userRepository, never()).findByEmail(any());
    verify(userRepository).findByEmailIn(any());
    verify(instructorScoreRepository).saveAll(anyList());
    verify(instructorScoreService).afterCsvImportCommitted(99L, 20L, 2);
  }
}
