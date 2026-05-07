package com.reviewflow.auth.repository;

import com.reviewflow.shared.domain.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM PasswordResetToken p WHERE p.expiresAt < :now")
  int deleteExpired(@Param("now") Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM PasswordResetToken p WHERE p.user.id = :userId AND p.consumedAt IS NULL")
  void deletePendingForUser(@Param("userId") Long userId);
}
