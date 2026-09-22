package com.nordicframtiden.chat;

public class ChatAccessDeniedException extends RuntimeException {
  public ChatAccessDeniedException() {
    super("You do not have access to this chat resource");
  }
}
