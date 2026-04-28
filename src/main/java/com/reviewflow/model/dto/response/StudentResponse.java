package com.reviewflow.model.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StudentResponse {
  String userId;
  String email;
  String firstName;
  String lastName;
  Instant enrolledAt;
}
