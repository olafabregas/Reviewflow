package com.reviewflow.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewflow.auth.dto.request.OAuthStateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OAuthService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${oauth.state-ttl-seconds:300}")
    private long stateTtlSeconds;

    private static final String STATE_KEY_PREFIX = "reviewflow:oauth:state:";

    public void storeOAuthState(String state, OAuthStateDto stateData) {
        try {
            String key = STATE_KEY_PREFIX + state;
            String json = objectMapper.writeValueAsString(stateData);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(stateTtlSeconds));
        } catch (Exception e) {
            log.error("Failed to store OAuth state: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to store OAuth state", e);
        }
    }

    public Optional<OAuthStateDto> consumeOAuthState(String state) {
        try {
            String key = STATE_KEY_PREFIX + state;
            String value = redisTemplate.opsForValue().getAndDelete(key);
            if (value == null) {
                return Optional.empty();
            }
            OAuthStateDto stateData = objectMapper.readValue(value, OAuthStateDto.class);
            return Optional.of(stateData);
        } catch (Exception e) {
            log.error("Failed to consume OAuth state: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to consume OAuth state", e);
        }
    }
}
