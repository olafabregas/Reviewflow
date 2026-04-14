package com.reviewflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reviewflow.exception.InactiveUserException;
import com.reviewflow.exception.TooManyRequestsException;
import com.reviewflow.model.dto.response.AuthUserResponse;
import com.reviewflow.model.entity.RefreshToken;
import com.reviewflow.model.entity.User;
import com.reviewflow.monitoring.ReviewFlowMetrics;
import com.reviewflow.repository.RefreshTokenRepository;
import com.reviewflow.repository.UserRepository;
import com.reviewflow.security.JwtService;
import com.reviewflow.security.ReviewFlowUserDetails;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final RateLimiterService rateLimiterService;
    private final ReviewFlowMetrics metrics;
    private final HashidService hashidService;
    private final PasswordPolicyService passwordPolicyService;

    @Transactional
    public LoginResult login(String email, String password, String ipAddress) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        passwordPolicyService.validateLoginInputBounds(password);

        // Check rate limiting
        if (rateLimiterService.isLoginRateLimited(ipAddress)) {
            metrics.recordLoginRateLimited();
            long retryAfter = rateLimiterService.getLoginRetryAfterSeconds(ipAddress);
            throw new TooManyRequestsException("Too many login attempts. Please try again later.", retryAfter);
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    rateLimiterService.recordFailedLogin(ipAddress);
                    auditService.log(null, "USER_LOGIN_FAILED", "User", null, "Email: " + normalizedEmail, ipAddress);
                    return new BadCredentialsException("Invalid credentials");
                });
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            rateLimiterService.recordFailedLogin(ipAddress);
            auditService.log(user.getId(), "USER_LOGIN_FAILED", "User", user.getId(), "Account deactivated", ipAddress);
            throw new InactiveUserException("Account is deactivated");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            rateLimiterService.recordFailedLogin(ipAddress);
            metrics.recordFailedLogin();
            auditService.log(user.getId(), "USER_LOGIN_FAILED", "User", user.getId(), "Invalid password", ipAddress);
            throw new BadCredentialsException("Invalid credentials");
        }

        // Successful login - clear rate limit for this IP
        rateLimiterService.clearFailedLogins(ipAddress);
        metrics.recordUserLogin();

        ReviewFlowUserDetails details = new ReviewFlowUserDetails(user);
        String accessToken = jwtService.generateAccessToken(details);
        String refreshTokenValue = jwtService.generateRefreshToken();
        Instant expiresAt = Instant.now().plusMillis(jwtService.getRefreshExpirationMs());
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashRefreshToken(refreshTokenValue))
                .expiresAt(expiresAt)
                .revoked(false)
                .createdAt(Instant.now())
                .build();
        refreshTokenRepository.save(refreshToken);

        auditService.log(user.getId(), "USER_LOGIN", "User", user.getId(), "Login successful", ipAddress);

        AuthUserResponse userResponse = AuthUserResponse.builder()
                .userId(hashidService.encode(user.getId()))
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .emailNotificationsEnabled(user.getEmailNotificationsEnabled())
                .isActive(user.getIsActive())
                .role(user.getRole())
                .build();

        return new LoginResult(userResponse, accessToken, refreshTokenValue);
    }

    public record LoginResult(AuthUserResponse user, String accessToken, String refreshToken) {

    }

    private String hashRefreshToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public long getRefreshExpirationMs() {
        return jwtService.getRefreshExpirationMs();
    }

    public long getAccessExpirationMs() {
        return jwtService.getAccessExpirationMs();
    }

    public RefreshResult refresh(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        String hash = hashRefreshToken(refreshTokenValue);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        // Detect token reuse attack: if token is already revoked, revoke ALL tokens for this user
        if (Boolean.TRUE.equals(token.getRevoked())) {
            User user = token.getUser();
            refreshTokenRepository.revokeAllForUser(user.getId());
            auditService.log(user.getId(), "TOKEN_REUSE_ATTACK", "User", user.getId(), "Refresh token reuse detected - all tokens revoked", null);
            throw new BadCredentialsException("Invalid refresh token");
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        User user = token.getUser();
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new InactiveUserException("Account is deactivated");
        }
        ReviewFlowUserDetails details = new ReviewFlowUserDetails(user);
        String newAccessToken = jwtService.generateAccessToken(details);
        // Optional rotation: issue new refresh token and revoke old
        String newRefreshValue = jwtService.generateRefreshToken();
        token.setRevoked(true);
        refreshTokenRepository.save(token);
        RefreshToken newToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashRefreshToken(newRefreshValue))
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshExpirationMs()))
                .revoked(false)
                .createdAt(Instant.now())
                .build();
        refreshTokenRepository.save(newToken);

        return new RefreshResult(newAccessToken, newRefreshValue);
    }

    public record RefreshResult(String accessToken, String refreshToken) {

    }

    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return;
        }
        String hash = hashRefreshToken(refreshTokenValue);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
            auditService.log(t.getUser().getId(), "USER_LOGOUT", "User", t.getUser().getId(), "Logout successful", null);
        });
    }
}
