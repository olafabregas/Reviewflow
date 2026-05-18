package com.reviewflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewflow.messaging.MessagingRedisSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Configuration
@ConditionalOnProperty(
    name = "redis.messaging.pubsub.enabled",
    havingValue = "true",
    matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class MessagingRedisConfig {

  private final RedisConnectionFactory connectionFactory;
  private final SimpMessagingTemplate messagingTemplate;
  private final ObjectMapper objectMapper;

  public static final String MESSAGING_CHANNEL = "reviewflow:messaging:push";

  @Bean
  public RedisMessageListenerContainer messagingListenerContainer() {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(
        messagingRedisListener(), new ChannelTopic(MESSAGING_CHANNEL));
    log.info("Redis messaging pub/sub enabled — channel: {}", MESSAGING_CHANNEL);
    return container;
  }

  @Bean
  public MessageListenerAdapter messagingRedisListener() {
    return new MessageListenerAdapter(
        new MessagingRedisSubscriber(messagingTemplate, objectMapper));
  }
}
