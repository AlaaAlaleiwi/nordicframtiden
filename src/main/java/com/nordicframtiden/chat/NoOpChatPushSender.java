package com.nordicframtiden.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "app.firebase.enabled",
    havingValue = "false",
    matchIfMissing = true
)
public class NoOpChatPushSender implements ChatPushSender {
  @Override
  public void send(String firebaseInstallationId) {
    // WebSocket delivery remains available when Firebase is disabled.
  }
}
