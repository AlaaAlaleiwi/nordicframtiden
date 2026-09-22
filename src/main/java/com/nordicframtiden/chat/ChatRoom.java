package com.nordicframtiden.chat;

import com.nordicframtiden.security.model.AppUser;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_room")
public class ChatRoom {
  public enum Type { CHANNEL, DIRECT }

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
  private Type type;
  @Column(length = 80) private String name;
  @Column(length = 500) private String description;
  @Column(name = "private_channel", nullable = false)
  private boolean privateChannel;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by") private AppUser createdBy;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Type getType() { return type; }
  public void setType(Type type) { this.type = type; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public boolean isPrivateChannel() { return privateChannel; }
  public void setPrivateChannel(boolean privateChannel) { this.privateChannel = privateChannel; }
  public AppUser getCreatedBy() { return createdBy; }
  public void setCreatedBy(AppUser createdBy) { this.createdBy = createdBy; }
  public Instant getCreatedAt() { return createdAt; }
}
