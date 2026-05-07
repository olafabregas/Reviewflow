package com.reviewflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, unique = true, length = 255)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  @Builder.Default
  private Boolean revoked = false;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Column(name = "session_issued_at", nullable = false)
  private Instant sessionIssuedAt;

  @Column(name = "family_id", nullable = false, length = 36)
  private String familyId;

  @Column(name = "parent_token_hash", length = 64)
  private String parentTokenHash;

  @Column(name = "device_id", length = 64)
  private String deviceId;

  @Column(name = "ip_created", length = 45)
  private String ipCreated;

  @Column(name = "user_agent_created", length = 500)
  private String userAgentCreated;

  @Column(name = "ip_last_seen", length = 45)
  private String ipLastSeen;

  @Column(name = "user_agent_last_seen", length = 500)
  private String userAgentLastSeen;

  @Column(name = "session_group_id")
  private Long sessionGroupId;
}
