package com.reviewflow.auth.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reviewflow.auth.dto.request.PasswordResetConfirmRequest;
import com.reviewflow.auth.dto.request.PasswordResetRequest;
import com.reviewflow.auth.service.PasswordResetService;
import com.reviewflow.shared.exception.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

  private final PasswordResetService passwordResetService;

  @PostMapping("/request")
  public ResponseEntity<ApiResponse<Map<String, String>>> request(
      @Valid @RequestBody PasswordResetRequest body, HttpServletRequest request) {
    String ip = clientIp(request);
    passwordResetService.requestReset(body.getEmail(), ip);
    return ResponseEntity.ok(
        ApiResponse.ok(
            Map.of("message", "If that email exists, a reset link has been sent.")));
  }

  @PostMapping("/confirm")
  public ResponseEntity<ApiResponse<Map<String, String>>> confirm(
      @Valid @RequestBody PasswordResetConfirmRequest body, HttpServletRequest request) {
    String ip = clientIp(request);
    passwordResetService.confirm(body.getResetToken(), body.getNewPassword(), ip);
    return ResponseEntity.ok(
        ApiResponse.ok(Map.of("message", "Password updated. Please log in.")));
  }

  private static String clientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isEmpty()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
