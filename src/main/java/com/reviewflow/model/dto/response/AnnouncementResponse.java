package com.reviewflow.model.dto.response;

import com.reviewflow.model.enums.AnnouncementTarget;
import com.reviewflow.model.enums.RecipientType;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/**
 * AnnouncementResponse — immutable DTO for announcement endpoints. Used by create, publish, list
 * endpoints.
 */
@Value
@Builder
public class AnnouncementResponse {

  String id; // Hashid-encoded
  String courseId; // Hashid-encoded, nullable for PLATFORM
  String title;
  String body;
  AnnouncementTarget target;
  RecipientType recipientType; // nullable for COURSE
  Boolean isDraft; // inverse of isPublished
  Instant publishedAt; // null if draft
  Instant createdAt;
  String createdByName; // display name of creator

  /**
   * Factory method to create AnnouncementResponse from entity. Handles hashid encoding and
   * null-safety.
   */
  public static AnnouncementResponse from(
      com.reviewflow.model.entity.Announcement announcement, String createdByName) {
    // IDs remain raw here because Hashid conversion is applied in the service mapping layer.
    return AnnouncementResponse.builder()
        .id(announcement.getId().toString()) // Will be encoded in service
        .courseId(
            announcement.getCourse() != null ? announcement.getCourse().getId().toString() : null)
        .title(announcement.getTitle())
        .body(announcement.getBody())
        .target(announcement.getTarget())
        .recipientType(announcement.getRecipientType())
        .isDraft(!announcement.getIsPublished())
        .publishedAt(announcement.getPublishedAt())
        .createdAt(announcement.getCreatedAt())
        .createdByName(createdByName)
        .build();
  }
}
