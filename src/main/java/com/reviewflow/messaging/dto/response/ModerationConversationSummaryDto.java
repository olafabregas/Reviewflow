package com.reviewflow.messaging.dto.response;

import com.reviewflow.shared.domain.ConversationType;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ModerationConversationSummaryDto {
  String id;
  ConversationType type;
  List<ParticipantSummaryDto> participants;
  long messageCount;
  Instant lastActivity;
}
