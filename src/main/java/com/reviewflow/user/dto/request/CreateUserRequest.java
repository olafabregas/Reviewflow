package com.reviewflow.user.dto.request;

import com.reviewflow.shared.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserRequest {

  @Schema(
      description = "User email address for account creation",
      example = "student@university.edu",
      requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
  @NotBlank
  @Email
  private String email;

  @Schema(
      description = "Initial password for the new user account",
      example = "SecurePassword123!",
      requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
  @NotBlank
  private String password;

  @Schema(
      description = "User's first name",
      example = "John",
      requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED)
  private String firstName;

  @Schema(
      description = "User's last name",
      example = "Doe",
      requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED)
  private String lastName;

  @Schema(
      description = "User's role in the system",
      example = "STUDENT",
      requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED)
  private UserRole role;
}
