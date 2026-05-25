package com.reviewflow.user.event;

import com.reviewflow.user.model.entity.PendingS3Deletion;
import com.reviewflow.user.repository.PendingS3DeletionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AvatarCleanupListener {

  private final PendingS3DeletionRepository deletionRepository;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleAvatarOrphaned(AvatarOrphanedEvent event) {
    PendingS3Deletion deletion =
        PendingS3Deletion.builder()
            .s3Key(event.s3Key())
            .entityType("avatar")
            .reason(event.reason())
            .createdAt(Instant.now())
            .build();
    deletionRepository.save(deletion);
  }
}
