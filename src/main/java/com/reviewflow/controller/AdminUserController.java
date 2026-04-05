package com.reviewflow.controller;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reviewflow.model.dto.request.CreateUserRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.AuthUserResponse;
import com.reviewflow.model.dto.response.UserDetailResponse;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.HashidService;
import com.reviewflow.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
@Tag(name = "Admin Users", description = "User management endpoints for administrators")
public class AdminUserController {

    private final UserService userService;
    private final HashidService hashidService;

    @Operation(
            summary = "List users",
            description = "Get paginated list of all users with optional filters by role, active status, and search term. "
            + "Admin-only endpoint."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Users retrieved successfully",
                content = @Content(schema = @Schema(implementation = Page.class))
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
        )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuthUserResponse>>> list(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<User> page = userService.listUsersFiltered(role, active, search, pageable);
        Page<AuthUserResponse> data = page.map(this::toResponse);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(
            summary = "Get user details",
            description = "Get user details including associated counts (courses, submissions, etc). "
            + "Admin-only endpoint."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "User details retrieved successfully",
                content = @Content(schema = @Schema(implementation = UserDetailResponse.class))
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
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getById(@PathVariable String id) {
        Long userId = hashidService.decodeOrThrow(id);
        UserDetailResponse user = userService.getUserByIdWithCounts(userId);
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    @Operation(
            summary = "Create user",
            description = "Create a new user account with specified email, password, name, and role. "
            + "Role defaults to STUDENT if not provided. Admin-only endpoint."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "User created successfully",
                content = @Content(schema = @Schema(implementation = AuthUserResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - invalid user data or email already exists",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
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
        )
    })
    @PostMapping
    public ResponseEntity<ApiResponse<AuthUserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(
                request.getEmail(), request.getPassword(),
                request.getFirstName(), request.getLastName(),
                request.getRole() != null ? request.getRole() : UserRole.STUDENT);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toResponse(user)));
    }

    @Operation(
            summary = "Update user",
            description = "Update user details (firstName, lastName, role). All fields optional. "
            + "Admin-only endpoint."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "User updated successfully",
                content = @Content(schema = @Schema(implementation = AuthUserResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - invalid data",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
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
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AuthUserResponse>> update(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Long userId = hashidService.decodeOrThrow(id);
        String firstName = (String) body.get("firstName");
        String lastName = (String) body.get("lastName");
        UserRole role = body.get("role") != null ? UserRole.valueOf(body.get("role").toString()) : null;
        User user = userService.updateUser(userId, firstName, lastName, role);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(user)));
    }

    @Operation(
            summary = "Deactivate user",
            description = "Deactivate a user account preventing login. Soft delete - user data retained. "
            + "Admin-only endpoint."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "User deactivated successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - ADMIN role required or cannot deactivate self",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - user does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deactivate(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails principal) {
        Long userId = hashidService.decodeOrThrow(id);
        userService.deactivateUser(userId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "message", "User deactivated",
                "isActive", false
        )));
    }

    @Operation(
            summary = "Reactivate user",
            description = "Reactivate a deactivated user account allowing login again. "
            + "Admin-only endpoint."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "User reactivated successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
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
    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reactivate(@PathVariable String id) {
        Long userId = hashidService.decodeOrThrow(id);
        userService.reactivateUser(userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "message", "User reactivated",
                "isActive", true
        )));
    }

    private AuthUserResponse toResponse(User u) {
        return AuthUserResponse.builder()
                .userId(hashidService.encode(u.getId()))
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .avatarUrl(u.getAvatarUrl())
                .emailNotificationsEnabled(u.getEmailNotificationsEnabled())
                .isActive(u.getIsActive())
                .role(u.getRole())
                .build();
    }
}
