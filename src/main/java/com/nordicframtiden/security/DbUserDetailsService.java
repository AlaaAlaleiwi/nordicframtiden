package com.nordicframtiden.security;

import com.nordicframtiden.security.repo.AppUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class DbUserDetailsService implements UserDetailsService {

  private final AppUserRepository repo;

  public DbUserDetailsService(AppUserRepository repo) {
    this.repo = repo;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    var user = repo.findByUsername(username)
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

    var authorities = user.getRoles().stream()
        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
        .toList();

    return User.builder()
        .username(user.getUsername())
        .password(user.getPasswordHash())
        .disabled(!user.isEnabled())
        .authorities(authorities)
        .build();
  }
}