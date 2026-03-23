package com.reviewflow.service;

import com.reviewflow.config.FileSecurityProperties;
import com.reviewflow.exception.AvatarInvalidTypeException;
import com.reviewflow.exception.AvatarTooLargeException;
import com.reviewflow.monitoring.SecurityMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class FileSecurityValidatorAvatarTest {

    @Mock
    private FileSecurityProperties securityProperties;

    @Mock
    private SecurityMetrics securityMetrics;

    private FileSecurityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileSecurityValidator(securityProperties, securityMetrics);
    }

    @Test
    void validateWithConfig_whenMimeIsSpoofed_rejectsUpload() {
        ValidationConfig config = new ValidationConfig(
                Set.of("jpg", "jpeg", "png", "webp"),
                Set.of("image/jpeg", "image/png", "image/webp"),
                2_097_152L
        );

        MockMultipartFile spoofed = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                "this is text, not an image".getBytes()
        );

        assertThrows(AvatarInvalidTypeException.class, () -> validator.validateWithConfig(spoofed, config));
    }

    @Test
    void validateWithConfig_whenFileTooLarge_rejectsUpload() {
        ValidationConfig config = new ValidationConfig(
                Set.of("jpg", "jpeg", "png", "webp"),
                Set.of("image/jpeg", "image/png", "image/webp"),
                8L
        );

        MockMultipartFile oversized = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "0123456789".getBytes()
        );

        assertThrows(AvatarTooLargeException.class, () -> validator.validateWithConfig(oversized, config));
    }

    @Test
    void validateWithConfig_whenFilenameMissing_rejectsUpload() {
        ValidationConfig config = new ValidationConfig(
                Set.of("jpg", "jpeg", "png", "webp"),
                Set.of("image/jpeg", "image/png", "image/webp"),
                2_097_152L
        );

        MockMultipartFile missingName = new MockMultipartFile(
                "file",
                "",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        assertThrows(AvatarInvalidTypeException.class, () -> validator.validateWithConfig(missingName, config));
    }
}
