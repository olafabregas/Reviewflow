package com.reviewflow.system.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.messaging.dto.response.ModerationConversationSummaryDto;
import com.reviewflow.messaging.dto.response.MessageDto;
import com.reviewflow.shared.domain.ConversationType;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.system.service.SystemService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class SystemControllerMessagingTest {

  @Mock private SystemService systemService;
  @Mock private HashidService hashidService;
  @Mock private HttpServletRequest request;

  private SystemController controller() {
    return new SystemController(systemService, hashidService);
  }

  private UsernamePasswordAuthenticationToken adminAuth() {
    User admin =
        User.builder()
            .id(1L)
            .email("admin@test.local")
            .passwordHash("x")
            .role(UserRole.SYSTEM_ADMIN)
            .isActive(true)
            .build();
    return new UsernamePasswordAuthenticationToken(new ReviewFlowUserDetails(admin), null);
  }

  @Test
  void moderationListCourseConversations_delegatesToSystemService() {
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    when(hashidService.decodeOrThrow("CRS1")).thenReturn(10L);
    when(systemService.moderationListCourseConversations(eq(10L), eq(1L), eq("127.0.0.1")))
        .thenReturn(
            Map.of(
                "conversations",
                List.of(
                    ModerationConversationSummaryDto.builder()
                        .id("cnv1")
                        .type(ConversationType.DIRECT)
                        .messageCount(2)
                        .lastActivity(Instant.now())
                        .participants(List.of())
                        .build())));

    ResponseEntity<com.reviewflow.shared.exception.ApiResponse<Map<String, Object>>> response =
        controller().moderationListCourseConversations("CRS1", adminAuth(), request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, ((List<?>) response.getBody().getData().get("conversations")).size());
  }

  @Test
  void moderationListConversationMessages_delegatesToSystemService() {
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    when(hashidService.decodeOrThrow("CNV1")).thenReturn(55L);
    var messagePage =
        new org.springframework.data.domain.PageImpl<>(
            List.of(MessageDto.builder().id("m1").build()));
    when(systemService.moderationListConversationMessages(
            eq(55L), eq(1L), eq("127.0.0.1"), eq(0), eq(50)))
        .thenReturn(messagePage);

    ResponseEntity<com.reviewflow.shared.exception.ApiResponse<Map<String, Object>>> response =
        controller().moderationListConversationMessages("CNV1", 0, 50, adminAuth(), request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, ((List<?>) response.getBody().getData().get("messages")).size());
  }
}
