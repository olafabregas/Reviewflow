package com.reviewflow.infrastructure.security;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtKeyConfigurationProperties {

  /** Legacy single secret; used when {@code keys} is empty and for kid-less token fallback. */
  private String secret;

  private boolean allowLegacyTokensWithoutKid = true;

  private List<KeyEntry> keys = new ArrayList<>();

  @Data
  public static class KeyEntry {
    private String kid;
    private String secret;
    private boolean active;
  }
}
