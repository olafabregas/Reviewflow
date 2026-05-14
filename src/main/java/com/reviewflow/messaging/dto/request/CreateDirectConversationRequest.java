package com.reviewflow.messaging.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDirectConversationRequest {

  @NotBlank private String recipientId;
}
