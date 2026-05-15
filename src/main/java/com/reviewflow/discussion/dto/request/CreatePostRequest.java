package com.reviewflow.discussion.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreatePostRequest {

  @NotBlank private String content;

  /** Hashed parent post id for replies; null for initial post. */
  private String parentPostId;
}
