package com.reviewflow.integration;

import com.reviewflow.controller.AvatarController;
import com.reviewflow.model.dto.request.UpdateEmailPreferenceRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.AuthUserResponse;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.util.HashidService;
import com.reviewflow.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarControllerIntegrationTest {

    @Mock
    private UserService userService;

    @Mock
    private HashidService hashidService;

    @Mock
    private HttpServletRequest request;

    private AvatarController controller() {
        return new AvatarController(userService, hashidService);
    }

    private ReviewFlowUserDetails studentPrincipal(Long userId) {
        User user = User.builder()
                .id(userId)
                .email("student@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .build();
        return new ReviewFlowUserDetails(user);
    }

    @Test
    void uploadAvatar_happyPath_returns200WithAvatarUrl() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", "img".getBytes());
        ReviewFlowUserDetails principal = studentPrincipal(77L);
        AuthUserResponse payload = AuthUserResponse.builder()
                .userId("HASH77")
                .email("student@test.local")
                .avatarUrl("https://bucket.s3.us-east-1.amazonaws.com/avatars/HASH77/avatar.jpg?v=1")
                .role(UserRole.STUDENT)
                .isActive(true)
                .build();

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(userService.uploadAvatar(77L, file, "127.0.0.1")).thenReturn(payload);

        ResponseEntity<ApiResponse<AuthUserResponse>> response = controller().uploadAvatar(principal, file, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().isSuccess());
        assertEquals("HASH77", response.getBody().getData().getUserId());
        assertEquals(payload.getAvatarUrl(), response.getBody().getData().getAvatarUrl());
    }

    @Test
    void adminDeleteAvatar_happyPath_decodesHashAndReturns200() {
        ReviewFlowUserDetails admin = studentPrincipal(99L);
        AuthUserResponse payload = AuthUserResponse.builder()
                .userId("HASH15")
                .email("student@test.local")
                .avatarUrl(null)
                .role(UserRole.STUDENT)
                .isActive(true)
                .build();

        when(hashidService.decodeOrThrow("USER15")).thenReturn(15L);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(userService.adminDeleteAvatar(15L, 99L, "127.0.0.1")).thenReturn(payload);

        ResponseEntity<ApiResponse<AuthUserResponse>> response = controller().adminDeleteAvatar("USER15", admin, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().isSuccess());
        assertEquals("HASH15", response.getBody().getData().getUserId());
        assertEquals(null, response.getBody().getData().getAvatarUrl());
    }

    @Test
    void updateMyPreferences_happyPath_returns200WithUpdatedPreference() {
        ReviewFlowUserDetails principal = studentPrincipal(88L);
        UpdateEmailPreferenceRequest prefRequest = new UpdateEmailPreferenceRequest();
        prefRequest.setEmailNotificationsEnabled(false);

        AuthUserResponse payload = AuthUserResponse.builder()
                .userId("HASH88")
                .email("student@test.local")
                .emailNotificationsEnabled(false)
                .role(UserRole.STUDENT)
                .isActive(true)
                .build();

        when(userService.updateEmailPreference(88L, false)).thenReturn(payload);

        ResponseEntity<ApiResponse<AuthUserResponse>> response = controller().updateMyPreferences(principal, prefRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().isSuccess());
        assertEquals("HASH88", response.getBody().getData().getUserId());
        assertEquals(false, response.getBody().getData().getEmailNotificationsEnabled());
    }
}
