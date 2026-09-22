package com.nordicframtiden.chat;

public interface ChatPushSender {
  void send(String firebaseInstallationId);
}
