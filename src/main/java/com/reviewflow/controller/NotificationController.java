package com.reviewflow.controller;

import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.NotificationDto;
import com.reviewflow.model.dto.response.NotificationResponse;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> list(
            @AuthenticationPrincipal ReviewFlowUserDetails user,
            @RequestParam(required = false) Boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        Page<NotificationResponse> page = notificationService
                .getNotifications(user.getUserId(), unreadOnly, pageable)
                .map(this::toResponse);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        long count = notificationService.getUnreadCount(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", count)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Map<String, String>>> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        notificationService.markAsRead(id, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Marked as read")));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markAllRead(
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        int updatedCount = notificationService.markAllAsRead(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "message", "All notifications marked as read",
                "updatedCount", updatedCount
        )));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        notificationService.deleteNotification(id, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Notification deleted")));
    }

    private NotificationResponse toResponse(NotificationDto n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .isRead(n.getIsRead())
                .actionUrl(n.getActionUrl())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
