package com.reviewflow.user.repository;

import com.reviewflow.user.model.entity.PendingS3Deletion;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PendingS3DeletionRepository extends JpaRepository<PendingS3Deletion, Long> {

  @Query(
      """
      SELECT p FROM PendingS3Deletion p
      WHERE p.completedAt IS NULL
        AND p.retryCount < p.maxRetries
        AND p.createdAt < :cutoff
      ORDER BY p.createdAt ASC
      """)
  List<PendingS3Deletion> findPendingOlderThan(
      @Param("cutoff") Instant cutoff, Pageable pageable);
}
