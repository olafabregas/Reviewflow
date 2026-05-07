package com.reviewflow.infrastructure.scheduling;

import com.reviewflow.auth.repository.PasswordResetTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetCleanupScheduler {

  private final PasswordResetTokenRepository passwordResetTokenRepository;

  @Scheduled(cron = "${auth.password-reset.cleanup-cron:0 0 3 * * *}")
  @Transactional
  public void purgeExpiredTokens() {
    int removed = passwordResetTokenRepository.deleteExpired(Instant.now());
    if (removed > 0) {
      log.info("Password reset token cleanup removed {} rows", removed);
    }
  }
}
