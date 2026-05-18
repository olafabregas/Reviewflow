package com.reviewflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.reviewflow.auth.dto.request.OAuthStateDto;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OAuthServiceRedisTest {

  private static final String STATE_KEY_PREFIX = "reviewflow:oauth:state:";

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOps;

  private OAuthService oauthService;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    oauthService = new OAuthService(redisTemplate, objectMapper);
    ReflectionTestUtils.setField(oauthService, "stateTtlSeconds", 300L);
  }

  @Test
  void storeOAuthState_writesKeyWithCorrectTtl() throws Exception {
    OAuthStateDto dto =
        new OAuthStateDto("req-1", "challenge", "http://localhost/callback", Instant.now());

    oauthService.storeOAuthState("state-abc", dto);

    ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(valueOps)
        .set(eq(STATE_KEY_PREFIX + "state-abc"), jsonCaptor.capture(), ttlCaptor.capture());
    assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(300));
    assertThat(jsonCaptor.getValue()).contains("req-1");
  }

  @Test
  void consumeOAuthState_deletesKeyAfterConsume() throws Exception {
    String key = STATE_KEY_PREFIX + "state-del";
    String json =
        objectMapper.writeValueAsString(
            new OAuthStateDto("req-2", "c", "http://localhost/cb", Instant.now()));
    when(valueOps.getAndDelete(key)).thenReturn(json).thenReturn(null);

    assertThat(oauthService.consumeOAuthState("state-del")).isPresent();
    assertThat(oauthService.consumeOAuthState("state-del")).isEmpty();
    verify(valueOps, org.mockito.Mockito.times(2)).getAndDelete(key);
  }

  @Test
  void consumeOAuthState_returnsEmptyWhenKeyMissing() {
    when(valueOps.getAndDelete(STATE_KEY_PREFIX + "never-stored")).thenReturn(null);

    assertThat(oauthService.consumeOAuthState("never-stored")).isEmpty();
  }
}
