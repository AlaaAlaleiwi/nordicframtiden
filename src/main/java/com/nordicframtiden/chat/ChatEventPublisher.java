package com.nordicframtiden.chat;

import java.util.Set;

public interface ChatEventPublisher {
  void publish(Long roomId, String type, Object payload);
  void publishTo(Set<String> usernames, Long roomId, String type, Object payload);
}
