package com.reviewflow.messaging.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SendMessageResponse {
  String messageId;
  String content;
  List<MessageAttachmentDto> attachments;
  Instant sentAt;
  Instant editedAt;
}
