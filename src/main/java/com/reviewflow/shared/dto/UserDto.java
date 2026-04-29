package com.reviewflow.shared.dto;

import com.reviewflow.shared.domain.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

  private String id;
  private String email;
  private String firstName;
  private String lastName;
  private String avatarUrl;
  private UserRole role;
  private Boolean isActive;
}
