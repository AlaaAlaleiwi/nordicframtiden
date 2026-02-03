package com.nordicframtiden.security.model;

import java.math.BigDecimal;
import jakarta.persistence.*;

@Entity
@Table(name = "user_profile")
public class UserProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String fullName;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false, unique = true)
  private String phone;

  @OneToOne(optional = false)
  @JoinColumn(name = "user_id", unique = true)
  private AppUser user;

  @Column(name = "hourly_cost", precision = 12, scale = 2)
  private BigDecimal hourlyCost;

  // ✅ NEW
  @Column(name = "year_of_birth", nullable = false)
  private Integer yearOfBirth;

  // ✅ NEW (län: 2 digits as string, ex: "01")
  @Column(name = "county_code", nullable = false, length = 2)
  private String countyCode;

  // ✅ NEW (kommun: 4 digits as string, ex: "0114")
  @Column(name = "municipality_code", nullable = false, length = 4)
  private String municipalityCode;

  // getters/setters...

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getFullName() { return fullName; }
  public void setFullName(String fullName) { this.fullName = fullName; }

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }

  public AppUser getUser() { return user; }
  public void setUser(AppUser user) { this.user = user; }

  public BigDecimal getHourlyCost() { return hourlyCost; }
  public void setHourlyCost(BigDecimal hourlyCost) { this.hourlyCost = hourlyCost; }

  public Integer getYearOfBirth() { return yearOfBirth; }
  public void setYearOfBirth(Integer yearOfBirth) { this.yearOfBirth = yearOfBirth; }

  public String getCountyCode() { return countyCode; }
  public void setCountyCode(String countyCode) { this.countyCode = countyCode; }

  public String getMunicipalityCode() { return municipalityCode; }
  public void setMunicipalityCode(String municipalityCode) { this.municipalityCode = municipalityCode; }
}