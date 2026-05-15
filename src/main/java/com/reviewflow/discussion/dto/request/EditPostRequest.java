package com.reviewflow.discussion.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EditPostRequest {
  @NotBlank private String content;
}
