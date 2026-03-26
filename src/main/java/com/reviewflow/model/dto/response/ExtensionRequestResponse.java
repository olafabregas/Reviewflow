package com.reviewflow.model.dto.response;

import com.reviewflow.model.enums.ExtensionRequestStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

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
