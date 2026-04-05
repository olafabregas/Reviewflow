package com.reviewflow.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.reviewflow.model.dto.request.UpdateEmailPreferenceRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.AuthUserResponse;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.HashidService;
import com.reviewflow.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "User avatar and preferences management")
public class AvatarController {

    private final UserService userService;
    private final HashidService hashidService;

    @Operation(
            summary = "Upload avatar",
            description = "Upload or replace user avatar image. Multipart file upload (image/png, image/jpeg). "
            + "Client IP logged for audit trail."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Avatar uploaded successfully",
                content = @Content(schema = @Schema(implementation = AuthUserResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - invalid file or missing file",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "413",
                description = "Payload Too Large - file exceeds size limit",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PutMapping("/users/me/avatar")
    public ResponseEntity<ApiResponse<AuthUserResponse>> uploadAvatar(
            @AuthenticationPrincipal ReviewFlowUserDetails user,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        AuthUserResponse data = userService.uploadAvatar(user.getUserId(), file, getClientIpAddress(request));
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(
            summary = "Delete my avatar",
            description = "Delete user's own avatar image. Returns user without avatar. Client IP logged for audit."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Avatar deleted successfully",
                content = @Content(schema = @Schema(implementation = AuthUserResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @DeleteMapping("/users/me/avatar")
    public ResponseEntity<ApiResponse<AuthUserResponse>> deleteMyAvatar(
            @AuthenticationPrincipal ReviewFlowUserDetails user,
            HttpServletRequest request) {
        AuthUserResponse data = userService.deleteAvatar(user.getUserId(), getClientIpAddress(request));
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(
            summary = "Update email preferences",
            description = "Update user email notification preferences. Controls whether user receives email notifications."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Preferences updated successfully",
                content = @Content(schema = @Schema(implementation = AuthUserResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - invalid preference data",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PatchMapping("/users/me/preferences")
    public ResponseEntity<ApiResponse<AuthUserResponse>> updateMyPreferences(
            @AuthenticationPrincipal ReviewFlowUserDetails user,
            @Valid @RequestBody UpdateEmailPreferenceRequest request) {
        AuthUserResponse data = userService.updateEmailPreference(
                user.getUserId(),
                request.getEmailNotificationsEnabled());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(
            summary = "Admin delete user avatar",
            description = "Delete avatar of any user. Admin-only endpoint. "
            + "Client IP logged for audit trail."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "User avatar deleted successfully",
                content = @Content(schema = @Schema(implementation = AuthUserResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - user does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @DeleteMapping("/admin/users/{id}/avatar")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
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
