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
  private final int studentIdleHours;
  private final int studentAbsoluteHours;
  private final int studentAccessTtlMinutes;
  private final int studentRefreshTtlHours;
  private final boolean studentRequireFingerprint;
  private final int instructorIdleHours;
  private final int instructorAbsoluteHours;
  private final int instructorAccessTtlMinutes;
  private final int instructorRefreshTtlHours;
  private final boolean instructorRequireFingerprint;
  private final int adminIdleHours;
  private final int adminAbsoluteHours;
  private final int adminAccessTtlMinutes;
  private final int adminRefreshTtlHours;
  private final boolean adminRequireFingerprint;
  private final int systemAdminIdleHours;
  private final int systemAdminAbsoluteHours;
  private final int systemAdminAccessTtlMinutes;
  private final int systemAdminRefreshTtlHours;
  private final boolean systemAdminRequireFingerprint;

  public SessionPolicyResolver(
      @Value("${jwt.access-expiration-ms:900000}") long defaultAccessTtlMs,
      @Value("${jwt.refresh-expiration-ms:604800000}") long defaultRefreshTtlMs,
      @Value("${session.absolute-ceiling-hours:12}") int defaultAbsoluteCeilingHours,
      @Value("${session.idle-timeout-hours:2}") int defaultIdleHours,
      @Value("${auth.session.policy.STUDENT.idle-hours:-1}") int studentIdleHours,
      @Value("${auth.session.policy.STUDENT.absolute-hours:-1}") int studentAbsoluteHours,
      @Value("${auth.session.policy.STUDENT.access-ttl-minutes:-1}") int studentAccessTtlMinutes,
      @Value("${auth.session.policy.STUDENT.refresh-ttl-hours:-1}") int studentRefreshTtlHours,
      @Value("${auth.session.policy.STUDENT.require-fingerprint:false}")
          boolean studentRequireFingerprint,
      @Value("${auth.session.policy.INSTRUCTOR.idle-hours:-1}") int instructorIdleHours,
      @Value("${auth.session.policy.INSTRUCTOR.absolute-hours:-1}") int instructorAbsoluteHours,
      @Value("${auth.session.policy.INSTRUCTOR.access-ttl-minutes:-1}")
          int instructorAccessTtlMinutes,
      @Value("${auth.session.policy.INSTRUCTOR.refresh-ttl-hours:-1}") int instructorRefreshTtlHours,
      @Value("${auth.session.policy.INSTRUCTOR.require-fingerprint:false}")
          boolean instructorRequireFingerprint,
      @Value("${auth.session.policy.ADMIN.idle-hours:-1}") int adminIdleHours,
      @Value("${auth.session.policy.ADMIN.absolute-hours:-1}") int adminAbsoluteHours,
      @Value("${auth.session.policy.ADMIN.access-ttl-minutes:-1}") int adminAccessTtlMinutes,
      @Value("${auth.session.policy.ADMIN.refresh-ttl-hours:-1}") int adminRefreshTtlHours,
      @Value("${auth.session.policy.ADMIN.require-fingerprint:false}") boolean adminRequireFingerprint,
      @Value("${auth.session.policy.SYSTEM_ADMIN.idle-hours:-1}") int systemAdminIdleHours,
      @Value("${auth.session.policy.SYSTEM_ADMIN.absolute-hours:-1}") int systemAdminAbsoluteHours,
      @Value("${auth.session.policy.SYSTEM_ADMIN.access-ttl-minutes:-1}")
          int systemAdminAccessTtlMinutes,
      @Value("${auth.session.policy.SYSTEM_ADMIN.refresh-ttl-hours:-1}")
          int systemAdminRefreshTtlHours,
      @Value("${auth.session.policy.SYSTEM_ADMIN.require-fingerprint:false}")
          boolean systemAdminRequireFingerprint) {
    this.defaultAccessTtlMs = defaultAccessTtlMs;
    this.defaultRefreshTtlMs = defaultRefreshTtlMs;
    this.defaultAbsoluteCeilingHours = defaultAbsoluteCeilingHours;
    this.defaultIdleHours = defaultIdleHours;
    this.studentIdleHours = studentIdleHours;
    this.studentAbsoluteHours = studentAbsoluteHours;
    this.studentAccessTtlMinutes = studentAccessTtlMinutes;
    this.studentRefreshTtlHours = studentRefreshTtlHours;
    this.studentRequireFingerprint = studentRequireFingerprint;
    this.instructorIdleHours = instructorIdleHours;
    this.instructorAbsoluteHours = instructorAbsoluteHours;
    this.instructorAccessTtlMinutes = instructorAccessTtlMinutes;
    this.instructorRefreshTtlHours = instructorRefreshTtlHours;
    this.instructorRequireFingerprint = instructorRequireFingerprint;
    this.adminIdleHours = adminIdleHours;
    this.adminAbsoluteHours = adminAbsoluteHours;
    this.adminAccessTtlMinutes = adminAccessTtlMinutes;
    this.adminRefreshTtlHours = adminRefreshTtlHours;
    this.adminRequireFingerprint = adminRequireFingerprint;
    this.systemAdminIdleHours = systemAdminIdleHours;
    this.systemAdminAbsoluteHours = systemAdminAbsoluteHours;
    this.systemAdminAccessTtlMinutes = systemAdminAccessTtlMinutes;
    this.systemAdminRefreshTtlHours = systemAdminRefreshTtlHours;
    this.systemAdminRequireFingerprint = systemAdminRequireFingerprint;
  }

  public SessionPolicy resolveFor(UserRole role) {
    int idleHours = defaultIdleHours;
    int absoluteHours = defaultAbsoluteCeilingHours;
    long accessTtlMs = defaultAccessTtlMs;
    long refreshTtlMs = defaultRefreshTtlMs;
    boolean requireFingerprint = false;

    if (role == UserRole.STUDENT) {
      idleHours = pickPositive(studentIdleHours, defaultIdleHours);
      absoluteHours = pickPositive(studentAbsoluteHours, defaultAbsoluteCeilingHours);
      accessTtlMs = toMsOrDefault(studentAccessTtlMinutes, defaultAccessTtlMs);
      refreshTtlMs = hoursToMsOrDefault(studentRefreshTtlHours, defaultRefreshTtlMs);
      requireFingerprint = studentRequireFingerprint;
    } else if (role == UserRole.INSTRUCTOR) {
      idleHours = pickPositive(instructorIdleHours, defaultIdleHours);
      absoluteHours = pickPositive(instructorAbsoluteHours, defaultAbsoluteCeilingHours);
      accessTtlMs = toMsOrDefault(instructorAccessTtlMinutes, defaultAccessTtlMs);
      refreshTtlMs = hoursToMsOrDefault(instructorRefreshTtlHours, defaultRefreshTtlMs);
      requireFingerprint = instructorRequireFingerprint;
    } else if (role == UserRole.ADMIN) {
      idleHours = pickPositive(adminIdleHours, defaultIdleHours);
      absoluteHours = pickPositive(adminAbsoluteHours, defaultAbsoluteCeilingHours);
      accessTtlMs = toMsOrDefault(adminAccessTtlMinutes, defaultAccessTtlMs);
      refreshTtlMs = hoursToMsOrDefault(adminRefreshTtlHours, defaultRefreshTtlMs);
      requireFingerprint = adminRequireFingerprint;
    } else if (role == UserRole.SYSTEM_ADMIN) {
      idleHours = pickPositive(systemAdminIdleHours, defaultIdleHours);
      absoluteHours = pickPositive(systemAdminAbsoluteHours, defaultAbsoluteCeilingHours);
      accessTtlMs = toMsOrDefault(systemAdminAccessTtlMinutes, defaultAccessTtlMs);
      refreshTtlMs = hoursToMsOrDefault(systemAdminRefreshTtlHours, defaultRefreshTtlMs);
      requireFingerprint = systemAdminRequireFingerprint;
    }

    return new SessionPolicy(idleHours, absoluteHours, accessTtlMs, refreshTtlMs, requireFingerprint);
  }

  private static int pickPositive(int value, int fallback) {
    return value > 0 ? value : fallback;
  }

  private static long toMsOrDefault(int minutes, long fallbackMs) {
    return minutes > 0 ? minutes * 60_000L : fallbackMs;
  }

  private static long hoursToMsOrDefault(int hours, long fallbackMs) {
    return hours > 0 ? hours * 3_600_000L : fallbackMs;
  }

  public record SessionPolicy(
      int idleTimeoutHours,
      int absoluteCeilingHours,
      long accessTtlMs,
      long refreshTtlMs,
      boolean requireFingerprint) {}
}
