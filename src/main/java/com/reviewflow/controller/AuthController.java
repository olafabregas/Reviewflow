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
import com.reviewflow.util.HashidService;
import com.reviewflow.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Operation(
        summary = "Login",
        description = "Authenticate user with email and password credentials. Sets HTTP-only secure cookies for access and refresh tokens. Implements rate limiting on failed attempts."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Authentication successful, tokens set in cookies",
            content = @Content(schema = @Schema(implementation = AuthUserResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Bad Request - missing or invalid email/password",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - invalid email or password",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - account is deactivated",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "429",
            description = "Too Many Requests - too many failed login attempts, try again later",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
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

    @Operation(
        summary = "Refresh access token",
        description = "Issue new access token using refresh token from cookie. Implements automatic token rotation for enhanced security. Requires valid refresh token in cookie."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Token refreshed successfully, new access token set in cookie",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - refresh token expired or invalid",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
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

    @Operation(
        summary = "Logout",
        description = "Revoke refresh token and clear authentication cookies. Requires valid access token. Immediately invalidates all active sessions for this user."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Logged out successfully, cookies cleared",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
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

    @Operation(
        summary = "Get current user profile",
        description = "Return authenticated user's profile data including name, email, avatar, role, and preferences. Typically called by frontend on app initialization."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "User profile returned",
            content = @Content(schema = @Schema(implementation = AuthUserResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthUserResponse>> me(@AuthenticationPrincipal ReviewFlowUserDetails user) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("UNAUTHORIZED", "Not authenticated"));
        }

        var persistedUser = userService.getUserById(user.getUserId());

        AuthUserResponse data = AuthUserResponse.builder()
                .userId(hashidService.encode(user.getUserId()))
                .email(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .avatarUrl(persistedUser.getAvatarUrl())
                .emailNotificationsEnabled(persistedUser.getEmailNotificationsEnabled())
                .isActive(user.isEnabled())
                .role(user.getRole())
                .build();
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(
        summary = "Get access token for WebSocket",
        description = "Extract access token from cookie for WebSocket authentication. WebSocket connections cannot use cookies directly, so this endpoint provides token value for auth header."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Access token returned",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
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
