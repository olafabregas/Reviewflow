package com.reviewflow.model.dto.response;

import com.reviewflow.model.entity.Notification;
import com.reviewflow.service.HashidService;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class NotificationDto {
    String  id;
    String  type;
    String  title;
    String  message;
    Boolean isRead;
    String  actionUrl;
    Instant createdAt;

    public static NotificationDto from(Notification n, HashidService hashidService) {
        return NotificationDto.builder()
                .id(hashidService.encode(n.getId()))
                .type(n.getType().name())
                .title(n.getTitle())
                .message(n.getMessage())
                .isRead(n.getIsRead())
                .actionUrl(buildActionUrl(n, hashidService))
                .createdAt(n.getCreatedAt())
                .build();
    }

    /**
     * Rewrites action URLs containing "{id}" placeholder with hashed ID.
     * Example: "/teams/{id}" + targetId=7 → "/teams/Xm2pNqR4"
     */
    private static String buildActionUrl(Notification n, HashidService h) {
        if (n.getActionUrl() == null) return null;
        if (n.getTargetId() == null) return n.getActionUrl();
        // Replace {id} placeholder with hashed ID
        return n.getActionUrl().replace("{id}", h.encode(n.getTargetId()));
    }
}
