package com.reviewflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class MessagingRedisConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(MessagingRedisConfig.class, RedisTestSupport.class);

  @Test
  void contextLoadsWithPubsubDisabled_noMessagingBeans() {
    contextRunner
        .withPropertyValues("redis.messaging.pubsub.enabled=false")
        .run(
            context ->
                assertThat(context).doesNotHaveBean("messagingListenerContainer"));
  }

  @Test
  void contextLoadsWithPubsubEnabled_createsMessagingBeans() {
    RedisConnectionFactory connectionFactory = Mockito.mock(RedisConnectionFactory.class);
    SimpMessagingTemplate messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);
    ObjectMapper objectMapper = new ObjectMapper();

    MessagingRedisConfig config =
        new MessagingRedisConfig(connectionFactory, messagingTemplate, objectMapper);

    assertThat(config.messagingListenerContainer()).isNotNull();
    assertThat(config.messagingRedisListener()).isNotNull();
  }

  @Configuration
  static class RedisTestSupport {

    @Bean
    RedisConnectionFactory redisConnectionFactory() {
      return Mockito.mock(RedisConnectionFactory.class);
    }

    @Bean
    SimpMessagingTemplate simpMessagingTemplate() {
      return Mockito.mock(SimpMessagingTemplate.class);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
