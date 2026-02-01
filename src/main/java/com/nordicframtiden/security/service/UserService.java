package com.nordicframtiden.security.service;

import com.nordicframtiden.security.model.*;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Set;

@Service
public class UserService {

  private final AppUserRepository userRepo;
  private final UserProfileRepository profileRepo;
  private final PasswordEncoder encoder;

  public UserService(AppUserRepository userRepo, UserProfileRepository profileRepo, PasswordEncoder encoder) {
    this.userRepo = userRepo;
    this.profileRepo = profileRepo;
    this.encoder = encoder;
  }
public DetailedUser getDetailedByUsername(String username) {
  var u = userRepo.findByUsername(username)
      .orElseThrow(() -> new IllegalArgumentException("User not found"));

  var profile = profileRepo.findByUserId(u.getId()).orElse(null);

  return new DetailedUser(
      u.getId(),
      u.getUsername(),
      u.isEnabled(),
      profile != null ? profile.getFullName() : u.getUsername(),
      profile != null ? profile.getEmail() : null,
      profile != null ? profile.getPhone() : null,
      profile != null ? profile.getHourlyCost() : null
  );
}
  public record UserRow(
      Long id,
      String username,
      boolean enabled,
      String fullName,
      String email,
      String phone,
      BigDecimal hourlyCost,
      String password) {
  }

  public record DetailedUser(
      Long id, String username, boolean enabled, String fullName, String email, String phone, BigDecimal hourlyCost) {
  }

  public DetailedUser getDetailedById(Long id) {
    return userRepo.findById(id)
        .map(u -> {
          var profile = profileRepo.findByUserId(u.getId()).orElse(null);
          return new DetailedUser(
              u.getId(),
              u.getUsername(),
              u.isEnabled(),
              profile != null ? profile.getFullName() : u.getUsername(),
              profile != null ? profile.getEmail() : null,
              profile != null ? profile.getPhone() : null,
              profile != null ? profile.getHourlyCost() : null);
        })
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
  }

  public List<UserRow> listDetailedByRole(Role role) {
    return userRepo.findAllByRole(role).stream().map(u -> {
      var p = profileRepo.findByUserId(u.getId()).orElse(null);
      return new UserRow(
          u.getId(),
          u.getUsername(),
          u.isEnabled(),
          p == null ? null : p.getFullName(),
          p == null ? null : p.getEmail(),
          p == null ? null : p.getPhone(),
          p == null ? null : p.getHourlyCost(),
          null);
    }).toList();
  }

  @Transactional
  public UserRow createWithProfile(Role role, boolean enabled, String fullName, String email, String phone,
      BigDecimal hourlyCost) {

    if (role != Role.USER && role != Role.STAFF) {
      throw new IllegalArgumentException("Only USER or STAFF can be created here");
    }
    if (profileRepo.existsByEmail(email))
      throw new IllegalArgumentException("Email already exists");
    if (profileRepo.existsByPhone(phone))
      throw new IllegalArgumentException("Phone already exists");

    String username = generateUniqueUsername(fullName);

    String rawPassword = generatePassword(12); // TODO: email this to the user

    AppUser u = new AppUser();
    u.setUsername(username);
    u.setPasswordHash(encoder.encode(rawPassword));
    u.setEnabled(enabled);
    u.setRoles(Set.of(role));
    u = userRepo.save(u);

    UserProfile p = new UserProfile();
    p.setFullName(fullName);
    p.setEmail(email);
    p.setPhone(phone);
    p.setHourlyCost(hourlyCost); // ✅ NEW
    p.setUser(u);
    profileRepo.save(p);

    return new UserRow(
        u.getId(),
        u.getUsername(),
        u.isEnabled(),
        p.getFullName(),
        p.getEmail(),
        p.getPhone(),
        p.getHourlyCost(),
        rawPassword);
  }

  @Transactional
  public UserRow updateWithProfile(
      Long id,
      String fullName,
      String email,
      String phone,
      Boolean enabled,
      BigDecimal hourlyCost // ✅ NEW
  ) {
    AppUser u = userRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (u.getRoles() != null && u.getRoles().contains(Role.ADMIN)) {
      throw new IllegalArgumentException("Cannot edit ADMIN from /api/users");
    }

    if (enabled != null)
      u.setEnabled(enabled);

    UserProfile p = profileRepo.findByUserId(id).orElseThrow(() -> new IllegalArgumentException("Profile not found"));

    if (fullName != null && !fullName.isBlank())
      p.setFullName(fullName);

    if (email != null && !email.isBlank() && !email.equalsIgnoreCase(p.getEmail())) {
      if (profileRepo.existsByEmail(email))
        throw new IllegalArgumentException("Email already exists");
      p.setEmail(email);
    }

    if (phone != null && !phone.isBlank() && !phone.equals(p.getPhone())) {
      if (profileRepo.existsByPhone(phone))
        throw new IllegalArgumentException("Phone already exists");
      p.setPhone(phone);
    }

    // ✅ NEW
    if (hourlyCost != null) {
      p.setHourlyCost(hourlyCost);
    }

    profileRepo.save(p);
    userRepo.save(u);

    return new UserRow(
        u.getId(),
        u.getUsername(),
        u.isEnabled(),
        p.getFullName(),
        p.getEmail(),
        p.getPhone(),
        p.getHourlyCost(),
        null);
  }

  @Transactional
  public void deleteUser(Long id) {
    AppUser u = userRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (u.getRoles() != null && u.getRoles().contains(Role.ADMIN)) {
      throw new IllegalArgumentException("Cannot delete ADMIN from /api/users");
    }

    profileRepo.findByUserId(id).ifPresent(profileRepo::delete);
    userRepo.delete(u);
  }

  private String generateUniqueUsername(String fullName) {
    String base = (fullName == null ? "user" : fullName)
        .trim()
        .toLowerCase()
        .replaceAll("[^a-z0-9]+", ".")
        .replaceAll("^\\.|\\.$", "");

    if (base.isBlank())
      base = "user";

    String candidate = base;
    int i = 1;
    while (userRepo.existsByUsername(candidate)) {
      i++;
      candidate = base + i;
    }
    return candidate;
  }

  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
  private static final SecureRandom RNG = new SecureRandom();

  private String generatePassword(int len) {
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }
}