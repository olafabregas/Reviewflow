package com.reviewflow.model.dto.response;

import com.reviewflow.model.entity.UserRole;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthUserResponse {

    String userId;
    String firstName;
    String lastName;
    String email;
    UserRole role;
}
