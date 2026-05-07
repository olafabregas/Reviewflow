package com.reviewflow.auth.controller;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reviewflow.auth.dto.request.PasswordResetConfirmRequest;
import com.reviewflow.auth.dto.request.PasswordResetRequest;
import com.reviewflow.auth.service.PasswordResetService;
import com.reviewflow.shared.exception.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerTest {

  @Mock private PasswordResetService passwordResetService;
  @Mock private HttpServletRequest request;

  @InjectMocks private PasswordResetController passwordResetController;

  @Test
  void requestUsesForwardedIpAndReturnsGenericMessage() {
    PasswordResetRequest body = new PasswordResetRequest("user@example.com");
    when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.7, 10.0.0.1");

    var result = passwordResetController.request(body, request);

    assertThat(result.getBody()).isNotNull();
    ApiResponse<Map<String, String>> payload = result.getBody();
    assertThat(payload.isSuccess()).isTrue();
    assertThat(payload.getData())
        .containsEntry("message", "If that email exists, a reset link has been sent.");

    verify(passwordResetService).requestReset("user@example.com", "198.51.100.7");
  }

  @Test
  void confirmFallsBackToRemoteAddressAndDelegatesResetToken() {
    PasswordResetConfirmRequest body = new PasswordResetConfirmRequest("reset-token", "new-password");
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("203.0.113.9");

    var result = passwordResetController.confirm(body, request);

    assertThat(result.getBody()).isNotNull();
    ApiResponse<Map<String, String>> payload = result.getBody();
    assertThat(payload.isSuccess()).isTrue();
    assertThat(payload.getData()).containsEntry("message", "Password updated. Please log in.");

    verify(passwordResetService).confirm("reset-token", "new-password", "203.0.113.9");
  }
}