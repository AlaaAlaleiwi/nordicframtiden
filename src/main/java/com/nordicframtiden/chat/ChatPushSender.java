package com.nordicframtiden.chat;

import java.util.Map;

public interface ChatPushSender {
  void send(String firebaseInstallationId, Map<String, String> data);

  default void send(String firebaseInstallationId) {
    send(firebaseInstallationId, Map.of(
        "title", "Nordic Framtiden Health",
        "body", "You have a new message",
        "url", "/chat",
        "type", "chat.message"));
  }
}
