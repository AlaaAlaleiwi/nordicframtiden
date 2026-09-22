package com.nordicframtiden.chat;

import com.nordicframtiden.security.model.AppUser;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_message")
public class ChatMessage {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "room_id") private ChatRoom room;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sender_id") private AppUser sender;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_message_id") private ChatMessage parent;
  @Column(nullable = false, length = 4000) private String body;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
  @Column(name = "edited_at") private Instant editedAt;
  @Column(name = "deleted_at") private Instant deletedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public ChatRoom getRoom() { return room; }
  public void setRoom(ChatRoom room) { this.room = room; }
  public AppUser getSender() { return sender; }
  public void setSender(AppUser sender) { this.sender = sender; }
  public ChatMessage getParent() { return parent; }
  public void setParent(ChatMessage parent) { this.parent = parent; }
  public String getBody() { return body; }
  public void setBody(String body) { this.body = body; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getEditedAt() { return editedAt; }
  public void setEditedAt(Instant editedAt) { this.editedAt = editedAt; }
  public Instant getDeletedAt() { return deletedAt; }
  public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
