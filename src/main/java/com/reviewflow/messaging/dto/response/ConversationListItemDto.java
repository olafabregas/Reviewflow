package com.reviewflow.messaging.dto.response;

import com.reviewflow.shared.domain.ConversationType;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConversationListItemDto {
  String id;
  ConversationType type;
  String teamName;
  String courseCode;
  List<ParticipantSummaryDto> participants;
  LastMessagePreviewDto lastMessage;
  long unreadCount;
}
