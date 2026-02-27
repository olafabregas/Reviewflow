package com.reviewflow.controller;

import com.reviewflow.model.entity.Notification;
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
    public ResponseEntity<Page<Notification>> list(
            @AuthenticationPrincipal ReviewFlowUserDetails user,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<Notification> page = notificationService.getNotifications(user.getUserId(), pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal ReviewFlowUserDetails user) {
        long count = notificationService.getUnreadCount(user.getUserId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Map<String, String>> markRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(Map.of("message", "Marked as read"));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllRead(@AuthenticationPrincipal ReviewFlowUserDetails user) {
        notificationService.markAllAsRead(user.getUserId());
        return ResponseEntity.ok(Map.of("message", "All marked as read"));
    }
}
