package com.reviewflow.discussion.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PinPostRequest {
  @NotNull private Boolean pinned;
}
