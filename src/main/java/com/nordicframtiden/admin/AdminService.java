package com.nordicframtiden.admin;

import com.nordicframtiden.admin.model.AdminProfile;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.admin.model.AdminProfileRepository;
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
      String phone,
      String password) {
  }

  /*
   * =========================
   * LIST (with profile fields)
   * =========================
   */
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
              p != null ? p.getPhone() : null,
              null);
        })
        .toList();
  }

  /*
   * =========================
   * CREATE (password generated)
   * =========================
   */
  @Transactional
  public AdminRow createAdminWithProfile(
      boolean enabled,
      String fullName,
      String email,
      String phone) {

    if (adminProfileRepo.existsByEmail(email))
      throw new IllegalArgumentException("Email already exists");

    if (adminProfileRepo.existsByPhone(phone))
      throw new IllegalArgumentException("Phone already exists");

    // 1️⃣ Generate username
    String username = generateUniqueUsername(fullName);
    String password = generatePassword();
    // 2️⃣ Create login user
    AppUser user = new AppUser();
    user.setUsername(username);
    user.setPasswordHash(encoder.encode(password));
    user.setEnabled(enabled);
    user.setRoles(Set.of(Role.ADMIN));
    user = repo.save(user);

    // 3️⃣ Create admin profile
    AdminProfile profile = new AdminProfile();
    profile.setFullName(fullName);
    profile.setEmail(email);
    profile.setPhone(phone);
    profile.setUser(user);
    adminProfileRepo.save(profile);

    return new AdminRow(
        user.getId(),
        user.getUsername(),
        user.isEnabled(),
        profile.getFullName(),
        profile.getEmail(),
        profile.getPhone(),
        password);
  }

  /*
   * =========================
   * UPDATE (profile + username + enabled)
   * =========================
   */
  @Transactional
  public AdminRow updateAdminWithProfile(
      Long id,
      String newUsername,
      Boolean enabled,
      String fullName,
      String email,
      String phone) {

    AppUser u = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Admin not found"));

    if (u.getRoles() == null || !u.getRoles().contains(Role.ADMIN)) {
      throw new IllegalArgumentException("User is not an admin");
    }

    // username
    if (newUsername != null && !newUsername.isBlank() && !newUsername.equals(u.getUsername())) {
      if (repo.existsByUsername(newUsername))
        throw new IllegalArgumentException("Username already exists");
      u.setUsername(newUsername);
    }

    // enabled
    if (enabled != null)
      u.setEnabled(enabled);

    // profile
    AdminProfile profile = adminProfileRepo.findByUserId(u.getId())
        .orElseGet(() -> {
          AdminProfile p = new AdminProfile();
          p.setUser(u);
          return p;
        });

    if (fullName != null && !fullName.isBlank())
      profile.setFullName(fullName);

    if (email != null && !email.isBlank() && !email.equals(profile.getEmail())) {
      if (adminProfileRepo.existsByEmail(email))
        throw new IllegalArgumentException("Email already exists");
      profile.setEmail(email);
    }

    if (phone != null && !phone.isBlank() && !phone.equals(profile.getPhone())) {
      if (adminProfileRepo.existsByPhone(phone))
        throw new IllegalArgumentException("Phone already exists");
      profile.setPhone(phone);
    }

    adminProfileRepo.save(profile);
    return new AdminRow(
        u.getId(),
        u.getUsername(),
        u.isEnabled(),
        profile.getFullName(),
        profile.getEmail(),
        profile.getPhone(),
        null);
  }

  /*
   * =========================
   * DELETE
   * =========================
   */
  @Transactional
  public void deleteAdmin(Long id) {
    AppUser u = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Admin not found"));

    if (u.getRoles() == null || !u.getRoles().contains(Role.ADMIN)) {
      throw new IllegalArgumentException("User is not an admin");
    }

    adminProfileRepo.findByUserId(u.getId()).ifPresent(adminProfileRepo::delete);
    repo.delete(u);
  }

  /*
   * =========================
   * PASSWORD GENERATOR
   * =========================
   */
  private static final SecureRandom RAND = new SecureRandom();

  private String generatePassword() {
    // 12-16 chars, URL-safe
    byte[] bytes = new byte[12];
    RAND.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String generateUniqueUsername(String fullName) {

    // Normalize accents: Å → A, é → e
    String base = java.text.Normalizer.normalize(fullName, java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
        .toLowerCase()
        .replaceAll("[^a-z ]", "")
        .trim()
        .replaceAll("\\s+", ".");

    // Use first + last name if possible
    String[] parts = base.split("\\.");
    if (parts.length >= 2) {
      base = parts[0] + "." + parts[parts.length - 1];
    }

    String username = base;
    int counter = 1;

    while (repo.existsByUsername(username)) {
      counter++;
      username = base + counter;
    }

    return username;
  }

  @Transactional(readOnly = true)
  public AdminRow getDetailedUser(String username) {

    AppUser u = repo.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

    // Must be ADMIN if we return AdminRow
    if (u.getRoles() == null || !u.getRoles().contains(Role.ADMIN)) {
      throw new IllegalArgumentException("User is not an admin");
    }

    AdminProfile p = adminProfileRepo.findByUserId(u.getId()).orElse(null);

    return new AdminRow(
        u.getId(),
        u.getUsername(),
        u.isEnabled(),
        p != null ? p.getFullName() : null,
        p != null ? p.getEmail() : null,
        p != null ? p.getPhone() : null,
        null // never expose password
    );
  }

  @Transactional
  public AdminRow resetAdminPassword(Long id) {
    AppUser u = repo.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

    if (u.getRoles() == null || !u.getRoles().contains(Role.ADMIN)) {
      throw new IllegalArgumentException("User is not an admin");
    }

    String newPassword = generatePassword();
    u.setPasswordHash(encoder.encode(newPassword));
    repo.save(u);

    AdminProfile p = adminProfileRepo.findByUserId(u.getId()).orElse(null);

    return new AdminRow(
        u.getId(),
        u.getUsername(),
        u.isEnabled(),
        p != null ? p.getFullName() : null,
        p != null ? p.getEmail() : null,
        p != null ? p.getPhone() : null,
        newPassword // ✅ returned ONCE so frontend can show it
    );
  }
}