package com.reviewflow.messaging.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MessageDto {
  String id;
  String senderId;
  String senderName;
  String senderAvatarUrl;
  String content;
  List<MessageAttachmentDto> attachments;
  boolean isDeleted;
  Instant sentAt;
  Instant editedAt;
}
