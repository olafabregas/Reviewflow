package com.reviewflow.event;

import com.reviewflow.model.entity.Announcement;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/**
 * AnnouncementPublishedEvent — fired when an announcement is published. Consumed by
 * NotificationEventListener to create notifications. Consumed by email framework to dispatch
 * AnnouncementPostedEmailEvent.
 */
@Value
@Builder
public class AnnouncementPublishedEvent {

  Long announcementId;
  Long courseId; // null for platform announcements
  String title;
  String body;
  String createdByName;
  String target; // COURSE or PLATFORM
  String recipientType; // null for COURSE, set for PLATFORM
  Instant publishedAt;

  public static AnnouncementPublishedEvent from(Announcement announcement, String createdByName) {
    return AnnouncementPublishedEvent.builder()
        .announcementId(announcement.getId())
        .courseId(announcement.getCourse() != null ? announcement.getCourse().getId() : null)
        .title(announcement.getTitle())
        .body(announcement.getBody())
        .createdByName(createdByName)
        .target(announcement.getTarget().toString())
        .recipientType(
            announcement.getRecipientType() != null
                ? announcement.getRecipientType().toString()
                : null)
        .publishedAt(announcement.getPublishedAt())
        .build();
  }
}
