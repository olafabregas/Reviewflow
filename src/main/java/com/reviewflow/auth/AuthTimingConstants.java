package com.reviewflow.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Precomputed bcrypt material so the login path always runs {@code passwordEncoder.matches} against
 * a real hash when the email is unknown, reducing login timing enumeration.
 */
public final class AuthTimingConstants {

  private AuthTimingConstants() {}

  /** Cost factor matches typical user password hashes (10). */
  public static final String DUMMY_PASSWORD_HASH;

  static {
    var encoder = new BCryptPasswordEncoder(10);
    DUMMY_PASSWORD_HASH = encoder.encode("__ReviewFlow_login_timing_dummy_v1__");
  }
}
