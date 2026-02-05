 package com.nordicframtiden.company;

import com.nordicframtiden.security.model.AppUser;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "staff_shift")
public class StaffShift {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private AppUser user;

  @Column(name = "start_at", nullable = false)
  private OffsetDateTime startAt;

  @Column(name = "end_at", nullable = false)
  private OffsetDateTime endAt;

  @Column(name = "note")
  private String note;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @PrePersist
  void prePersist() {
    var now = OffsetDateTime.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = OffsetDateTime.now();
  }

  // getters/setters
  public Long getId() { return id; }

  public AppUser getUser() { return user; }
  public void setUser(AppUser user) { this.user = user; }

  public OffsetDateTime getStartAt() { return startAt; }
  public void setStartAt(OffsetDateTime startAt) { this.startAt = startAt; }

  public OffsetDateTime getEndAt() { return endAt; }
  public void setEndAt(OffsetDateTime endAt) { this.endAt = endAt; }

  public String getNote() { return note; }
  public void setNote(String note) { this.note = note; }
  
}