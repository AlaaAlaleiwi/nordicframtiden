package com.nordicframtiden.contact;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "contact_request")
public class ContactRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 30)
  private String type; // APOTEK | FARMACEUT

  @Column(nullable = false, length = 160)
  private String name;

  @Column(length = 200)
  private String organization;

  @Column(nullable = false, length = 200)
  private String email;

  @Column(length = 60)
  private String phone;

  @Column(nullable = false, length = 40)
  private String topic; // KONSULTATION | BEMANNING | LON_ERSATTNING | ANNAT

  @Column(nullable = false, length = 4000)
  private String message;

  @Column(nullable = false)
  private boolean handled = false;

  @Column(length = 600)
  private String adminNote;

  @Column(nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  @Column
  private OffsetDateTime handledAt;

  // getters/setters

  public Long getId() { return id; }

  public String getType() { return type; }
  public void setType(String type) { this.type = type; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getOrganization() { return organization; }
  public void setOrganization(String organization) { this.organization = organization; }

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }

  public String getTopic() { return topic; }
  public void setTopic(String topic) { this.topic = topic; }

  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }

  public boolean isHandled() { return handled; }
  public void setHandled(boolean handled) { this.handled = handled; }

  public String getAdminNote() { return adminNote; }
  public void setAdminNote(String adminNote) { this.adminNote = adminNote; }

  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

  public OffsetDateTime getHandledAt() { return handledAt; }
  public void setHandledAt(OffsetDateTime handledAt) { this.handledAt = handledAt; }
}