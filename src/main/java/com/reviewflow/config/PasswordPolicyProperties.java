package com.reviewflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "security.password")
public class PasswordPolicyProperties {

    private int minLength = 8;
    private int maxLength = 64;
    private boolean requireUppercase = true;
    private boolean requireLowercase = true;
    private boolean requireNumber = true;
    private boolean requireSpecial = true;
    private boolean allowWhitespace = false;

    @PostConstruct
    @SuppressWarnings("unused")
    void validate() {
        if (minLength < 1) {
            throw new IllegalStateException("security.password.min-length must be at least 1");
        }
        if (maxLength < minLength) {
            throw new IllegalStateException("security.password.max-length must be greater than or equal to min-length");
        }
    }
}
