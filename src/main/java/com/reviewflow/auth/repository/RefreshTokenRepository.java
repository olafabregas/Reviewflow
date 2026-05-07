package com.reviewflow.auth.repository;

import com.reviewflow.shared.domain.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  List<RefreshToken> findByUser_IdAndRevokedFalseAndExpiresAtAfter(Long userId, Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked ="
          + " false")
  int revokeAllForUser(@Param("userId") Long userId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.familyId = :familyId AND rt.revoked ="
          + " false")
  int revokeActiveTokensInFamily(@Param("familyId") String familyId);
}
