package com.reviewflow.infrastructure.scheduling;

import com.reviewflow.infrastructure.storage.S3Service;
import com.reviewflow.user.model.entity.PendingS3Deletion;
import com.reviewflow.user.repository.PendingS3DeletionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3CleanupScheduler {

  private final PendingS3DeletionRepository deletionRepository;
  private final S3Service s3Service;

  @Value("${s3.cleanup.grace-period-hours:1}")
  private int gracePeriodHours;

  @Value("${s3.cleanup.batch-size:50}")
  private int batchSize;

  @Scheduled(cron = "${s3.cleanup.cron:0 0 2 * * *}")
  @Transactional
  public void reconcileOrphanedObjects() {
    Instant cutoff = Instant.now().minus(gracePeriodHours, ChronoUnit.HOURS);

    List<PendingS3Deletion> pending =
        deletionRepository.findPendingOlderThan(cutoff, PageRequest.of(0, batchSize));

    if (pending.isEmpty()) {
      return;
    }

    log.info("S3 cleanup: processing {} pending deletions", pending.size());

    for (PendingS3Deletion entry : pending) {
      try {
        s3Service.deleteObjectSilently(entry.getS3Key());
        entry.setCompletedAt(Instant.now());
        deletionRepository.save(entry);
      } catch (Exception e) {
        entry.setRetryCount(entry.getRetryCount() + 1);
        entry.setErrorMessage(e.getMessage());
        entry.setLastAttemptedAt(Instant.now());
        deletionRepository.save(entry);

        if (entry.getRetryCount() >= entry.getMaxRetries()) {
          log.error(
              "S3 cleanup: dead-lettered key={} after {} retries",
              entry.getS3Key(),
              entry.getMaxRetries());
        }
      }
    }
  }
}
