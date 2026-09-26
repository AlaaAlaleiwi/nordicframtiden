package com.nordicframtiden.service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Frozen payslip for an ended salary month, stored as the serialized
 * {@link com.nordicframtiden.service.model.NetSalaryResponse} JSON. Past
 * months are served verbatim from this table so later changes to the hourly
 * cost (or other profile data) never rewrite history.
 */
@Entity
@Table(name = "payslip_snapshot",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_payslip_snapshot",
        columnNames = {"user_id", "year", "month", "role"}))
public class PayslipSnapshot {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false)
  private Integer year;

  @Column(nullable = false)
  private Integer month;

  @Column(nullable = false, length = 16)
  private String role = "USER";

  /** Serialized NetSalaryResponse. */
  @Column(nullable = false, columnDefinition = "text")
  private String payload;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public Long getId() { return id; }
  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public Integer getYear() { return year; }
  public void setYear(Integer year) { this.year = year; }
  public Integer getMonth() { return month; }
  public void setMonth(Integer month) { this.month = month; }
  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }
  public String getPayload() { return payload; }
  public void setPayload(String payload) { this.payload = payload; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void touch() { this.updatedAt = Instant.now(); }
}
