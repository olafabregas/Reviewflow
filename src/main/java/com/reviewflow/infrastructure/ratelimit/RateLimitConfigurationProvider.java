package com.reviewflow.infrastructure.ratelimit;

import com.reviewflow.shared.domain.UserRole;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RateLimitConfigurationProvider {

  @Value("${rate-limit.auth.login.limit:10}")
  private int authLoginLimit;

  @Value("${rate-limit.auth.login.window-minutes:15}")
  private int authLoginWindowMinutes;

  @Value("${rate-limit.auth.refresh.ip.limit:30}")
  private int authRefreshIpLimit;

  @Value("${rate-limit.auth.refresh.user.limit:60}")
  private int authRefreshUserLimit;

  @Value("${rate-limit.auth.refresh.window-seconds:60}")
  private int authRefreshWindowSeconds;

  @Value("${rate-limit.auth.password-reset.request.ip.limit:5}")
  private int passwordResetRequestIpLimit;

  @Value("${rate-limit.auth.password-reset.request.ip.window-hours:1}")
  private int passwordResetRequestIpWindowHours;

  @Value("${rate-limit.auth.password-reset.email.limit:3}")
  private int passwordResetEmailLimit;

  @Value("${rate-limit.auth.password-reset.email.window-hours:1}")
  private int passwordResetEmailWindowHours;

  @Value("${rate-limit.auth.password-reset.confirm.ip.limit:10}")
  private int passwordResetConfirmIpLimit;

  @Value("${rate-limit.auth.password-reset.confirm.ip.window-minutes:15}")
  private int passwordResetConfirmIpWindowMinutes;

  @Value("${rate-limit.auth.step-up.limit:5}")
  private int authStepUpLimit;

  @Value("${rate-limit.auth.step-up.window-minutes:15}")
  private int authStepUpWindowMinutes;

  @Value("${rate-limit.auth.jwt-failure.limit:20}")
  private int authJwtFailureLimit;

  @Value("${rate-limit.auth.jwt-failure.window-seconds:60}")
  private int authJwtFailureWindowSeconds;

  @Value("${rate-limit.auth.ws-ticket.limit:30}")
  private long wsTicketLimit;

  @Value("${rate-limit.auth.ws-ticket.window-minutes:15}")
  private long wsTicketWindowMinutes;

  @Value("${rate-limit.upload.student.limit:10}")
  private long uploadBlockStudentLimit;

  @Value("${rate-limit.upload.instructor.limit:30}")
  private long uploadBlockInstructorLimit;

  @Value("${rate-limit.upload.admin.limit:60}")
  private long uploadBlockAdminLimit;

  @Value("${rate-limit.upload.window-hours:1}")
  private long uploadBlockWindowHours;

  @Value("${rate-limit.messaging.send.limit:30}")
  private int messagingSendLimit;

  @Value("${rate-limit.messaging.send.window-seconds:60}")
  private int messagingSendWindowSeconds;

  @Value("${rate-limit.messaging.create.limit:10}")
  private int messagingCreateLimit;

  @Value("${rate-limit.messaging.create.window-hours:1}")
  private int messagingCreateWindowHours;

  @Value("${rate-limit.api.public.capacity:20}")
  private long apiPublicCapacity;

  @Value("${rate-limit.api.public.refill-per-minute:10}")
  private long apiPublicRefill;

  @Value("${rate-limit.api.read.default.capacity:200}")
  private long apiReadDefaultCapacity;

  @Value("${rate-limit.api.read.default.refill:100}")
  private long apiReadDefaultRefill;

  @Value("${rate-limit.api.read.elevated.capacity:400}")
  private long apiReadElevatedCapacity;

  @Value("${rate-limit.api.read.elevated.refill:200}")
  private long apiReadElevatedRefill;

  @Value("${rate-limit.api.write.default.capacity:60}")
  private long apiWriteDefaultCapacity;

  @Value("${rate-limit.api.write.default.refill:30}")
  private long apiWriteDefaultRefill;

  @Value("${rate-limit.api.write.elevated.capacity:120}")
  private long apiWriteElevatedCapacity;

  @Value("${rate-limit.api.write.elevated.refill:60}")
  private long apiWriteElevatedRefill;

  @Value("${rate-limit.api.export.default.capacity:5}")
  private long apiExportDefaultCapacity;

  @Value("${rate-limit.api.export.default.refill:2}")
  private long apiExportDefaultRefill;

  @Value("${rate-limit.api.export.elevated.capacity:10}")
  private long apiExportElevatedCapacity;

  @Value("${rate-limit.api.export.elevated.refill:5}")
  private long apiExportElevatedRefill;

  public BucketConfiguration getConfig(RateLimitStrategy strategy, UserRole role) {
  BucketTier tier = tierFor(role);
    return switch (strategy) {
      case AUTH_LOGIN ->
          sliding(authLoginLimit, Duration.ofMinutes(authLoginWindowMinutes));
      case AUTH_REFRESH_IP ->
          sliding(authRefreshIpLimit, Duration.ofSeconds(authRefreshWindowSeconds));
      case AUTH_REFRESH_USER ->
          sliding(authRefreshUserLimit, Duration.ofSeconds(authRefreshWindowSeconds));
      case AUTH_PASSWORD_RESET_REQUEST_IP ->
          sliding(passwordResetRequestIpLimit, Duration.ofHours(passwordResetRequestIpWindowHours));
      case AUTH_PASSWORD_RESET_EMAIL ->
          sliding(passwordResetEmailLimit, Duration.ofHours(passwordResetEmailWindowHours));
      case AUTH_PASSWORD_RESET_CONFIRM_IP ->
          sliding(
              passwordResetConfirmIpLimit, Duration.ofMinutes(passwordResetConfirmIpWindowMinutes));
      case AUTH_STEP_UP -> sliding(authStepUpLimit, Duration.ofMinutes(authStepUpWindowMinutes));
      case AUTH_JWT_FAILURE ->
          sliding(authJwtFailureLimit, Duration.ofSeconds(authJwtFailureWindowSeconds));
      case AUTH_WS_TICKET -> sliding(wsTicketLimit, Duration.ofMinutes(wsTicketWindowMinutes));
      case MSG_SEND -> sliding(messagingSendLimit, Duration.ofSeconds(messagingSendWindowSeconds));
      case MSG_CREATE ->
          sliding(messagingCreateLimit, Duration.ofHours(messagingCreateWindowHours));
      case UPLOAD_BLOCK ->
          sliding(uploadBlockCapacity(role), Duration.ofHours(uploadBlockWindowHours));
      case API_PUBLIC -> tokenBucket(apiPublicCapacity, apiPublicRefill);
      case API_READ -> tokenBucket(readCapacity(tier), readRefill(tier));
      case API_WRITE -> tokenBucket(writeCapacity(tier), writeRefill(tier));
      case API_EXPORT -> tokenBucket(exportCapacity(tier), exportRefill(tier));
    };
  }

  public long limitCapacity(RateLimitStrategy strategy, UserRole role) {
    BucketTier tier = tierFor(role);
    return switch (strategy) {
      case AUTH_LOGIN -> authLoginLimit;
      case AUTH_REFRESH_IP -> authRefreshIpLimit;
      case AUTH_REFRESH_USER -> authRefreshUserLimit;
      case AUTH_PASSWORD_RESET_REQUEST_IP -> passwordResetRequestIpLimit;
      case AUTH_PASSWORD_RESET_EMAIL -> passwordResetEmailLimit;
      case AUTH_PASSWORD_RESET_CONFIRM_IP -> passwordResetConfirmIpLimit;
      case AUTH_STEP_UP -> authStepUpLimit;
      case AUTH_JWT_FAILURE -> authJwtFailureLimit;
      case AUTH_WS_TICKET -> wsTicketLimit;
      case MSG_SEND -> messagingSendLimit;
      case MSG_CREATE -> messagingCreateLimit;
      case UPLOAD_BLOCK -> uploadBlockCapacity(role);
      case API_PUBLIC -> apiPublicCapacity;
      case API_READ -> readCapacity(tier);
      case API_WRITE -> writeCapacity(tier);
      case API_EXPORT -> exportCapacity(tier);
    };
  }

  BucketTier tierFor(UserRole role) {
    if (role == UserRole.SYSTEM_ADMIN || role == UserRole.ADMIN) {
      return BucketTier.ELEVATED;
    }
    return BucketTier.DEFAULT;
  }

  private long uploadBlockCapacity(UserRole role) {
    if (role == null) {
      return uploadBlockStudentLimit;
    }
    return switch (role) {
      case SYSTEM_ADMIN, ADMIN -> uploadBlockAdminLimit;
      case INSTRUCTOR -> uploadBlockInstructorLimit;
      default -> uploadBlockStudentLimit;
    };
  }

  private BucketConfiguration sliding(long limit, Duration window) {
    return BucketConfiguration.builder()
        .addLimit(Bandwidth.builder().capacity(limit).refillIntervally(limit, window).build())
        .build();
  }

  private BucketConfiguration tokenBucket(long capacity, long refillPerMinute) {
    return BucketConfiguration.builder()
        .addLimit(
            Bandwidth.classic(
                capacity, Refill.greedy(refillPerMinute, Duration.ofMinutes(1))))
        .build();
  }

  private long readCapacity(BucketTier tier) {
    return tier == BucketTier.ELEVATED ? apiReadElevatedCapacity : apiReadDefaultCapacity;
  }

  private long readRefill(BucketTier tier) {
    return tier == BucketTier.ELEVATED ? apiReadElevatedRefill : apiReadDefaultRefill;
  }

  private long writeCapacity(BucketTier tier) {
    return tier == BucketTier.ELEVATED ? apiWriteElevatedCapacity : apiWriteDefaultCapacity;
  }

  private long writeRefill(BucketTier tier) {
    return tier == BucketTier.ELEVATED ? apiWriteElevatedRefill : apiWriteDefaultRefill;
  }

  private long exportCapacity(BucketTier tier) {
    return tier == BucketTier.ELEVATED ? apiExportElevatedCapacity : apiExportDefaultCapacity;
  }

  private long exportRefill(BucketTier tier) {
    return tier == BucketTier.ELEVATED ? apiExportElevatedRefill : apiExportDefaultRefill;
  }
}
