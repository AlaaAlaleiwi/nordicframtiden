package com.nordicframtiden.chat;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.util.List;

@Component
public class ChatWebSocketHandshakeHandler extends DefaultHandshakeHandler {
  @Override
  protected String selectProtocol(List<String> requestedProtocols, WebSocketHandler wsHandler) {
    return requestedProtocols.stream()
        .filter("bearer"::equalsIgnoreCase)
        .findFirst()
        .orElse(null);
  }
}
