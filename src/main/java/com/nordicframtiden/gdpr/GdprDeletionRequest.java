package com.nordicframtiden.gdpr;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Account/data deletion is NOT immediate (Art. 17): deleting a user wipes
 * their schedule history and therefore the payment records tied to it. The
 * data subject files a request; an ADMIN approves and schedules an execution
 * date; the nightly job deletes after that date and both parties are
 * notified. Chain of states:
 * PENDING -> APPROVED(scheduledDate) -> DELETED
 * PENDING -> REJECTED
 * any pre-DELETED state -> CANCELLED by the requester.
 */
@Entity
@Table(name = "gdpr_deletion_request")
public class GdprDeletionRequest {

  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_APPROVED = "APPROVED";
  public static final String STATUS_REJECTED = "REJECTED";
  public static final String STATUS_DELETED = "DELETED";
  public static final String STATUS_CANCELLED = "CANCELLED";

  public static final int MIN_GRACE_DAYS = 30;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(length = 120)
  private String username;

  /** Frozen copy of the profile email so approval notices reach the user. */
  @Column(length = 255)
  private String email;

  @Column(nullable = false, length = 20)
  private String status = STATUS_PENDING;

  /** Optional reason entered by the requester (helps the admin decide). */
  @Column(length = 1000)
  private String reason;

  /** Date on/after which the nightly job executes the deletion. */
  @Column(name = "scheduled_date")
  private LocalDate scheduledDate;

  @Column(name = "approved_by", length = 120)
  private String approvedBy;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "rejected_by", length = 120)
  private String rejectedBy;

  @Column(name = "rejected_reason", length = 1000)
  private String rejectedReason;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public GdprDeletionRequest() {}

  public GdprDeletionRequest(Long userId, String username, String email, String reason) {
    this.userId = userId;
    this.username = username;
    this.email = email;
    this.reason = reason;
  }

  public Long getId() { return id; }
  public Long getUserId() { return userId; }
  public String getUsername() { return username; }
  public String getEmail() { return email; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public LocalDate getScheduledDate() { return scheduledDate; }
  public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
  public String getApprovedBy() { return approvedBy; }
  public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
  public Instant getApprovedAt() { return approvedAt; }
  public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
  public String getRejectedBy() { return rejectedBy; }
  public void setRejectedBy(String rejectedBy) { this.rejectedBy = rejectedBy; }
  public String getRejectedReason() { return rejectedReason; }
  public void setRejectedReason(String rejectedReason) { this.rejectedReason = rejectedReason; }
  public Instant getDeletedAt() { return deletedAt; }
  public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
  public Instant getCancelledAt() { if (cancelledAt != null) { return cancelledAt; } return null; }
  public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
  public Instant getCreatedAt() { return createdAt; }
}
