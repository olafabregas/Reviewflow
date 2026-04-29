package com.reviewflow.user.dto.response;

import com.reviewflow.shared.domain.UserRole;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthUserResponse {

  String userId;
  String firstName;
  String lastName;
  String email;
  String avatarUrl;
  Boolean emailNotificationsEnabled;
  Boolean isActive;
  UserRole role;
}
