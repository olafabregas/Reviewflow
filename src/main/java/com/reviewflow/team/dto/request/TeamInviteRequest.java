package com.reviewflow.team.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TeamInviteRequest {
  @Schema(
      description = "Email address of the team member to invite",
      example = "student@university.edu",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "inviteeEmail is required")
  @Email(message = "inviteeEmail must be a valid email")
  private String inviteeEmail;
}
