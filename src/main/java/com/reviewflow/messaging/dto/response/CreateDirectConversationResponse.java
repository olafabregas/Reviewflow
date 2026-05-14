package com.reviewflow.messaging.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CreateDirectConversationResponse {
  String conversationId;
  String type;
  boolean alreadyExisted;
  List<ParticipantSummaryDto> participants;
}
