package com.reviewflow.messaging.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EditMessageResponse {
  String messageId;
  String content;
  Instant editedAt;
  List<MessageAttachmentDto> attachments;
}
