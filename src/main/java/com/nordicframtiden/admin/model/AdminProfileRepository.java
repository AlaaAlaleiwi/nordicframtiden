package com.nordicframtiden.admin.model;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdminProfileRepository extends JpaRepository<AdminProfile, Long> {
  boolean existsByEmail(String email);

  boolean existsByPhone(String phone);

  Optional<AdminProfile> findByUserId(Long userId);

}