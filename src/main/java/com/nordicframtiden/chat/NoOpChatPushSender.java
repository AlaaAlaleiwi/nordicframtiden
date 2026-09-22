package com.nordicframtiden.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(ChatPushSender.class)
public class NoOpChatPushSender implements ChatPushSender {
  @Override
  public void send(String firebaseInstallationId) {
    // WebSocket delivery remains available when Firebase is disabled.
  }
}
