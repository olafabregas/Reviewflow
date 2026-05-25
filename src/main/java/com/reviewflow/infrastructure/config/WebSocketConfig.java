package com.reviewflow.infrastructure.config;

import com.reviewflow.infrastructure.security.WebSocketAuthInterceptor;
import com.reviewflow.infrastructure.websocket.WebSocketTransportTrackingDecorator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Value("${app.cors.allowed-origins:http://localhost:5173}")
  private String allowedOrigins;

  @Value("${websocket.transport.message-size-limit:262144}")
  private int messageSizeLimitBytes;

  @Value("${websocket.transport.send-buffer-size-limit:524288}")
  private int sendBufferSizeLimitBytes;

  @Value("${websocket.transport.send-time-limit:10000}")
  private int sendTimeLimitMs;

  @Value("${websocket.sockjs.enabled:true}")
  private boolean sockJsEnabled;

  @Autowired private WebSocketAuthInterceptor webSocketAuthInterceptor;

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    // /queue → point-to-point (one specific user)
    // /topic → broadcast (all subscribers) — available for future use
    config.enableSimpleBroker("/queue", "/topic");

    // Prefix for messages sent FROM client TO server
    config.setApplicationDestinationPrefixes("/app");

    // Prefix for user-specific destinations
    // Makes /user/{userId}/queue/notifications work correctly
    config.setUserDestinationPrefix("/user");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    StompWebSocketEndpointRegistration endpoint =
        registry
            .addEndpoint("/ws")
            .setAllowedOriginPatterns(allowedOrigins.split("\\s*,\\s*"));

    if (sockJsEnabled) {
      endpoint.withSockJS();
    }
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration
        .setMessageSizeLimit(messageSizeLimitBytes)
        .setSendBufferSizeLimit(sendBufferSizeLimitBytes)
        .setSendTimeLimit(sendTimeLimitMs)
        .addDecoratorFactory(WebSocketTransportTrackingDecorator::new);
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(webSocketAuthInterceptor);
  }
}
