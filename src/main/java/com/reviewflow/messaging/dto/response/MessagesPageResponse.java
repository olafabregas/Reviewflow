package com.reviewflow.messaging.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MessagesPageResponse {
  List<MessageDto> messages;
  boolean hasMore;
  String oldestMessageId;
}
