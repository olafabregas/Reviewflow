package com.reviewflow.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@RequiredArgsConstructor
@Slf4j
public class MessagingRedisSubscriber implements MessageListener {

  private final SimpMessagingTemplate messagingTemplate;
  private final ObjectMapper objectMapper;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      RedisMessagePayload payload =
          objectMapper.readValue(message.getBody(), RedisMessagePayload.class);

      messagingTemplate.convertAndSendToUser(
          payload.targetUserId(), "/queue/messages", payload.content());
    } catch (Exception e) {
      log.warn("Redis messaging push failed: {}", e.getMessage());
    }
  }
}
