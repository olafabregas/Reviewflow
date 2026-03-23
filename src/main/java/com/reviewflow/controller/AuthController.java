package com.reviewflow.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reviewflow.model.dto.request.LoginRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.AuthUserResponse;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.AuthService;
import com.reviewflow.service.AuthService.LoginResult;
import com.reviewflow.service.AuthService.RefreshResult;
import com.reviewflow.service.HashidService;
import com.reviewflow.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String ACCESS_COOKIE = "reviewflow_access";
    private static final String REFRESH_COOKIE = "reviewflow_refresh";
    private static final String COOKIE_PATH = "/";

    private final AuthService authService;
    private final HashidService hashidService;
    private final UserService userService;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Operation(summary = "Login", description = "Authenticate with email and password. Sets HTTP-only cookies for access and refresh tokens.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Account deactivated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many failed attempts")
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthUserResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        String ipAddress = getClientIpAddress(servletRequest);
        LoginResult result = authService.login(request.getEmail(), request.getPassword(), ipAddress);

        addCookie(response, ACCESS_COOKIE, result.accessToken(), authService.getAccessExpirationMs() / 1000);
        addCookie(response, REFRESH_COOKIE, result.refreshToken(), authService.getRefreshExpirationMs() / 1000);

        return ResponseEntity.ok(ApiResponse.ok(result.user()));
    }

    @Operation(summary = "Refresh token", description = "Issue new access token using refresh cookie. Optionally rotates refresh token.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshValue = getCookieValue(request, REFRESH_COOKIE);
        RefreshResult result = authService.refresh(refreshValue);

        addCookie(response, ACCESS_COOKIE, result.accessToken(), authService.getAccessExpirationMs() / 1000);
        addCookie(response, REFRESH_COOKIE, result.refreshToken(), authService.getRefreshExpirationMs() / 1000);

        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Token refreshed")));
    }

    @Operation(summary = "Logout", description = "Revoke refresh token and clear cookies. Requires valid access token.")
    @ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged out successfully"))
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(
            @AuthenticationPrincipal ReviewFlowUserDetails user,
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshValue = getCookieValue(request, REFRESH_COOKIE);
        authService.logout(refreshValue);

        clearCookie(response, ACCESS_COOKIE);
        clearCookie(response, REFRESH_COOKIE);

        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Logged out successfully")));
    }

    @Operation(summary = "Current user", description = "Return the authenticated user's profile. Used by frontend on app load.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User profile"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthUserResponse>> me(@AuthenticationPrincipal ReviewFlowUserDetails user) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("UNAUTHORIZED", "Not authenticated"));
        }

        AuthUserResponse data = AuthUserResponse.builder()
                .userId(hashidService.encode(user.getUserId()))
                .email(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .avatarUrl(userService.getUserById(user.getUserId()).getAvatarUrl())
                .isActive(user.isEnabled())
                .role(user.getRole())
                .build();
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(summary = "Get access token value for WebSocket authentication")
    @GetMapping("/token")
    public ResponseEntity<ApiResponse<Map<String, String>>> getTokenForWebSocket(
            HttpServletRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {

        if (user == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("UNAUTHORIZED", "Not authenticated"));
        }

        String token = getCookieValue(request, ACCESS_COOKIE);
        if (token == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("UNAUTHORIZED", "Access token not found"));
        }

        return ResponseEntity.ok(ApiResponse.ok(Map.of("token", token)));
    }

    private void addCookie(HttpServletResponse response, String name, String value, long maxAgeSeconds) {
        String cookie = name + "=" + value + "; Path=" + COOKIE_PATH + "; Max-Age=" + maxAgeSeconds
                + "; HttpOnly; SameSite=Lax"
                + (cookieSecure ? "; Secure" : "");
        response.addHeader("Set-Cookie", cookie);
    }

    private void clearCookie(HttpServletResponse response, String name) {
        response.addHeader("Set-Cookie", name + "=; Path=" + COOKIE_PATH + "; Max-Age=0; HttpOnly; SameSite=Lax"
                + (cookieSecure ? "; Secure" : ""));
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
