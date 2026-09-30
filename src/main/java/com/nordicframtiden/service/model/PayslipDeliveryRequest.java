package com.nordicframtiden.service.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Delivery queue for the automatic monthly payslip run. When the payslip for
 * the previous work month becomes ready (the 21st, or the previous working
 * day when the 21st is not a working day) the scheduled job queues one row
 * per USER/STAFF account, then emails the payslip PDF and sends the "payslip
 * ready" notification. Rows double as the audit trail of when each payslip
 * was delivered.
 *
 * Plain user_id column (like gdpr_export_request) instead of an association:
 * the nightly job reads rows outside a transaction, so lazy loading would
 * blow up; the FK's ON DELETE CASCADE cleans up on account deletion.
 */
@Entity
@Table(name = "payslip_delivery_request",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "work_year", "work_month", "role"}))
public class PayslipDeliveryRequest {

  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_SENDING = "SENDING";
  public static final String STATUS_SENT = "SENT";
  public static final String STATUS_FAILED = "FAILED";
  public static final String STATUS_SKIPPED = "SKIPPED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  /** Snapshot of the profile email at queue time (mail is sent to this). */
  @Column(name = "email", nullable = false)
  private String email;

  /** Work month this payslip covers (payout happens the following month). */
  @Column(name = "work_year", nullable = false)
  private int workYear;

  @Column(name = "work_month", nullable = false)
  private int workMonth;

  /** Payroll role of the payslip: USER (pharmacist) or STAFF. */
  @Column(name = "role", nullable = false, length = 10)
  private String role;

  @Column(name = "status", nullable = false, length = 20)
  private String status = STATUS_PENDING;

  @Column(name = "attempts", nullable = false)
  private int attempts = 0;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  /** When a worker atomically claimed this row (PENDING -> SENDING). */
  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  public PayslipDeliveryRequest() {
  }

  public PayslipDeliveryRequest(Long userId, String email, int workYear, int workMonth, String role) {
    this.userId = userId;
    this.email = email;
    this.workYear = workYear;
    this.workMonth = workMonth;
    this.role = role;
  }

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public int getWorkYear() { return workYear; }
  public void setWorkYear(int workYear) { this.workYear = workYear; }
  public int getWorkMonth() { return workMonth; }
  public void setWorkMonth(int workMonth) { this.workMonth = workMonth; }
  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public int getAttempts() { return attempts; }
  public void setAttempts(int attempts) { this.attempts = attempts; }
  public String getLastError() { return lastError; }
  public void setLastError(String lastError) { this.lastError = lastError; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getClaimedAt() { return claimedAt; }
  public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }
  public Instant getSentAt() { return sentAt; }
  public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}
