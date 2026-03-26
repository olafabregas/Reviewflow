package com.reviewflow.model.dto.response;

import com.reviewflow.model.enums.ExtensionRequestStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

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
