package com.reviewflow.auth.service;

import com.reviewflow.shared.domain.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Per-role session clocks and token TTLs. Idle/absolute refresh checks use global
 * {@code session.*} unless extended later; access TTL can be tightened for elevated roles.
 */
@Service
public class SessionPolicyResolver {

  private final long defaultAccessTtlMs;
  private final long defaultRefreshTtlMs;
  private final int defaultAbsoluteCeilingHours;
  private final int defaultIdleHours;
  private final int adminAccessTtlMinutes;
  private final int systemAdminAccessTtlMinutes;

  public SessionPolicyResolver(
      @Value("${jwt.access-expiration-ms:900000}") long defaultAccessTtlMs,
      @Value("${jwt.refresh-expiration-ms:604800000}") long defaultRefreshTtlMs,
      @Value("${session.absolute-ceiling-hours:12}") int defaultAbsoluteCeilingHours,
      @Value("${session.idle-timeout-hours:2}") int defaultIdleHours,
      @Value("${auth.session.policy.ADMIN.access-ttl-minutes:0}") int adminAccessTtlMinutes,
      @Value("${auth.session.policy.SYSTEM_ADMIN.access-ttl-minutes:0}")
          int systemAdminAccessTtlMinutes) {
    this.defaultAccessTtlMs = defaultAccessTtlMs;
    this.defaultRefreshTtlMs = defaultRefreshTtlMs;
    this.defaultAbsoluteCeilingHours = defaultAbsoluteCeilingHours;
    this.defaultIdleHours = defaultIdleHours;
    this.adminAccessTtlMinutes = adminAccessTtlMinutes;
    this.systemAdminAccessTtlMinutes = systemAdminAccessTtlMinutes;
  }

  public SessionPolicy resolveFor(UserRole role) {
    long access = defaultAccessTtlMs;
    if (role == UserRole.ADMIN && adminAccessTtlMinutes > 0) {
      access = adminAccessTtlMinutes * 60_000L;
    } else if (role == UserRole.SYSTEM_ADMIN && systemAdminAccessTtlMinutes > 0) {
      access = systemAdminAccessTtlMinutes * 60_000L;
    }
    return new SessionPolicy(
        defaultIdleHours, defaultAbsoluteCeilingHours, access, defaultRefreshTtlMs);
  }

  public record SessionPolicy(
      int idleTimeoutHours,
      int absoluteCeilingHours,
      long accessTtlMs,
      long refreshTtlMs) {}
}
