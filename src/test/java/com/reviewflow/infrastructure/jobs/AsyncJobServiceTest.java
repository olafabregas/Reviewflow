package com.reviewflow.infrastructure.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.reviewflow.grading.job.JobState;
import com.reviewflow.grading.job.JobStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AsyncJobServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOps;

  private ObjectMapper objectMapper;
  private AsyncJobService asyncJobService;

  @BeforeEach
  void setUp() {
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    asyncJobService = new AsyncJobService(redisTemplate, objectMapper);
    ReflectionTestUtils.setField(asyncJobService, "jobTtlHours", 24);
    ReflectionTestUtils.setField(asyncJobService, "importLockTtlHours", 2);
  }

  @Test
  void saveJobAndGetJob_roundTripsState() throws Exception {
    JobState state =
        new JobState(
            "job-1",
            JobStatus.UPLOADED,
            "asgHash",
            "instHash",
            "courseHash",
            0,
            0,
            0,
            0,
            "imports/job-1/source.csv",
            null,
            null,
            null,
            Instant.parse("2026-05-18T10:00:00Z"),
            Instant.parse("2026-05-18T10:00:00Z"));

    ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
    asyncJobService.saveJob(state);
    verify(valueOps)
        .set(eq("reviewflow:job:job-1"), jsonCaptor.capture(), eq(Duration.ofHours(24)));

    when(valueOps.get("reviewflow:job:job-1")).thenReturn(jsonCaptor.getValue());

    assertThat(asyncJobService.getJob("job-1")).isPresent();
    assertThat(asyncJobService.getJob("job-1").get().status()).isEqualTo(JobStatus.UPLOADED);
    assertThat(asyncJobService.getJob("job-1").get().sourceS3Key())
        .isEqualTo("imports/job-1/source.csv");
  }

  @Test
  void acquireImportLock_returnsFalseWhenAlreadyHeld() {
    when(valueOps.setIfAbsent(eq("reviewflow:import:lock:courseHash"), eq("job-2"), any(Duration.class)))
        .thenReturn(false);

    assertThat(asyncJobService.acquireImportLock("courseHash", "job-2")).isFalse();
  }

  @Test
  void acquireImportLock_returnsTrueWhenFree() {
    when(valueOps.setIfAbsent(eq("reviewflow:import:lock:courseHash"), eq("job-2"), any(Duration.class)))
        .thenReturn(true);

    assertThat(asyncJobService.acquireImportLock("courseHash", "job-2")).isTrue();
  }

  @Test
  void releaseImportLock_deletesKey() {
    asyncJobService.releaseImportLock("courseHash");
    verify(redisTemplate).delete("reviewflow:import:lock:courseHash");
  }
}
