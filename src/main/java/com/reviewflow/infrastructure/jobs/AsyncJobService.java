package com.reviewflow.infrastructure.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewflow.grading.job.JobState;
import com.reviewflow.grading.job.JobStatus;
import com.reviewflow.shared.exception.StorageException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncJobService {

  private static final String JOB_KEY_PREFIX = "reviewflow:job:";
  private static final String IMPORT_LOCK_PREFIX = "reviewflow:import:lock:";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Value("${async.job.ttl-hours:24}")
  private int jobTtlHours;

  @Value("${async.import.lock.ttl-hours:2}")
  private int importLockTtlHours;

  public void saveJob(JobState state) {
    String key = JOB_KEY_PREFIX + state.jobId();
    try {
      redisTemplate
          .opsForValue()
          .set(key, objectMapper.writeValueAsString(state), Duration.ofHours(jobTtlHours));
    } catch (JsonProcessingException e) {
      throw new StorageException("Failed to persist job state", e);
    }
  }

  public Optional<JobState> getJob(String jobId) {
    String value = redisTemplate.opsForValue().get(JOB_KEY_PREFIX + jobId);
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readValue(value, JobState.class));
    } catch (JsonProcessingException e) {
      log.warn("Corrupt job state for {}: {}", jobId, e.getMessage());
      return Optional.empty();
    }
  }

  public void updateStatus(String jobId, JobStatus status) {
    updateJob(jobId, state -> withStatus(state, status, Instant.now()));
  }

  public void updateProgress(String jobId, int processedRows) {
    updateJob(
        jobId,
        state ->
            new JobState(
                state.jobId(),
                state.status(),
                state.assignmentId(),
                state.instructorId(),
                state.courseId(),
                state.totalRows(),
                state.validRows(),
                state.invalidRows(),
                processedRows,
                state.sourceS3Key(),
                state.validatedRowsS3Key(),
                state.errorCsvS3Key(),
                state.errorMessage(),
                state.createdAt(),
                Instant.now()));
  }

  public void updateJob(String jobId, UnaryOperator<JobState> updater) {
    getJob(jobId).ifPresent(state -> saveJob(updater.apply(state)));
  }

  public boolean acquireImportLock(String courseId, String jobId) {
    String lockKey = IMPORT_LOCK_PREFIX + courseId;
    Boolean acquired =
        redisTemplate
            .opsForValue()
            .setIfAbsent(lockKey, jobId, Duration.ofHours(importLockTtlHours));
    return Boolean.TRUE.equals(acquired);
  }

  public void releaseImportLock(String courseId) {
    redisTemplate.delete(IMPORT_LOCK_PREFIX + courseId);
  }

  private static JobState withStatus(JobState state, JobStatus status, Instant updatedAt) {
    return new JobState(
        state.jobId(),
        status,
        state.assignmentId(),
        state.instructorId(),
        state.courseId(),
        state.totalRows(),
        state.validRows(),
        state.invalidRows(),
        state.processedRows(),
        state.sourceS3Key(),
        state.validatedRowsS3Key(),
        state.errorCsvS3Key(),
        state.errorMessage(),
        state.createdAt(),
        updatedAt);
  }
}
