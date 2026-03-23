package com.reviewflow.controller;

import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.AuthUserResponse;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.HashidService;
import com.reviewflow.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AvatarController {

    private final UserService userService;
    private final HashidService hashidService;

    @PutMapping("/users/me/avatar")
    public ResponseEntity<ApiResponse<AuthUserResponse>> uploadAvatar(
            @AuthenticationPrincipal ReviewFlowUserDetails user,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        AuthUserResponse data = userService.uploadAvatar(user.getUserId(), file, getClientIpAddress(request));
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @DeleteMapping("/users/me/avatar")
    public ResponseEntity<ApiResponse<AuthUserResponse>> deleteMyAvatar(
            @AuthenticationPrincipal ReviewFlowUserDetails user,
            HttpServletRequest request) {
        AuthUserResponse data = userService.deleteAvatar(user.getUserId(), getClientIpAddress(request));
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @DeleteMapping("/admin/users/{id}/avatar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AuthUserResponse>> adminDeleteAvatar(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails admin,
            HttpServletRequest request) {
        Long targetUserId = hashidService.decodeOrThrow(id);
        AuthUserResponse data = userService.adminDeleteAvatar(targetUserId, admin.getUserId(), getClientIpAddress(request));
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
