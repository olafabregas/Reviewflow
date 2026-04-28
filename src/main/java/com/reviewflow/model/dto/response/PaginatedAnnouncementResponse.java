package com.reviewflow.model.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PaginatedAnnouncementResponse — paginated list response for GET /courses/{id}/announcements.
 * Wraps announcement list with pagination metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedAnnouncementResponse {

  List<AnnouncementListItem> content;
  Integer page;
  Integer size;
  Long totalElements;

  /**
   * AnnouncementListItem — lightweight announcement DTO for list views. Omits body for initial list
   * view (client can fetch individual for details).
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AnnouncementListItem {

    String id; // Hashid
    String title;
    String body;
    Instant publishedAt;
    String createdByName;
  }
}
