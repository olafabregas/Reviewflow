package com.reviewflow.model.dto.response;

import com.reviewflow.model.entity.Notification;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class NotificationDto {
    Long    id;
    String  type;
    String  title;
    String  message;
    Boolean isRead;
    String  actionUrl;
    Instant createdAt;

    public static NotificationDto from(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .type(n.getType().name())
                .title(n.getTitle())
                .message(n.getMessage())
                .isRead(n.getIsRead())
                .actionUrl(n.getActionUrl())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
