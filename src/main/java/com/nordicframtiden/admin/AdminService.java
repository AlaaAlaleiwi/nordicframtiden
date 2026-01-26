package com.nordicframtiden.security.service;

import com.nordicframtiden.security.model.AdminProfile;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.repo.AdminProfileRepository;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
public class AdminService {

  private final AppUserRepository repo;
  private final AdminProfileRepository adminProfileRepo;
  private final PasswordEncoder encoder;

  public AdminService(AppUserRepository repo, AdminProfileRepository adminProfileRepo, PasswordEncoder encoder) {
    this.repo = repo;
    this.adminProfileRepo = adminProfileRepo;
    this.encoder = encoder;
  }

  public record AdminRow(
      Long id,
      String username,
      boolean enabled,
      String fullName,
      String email,
      String phone
  ) {}

  /* =========================
     LIST (with profile fields)
     ========================= */
  @Transactional(readOnly = true)
  public List<AdminRow> listAdminsDetailed() {
    return repo.findAllAdmins().stream()
        .map(u -> {
          AdminProfile p = adminProfileRepo.findByUserId(u.getId()).orElse(null);
          return new AdminRow(
              u.getId(),
              u.getUsername(),
              u.isEnabled(),
              p != null ? p.getFullName() : null,
              p != null ? p.getEmail() : null,
              p != null ? p.getPhone() : null
          );
        })
        .toList();
  }

  /* =========================
     CREATE (password generated)
     ========================= */
  @Transactional
  public AdminRow createAdminWithProfile(
      String username,
      boolean enabled,
      String fullName,
      String email,
      String phone
  ) {

    if (repo.existsByUsername(username)) throw new IllegalArgumentException("Username already exists");
    if (adminProfileRepo.existsByEmail(email)) throw new IllegalArgumentException("Email already exists");
    if (adminProfileRepo.existsByPhone(phone)) throw new IllegalArgumentException("Phone already exists");

    // ✅ generate password in backend
    String rawPassword = generatePassword();

    // 1) create login user
    AppUser u = new AppUser();
    u.setUsername(username);
    u.setPasswordHash(encoder.encode(rawPassword));
    u.setEnabled(enabled);
    u.setRoles(Set.of(Role.ADMIN));
    u = repo.save(u);

    // 2) create admin profile
    AdminProfile profile = new AdminProfile();
    profile.setFullName(fullName);
    profile.setEmail(email);
    profile.setPhone(phone);
    profile.setUser(u);
    adminProfileRepo.save(profile);

    // TODO: send email with rawPassword (use JavaMailSender)
    // emailService.sendNewAdminPassword(email, username, rawPassword);

    return new AdminRow(u.getId(), u.getUsername(), u.isEnabled(), fullName, email, phone);
  }

  /* =========================
     UPDATE (profile + username + enabled)
     ========================= */
  @Transactional
  public AdminRow updateAdminWithProfile(
      Long id,
      String newUsername,
      Boolean enabled,
      String fullName,
      String email,
      String phone
  ) {

    AppUser u = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Admin not found"));

    if (u.getRoles() == null || !u.getRoles().contains(Role.ADMIN)) {
      throw new IllegalArgumentException("User is not an admin");
    }

    // username
    if (newUsername != null && !newUsername.isBlank() && !newUsername.equals(u.getUsername())) {
      if (repo.existsByUsername(newUsername)) throw new IllegalArgumentException("Username already exists");
      u.setUsername(newUsername);
    }

    // enabled
    if (enabled != null) u.setEnabled(enabled);

    // profile
    AdminProfile profile = adminProfileRepo.findByUserId(u.getId())
        .orElseThrow(() -> new IllegalArgumentException("Admin profile not found"));

    if (fullName != null && !fullName.isBlank()) profile.setFullName(fullName);

    if (email != null && !email.isBlank() && !email.equals(profile.getEmail())) {
      if (adminProfileRepo.existsByEmail(email)) throw new IllegalArgumentException("Email already exists");
      profile.setEmail(email);
    }

    if (phone != null && !phone.isBlank() && !phone.equals(profile.getPhone())) {
      if (adminProfileRepo.existsByPhone(phone)) throw new IllegalArgumentException("Phone already exists");
      profile.setPhone(phone);
    }

    adminProfileRepo.save(profile);
    u = repo.save(u);

    return new AdminRow(
        u.getId(),
        u.getUsername(),
        u.isEnabled(),
        profile.getFullName(),
        profile.getEmail(),
        profile.getPhone()
    );
  }

  /* =========================
     DELETE
     ========================= */
  @Transactional
  public void deleteAdmin(Long id) {
    AppUser u = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Admin not found"));

    if (u.getRoles() == null || !u.getRoles().contains(Role.ADMIN)) {
      throw new IllegalArgumentException("User is not an admin");
    }

    adminProfileRepo.findByUserId(u.getId()).ifPresent(adminProfileRepo::delete);
    repo.delete(u);
  }

  /* =========================
     PASSWORD GENERATOR
     ========================= */
  private static final SecureRandom RAND = new SecureRandom();

  private String generatePassword() {
    // 12-16 chars, URL-safe
    byte[] bytes = new byte[12];
    RAND.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}