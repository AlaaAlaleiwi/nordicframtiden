package com.nordicframtiden.security.repo;

import com.nordicframtiden.security.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

  Optional<UserProfile> findByUserId(Long userId);

  boolean existsByEmail(String email);
  boolean existsByPhone(String phone);
}