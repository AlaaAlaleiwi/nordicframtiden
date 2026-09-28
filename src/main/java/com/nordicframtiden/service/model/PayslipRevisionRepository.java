package com.nordicframtiden.service.model;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipRevisionRepository extends JpaRepository<PayslipRevision, Long> {
  /** Signed corrections feed future draft annual estimates without rewriting historical inputs. */
  @org.springframework.data.jpa.repository.Query(value = """
      select coalesce(sum(cast(cast(r.changes as jsonb) ->> 'oneTimeGrossDelta' as numeric)), 0)
      from payslip_revision r join payslip_snapshot s on s.id = r.snapshot_id
      where s.user_id = :userId and s.year = :year and s.month <> :excludedMonth
      """, nativeQuery = true)
  java.math.BigDecimal annualOneTimeCorrectionTotal(@org.springframework.data.repository.query.Param("userId") Long userId,
      @org.springframework.data.repository.query.Param("year") int year,
      @org.springframework.data.repository.query.Param("excludedMonth") int excludedMonth);

  List<PayslipRevision> findBySnapshotIdOrderByRevisionAsc(Long snapshotId);
  Optional<PayslipRevision> findTopBySnapshotIdOrderByRevisionDesc(Long snapshotId);
}
