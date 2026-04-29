package com.reviewflow.announcement.dto.response;

import java.time.Instant;

import com.reviewflow.shared.domain.Announcement;
import com.reviewflow.shared.domain.AnnouncementTarget;
import com.reviewflow.shared.domain.RecipientType;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AnnouncementResponse {

  String id;
  String courseId;
  String title;
  String body;
  AnnouncementTarget target;
  RecipientType recipientType;
  Boolean isDraft;
  Instant publishedAt;
  Instant createdAt;
  String createdByName;

  public static AnnouncementResponse from(Announcement announcement, String createdByName) {
    return AnnouncementResponse.builder()
        .id(announcement.getId().toString())
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
