package com.reviewflow.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reviewflow.admin.service.AuditService;
import com.reviewflow.messaging.dto.response.ModerationConversationSummaryDto;
import com.reviewflow.messaging.dto.response.MessageDto;
import com.reviewflow.messaging.service.MessagingService;
import com.reviewflow.shared.domain.ConversationType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemMessagingServiceTest {

  @Mock private MessagingService messagingService;
  @Mock private AuditService auditService;

  @InjectMocks private SystemMessagingService systemMessagingService;

  @Test
  void getCourseConversationsForApi_auditsAndReturnsPayload() {
    List<ModerationConversationSummaryDto> list =
        List.of(
            ModerationConversationSummaryDto.builder()
                .id("c1")
                .type(ConversationType.DIRECT)
                .messageCount(1)
                .lastActivity(Instant.now())
                .participants(List.of())
                .build());
    when(messagingService.listConversationsForModeration(9L)).thenReturn(list);

    Map<String, Object> payload =
        systemMessagingService.getCourseConversationsForApi(9L, 1L, "10.0.0.1");

    assertEquals(list, payload.get("conversations"));
    verify(auditService)
        .log(eq(1L), eq("CONVERSATION_LIST_VIEWED"), eq("COURSE"), eq(9L), eq(Map.of("courseId", 9L)), eq("10.0.0.1"));
  }

  @Test
  void getConversationMessagesForApi_auditsAndReturnsPayload() {
    List<MessageDto> messages = List.of(MessageDto.builder().id("m1").build());
    when(messagingService.listMessagesForModeration(55L)).thenReturn(messages);

    Map<String, Object> payload =
        systemMessagingService.getConversationMessagesForApi(55L, 1L, "10.0.0.1");

    assertEquals(messages, payload.get("messages"));
    verify(auditService)
        .log(
            eq(1L),
            eq("CONVERSATION_VIEWED"),
            eq("CONVERSATION"),
            eq(55L),
            eq(Map.of("conversationId", 55L)),
            eq("10.0.0.1"));
  }
}
