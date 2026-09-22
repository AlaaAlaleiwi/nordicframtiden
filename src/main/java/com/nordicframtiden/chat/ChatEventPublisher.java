package com.nordicframtiden.chat;

public interface ChatEventPublisher {
  void publish(Long roomId, String type, Object payload);
}
