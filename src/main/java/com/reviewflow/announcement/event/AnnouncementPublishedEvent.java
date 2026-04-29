package com.reviewflow.announcement.event;

import java.time.Instant;

import com.reviewflow.shared.domain.Announcement;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AnnouncementPublishedEvent {

  Long announcementId;
  Long courseId;
  String title;
  String body;
  String createdByName;
  String target;
  String recipientType;
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
