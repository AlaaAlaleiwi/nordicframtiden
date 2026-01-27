package com.nordicframtiden.pharmacy;

import jakarta.persistence.*;

@Entity
@Table(name = "pharmacy")
public class Pharmacy {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(length = 160)
  private String email;

  @Column(length = 40)
  private String phone;

  @Column(length = 220)
  private String address;

  // Contact person
  @Column(name = "contact_name", length = 160)
  private String contactName;

  @Column(name = "contact_email", length = 160)
  private String contactEmail;

  @Column(name = "contact_phone", length = 40)
  private String contactPhone;

  @Column(nullable = false)
  private boolean enabled = true;

  public Long getId() { return id; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }

  public String getAddress() { return address; }
  public void setAddress(String address) { this.address = address; }

  public String getContactName() { return contactName; }
  public void setContactName(String contactName) { this.contactName = contactName; }

  public String getContactEmail() { return contactEmail; }
  public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

  public String getContactPhone() { return contactPhone; }
  public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
}