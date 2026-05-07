package com.reviewflow.infrastructure.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthAccessTokenResolver {

  @Value("${jwt.cookie-name:reviewflow_access}")
  private String accessCookieName;

  public Optional<String> resolveRawAccessToken(HttpServletRequest request) {
    if (request.getCookies() != null) {
      for (Cookie c : request.getCookies()) {
        if (accessCookieName.equals(c.getName())) {
          String v = c.getValue();
          if (v != null && !v.isBlank()) {
            return Optional.of(v);
          }
        }
      }
    }
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String t = authHeader.substring(7).trim();
      if (!t.isBlank()) {
        return Optional.of(t);
      }
    }
    return Optional.empty();
  }
}
