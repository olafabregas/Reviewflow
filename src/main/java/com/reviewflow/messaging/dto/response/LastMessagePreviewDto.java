package com.reviewflow.messaging.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LastMessagePreviewDto {
  String content;
  String senderName;
  Instant sentAt;
  boolean hasAttachments;
}
