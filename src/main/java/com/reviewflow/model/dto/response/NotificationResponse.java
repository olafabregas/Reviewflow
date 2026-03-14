package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class NotificationResponse {
    String id;
    String type;
    String title;
    String message;
    Boolean isRead;
    String actionUrl;
    Instant createdAt;
}
