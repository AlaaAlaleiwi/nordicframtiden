package com.nordicframtiden.chat;

import com.nordicframtiden.security.model.AppUser;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "chat_push_subscription")
public class ChatPushSubscription {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
  private AppUser user;
  @Column(name = "firebase_installation_id", nullable = false, unique = true, length = 255)
  private String firebaseInstallationId;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public Long getId() { return id; }
  public AppUser getUser() { return user; }
  public void setUser(AppUser user) { this.user = user; }
  public String getFirebaseInstallationId() { return firebaseInstallationId; }
  public void setFirebaseInstallationId(String firebaseInstallationId) { this.firebaseInstallationId = firebaseInstallationId; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
