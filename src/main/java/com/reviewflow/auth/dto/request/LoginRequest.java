package com.reviewflow.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

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

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
