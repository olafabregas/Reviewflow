package com.reviewflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.reviewflow.model.entity.RefreshToken;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.monitoring.ReviewFlowMetrics;
import com.reviewflow.repository.RefreshTokenRepository;
import com.reviewflow.repository.UserRepository;
import com.reviewflow.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuditService auditService;
    @Mock
    private RateLimiterService rateLimiterService;
    @Mock
    private ReviewFlowMetrics metrics;
    @Mock
    private HashidService hashidService;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_trimsAndLowercasesEmailBeforeLookup() {
        User user = User.builder()
                .id(1L)
                .email("admin@reviewflow.com")
                .passwordHash("hash")
                .firstName("Admin")
                .lastName("User")
                .role(UserRole.ADMIN)
                .isActive(true)
                .emailNotificationsEnabled(true)
                .build();

        when(rateLimiterService.isLoginRateLimited("127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("admin@reviewflow.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Test@1234", "hash")).thenReturn(true);
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtService.getRefreshExpirationMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hashidService.encode(1L)).thenReturn("U1HASH");

        AuthService.LoginResult result = authService.login("  ADMIN@ReviewFlow.com  ", "Test@1234", "127.0.0.1");

        assertNotNull(result);
        assertEquals("admin@reviewflow.com", result.user().getEmail());
        verify(userRepository).findByEmail("admin@reviewflow.com");
        verify(passwordEncoder).matches("Test@1234", "hash");
        verify(metrics).recordUserLogin();
        verify(auditService).log(1L, "USER_LOGIN", "User", 1L, "Login successful", "127.0.0.1");
    }

    @Test
    void login_doesNotTrimPassword() {
        User user = User.builder()
                .id(2L)
                .email("admin@reviewflow.com")
                .passwordHash("hash")
                .firstName("Admin")
                .lastName("User")
                .role(UserRole.ADMIN)
                .isActive(true)
                .emailNotificationsEnabled(true)
                .build();

        when(rateLimiterService.isLoginRateLimited("127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("admin@reviewflow.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("  Test@1234  ", "hash")).thenReturn(false);

        try {
            authService.login("  admin@reviewflow.com  ", "  Test@1234  ", "127.0.0.1");
            fail("Expected BadCredentialsException");
        } catch (BadCredentialsException ex) {
            assertNotNull(ex);
        }

        verify(passwordEncoder).matches("  Test@1234  ", "hash");
        verify(passwordEncoder, never()).matches("Test@1234", "hash");
        verify(metrics).recordFailedLogin();
        verify(auditService).log(2L, "USER_LOGIN_FAILED", "User", 2L, "Invalid password", "127.0.0.1");
    }
}
