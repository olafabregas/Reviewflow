package com.reviewflow.shared.domain;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationParticipantKey implements Serializable {

  private Long conversationId;
  private Long userId;
}
