package com.nordicframtiden.gdpr;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GdprDeletionRequestRepository extends JpaRepository<GdprDeletionRequest, Long> {

  List<GdprDeletionRequest> findTop50ByOrderByCreatedAtDesc();

  List<GdprDeletionRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

  /** Queue for the execution job. */
  List<GdprDeletionRequest> findByStatusAndScheduledDateLessThanEqual(
      String status, java.time.LocalDate date);

  Optional<GdprDeletionRequest> findTopByUserIdAndStatusInOrderByCreatedAtDesc(
      Long userId, List<String> statuses);
}
