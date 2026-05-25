package com.reviewflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reviewflow.infrastructure.security.JwtKeyConfigurationProperties;
import com.reviewflow.infrastructure.security.JwtKeyRegistry;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JwtKeyRegistryTest {

  @Test
  void constructor_withKeySet_requiresExactlyOneActiveKey() {
    JwtKeyConfigurationProperties props = baseProps();
    props.getKeys().add(key("k1", secret("k1"), false));
    props.getKeys().add(key("k2", secret("k2"), false));

    assertThatThrownBy(() -> new JwtKeyRegistry(props))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Exactly one jwt.keys[] entry must be marked active=true");
  }

  @Test
  void constructor_withSecretShorterThan32Bytes_failsFast() {
    JwtKeyConfigurationProperties props = baseProps();
    props.setSecret("too-short");

    assertThatThrownBy(() -> new JwtKeyRegistry(props))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET must be at least 32 bytes");
  }

  @Test
  void constructor_withDuplicateKid_failsFast() {
    JwtKeyConfigurationProperties props = baseProps();
    props.getKeys().add(key("dup", secret("a"), true));
    props.getKeys().add(key("dup", secret("b"), false));

    assertThatThrownBy(() -> new JwtKeyRegistry(props))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate jwt kid configured");
  }

  @Test
  void activeSigningKey_returnsConfiguredActiveKid() {
    JwtKeyConfigurationProperties props = baseProps();
    props.getKeys().add(key("old", secret("old"), false));
    props.getKeys().add(key("new", secret("new"), true));

    JwtKeyRegistry registry = new JwtKeyRegistry(props);

    assertThat(registry.activeSigningKey().kid()).isEqualTo("new");
  }

  @Test
  void verificationKeyForHeader_whenNoKidAndLegacyDisabled_rejectsToken() {
    JwtKeyConfigurationProperties props = baseProps();
    props.setAllowLegacyTokensWithoutKid(false);
    props.getKeys().add(key("active", secret("active"), true));

    JwtKeyRegistry registry = new JwtKeyRegistry(props);
    String tokenWithoutKid =
        Jwts.builder()
            .subject("user@test.com")
            .signWith(Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8)))
            .compact();

    assertThatThrownBy(
            () ->
                Jwts.parser()
                    .keyLocator(header -> registry.verificationKeyForHeader(header))
                    .build()
                    .parseSignedClaims(tokenWithoutKid))
        .isInstanceOf(io.jsonwebtoken.JwtException.class)
        .hasMessageContaining("JWT kid is required");
  }

  private static JwtKeyConfigurationProperties baseProps() {
    JwtKeyConfigurationProperties props = new JwtKeyConfigurationProperties();
    props.setSecret(secret("legacy"));
    return props;
  }

  private static JwtKeyConfigurationProperties.KeyEntry key(
      String kid, String secret, boolean active) {
    JwtKeyConfigurationProperties.KeyEntry entry = new JwtKeyConfigurationProperties.KeyEntry();
    entry.setKid(kid);
    entry.setSecret(secret);
    entry.setActive(active);
    return entry;
  }

  private static String secret(String suffix) {
    return "test-secret-key-for-" + suffix + "-that-is-long-enough-for-sha256";
  }
}
