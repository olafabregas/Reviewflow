package com.reviewflow.user.dto.request;

import com.reviewflow.shared.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class UpdateUserRequest {

  @Schema(description = "Updated first name of the user", example = "John")
  private String firstName;

  @Schema(description = "Updated last name of the user", example = "Doe")
  private String lastName;

  @Schema(description = "Updated role for the user", example = "INSTRUCTOR")
  private UserRole role;
}
