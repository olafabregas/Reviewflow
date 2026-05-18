package com.reviewflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reviewflow.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisTokenVersionStoreTest {

  @Mock private StringRedisTemplate redis;
  @Mock private ValueOperations<String, String> valueOps;
  @Mock private UserRepository userRepository;

  private RedisTokenVersionStore store;

  @BeforeEach
  void setUp() {
    when(redis.opsForValue()).thenReturn(valueOps);
    store = new RedisTokenVersionStore(redis, userRepository, 300, 30, 10_000, "tv-invalidations");
  }

  @Test
  void getCurrentVersion_readsRedisThenCachesLocally() {
    when(valueOps.get("reviewflow:token:version:7")).thenReturn("4");

    int first = store.getCurrentVersion(7L);
    int second = store.getCurrentVersion(7L);

    assertThat(first).isEqualTo(4);
    assertThat(second).isEqualTo(4);
    verify(valueOps, times(1)).get("reviewflow:token:version:7");
    verify(userRepository, never()).findTokenVersionById(any());
  }

  @Test
  void getCurrentVersion_usesReviewflowTokenVersionKeyPrefix() {
    when(valueOps.get("reviewflow:token:version:99")).thenReturn(null);
    when(userRepository.findTokenVersionById(99L)).thenReturn(Optional.of(5));

    int version = store.getCurrentVersion(99L);

    assertThat(version).isEqualTo(5);
    verify(valueOps).get("reviewflow:token:version:99");
    verify(valueOps).set(eq("reviewflow:token:version:99"), eq("5"), eq(Duration.ofSeconds(300)));
  }

  @Test
  void invalidate_setsRedisValueAndPublishesInvalidationEvent() {
    when(valueOps.get("reviewflow:token:version:11")).thenReturn("2");
    when(userRepository.findTokenVersionById(11L)).thenReturn(Optional.of(3));

    store.getCurrentVersion(11L);
    store.invalidate(11L);
    store.onMessage(
        new DefaultMessage(
            "tv-invalidations".getBytes(StandardCharsets.UTF_8),
            "11".getBytes(StandardCharsets.UTF_8)),
        null);
    store.getCurrentVersion(11L);

    verify(valueOps).set(eq("reviewflow:token:version:11"), eq("3"), eq(Duration.ofSeconds(300)));
    verify(redis).convertAndSend("tv-invalidations", "11");
    verify(valueOps, times(2)).get("reviewflow:token:version:11");
  }
}
