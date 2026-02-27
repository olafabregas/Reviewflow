package com.reviewflow.controller;

import com.reviewflow.model.dto.request.CreateUserRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.AuthUserResponse;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuthUserResponse>>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<User> page = userService.listUsers(pageable);
        Page<AuthUserResponse> data = page.map(this::toResponse);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AuthUserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(
                request.getEmail(), request.getPassword(),
                request.getFirstName(), request.getLastName(),
                request.getRole() != null ? request.getRole() : UserRole.STUDENT);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(user)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AuthUserResponse>> update(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String firstName = (String) body.get("firstName");
        String lastName = (String) body.get("lastName");
        UserRole role = body.get("role") != null ? UserRole.valueOf(body.get("role").toString()) : null;
        User user = userService.updateUser(id, firstName, lastName, role);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(user)));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Map<String, String>>> deactivate(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "User deactivated")));
    }

    private AuthUserResponse toResponse(User u) {
        return AuthUserResponse.builder()
                .userId(u.getId())
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .role(u.getRole())
                .build();
    }
}
