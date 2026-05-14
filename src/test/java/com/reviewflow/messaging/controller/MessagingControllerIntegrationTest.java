package com.reviewflow.messaging.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.messaging.dto.request.CreateDirectConversationRequest;
import com.reviewflow.messaging.dto.request.EditMessageRequest;
import com.reviewflow.messaging.dto.response.CreateDirectConversationResponse;
import com.reviewflow.messaging.dto.response.EditMessageResponse;
import com.reviewflow.messaging.dto.response.MessagesPageResponse;
import com.reviewflow.messaging.dto.response.SendMessageResponse;
import com.reviewflow.messaging.service.MessagingService;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.util.HashidService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class MessagingControllerIntegrationTest {

  @Mock private MessagingService messagingService;
  @Mock private HashidService hashidService;
  @Mock private HttpServletRequest request;

  private MessagingController controller() {
    return new MessagingController(messagingService, hashidService);
  }

  private ReviewFlowUserDetails studentPrincipal() {
    User user =
        User.builder()
            .id(9L)
            .email("student@test.local")
            .passwordHash("x")
            .role(UserRole.STUDENT)
            .isActive(true)
            .build();
    return new ReviewFlowUserDetails(user);
  }

  @Test
  void createDirect_newConversation_returnsCreated() {
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    when(hashidService.decodeOrThrow("CRS1")).thenReturn(101L);
    when(hashidService.decodeOrThrow("REC1")).thenReturn(202L);
    CreateDirectConversationResponse data =
        CreateDirectConversationResponse.builder()
            .conversationId("cnv1")
            .type("DIRECT")
            .alreadyExisted(false)
            .participants(List.of())
            .build();
    when(messagingService.createDirectConversation(101L, 9L, 202L, "127.0.0.1"))
        .thenReturn(data);
    CreateDirectConversationRequest body = new CreateDirectConversationRequest();
    body.setRecipientId("REC1");

    ResponseEntity<?> response =
        controller().createDirect("CRS1", body, studentPrincipal(), request);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
  }

  @Test
  void createDirect_existingConversation_returnsOk() {
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    when(hashidService.decodeOrThrow("CRS1")).thenReturn(101L);
    when(hashidService.decodeOrThrow("REC1")).thenReturn(202L);
    CreateDirectConversationResponse data =
        CreateDirectConversationResponse.builder()
            .conversationId("cnv1")
            .type("DIRECT")
            .alreadyExisted(true)
            .participants(List.of())
            .build();
    when(messagingService.createDirectConversation(101L, 9L, 202L, "127.0.0.1"))
        .thenReturn(data);
    CreateDirectConversationRequest body = new CreateDirectConversationRequest();
    body.setRecipientId("REC1");

    ResponseEntity<?> response =
        controller().createDirect("CRS1", body, studentPrincipal(), request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  @Test
  void listCourseConversations_wrapsTotalUnread() {
    when(hashidService.decodeOrThrow("CRS1")).thenReturn(101L);
    when(messagingService.listCourseConversations(101L, 9L)).thenReturn(List.of());

    var response = controller().listCourseConversations("CRS1", studentPrincipal());

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(0L, ((Map<?, ?>) response.getBody().getData()).get("totalUnread"));
  }

  @Test
  void markRead_returnsUnreadCountFromService() {
    when(hashidService.decodeOrThrow("CNV1")).thenReturn(55L);
    when(messagingService.markConversationRead(55L, 9L)).thenReturn(3L);

    var response = controller().markRead("CNV1", studentPrincipal());

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(3L, response.getBody().getData().get("unreadCount"));
  }

  @Test
  void sendMessage_delegatesWithDecodedIds() throws IOException {
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("10.0.0.1");
    when(hashidService.decodeOrThrow("CNV1")).thenReturn(55L);
    SendMessageResponse svc =
        SendMessageResponse.builder()
            .messageId("m1")
            .content("hi")
            .attachments(List.of())
            .sentAt(Instant.now())
            .editedAt(null)
            .build();
    when(messagingService.sendMessage(55L, 9L, "hi", null, "10.0.0.1")).thenReturn(svc);

    var response =
        controller().sendMessage("CNV1", "hi", null, studentPrincipal(), request);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals("m1", response.getBody().getData().getMessageId());
  }

  @Test
  void editMessage_delegates() {
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    when(hashidService.decodeOrThrow("MSG1")).thenReturn(777L);
    EditMessageResponse svc =
        EditMessageResponse.builder()
            .messageId("MSG1")
            .content("fixed")
            .editedAt(Instant.now())
            .attachments(List.of())
            .build();
    when(messagingService.editMessage(777L, 9L, "fixed", "127.0.0.1")).thenReturn(svc);
    EditMessageRequest body = new EditMessageRequest();
    body.setContent("fixed");

    var response = controller().editMessage("MSG1", body, studentPrincipal(), request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("fixed", response.getBody().getData().getContent());
  }

  @Test
  void listMessages_passesBeforeCursor() {
    when(hashidService.decodeOrThrow("CNV1")).thenReturn(55L);
    when(hashidService.decodeOrThrow("OLD")).thenReturn(400L);
    MessagesPageResponse page =
        MessagesPageResponse.builder().messages(List.of()).hasMore(false).oldestMessageId(null).build();
    when(messagingService.getMessagesForParticipant(55L, 9L, 400L, null)).thenReturn(page);

    var response = controller().listMessages("CNV1", "OLD", null, studentPrincipal());

    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  @Test
  void listMessages_passesLimitToService() {
    when(hashidService.decodeOrThrow("CNV1")).thenReturn(55L);
    MessagesPageResponse page =
        MessagesPageResponse.builder().messages(List.of()).hasMore(false).oldestMessageId(null).build();
    when(messagingService.getMessagesForParticipant(55L, 9L, null, 20)).thenReturn(page);

    var response = controller().listMessages("CNV1", null, 20, studentPrincipal());

    assertEquals(HttpStatus.OK, response.getStatusCode());
  }
}
