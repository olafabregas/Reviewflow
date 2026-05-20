package com.reviewflow.integration;

import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.UPLOAD_BLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.reviewflow.infrastructure.monitoring.ReviewFlowMetrics;
import com.reviewflow.infrastructure.ratelimit.DefaultRateLimitService;
import com.reviewflow.infrastructure.ratelimit.RateLimitConfig;
import com.reviewflow.infrastructure.ratelimit.RateLimitConfigurationProvider;
import com.reviewflow.infrastructure.ratelimit.RateLimitResult;
import com.reviewflow.infrastructure.ratelimit.RateLimitService;
import com.reviewflow.shared.domain.UserRole;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.DockerClientFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Exercises {@link RateLimitStrategy#UPLOAD_BLOCK} against a real Redis-backed Bucket4j proxy
 * (same wiring as production via {@link RateLimitConfig}). Skipped when Docker is not available.
 */
@EnabledIf("dockerAvailable")
@Testcontainers
@SpringBootTest(
    classes = {
      RateLimitConfig.class,
      RateLimitConfigurationProvider.class,
      DefaultRateLimitService.class,
      ReviewFlowMetrics.class,
      UploadRateLimitIntegrationTest.MetricsTestConfig.class
    })
@TestPropertySource(
    properties = {
      "rate-limit.upload.student.limit=2",
      "rate-limit.upload.instructor.limit=3",
      "rate-limit.upload.admin.limit=4",
      "rate-limit.upload.window-hours=1"
    })
class UploadRateLimitIntegrationTest {

  static boolean dockerAvailable() {
    return DockerClientFactory.instance().isDockerAvailable();
  }

  private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

  @Container
  static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);

  @DynamicPropertySource
  static void registerRedis(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired private RateLimitService rateLimitService;

  @Test
  void uploadBlock_studentAllowsConfiguredCapacityThenDeniesWithRetryAfter() {
    String userId = "student-" + UUID.randomUUID();

    assertThat(consumeUpload(userId, UserRole.STUDENT).allowed()).isTrue();
    assertThat(consumeUpload(userId, UserRole.STUDENT).allowed()).isTrue();

    RateLimitResult denied = consumeUpload(userId, UserRole.STUDENT);
    assertThat(denied.allowed()).isFalse();
    assertThat(denied.strategy()).isEqualTo(UPLOAD_BLOCK);
    assertThat(denied.retryAfterSeconds()).isGreaterThanOrEqualTo(1L);
    assertThat(denied.limitCapacity()).isEqualTo(2L);
  }

  @Test
  void uploadBlock_instructorHasHigherCapacityThanStudent() {
    String studentId = "student-cap-" + UUID.randomUUID();
    String instructorId = "instructor-cap-" + UUID.randomUUID();

    exhaustUploadBlock(studentId, UserRole.STUDENT, 2);
    assertThat(consumeUpload(studentId, UserRole.STUDENT).allowed()).isFalse();

    for (int i = 0; i < 3; i++) {
      assertThat(consumeUpload(instructorId, UserRole.INSTRUCTOR).allowed()).isTrue();
    }
    assertThat(consumeUpload(instructorId, UserRole.INSTRUCTOR).allowed()).isFalse();
  }

  @Test
  void uploadBlock_resetClearsBucketForNewUploads() {
    String userId = "reset-" + UUID.randomUUID();

    exhaustUploadBlock(userId, UserRole.STUDENT, 2);
    assertThat(consumeUpload(userId, UserRole.STUDENT).allowed()).isFalse();

    rateLimitService.reset(userId, UPLOAD_BLOCK);

    assertThat(consumeUpload(userId, UserRole.STUDENT).allowed()).isTrue();
  }

  private RateLimitResult consumeUpload(String userId, UserRole role) {
    return rateLimitService.tryConsume(userId, UPLOAD_BLOCK, role);
  }

  private void exhaustUploadBlock(String userId, UserRole role, int limit) {
    for (int i = 0; i < limit; i++) {
      assertThat(consumeUpload(userId, role).allowed()).isTrue();
    }
  }

  @TestConfiguration
  static class MetricsTestConfig {
    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
