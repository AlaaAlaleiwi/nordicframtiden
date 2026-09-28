package com.nordicframtiden.gdpr;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Queued data-export delivery (Art. 15/20). Created when the user confirms
 * the request in the app/web; processed by {@link GdprExportMailJob} at 03:00,
 * which emails the JSON export to {@code email} and marks the row SENT.
 * Rows are the delivery audit trail; they are NOT deleted on erasure (same
 * rationale as gdpr_consent: accountability evidence outlives the account).
 */
@Entity
@Table(name = "gdpr_export_request")
public class GdprExportRequest {

  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_SENT = "SENT";
  public static final String STATUS_FAILED = "FAILED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Column(length = 120)
  private String username;

  @Column(nullable = false)
  private String email;

  @Column(nullable = false, length = 20)
  private String status = STATUS_PENDING;

  @Column(nullable = false)
  private int attempts = 0;

  @Column
  private String lastError;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "sent_at")
  private Instant sentAt;

  public GdprExportRequest() {}

  public GdprExportRequest(Long userId, String username, String email) {
    this.userId = userId;
    this.username = username;
    this.email = email;
  }

  public Long getId() { return id; }
  public Long getUserId() { return userId; }
  public String getUsername() { return username; }
  public String getEmail() { return email; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public int getAttempts() { return attempts; }
  public void setAttempts(int attempts) { this.attempts = attempts; }
  public String getLastError() { return lastError; }
  public void setLastError(String lastError) { this.lastError = lastError; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getSentAt() { return sentAt; }
  public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}
