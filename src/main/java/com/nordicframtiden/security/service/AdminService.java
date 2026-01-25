package com.nordicframtiden.security.service;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class AdminService {

  private final AppUserRepository repo;
  private final PasswordEncoder encoder;

  public AdminService(AppUserRepository repo, PasswordEncoder encoder) {
    this.repo = repo;
    this.encoder = encoder;
  }

  @Transactional
  public AppUser createAdmin(String username, String rawPassword, boolean enabled) {
    if (repo.existsByUsername(username)) {
      throw new IllegalArgumentException("Username already exists");
    }

    AppUser u = new AppUser();
    u.setUsername(username);
    u.setPasswordHash(encoder.encode(rawPassword));
    u.setEnabled(enabled);
    u.setRoles(Set.of(Role.ADMIN));

    return repo.save(u);
  }

  @Transactional
  public AppUser updateAdmin(Long id, String newUsername, String newRawPassword, Boolean enabled) {
    AppUser u = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Admin not found"));

    // Ensure target is admin
    if (u.getRoles() == null || !u.getRoles().contains(Role.ADMIN)) {
      throw new IllegalArgumentException("User is not an admin");
    }

    if (newUsername != null && !newUsername.isBlank() && !newUsername.equals(u.getUsername())) {
      if (repo.existsByUsername(newUsername)) {
        throw new IllegalArgumentException("Username already exists");
      }
      u.setUsername(newUsername);
    }

    if (newRawPassword != null && !newRawPassword.isBlank()) {
      u.setPasswordHash(encoder.encode(newRawPassword));
    }

    if (enabled != null) {
      u.setEnabled(enabled);
    }

    return repo.save(u);
  }

  @Transactional
  public void deleteAdmin(Long id) {
    AppUser u = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Admin not found"));
    if (u.getRoles() == null || !u.getRoles().contains(Role.ADMIN)) {
      throw new IllegalArgumentException("User is not an admin");
    }
    repo.delete(u);
  }
}