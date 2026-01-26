package com.nordicframtiden.security.repo;

import com.nordicframtiden.security.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
  Optional<AppUser> findByUsername(String username);

  boolean existsByUsername(String username);

  @Query("select u from AppUser u join u.roles r where r = com.nordicframtiden.security.model.Role.ADMIN")
  List<AppUser> findAllAdmins();
}