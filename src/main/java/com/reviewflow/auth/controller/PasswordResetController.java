package com.reviewflow.auth.controller;

import com.reviewflow.auth.dto.request.PasswordResetConfirmDto;
import com.reviewflow.auth.dto.request.PasswordResetRequestDto;
import com.reviewflow.auth.service.PasswordResetService;
import com.reviewflow.shared.exception.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

  private final PasswordResetService passwordResetService;

  @PostMapping("/request")
  public ResponseEntity<ApiResponse<Map<String, String>>> request(
      @Valid @RequestBody PasswordResetRequestDto body, HttpServletRequest request) {
    String ip = clientIp(request);
    passwordResetService.requestReset(body.getEmail(), ip);
    return ResponseEntity.ok(
        ApiResponse.ok(
            Map.of("message", "If that email exists, a reset link has been sent.")));
  }

  @PostMapping("/confirm")
  public ResponseEntity<ApiResponse<Map<String, String>>> confirm(
      @Valid @RequestBody PasswordResetConfirmDto body, HttpServletRequest request) {
    String ip = clientIp(request);
    passwordResetService.confirm(body.getToken(), body.getNewPassword(), ip);
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
