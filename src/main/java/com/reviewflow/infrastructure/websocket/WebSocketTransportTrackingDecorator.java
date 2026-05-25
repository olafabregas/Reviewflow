package com.reviewflow.infrastructure.websocket;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

public class WebSocketTransportTrackingDecorator extends WebSocketHandlerDecorator {

  public static final String TRANSPORT_SESSION_KEY = "reviewflow.ws.transport";

  public WebSocketTransportTrackingDecorator(WebSocketHandler delegate) {
    super(delegate);
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    session.getAttributes().put(TRANSPORT_SESSION_KEY, session);
    super.afterConnectionEstablished(session);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus)
      throws Exception {
    session.getAttributes().remove(TRANSPORT_SESSION_KEY);
    super.afterConnectionClosed(session, closeStatus);
  }
}
