package com.nordicframtiden.security.service;

import com.nordicframtiden.security.model.*;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class UserService {
  private final AppUserRepository repo;
  private final PasswordEncoder encoder;

  public UserService(AppUserRepository repo, PasswordEncoder encoder) {
    this.repo = repo;
    this.encoder = encoder;
  }

  public AppUser create(String username, String rawPassword, Set<Role> roles) {
    AppUser u = new AppUser();
    u.setUsername(username);
    u.setPasswordHash(encoder.encode(rawPassword));
    u.setRoles(roles);
    return repo.save(u);
  }
}