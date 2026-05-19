package com.reviewflow.grading.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JobErrorDownloadResponse {
  String downloadUrl;
}
