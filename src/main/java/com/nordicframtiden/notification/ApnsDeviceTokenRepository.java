package com.nordicframtiden.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApnsDeviceTokenRepository extends JpaRepository<ApnsDeviceToken, Long> {
  Optional<ApnsDeviceToken> findByToken(String token);
  List<ApnsDeviceToken> findByUserId(Long userId);
  void deleteByTokenAndUserId(String token, Long userId);
}
