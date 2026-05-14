package com.reviewflow.messaging.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MessageAttachmentDto {
  String id;
  String fileName;
  long fileSizeBytes;
  String contentType;
  String downloadUrl;
}
