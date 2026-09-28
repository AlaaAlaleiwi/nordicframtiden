package com.nordicframtiden.gdpr;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GdprConsentRepository extends JpaRepository<GdprConsent, Long> {

  List<GdprConsent> findByUserIdOrderByCreatedAtDesc(Long userId);

  /** Latest event for a user+type — the current consent state. */
  Optional<GdprConsent> findTopByUserIdAndConsentTypeOrderByCreatedAtDesc(Long userId, String consentType);
}
