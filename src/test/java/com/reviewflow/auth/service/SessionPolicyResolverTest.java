package com.reviewflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.reviewflow.auth.service.SessionPolicyResolver.SessionPolicy;
import com.reviewflow.shared.domain.UserRole;

class SessionPolicyResolverTest {

  @Test
  void resolveFor_studentRole_usesDefaults() {
    SessionPolicyResolver resolver =
        new SessionPolicyResolver(
            900_000L, // default access 15 min
            604_800_000L, // default refresh 7 days
            12, // absolute ceiling 12 hours
            2, // idle timeout 2 hours
            0, // admin access (disabled)
            0 // system admin access (disabled)
            );

    SessionPolicy policy = resolver.resolveFor(UserRole.STUDENT);

    assertThat(policy.idleTimeoutHours()).isEqualTo(2);
    assertThat(policy.absoluteCeilingHours()).isEqualTo(12);
    assertThat(policy.accessTtlMs()).isEqualTo(900_000L);
    assertThat(policy.refreshTtlMs()).isEqualTo(604_800_000L);
  }

  @Test
  void resolveFor_adminRole_withCustomAccessTtl_usesAdminTtl() {
    SessionPolicyResolver resolver =
        new SessionPolicyResolver(
            900_000L, // default access 15 min
            604_800_000L, // default refresh 7 days
            12, // absolute ceiling 12 hours
            2, // idle timeout 2 hours
            5, // admin access 5 minutes
            0 // system admin access (disabled)
            );

    SessionPolicy policy = resolver.resolveFor(UserRole.ADMIN);

    assertThat(policy.idleTimeoutHours()).isEqualTo(2);
    assertThat(policy.absoluteCeilingHours()).isEqualTo(12);
    assertThat(policy.accessTtlMs()).isEqualTo(5 * 60_000L); // 5 min in ms
    assertThat(policy.refreshTtlMs()).isEqualTo(604_800_000L);
  }

  @Test
  void resolveFor_systemAdminRole_withCustomAccessTtl_usesSystemAdminTtl() {
    SessionPolicyResolver resolver =
        new SessionPolicyResolver(
            900_000L, // default access 15 min
            604_800_000L, // default refresh 7 days
            12, // absolute ceiling 12 hours
            2, // idle timeout 2 hours
            0, // admin access (disabled)
            3 // system admin access 3 minutes
            );

    SessionPolicy policy = resolver.resolveFor(UserRole.SYSTEM_ADMIN);

    assertThat(policy.idleTimeoutHours()).isEqualTo(2);
    assertThat(policy.absoluteCeilingHours()).isEqualTo(12);
    assertThat(policy.accessTtlMs()).isEqualTo(3 * 60_000L); // 3 min in ms
    assertThat(policy.refreshTtlMs()).isEqualTo(604_800_000L);
  }

  @Test
  void resolveFor_adminRole_withZeroCustomAccessTtl_usesDefault() {
    SessionPolicyResolver resolver =
        new SessionPolicyResolver(
            900_000L, // default access 15 min
            604_800_000L, // default refresh 7 days
            12, // absolute ceiling 12 hours
            2, // idle timeout 2 hours
            0, // admin access disabled (0)
            0 // system admin access (disabled)
            );

    SessionPolicy policy = resolver.resolveFor(UserRole.ADMIN);

    assertThat(policy.accessTtlMs()).isEqualTo(900_000L); // Falls back to default
  }

  @Test
  void resolveFor_instructorRole_usesDefaults() {
    SessionPolicyResolver resolver =
        new SessionPolicyResolver(
            900_000L, // default access 15 min
            604_800_000L, // default refresh 7 days
            12, // absolute ceiling 12 hours
            2, // idle timeout 2 hours
            5, // admin access 5 minutes
            3 // system admin access 3 minutes
            );

    SessionPolicy policy = resolver.resolveFor(UserRole.INSTRUCTOR);

    assertThat(policy.idleTimeoutHours()).isEqualTo(2);
    assertThat(policy.absoluteCeilingHours()).isEqualTo(12);
    assertThat(policy.accessTtlMs()).isEqualTo(900_000L); // Not ADMIN or SYSTEM_ADMIN
    assertThat(policy.refreshTtlMs()).isEqualTo(604_800_000L);
  }
}
