package com.reviewflow.grading.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewflow.assignment.repository.AssignmentRepository;
import com.reviewflow.grading.job.JobState;
import com.reviewflow.grading.job.JobStatus;
import com.reviewflow.grading.job.ValidatedRow;
import com.reviewflow.infrastructure.jobs.AsyncJobService;
import com.reviewflow.infrastructure.storage.S3Service;
import com.reviewflow.shared.domain.Assignment;
import com.reviewflow.shared.domain.SubmissionType;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional CSV commit — must be called from a Spring proxy (not raw async thread). */
@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImportCommitService {

  private final AsyncJobService asyncJobService;
  private final S3Service s3Service;
  private final ObjectMapper objectMapper;
  private final AssignmentRepository assignmentRepository;
  private final UserRepository userRepository;
  private final InstructorScoreService instructorScoreService;
  private final HashidService hashidService;

  @Transactional
  public void commitJob(String jobId) {
    JobState state =
        asyncJobService
            .getJob(jobId)
            .orElseThrow(() -> new IllegalStateException("Job missing: " + jobId));

    Long assignmentId = hashidService.decodeOrThrow(state.assignmentId());
    Long actorId = hashidService.decodeOrThrow(state.instructorId());

    if (state.validatedRowsS3Key() == null) {
      asyncJobService.updateStatus(jobId, JobStatus.FAILED);
      return;
    }

    byte[] json = s3Service.getObject(state.validatedRowsS3Key());
    List<ValidatedRow> rows;
    try {
      rows = objectMapper.readValue(json, new TypeReference<List<ValidatedRow>>() {});
    } catch (Exception e) {
      markFailed(jobId, state, e.getMessage());
      return;
    }

    Assignment assignment =
        assignmentRepository
            .findWithDetailsById(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));
    boolean teamMode = assignment.getSubmissionType() == SubmissionType.TEAM;

    for (ValidatedRow row : rows) {
      if (teamMode) {
        Long teamId = parseTeamId(row.teamId());
        instructorScoreService.create(
            assignmentId, actorId, null, teamId, row.score(), row.comment());
      } else {
        User student =
            userRepository
                .findByEmail(row.studentEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", row.studentEmail()));
        instructorScoreService.create(
            assignmentId, actorId, student.getId(), null, row.score(), row.comment());
      }
    }

    asyncJobService.updateStatus(jobId, JobStatus.COMPLETED);
    if (state.sourceS3Key() != null) {
      s3Service.deleteObject(state.sourceS3Key());
    }
    s3Service.deleteObject(state.validatedRowsS3Key());
  }

  private static Long parseTeamId(String teamIdRaw) {
    if (teamIdRaw == null || teamIdRaw.isBlank()) {
      throw new IllegalArgumentException("team_id is required");
    }
    try {
      return Long.valueOf(teamIdRaw.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid team_id: " + teamIdRaw);
    }
  }

  private void markFailed(String jobId, JobState state, String message) {
    asyncJobService.updateJob(
        jobId,
        s ->
            new JobState(
                s.jobId(),
                JobStatus.FAILED,
                s.assignmentId(),
                s.instructorId(),
                s.courseId(),
                s.totalRows(),
                s.validRows(),
                s.invalidRows(),
                s.processedRows(),
                s.sourceS3Key(),
                s.validatedRowsS3Key(),
                s.errorCsvS3Key(),
                message,
                s.createdAt(),
                Instant.now()));
  }
}
