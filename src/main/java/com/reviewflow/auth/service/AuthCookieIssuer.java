package com.reviewflow.auth.service;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Single place for auth cookie names, flags, and paths (refresh scoped to /api/v1/auth per Auth
 * Hardening).
 */
@Component
@RequiredArgsConstructor
public class AuthCookieIssuer {

  @Value("${jwt.cookie-name:reviewflow_access}")
  private String accessCookieName;

  @Value("${jwt.refresh-cookie-name:reviewflow_refresh}")
  private String refreshCookieName;

  @Value("${app.cookie.secure:false}")
  private boolean cookieSecure;

  public static final String REFRESH_COOKIE_PATH = "/api/v1/auth";
  private static final String ACCESS_COOKIE_PATH = "/";

  public void writeAccess(HttpServletResponse response, String jwt, long maxAgeSeconds) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(accessCookieName, jwt)
            .path(ACCESS_COOKIE_PATH)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .build()
            .toString());
  }

  public void writeRefresh(HttpServletResponse response, String rawRefresh, long maxAgeSeconds) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(refreshCookieName, rawRefresh)
            .path(REFRESH_COOKIE_PATH)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .build()
            .toString());
  }

  public void clearAccess(HttpServletResponse response) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(accessCookieName, "")
            .path(ACCESS_COOKIE_PATH)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .maxAge(Duration.ZERO)
            .build()
            .toString());
  }

  public void clearRefresh(HttpServletResponse response) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(refreshCookieName, "")
            .path(REFRESH_COOKIE_PATH)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .maxAge(Duration.ZERO)
            .build()
            .toString());
  }

  public void clearAllAuthCookies(HttpServletResponse response) {
    clearAccess(response);
    clearRefresh(response);
  }

  public String getAccessCookieName() {
    return accessCookieName;
  }

  public String getRefreshCookieName() {
    return refreshCookieName;
  }
}
