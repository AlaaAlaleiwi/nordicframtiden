package com.nordicframtiden.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(
    name = "app.firebase.enabled",
    havingValue = "false",
    matchIfMissing = true
)
public class NoOpChatPushSender implements ChatPushSender {
  @Override
  public void send(String firebaseInstallationId, Map<String, String> data) {
    // WebSocket delivery remains available when Firebase is disabled.
  }
}
