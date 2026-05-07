package com.reviewflow.auth.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WsTicketResponse {
  String ticket;
  int expiresInSeconds;
}
