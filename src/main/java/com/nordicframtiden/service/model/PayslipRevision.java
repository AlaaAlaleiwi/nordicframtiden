package com.nordicframtiden.service.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payslip_revision")
public class PayslipRevision {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "snapshot_id", nullable = false) private Long snapshotId;
  @Column(nullable = false) private int revision;
  @Column(nullable = false) private String actor;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(nullable = false, columnDefinition = "text") private String reason;
  @Column(columnDefinition = "text") private String changes;
  @Column(nullable = false, columnDefinition = "text") private String payload;

  protected PayslipRevision() {}
  public PayslipRevision(Long snapshotId, int revision, String actor, String reason, String changes, String payload) {
    this.snapshotId = snapshotId;
    this.revision = revision;
    this.actor = actor;
    this.reason = reason;
    this.changes = changes;
    this.payload = payload;
    this.createdAt = Instant.now();
  }
  public Long getId() { return id; }
  public Long getSnapshotId() { return snapshotId; }
  public int getRevision() { return revision; }
  public String getActor() { return actor; }
  public Instant getCreatedAt() { return createdAt; }
  public String getReason() { return reason; }
  public String getChanges() { return changes; }
  public String getPayload() { return payload; }
}
