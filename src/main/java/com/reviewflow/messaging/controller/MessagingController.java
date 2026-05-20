package com.reviewflow.messaging.controller;

import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.messaging.dto.request.CreateDirectConversationRequest;
import com.reviewflow.messaging.dto.request.EditMessageRequest;
import com.reviewflow.messaging.dto.response.CreateDirectConversationResponse;
import com.reviewflow.messaging.dto.response.EditMessageResponse;
import com.reviewflow.messaging.dto.response.MessagesPageResponse;
import com.reviewflow.messaging.dto.response.SendMessageResponse;
import com.reviewflow.messaging.service.MessagingService;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.exception.ApiResponse;
import com.reviewflow.shared.util.HashidService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Messaging", description = "PRD-18 course messaging")
public class MessagingController {

  private final MessagingService messagingService;
  private final HashidService hashidService;

  @PostMapping("/courses/{courseId}/conversations")
  @PreAuthorize("hasAnyRole('STUDENT','INSTRUCTOR')")
  public ResponseEntity<ApiResponse<CreateDirectConversationResponse>> createDirect(
      @PathVariable String courseId,
      @Valid @RequestBody CreateDirectConversationRequest body,
      @AuthenticationPrincipal ReviewFlowUserDetails user,
      HttpServletRequest request) {
    Long courseLong = hashidService.decodeOrThrow(courseId);
    Long recipientId = hashidService.decodeOrThrow(body.getRecipientId());
    CreateDirectConversationResponse data =
        messagingService.createDirectConversation(
            courseLong, user.getUserId(), recipientId, clientIp(request));
    HttpStatus status = data.isAlreadyExisted() ? HttpStatus.OK : HttpStatus.CREATED;
    return ResponseEntity.status(status).body(ApiResponse.ok(data));
  }

  @PostMapping(
      value = "/conversations/{conversationId}/messages",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('STUDENT','INSTRUCTOR')")
  public ResponseEntity<ApiResponse<SendMessageResponse>> sendMessage(
      @PathVariable String conversationId,
      @RequestParam(value = "content", required = false) String content,
      @RequestParam(value = "files", required = false) List<MultipartFile> files,
      @AuthenticationPrincipal ReviewFlowUserDetails user,
      HttpServletRequest request)
      throws IOException {
    Long convId = hashidService.decodeOrThrow(conversationId);
    SendMessageResponse data =
        messagingService.sendMessage(convId, user.getUserId(), content, files, clientIp(request));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
  }

  @PatchMapping("/messages/{messageId}")
  @PreAuthorize("hasAnyRole('STUDENT','INSTRUCTOR')")
  public ResponseEntity<ApiResponse<EditMessageResponse>> editMessage(
      @PathVariable String messageId,
      @Valid @RequestBody EditMessageRequest body,
      @AuthenticationPrincipal ReviewFlowUserDetails user,
      HttpServletRequest request) {
    Long id = hashidService.decodeOrThrow(messageId);
    EditMessageResponse data =
        messagingService.editMessage(id, user.getUserId(), body.getContent(), clientIp(request));
    return ResponseEntity.ok(ApiResponse.ok(data));
  }

  @GetMapping("/conversations/{conversationId}/messages")
  @PreAuthorize("hasAnyRole('STUDENT','INSTRUCTOR')")
  public ResponseEntity<ApiResponse<MessagesPageResponse>> listMessages(
      @PathVariable String conversationId,
      @RequestParam(value = "before", required = false) String before,
      @RequestParam(value = "limit", required = false) Integer limit,
      @AuthenticationPrincipal ReviewFlowUserDetails user) {
    Long convId = hashidService.decodeOrThrow(conversationId);
    Long beforeId = before != null && !before.isBlank() ? hashidService.decodeOrThrow(before) : null;
    MessagesPageResponse data =
        messagingService.getMessagesForParticipant(convId, user.getUserId(), beforeId, limit);
    return ResponseEntity.ok(ApiResponse.ok(data));
  }

  @GetMapping("/courses/{courseId}/conversations")
  @PreAuthorize("hasAnyRole('STUDENT','INSTRUCTOR')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> listCourseConversations(
      @PathVariable String courseId, @AuthenticationPrincipal ReviewFlowUserDetails user) {
    Long courseLong = hashidService.decodeOrThrow(courseId);
    var list = messagingService.listCourseConversations(courseLong, user.getUserId());
    long totalUnread = list.stream().mapToLong(c -> c.getUnreadCount()).sum();
    Map<String, Object> payload = new HashMap<>();
    payload.put("conversations", list);
    payload.put("totalUnread", totalUnread);
    return ResponseEntity.ok(ApiResponse.ok(payload));
  }

  @PatchMapping("/conversations/{conversationId}/read")
  @PreAuthorize("hasAnyRole('STUDENT','INSTRUCTOR')")
  public ResponseEntity<ApiResponse<Map<String, Long>>> markRead(
      @PathVariable String conversationId, @AuthenticationPrincipal ReviewFlowUserDetails user) {
    Long convId = hashidService.decodeOrThrow(conversationId);
    long unread = messagingService.markConversationRead(convId, user.getUserId());
    return ResponseEntity.ok(ApiResponse.ok(Map.of("unreadCount", unread)));
  }

  @DeleteMapping("/messages/{messageId}")
  @PreAuthorize("hasAnyRole('STUDENT','INSTRUCTOR','SYSTEM_ADMIN')")
  public ResponseEntity<ApiResponse<Void>> deleteMessage(
      @PathVariable String messageId,
      @AuthenticationPrincipal ReviewFlowUserDetails user,
      HttpServletRequest request) {
    Long id = hashidService.decodeOrThrow(messageId);
    boolean systemAdmin = user.getRole() == UserRole.SYSTEM_ADMIN;
    messagingService.deleteMessage(id, user.getUserId(), systemAdmin, clientIp(request));
    return ResponseEntity.ok(ApiResponse.ok(null));
  }

  @GetMapping("/conversations/unread-count")
  @PreAuthorize("hasAnyRole('STUDENT','INSTRUCTOR')")
  public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(
      @AuthenticationPrincipal ReviewFlowUserDetails user) {
    long total = messagingService.sumUnreadForUserAcrossConversations(user.getUserId());
    return ResponseEntity.ok(ApiResponse.ok(Map.of("totalUnread", total)));
  }

  private static String clientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
