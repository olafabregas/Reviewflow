package com.reviewflow.infrastructure.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
public class SecurityStartupValidator {

  @Value("${app.cors.allowed-origins:}")
  private String corsAllowedOrigins;

  @Value("${app.cookie.secure:false}")
  private boolean cookieSecure;

  @Value("${security.token.fingerprinting-enabled:false}")
  private boolean fingerprintingEnabled;

  @PostConstruct
  public void validate() {
    List<String> violations = new ArrayList<>();

    if (corsAllowedOrigins.isBlank()) {
      violations.add(
          "app.cors.allowed-origins must be set in production "
              + "(CORS_ALLOWED_ORIGINS env var is empty)");
    }
    Arrays.stream(corsAllowedOrigins.split(","))
        .map(String::trim)
        .filter(o -> o.equals("*") || o.equals("*://*"))
        .findAny()
        .ifPresent(
            wildcard ->
                violations.add(
                    "app.cors.allowed-origins must not contain wildcard '*' in production. "
                        + "Current value: "
                        + corsAllowedOrigins));

    if (!cookieSecure) {
      violations.add("app.cookie.secure must be true in production");
    }

    if (!fingerprintingEnabled) {
      violations.add("security.token.fingerprinting-enabled must be true in production");
    }

    if (!violations.isEmpty()) {
      String message =
          "PRODUCTION SECURITY MISCONFIGURATION:\n"
              + String.join(
                  "\n", violations.stream().map(v -> "  - " + v).toList());
      log.error(message);
      throw new IllegalStateException(message);
    }

    log.info("Security startup validation passed (prod profile)");
  }
}
