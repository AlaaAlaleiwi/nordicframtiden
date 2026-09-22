package com.nordicframtiden.chat;

import com.nordicframtiden.security.model.AppUser;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "chat_reaction")
@IdClass(ChatReaction.Id.class)
public class ChatReaction {
  @jakarta.persistence.Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "message_id") private ChatMessage message;
  @jakarta.persistence.Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private AppUser user;
  @jakarta.persistence.Id @Column(length = 32) private String emoji;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
  public ChatMessage getMessage() { return message; }
  public void setMessage(ChatMessage message) { this.message = message; }
  public AppUser getUser() { return user; }
  public void setUser(AppUser user) { this.user = user; }
  public String getEmoji() { return emoji; }
  public void setEmoji(String emoji) { this.emoji = emoji; }
  public static class Id implements Serializable {
    private Long message; private Long user; private String emoji;
    public Id() {}
    public boolean equals(Object other) { return other instanceof Id id && Objects.equals(message, id.message) && Objects.equals(user, id.user) && Objects.equals(emoji, id.emoji); }
    public int hashCode() { return Objects.hash(message, user, emoji); }
  }
}
