package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PreviewResponseDto {

  String previewUrl;
  String contentType;
  Long expiresInSeconds;
  String filename;
}
