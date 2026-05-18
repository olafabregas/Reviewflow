package com.reviewflow.system.service;

import com.reviewflow.admin.service.AuditService;
import com.reviewflow.messaging.dto.response.MessageDto;
import com.reviewflow.messaging.dto.response.ModerationConversationSummaryDto;
import com.reviewflow.messaging.service.MessagingService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PRD-18: SYSTEM_ADMIN messaging moderation (delegates to {@link MessagingService}). */
@Service
@RequiredArgsConstructor
public class SystemMessagingService {

  private final MessagingService messagingService;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<ModerationConversationSummaryDto> getCourseConversations(Long courseId, Long actorId) {
    return messagingService.listConversationsForModeration(courseId);
  }

  @Transactional(readOnly = true)
  public List<MessageDto> getConversationMessages(Long conversationId, Long actorId) {
    return messagingService.listMessagesForModeration(conversationId);
  }

  public Map<String, Object> getCourseConversationsForApi(Long courseId, Long actorId, String ip) {
    List<ModerationConversationSummaryDto> list = getCourseConversations(courseId, actorId);
    auditService.log(
        actorId,
        "CONVERSATION_LIST_VIEWED",
        "COURSE",
        courseId,
        Map.of("courseId", courseId),
        ip);
    return Map.of("conversations", list);
  }

  public Map<String, Object> getConversationMessagesForApi(
      Long conversationId, Long actorId, String ip) {
    List<MessageDto> messages = getConversationMessages(conversationId, actorId);
    auditService.log(
        actorId,
        "CONVERSATION_VIEWED",
        "CONVERSATION",
        conversationId,
        Map.of("conversationId", conversationId),
        ip);
    return Map.of("messages", messages);
  }
}
