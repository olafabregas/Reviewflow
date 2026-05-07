package com.reviewflow.auth.controller;

import com.reviewflow.auth.dto.request.LoginRequest;
import com.reviewflow.auth.dto.response.WsTicketResponse;
import com.reviewflow.auth.service.AuthCookieIssuer;
import com.reviewflow.auth.service.AuthService;
import com.reviewflow.auth.service.AuthService.LoginResult;
import com.reviewflow.auth.service.AuthService.RefreshResult;
import com.reviewflow.auth.service.WsTicketService;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.shared.exception.ApiResponse;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.user.dto.response.AuthUserResponse;
import com.reviewflow.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final AuthCookieIssuer authCookieIssuer;
  private final HashidService hashidService;
  private final UserService userService;
  private final WsTicketService wsTicketService;

  @Operation(
      summary = "Login",
      description =
          "Authenticate user with email and password credentials. Sets HTTP-only secure cookies for"
              + " access and refresh tokens. Implements rate limiting on failed attempts.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Authentication successful, tokens set in cookies",
        content = @Content(schema = @Schema(implementation = AuthUserResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Bad Request - missing or invalid email/password",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized - invalid email or password",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden - account is deactivated",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "429",
        description = "Too Many Requests - too many failed login attempts, try again later",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
  })
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthUserResponse>> login(
      @Valid @RequestBody LoginRequest request,
      @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
      HttpServletRequest servletRequest,
      HttpServletResponse response) {
    String ipAddress = getClientIpAddress(servletRequest);
    String userAgent = servletRequest.getHeader("User-Agent");
    LoginResult result =
        authService.login(
            request.getEmail(), request.getPassword(), ipAddress, deviceId, userAgent);

    authCookieIssuer.writeAccess(response, result.accessToken(), result.accessTtlMs() / 1000);
    authCookieIssuer.writeRefresh(response, result.refreshToken(), result.refreshTtlMs() / 1000);

    return ResponseEntity.ok(ApiResponse.ok(result.user()));
  }

  @Operation(
      summary = "Refresh access token",
      description =
          "Issue new access token using refresh token from cookie. Implements automatic token"
              + " rotation for enhanced security. Requires valid refresh token in cookie.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Token refreshed successfully, new access token set in cookie",
        content = @Content(schema = @Schema(implementation = Map.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized - refresh token expired or invalid",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
  })
  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<Map<String, String>>> refresh(
      HttpServletRequest request, HttpServletResponse response) {
    String refreshValue = getCookieValue(request, authCookieIssuer.getRefreshCookieName());
    String clientIp = getClientIpAddress(request);
    String userAgent = request.getHeader("User-Agent");
    RefreshResult result = authService.refresh(refreshValue, clientIp, userAgent);

    authCookieIssuer.writeAccess(response, result.accessToken(), result.accessTtlMs() / 1000);
    authCookieIssuer.writeRefresh(response, result.refreshToken(), result.refreshTtlMs() / 1000);

    return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Token refreshed")));
  }

  @Operation(
      summary = "Logout",
      description =
          "Revoke refresh token and clear authentication cookies. Requires valid access token."
              + " Immediately invalidates all active sessions for this user.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Logged out successfully, cookies cleared",
        content = @Content(schema = @Schema(implementation = Map.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized - authentication required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
  })
  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Map<String, String>>> logout(
      @AuthenticationPrincipal ReviewFlowUserDetails user,
      HttpServletRequest request,
      HttpServletResponse response) {
    String refreshValue = getCookieValue(request, authCookieIssuer.getRefreshCookieName());
    authService.logout(refreshValue);

    authCookieIssuer.clearAllAuthCookies(response);

    return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Logged out successfully")));
  }

  @Operation(
      summary = "Get current user profile",
      description =
          "Return authenticated user's profile data including name, email, avatar, role, and"
              + " preferences. Typically called by frontend on app initialization.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "User profile returned",
        content = @Content(schema = @Schema(implementation = AuthUserResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized - authentication required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
  })
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<AuthUserResponse>> me(
      @AuthenticationPrincipal ReviewFlowUserDetails user) {
    if (user == null) {
      return ResponseEntity.status(401)
          .body(ApiResponse.error("UNAUTHORIZED", "Not authenticated"));
    }

    var persistedUser = userService.getUserById(user.getUserId());

    AuthUserResponse data =
        AuthUserResponse.builder()
            .userId(hashidService.encode(user.getUserId()))
            .email(user.getUsername())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .avatarUrl(persistedUser.getAvatarUrl())
            .emailNotificationsEnabled(persistedUser.getEmailNotificationsEnabled())
            .isActive(user.isEnabled())
            .role(user.getRole())
            .deviceId(null)
            .build();
    return ResponseEntity.ok(ApiResponse.ok(data));
  }

  @Operation(
      summary = "Issue single-use WebSocket ticket",
      description = "Short-lived ticket for STOMP CONNECT (X-Auth-Ticket). Replaces exposing JWT to JS.")
  @GetMapping("/ws-ticket")
  public ResponseEntity<ApiResponse<WsTicketResponse>> wsTicket(
      @AuthenticationPrincipal ReviewFlowUserDetails user) {
    if (user == null) {
      return ResponseEntity.status(401)
          .body(ApiResponse.error("UNAUTHORIZED", "Not authenticated"));
    }
    return ResponseEntity.ok(ApiResponse.ok(wsTicketService.issueTicket(user.getUserId())));
  }

  @Operation(
      summary = "Removed: use ws-ticket",
      description = "Deprecated. Returns 410 Gone with ENDPOINT_REMOVED.")
  @GetMapping("/token")
  public ResponseEntity<ApiResponse<Map<String, String>>> getTokenForWebSocketRemoved() {
    return ResponseEntity.status(HttpStatus.GONE)
        .body(
            ApiResponse.error(
                "ENDPOINT_REMOVED", "GET /api/v1/auth/token has been removed; use GET /api/v1/auth/ws-ticket"));
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
