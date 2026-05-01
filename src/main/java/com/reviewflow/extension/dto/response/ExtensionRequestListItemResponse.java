package com.reviewflow.extension.dto.response;

import java.time.Instant;

import com.reviewflow.shared.domain.ExtensionRequestStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExtensionRequestListItemResponse {

  String id;
  String studentName;
  String teamName;
  String reason;
  Instant requestedDueAt;
  ExtensionRequestStatus status;
  String instructorNote;
  Instant respondedAt;
  Instant createdAt;
}
