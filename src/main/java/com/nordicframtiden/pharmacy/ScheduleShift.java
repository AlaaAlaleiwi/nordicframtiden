package com.nordicframtiden.pharmacy;

import com.nordicframtiden.security.model.AppUser;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "schedule_shift")
public class ScheduleShift {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "pharmacy_id", nullable = true)
  private Pharmacy pharmacy;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private AppUser user;

  @Column(name = "start_at", nullable = false)
  private OffsetDateTime startAt;

  @Column(name = "end_at", nullable = false)
  private OffsetDateTime endAt;

  @Column(name = "note", length = 300)
  private String note;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  @PreUpdate
  void preUpdate() {
    updatedAt = OffsetDateTime.now();
  }

  public Long getId() { return id; }

  public Pharmacy getPharmacy() { return pharmacy; }
  public void setPharmacy(Pharmacy pharmacy) { this.pharmacy = pharmacy; }

  public AppUser getUser() { return user; }
  public void setUser(AppUser user) { this.user = user; }

  public OffsetDateTime getStartAt() { return startAt; }
  public void setStartAt(OffsetDateTime startAt) { this.startAt = startAt; }

  public OffsetDateTime getEndAt() { return endAt; }
  public void setEndAt(OffsetDateTime endAt) { this.endAt = endAt; }

  public String getNote() { return note; }
  public void setNote(String note) { this.note = note; }

  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}