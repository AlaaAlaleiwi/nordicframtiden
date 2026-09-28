package com.nordicframtiden.service.model;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipRevisionRepository extends JpaRepository<PayslipRevision, Long> {
  List<PayslipRevision> findBySnapshotIdOrderByRevisionAsc(Long snapshotId);
  Optional<PayslipRevision> findTopBySnapshotIdOrderByRevisionDesc(Long snapshotId);
}
