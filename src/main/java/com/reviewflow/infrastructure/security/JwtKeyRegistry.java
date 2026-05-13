package com.reviewflow.infrastructure.security;

import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class JwtKeyRegistry {

  private final Map<String, SecretKey> keysByKid = new LinkedHashMap<>();
  @Getter private final String activeKid;
  private final SecretKey legacyKey;
  private final boolean allowLegacyTokensWithoutKid;

  public JwtKeyRegistry(JwtKeyConfigurationProperties props) {
    if (props.getSecret() == null || props.getSecret().isBlank()) {
      throw new IllegalStateException("jwt.secret must be set");
    }
    this.legacyKey = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    this.allowLegacyTokensWithoutKid = props.isAllowLegacyTokensWithoutKid();

    String chosenActive = null;
    int activeCount = 0;
    if (props.getKeys() != null) {
      for (JwtKeyConfigurationProperties.KeyEntry entry : props.getKeys()) {
        if (entry.getKid() == null || entry.getKid().isBlank()) {
          throw new IllegalStateException("jwt.keys[].kid must be set");
        }
        if (entry.getSecret() == null || entry.getSecret().isBlank()) {
          throw new IllegalStateException("jwt.keys[" + entry.getKid() + "].secret must be set");
        }
        if (keysByKid.containsKey(entry.getKid())) {
          throw new IllegalStateException("Duplicate jwt kid configured: " + entry.getKid());
        }
        SecretKey k = Keys.hmacShaKeyFor(entry.getSecret().getBytes(StandardCharsets.UTF_8));
        keysByKid.put(entry.getKid(), k);
        if (entry.isActive()) {
          chosenActive = entry.getKid();
          activeCount++;
        }
      }
    }

    if (keysByKid.isEmpty()) {
      keysByKid.put("default", legacyKey);
      this.activeKid = "default";
    } else if (activeCount == 1 && chosenActive != null && keysByKid.containsKey(chosenActive)) {
      this.activeKid = chosenActive;
    } else {
      if (activeCount == 0) {
        throw new IllegalStateException(
            "Exactly one jwt.keys[] entry must be marked active=true when jwt.keys[] is configured");
      }
      throw new IllegalStateException(
          "Exactly one jwt.keys[] entry must be marked active=true; found " + activeCount);
    }
  }

  public SigningKey activeSigningKey() {
    SecretKey key = keysByKid.get(activeKid);
    if (key == null) {
      throw new JwtException("No signing key for active kid: " + activeKid);
    }
    return new SigningKey(activeKid, key);
  }

  public SecretKey verificationKeyForHeader(Header header) {
    Object kidObj = header.get("kid");
    String kid = kidObj != null ? kidObj.toString() : null;
    if (kid == null || kid.isBlank()) {
      if (allowLegacyTokensWithoutKid) {
        return legacyKey;
      }
      throw new JwtException("JWT kid is required");
    }
    SecretKey key = keysByKid.get(kid);
    if (key == null) {
      throw new JwtException("Unknown jwt kid: " + kid);
    }
    return key;
  }

  public record SigningKey(String kid, SecretKey secretKey) {}

  public static JwtKeyRegistry forTests(String secret) {
    JwtKeyConfigurationProperties p = new JwtKeyConfigurationProperties();
    p.setSecret(secret);
    return new JwtKeyRegistry(p);
  }
}
