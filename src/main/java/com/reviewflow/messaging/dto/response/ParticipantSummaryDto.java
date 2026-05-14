package com.reviewflow.messaging.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ParticipantSummaryDto {
  String id;
  String name;
  String avatarUrl;
}
