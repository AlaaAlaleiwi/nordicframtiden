package com.nordicframtiden.chat;

import com.nordicframtiden.security.model.AppUser;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "chat_room_member")
@IdClass(ChatRoomMember.Id.class)
public class ChatRoomMember {
  @jakarta.persistence.Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "room_id")
  private ChatRoom room;
  @jakarta.persistence.Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id")
  private AppUser user;
  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt = Instant.now();
  @Column(name = "last_read_message_id") private Long lastReadMessageId;

  public ChatRoom getRoom() { return room; }
  public void setRoom(ChatRoom room) { this.room = room; }
  public AppUser getUser() { return user; }
  public void setUser(AppUser user) { this.user = user; }
  public Long getLastReadMessageId() { return lastReadMessageId; }
  public void setLastReadMessageId(Long value) { lastReadMessageId = value; }

  public static class Id implements Serializable {
    private Long room;
    private Long user;
    public Id() {}
    public boolean equals(Object other) { return other instanceof Id id && Objects.equals(room, id.room) && Objects.equals(user, id.user); }
    public int hashCode() { return Objects.hash(room, user); }
  }
}
