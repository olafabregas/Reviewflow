package com.reviewflow.service;

import com.reviewflow.model.dto.response.AuthUserResponse;
import com.reviewflow.model.entity.RefreshToken;
import com.reviewflow.model.entity.User;
import com.reviewflow.repository.RefreshTokenRepository;
import com.reviewflow.repository.UserRepository;
import com.reviewflow.security.JwtService;
import com.reviewflow.security.ReviewFlowUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Transactional
    public LoginResult login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new InactiveUserException("Account is deactivated");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
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

        AuthUserResponse userResponse = AuthUserResponse.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();

        return new LoginResult(userResponse, accessToken, refreshTokenValue);
    }

    public record LoginResult(AuthUserResponse user, String accessToken, String refreshToken) {}

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
        if (Boolean.TRUE.equals(token.getRevoked()) || token.getExpiresAt().isBefore(Instant.now())) {
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

    public record RefreshResult(String accessToken, String refreshToken) {}

    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) return;
        String hash = hashRefreshToken(refreshTokenValue);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }
}