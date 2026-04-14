package com.reviewflow.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.reviewflow.config.PasswordPolicyProperties;
import com.reviewflow.exception.ValidationException;

class PasswordPolicyServiceTest {

    private PasswordPolicyService passwordPolicyService;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        PasswordPolicyProperties properties = new PasswordPolicyProperties();
        properties.setMinLength(8);
        properties.setMaxLength(64);
        properties.setRequireUppercase(true);
        properties.setRequireLowercase(true);
        properties.setRequireNumber(true);
        properties.setRequireSpecial(true);
        properties.setAllowWhitespace(false);

        passwordPolicyService = new PasswordPolicyService(properties);
    }

    @Test
    void validateForCreateOrUpdate_whenPasswordValid_passes() {
        assertDoesNotThrow(() -> passwordPolicyService.validateForCreateOrUpdate("Strong@123", "user@reviewflow.com"));
    }

    @Test
    void validateForCreateOrUpdate_whenMissingUppercase_throwsValidationException() {
        ValidationException thrown = assertThrows(ValidationException.class,
                () -> passwordPolicyService.validateForCreateOrUpdate("strong@123", "user@reviewflow.com"));
        assertNotNull(thrown);
        assertEquals("VALIDATION_ERROR", thrown.getCode());
    }

    @Test
    void validateForCreateOrUpdate_whenContainsWhitespace_throwsValidationException() {
        ValidationException thrown = assertThrows(ValidationException.class,
                () -> passwordPolicyService.validateForCreateOrUpdate("Strong @123", "user@reviewflow.com"));
        assertNotNull(thrown);
        assertEquals("VALIDATION_ERROR", thrown.getCode());
    }

    @Test
    void validateForCreateOrUpdate_whenMatchesEmailLocalPart_throwsValidationException() {
        ValidationException thrown = assertThrows(ValidationException.class,
                () -> passwordPolicyService.validateForCreateOrUpdate("Strong123!", "Strong123!@reviewflow.com"));
        assertNotNull(thrown);
        assertEquals("VALIDATION_ERROR", thrown.getCode());
    }

    @Test
    void validateLoginInputBounds_whenTooLong_throwsValidationException() {
        ValidationException thrown = assertThrows(ValidationException.class,
                () -> passwordPolicyService.validateLoginInputBounds("x".repeat(65)));
        assertNotNull(thrown);
        assertEquals("VALIDATION_ERROR", thrown.getCode());
    }

    @Test
    void validateLoginInputBounds_whenWithinBounds_passes() {
        assertDoesNotThrow(() -> passwordPolicyService.validateLoginInputBounds("LegacyPwd"));
    }
}
