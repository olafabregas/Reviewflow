package com.reviewflow.announcement.dto.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedAnnouncementResponse {

  List<AnnouncementListItem> content;
  Integer page;
  Integer size;
  Long totalElements;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AnnouncementListItem {

    String id;
    String title;
    String body;
    Instant publishedAt;
    String createdByName;
  }
}
