package com.reviewflow.grading.service;

import com.reviewflow.grading.dto.response.JobErrorDownloadResponse;
import com.reviewflow.grading.dto.response.JobStatusDto;
import com.reviewflow.grading.exception.JobNotFoundException;
import com.reviewflow.grading.exception.JobNotReadyForCommitException;
import com.reviewflow.grading.exception.NoErrorsToDownloadException;
import com.reviewflow.grading.job.JobState;
import com.reviewflow.grading.job.JobStatus;
import com.reviewflow.infrastructure.jobs.AsyncJobService;
import com.reviewflow.infrastructure.jobs.SseEmitterRegistry;
import com.reviewflow.infrastructure.storage.S3Service;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.exception.AccessDeniedException;
import com.reviewflow.shared.util.HashidService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ImportJobService {

  private final AsyncJobService asyncJobService;
  private final CsvImportService csvImportService;
  private final SseEmitterRegistry sseEmitterRegistry;
  private final S3Service s3Service;
  private final HashidService hashidService;
  private final Executor csvWorkerExecutor;

  public ImportJobService(
      AsyncJobService asyncJobService,
      CsvImportService csvImportService,
      SseEmitterRegistry sseEmitterRegistry,
      S3Service s3Service,
      HashidService hashidService,
      @Qualifier("csvWorkerExecutor") Executor csvWorkerExecutor) {
    this.asyncJobService = asyncJobService;
    this.csvImportService = csvImportService;
    this.sseEmitterRegistry = sseEmitterRegistry;
    this.s3Service = s3Service;
    this.hashidService = hashidService;
    this.csvWorkerExecutor = csvWorkerExecutor;
  }

  public JobStatusDto getStatus(String jobId, Long actorId, UserRole actorRole) {
    JobState state = requireJobForActor(jobId, actorId, actorRole);
    return toDto(state);
  }

  public SseEmitter subscribeProgress(String jobId, Long actorId, UserRole actorRole) {
    requireJobForActor(jobId, actorId, actorRole);
    return sseEmitterRegistry.register(jobId);
  }

  public JobErrorDownloadResponse getErrorDownloadUrl(
      String jobId, Long actorId, UserRole actorRole) {
    JobState state = requireJobForActor(jobId, actorId, actorRole);
    if (state.status() != JobStatus.VALIDATION_FAILED || state.errorCsvS3Key() == null) {
      throw new NoErrorsToDownloadException(
          "Error CSV is only available when validation failed");
    }
    return JobErrorDownloadResponse.builder()
        .downloadUrl(s3Service.generatePresignedDownloadUrl(state.errorCsvS3Key()))
        .build();
  }

  public JobStatusDto commit(String jobId, Long actorId, UserRole actorRole) {
    JobState state = requireJobForActor(jobId, actorId, actorRole);
    if (state.status() != JobStatus.VALIDATION_PASSED) {
      throw new JobNotReadyForCommitException(
          "Commit is only allowed when status is VALIDATION_PASSED");
    }
    asyncJobService.updateStatus(jobId, JobStatus.COMMITTING);
    CompletableFuture.runAsync(() -> csvImportService.runCommit(jobId), csvWorkerExecutor);
    return JobStatusDto.builder().jobId(jobId).status(JobStatus.COMMITTING).build();
  }

  private JobState requireJobForActor(String jobId, Long actorId, UserRole actorRole) {
    JobState state =
        asyncJobService.getJob(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
    if (actorRole != UserRole.SYSTEM_ADMIN) {
      Long ownerId = hashidService.decode(state.instructorId());
      if (ownerId == null || !ownerId.equals(actorId)) {
        throw new AccessDeniedException("Not authorized to access this import job");
      }
    }
    return state;
  }

  static JobStatusDto toDto(JobState state) {
    return JobStatusDto.builder()
        .jobId(state.jobId())
        .status(state.status())
        .assignmentId(state.assignmentId())
        .totalRows(state.totalRows())
        .validRows(state.validRows())
        .invalidRows(state.invalidRows())
        .processedRows(state.processedRows())
        .errorMessage(state.errorMessage())
        .createdAt(state.createdAt())
        .updatedAt(state.updatedAt())
        .build();
  }
}
