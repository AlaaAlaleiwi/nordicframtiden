package com.nordicframtiden.gdpr;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GdprExportRequestRepository extends JpaRepository<GdprExportRequest, Long> {

  /** Queue for the nightly job, oldest first. */
  List<GdprExportRequest> findByStatusOrderByCreatedAtAsc(String status);

  /** Latest request per user (newest first) for status display. */
  List<GdprExportRequest> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
