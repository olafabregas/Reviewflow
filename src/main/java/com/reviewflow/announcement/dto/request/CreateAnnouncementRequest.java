package com.reviewflow.announcement.dto.request;

import com.reviewflow.shared.domain.RecipientType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

  private RecipientType recipientType;
}
