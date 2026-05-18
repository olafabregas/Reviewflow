package com.reviewflow.messaging;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class MessagingRedisSubscriberTest {

  @Mock private SimpMessagingTemplate messagingTemplate;

  private ObjectMapper objectMapper;
  private MessagingRedisSubscriber subscriber;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    subscriber = new MessagingRedisSubscriber(messagingTemplate, objectMapper);
  }

  @Test
  void onMessage_validPayload_reachesSimpMessagingTemplate() throws Exception {
    Map<String, Object> content = Map.of("type", "NEW_MESSAGE", "conversationId", "abc");
    String json =
        objectMapper.writeValueAsString(new RedisMessagePayload("42", content));
    DefaultMessage message =
        new DefaultMessage("reviewflow:messaging:push".getBytes(StandardCharsets.UTF_8), json.getBytes(StandardCharsets.UTF_8));

    subscriber.onMessage(message, null);

    verify(messagingTemplate).convertAndSendToUser(eq("42"), eq("/queue/messages"), eq(content));
  }

  @Test
  void onMessage_malformedPayload_loggedNotThrown() {
    DefaultMessage message =
        new DefaultMessage(
            "reviewflow:messaging:push".getBytes(StandardCharsets.UTF_8),
            "not-json".getBytes(StandardCharsets.UTF_8));

    subscriber.onMessage(message, null);

    verify(messagingTemplate, never()).convertAndSendToUser(eq("42"), eq("/queue/messages"), eq(Map.of()));
  }
}
