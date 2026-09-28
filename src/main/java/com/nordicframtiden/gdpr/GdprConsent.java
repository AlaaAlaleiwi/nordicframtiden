package com.nordicframtiden.gdpr;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * GDPR consent/audit ledger (Art. 7 accountability). One row per consent
 * event: a grant or withdrawal of a consent type, or an access/erasure
 * request marker. Rows survive account erasure (user_id is not a FK) so the
 * records remain available as evidence after Art. 17 deletion.
 */
@Entity
@Table(name = "gdpr_consent")
public class GdprConsent {

  public static final String TYPE_TERMS = "TERMS";
  public static final String TYPE_DATA_PROCESSING = "DATA_PROCESSING";
  public static final String TYPE_EMAIL_NOTIFICATIONS = "EMAIL_NOTIFICATIONS";
  public static final String TYPE_PUSH_NOTIFICATIONS = "PUSH_NOTIFICATIONS";
  public static final String TYPE_ACCESS_REQUEST = "ACCESS_REQUEST";
  public static final String TYPE_ERASURE_REQUEST = "ERASURE_REQUEST";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Plain column (no FK): the ledger outlives the erased account. */
  @Column
  private Long userId;

  /** Username snapshot so erased accounts remain attributable in audits. */
  @Column(length = 120)
  private String username;

  @Column(name = "consent_type", nullable = false, length = 40)
  private String consentType;

  @Column(nullable = false)
  private boolean granted;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public GdprConsent() {}

  public GdprConsent(Long userId, String username, String consentType, boolean granted) {
    this.userId = userId;
    this.username = username;
    this.consentType = consentType;
    this.granted = granted;
  }

  public Long getId() { return id; }
  public Long getUserId() { return userId; }
  public String getUsername() { return username; }
  public String getConsentType() { return consentType; }
  public boolean isGranted() { return granted; }
  public Instant getCreatedAt() { return createdAt; }
}
