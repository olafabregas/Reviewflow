package com.reviewflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.reviewflow.auth.service.SessionPolicyResolver.SessionPolicy;
import com.reviewflow.shared.domain.UserRole;

class SessionPolicyResolverTest {

  private static SessionPolicyResolver resolverWithDefaults() {
    return new SessionPolicyResolver(
        900_000L,
        604_800_000L,
        12,
        2,
        -1,
        -1,
        -1,
        -1,
        false,
        -1,
        -1,
        -1,
        -1,
        false,
        -1,
        -1,
        -1,
        -1,
        false,
        -1,
        -1,
        -1,
        -1,
        false);
  }

  @Test
  void resolveFor_studentRole_usesDefaults() {
    SessionPolicyResolver resolver = resolverWithDefaults();

    SessionPolicy policy = resolver.resolveFor(UserRole.STUDENT);

    assertThat(policy.idleTimeoutHours()).isEqualTo(2);
    assertThat(policy.absoluteCeilingHours()).isEqualTo(12);
    assertThat(policy.accessTtlMs()).isEqualTo(900_000L);
    assertThat(policy.refreshTtlMs()).isEqualTo(604_800_000L);
    assertThat(policy.requireFingerprint()).isFalse();
  }

  @Test
  void resolveFor_adminRole_withFullCustomPolicy_usesAdminPolicy() {
    SessionPolicyResolver resolver =
        new SessionPolicyResolver(
            900_000L,
            604_800_000L,
            12,
            2,
            -1,
            -1,
            -1,
            -1,
            false,
            -1,
            -1,
            -1,
            -1,
            false,
            4,
            24,
            10,
            24,
            false,
            -1,
            -1,
            -1,
            -1,
            true);

    SessionPolicy policy = resolver.resolveFor(UserRole.ADMIN);

    assertThat(policy.idleTimeoutHours()).isEqualTo(4);
    assertThat(policy.absoluteCeilingHours()).isEqualTo(24);
    assertThat(policy.accessTtlMs()).isEqualTo(10 * 60_000L);
    assertThat(policy.refreshTtlMs()).isEqualTo(24 * 3_600_000L);
    assertThat(policy.requireFingerprint()).isFalse();
  }

  @Test
  void resolveFor_systemAdminRole_withFingerprintRequired_enablesFlag() {
    SessionPolicyResolver resolver =
        new SessionPolicyResolver(
            900_000L,
            604_800_000L,
            12,
            2,
            -1,
            -1,
            -1,
            -1,
            false,
            -1,
            -1,
            -1,
            -1,
            false,
            -1,
            -1,
            -1,
            -1,
            false,
            1,
            8,
            5,
            8,
            true);

    SessionPolicy policy = resolver.resolveFor(UserRole.SYSTEM_ADMIN);

    assertThat(policy.idleTimeoutHours()).isEqualTo(1);
    assertThat(policy.absoluteCeilingHours()).isEqualTo(8);
    assertThat(policy.accessTtlMs()).isEqualTo(5 * 60_000L);
    assertThat(policy.refreshTtlMs()).isEqualTo(8 * 3_600_000L);
    assertThat(policy.requireFingerprint()).isTrue();
  }

  @Test
  void resolveFor_instructorRole_usesDefaultsWhenOverridesDisabled() {
    SessionPolicyResolver resolver = resolverWithDefaults();

    SessionPolicy policy = resolver.resolveFor(UserRole.INSTRUCTOR);

    assertThat(policy.idleTimeoutHours()).isEqualTo(2);
    assertThat(policy.absoluteCeilingHours()).isEqualTo(12);
    assertThat(policy.accessTtlMs()).isEqualTo(900_000L);
    assertThat(policy.refreshTtlMs()).isEqualTo(604_800_000L);
    assertThat(policy.requireFingerprint()).isFalse();
  }
}
