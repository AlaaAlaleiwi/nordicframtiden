package com.nordicframtiden.security.model;

import jakarta.persistence.*;

@Entity
@Table(
    name = "admin_profile",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_admin_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_admin_phone", columnNames = "phone")
    }
)
public class AdminProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String fullName;

  @Column(nullable = false)
  private String email;

  @Column(nullable = false)
  private String phone;

  @OneToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private AppUser user;

  // getters/setters
  public Long getId() { return id; }

  public String getFullName() { return fullName; }
  public void setFullName(String fullName) { this.fullName = fullName; }

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }

  public AppUser getUser() { return user; }
  public void setUser(AppUser user) { this.user = user; }
}