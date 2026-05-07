package com.reviewflow.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordResetConfirmDto {

  @NotBlank private String token;

  @NotBlank private String newPassword;
}
