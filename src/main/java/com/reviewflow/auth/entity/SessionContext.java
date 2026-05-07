package com.reviewflow.auth.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "session_contexts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionContext {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "family_id", referencedColumnName = "id", nullable = false)
  private RefreshTokenFamily family;

  @Column(name = "device_id", length = 64)
  private String deviceId;

  @Column(name = "user_agent", length = 1024)
  private String userAgent;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "created_at_context")
  private Instant createdAtContext;
}
