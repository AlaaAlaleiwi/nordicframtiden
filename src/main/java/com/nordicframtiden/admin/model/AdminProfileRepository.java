package com.nordicframtiden.security.repo;

import com.nordicframtiden.security.model.AdminProfile;
import com.nordicframtiden.security.model.AppUser;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminProfileRepository extends JpaRepository<AdminProfile, Long> {
  boolean existsByEmail(String email);
  boolean existsByPhone(String phone);
  Optional<AdminProfile> findByUserId(Long userId);

}