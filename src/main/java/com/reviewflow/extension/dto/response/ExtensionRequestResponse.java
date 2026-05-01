package com.reviewflow.extension.dto.response;

import java.time.Instant;

import com.reviewflow.shared.domain.ExtensionRequestStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExtensionRequestResponse {

  String id;
  String assignmentId;
  String teamId;
  String studentId;
  String requestedById;
  String respondedById;
  ExtensionRequestStatus status;
  String reason;
  Instant requestedDueAt;
  String instructorNote;
  Instant respondedAt;
  Instant createdAt;
}
