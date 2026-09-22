package com.nordicframtiden.chat;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class ChatWebSocketConfig implements WebSocketConfigurer {
  private final ChatWebSocketHandler handler;
  public ChatWebSocketConfig(ChatWebSocketHandler handler) { this.handler = handler; }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws/chat")
        .setAllowedOrigins(
            "http://localhost:5173", "http://localhost:3000",
            "https://nordicframtiden-frontend-34c6b049a0f5.herokuapp.com",
            "https://nordicframtiden-frontend-644311628279.europe-north1.run.app",
            "https://nordicframtiden-frontend-mbtjtlqpcq-lz.a.run.app");
  }
}
