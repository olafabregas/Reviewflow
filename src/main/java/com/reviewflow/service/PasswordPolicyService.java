package com.reviewflow.service;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.reviewflow.config.PasswordPolicyProperties;
import com.reviewflow.exception.ValidationException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

    private static final String ERROR_CODE = "VALIDATION_ERROR";

    private final PasswordPolicyProperties properties;

    public void validateForCreateOrUpdate(String password, String email) {
        validateLoginInputBounds(password);

        if (properties.isRequireUppercase() && !hasUppercase(password)) {
            throw invalidPolicy();
        }
        if (properties.isRequireLowercase() && !hasLowercase(password)) {
            throw invalidPolicy();
        }
        if (properties.isRequireNumber() && !hasNumber(password)) {
            throw invalidPolicy();
        }
        if (properties.isRequireSpecial() && !hasSpecial(password)) {
            throw invalidPolicy();
        }
        if (!properties.isAllowWhitespace() && containsWhitespace(password)) {
            throw invalidPolicy();
        }
        if (matchesEmailIdentity(password, email)) {
            throw new ValidationException("Password must not match email", ERROR_CODE);
        }
    }

    public void validateLoginInputBounds(String password) {
        if (password == null || password.isBlank()) {
            throw new ValidationException("Password is required", ERROR_CODE);
        }

        int minLength = properties.getMinLength();
        int maxLength = properties.getMaxLength();
        int length = password.length();

        if (length < minLength || length > maxLength) {
            throw new ValidationException(
                    "Password must be between " + minLength + " and " + maxLength + " characters",
                    ERROR_CODE);
        }
    }

    private ValidationException invalidPolicy() {
        return new ValidationException(
                "Password must be between " + properties.getMinLength() + " and " + properties.getMaxLength()
                + " characters and include uppercase, lowercase, number, and special character",
                ERROR_CODE);
    }

    private boolean hasUppercase(String value) {
        return value.chars().anyMatch(Character::isUpperCase);
    }

    private boolean hasLowercase(String value) {
        return value.chars().anyMatch(Character::isLowerCase);
    }

    private boolean hasNumber(String value) {
        return value.chars().anyMatch(Character::isDigit);
    }

    private boolean hasSpecial(String value) {
        return value.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch));
    }

    private boolean containsWhitespace(String value) {
        return value.chars().anyMatch(Character::isWhitespace);
    }

    private boolean matchesEmailIdentity(String password, String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String normalizedPassword = password.trim().toLowerCase(Locale.ROOT);

        if (normalizedPassword.equals(normalizedEmail)) {
            return true;
        }

        int atIndex = normalizedEmail.indexOf('@');
        if (atIndex <= 0) {
            return false;
        }

        String localPart = normalizedEmail.substring(0, atIndex);
        return normalizedPassword.equals(localPart);
    }
}
