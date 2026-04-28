package com.reviewflow.model.dto.request;

import com.reviewflow.model.enums.RecipientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CreateAnnouncementRequest — request body for POST /courses/{id}/announcements and POST
 * /admin/announcements. The endpoint handler determines if COURSE or PLATFORM based on URL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAnnouncementRequest {

  @NotBlank(message = "title is required")
  @Size(min = 1, max = 255, message = "title must be between 1 and 255 characters")
  private String title;

  @NotBlank(message = "body is required")
  private String body;

  /**
   * recipientType — only used for platform announcements. For course announcements, this is
   * ignored/null.
   */
  private RecipientType recipientType;
}
