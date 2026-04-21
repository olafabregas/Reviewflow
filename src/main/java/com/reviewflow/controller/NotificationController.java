package com.reviewflow.controller;

import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.MarkAllReadResponse;
import com.reviewflow.model.dto.response.NotificationDto;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.NotificationService;
import com.reviewflow.util.HashidService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Notifications", description = "User notification management")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final HashidService hashidService;

    @Operation(
        summary = "List notifications",
        description = "Get paginated list of notifications for authenticated user. " +
                    "Optional unreadOnly filter returns only unread notifications."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Notifications retrieved successfully",
            content = @Content(schema = @Schema(implementation = Page.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationDto>>> list(
            @AuthenticationPrincipal ReviewFlowUserDetails user,
            @RequestParam(required = false) Boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        Page<NotificationDto> page = notificationService
                .getNotifications(user.getUserId(), unreadOnly, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @Operation(
        summary = "Get unread count",
        description = "Get count of unread notifications for authenticated user."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Unread count retrieved successfully",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        long count = notificationService.getUnreadCount(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", count)));
    }

    @Operation(
        summary = "Mark notification as read",
        description = "Mark a single notification as read by ID."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Notification marked as read successfully",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - notification does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Map<String, String>>> markRead(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long notificationId = hashidService.decodeOrThrow(id);
        notificationService.markAsRead(notificationId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Marked as read")));
    }

    @Operation(
        summary = "Mark all notifications as read",
        description = "Mark all notifications as read for authenticated user."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "All notifications marked as read successfully",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<MarkAllReadResponse>> markAllRead(
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        int updatedCount = notificationService.markAllAsRead(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(MarkAllReadResponse.builder()
                .message("All notifications marked as read")
                .updatedCount(updatedCount)
                .build()));
    }

    @Operation(
        summary = "Delete notification",
        description = "Delete a notification by ID. Soft delete - notification archived for user."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Notification deleted successfully",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - notification does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long notificationId = hashidService.decodeOrThrow(id);
        notificationService.deleteNotification(notificationId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Notification deleted")));
    }

}
