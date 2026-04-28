package com.reviewflow.model.dto.response;

import com.reviewflow.model.entity.UserRole;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserDetailResponse {

  private String id;
  private String email;
  private String firstName;
  private String lastName;
  private String avatarUrl;
  private Boolean emailNotificationsEnabled;
  private UserRole role;
  private Boolean isActive;
  private Instant createdAt;
  private Long courseCount;
  private Long teamCount;
}
