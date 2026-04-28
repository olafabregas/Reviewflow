package com.reviewflow.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

  @Schema(
      description = "User email address used for authentication",
      example = "student@university.edu",
      requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "Email is required")
  @Email
  private String email;

  @Schema(
      description = "User password for account access",
      example = "SecurePassword123!",
      requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "Password is required")
  private String password;
}
