package com.reviewflow.notification.dto.response;

import java.time.Instant;

import com.reviewflow.shared.domain.Notification;
import com.reviewflow.shared.util.HashidService;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NotificationDto {
  String id;
  String type;
  String title;
  String message;
  Boolean isRead;
  String actionUrl;
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
    return n.getActionUrl().replace("{id}", h.encode(n.getTargetId()));
  }
}
