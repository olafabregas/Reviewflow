package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class StudentResponse {
    String userId;
    String email;
    String firstName;
    String lastName;
    Instant enrolledAt;
}
