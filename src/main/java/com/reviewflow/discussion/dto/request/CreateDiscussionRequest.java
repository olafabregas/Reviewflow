package com.reviewflow.discussion.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Data;

@Data
public class CreateDiscussionRequest {

  @NotBlank @Size(max = 255) private String title;

  @NotBlank private String prompt;

  @NotNull private Instant dueAt;

  private boolean requirePostBeforeReading = true;

  private boolean allowAnonymous;

  private boolean isGraded;

  /** Hashed assignment id when {@code isGraded} is true. */
  private String assignmentId;
}
