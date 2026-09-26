package com.nordicframtiden.service.model;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipSnapshotRepository extends JpaRepository<PayslipSnapshot, Long> {

  Optional<PayslipSnapshot> findByUserIdAndYearAndMonthAndRole(Long userId, Integer year, Integer month, String role);

  void deleteByUserIdAndYearAndMonthAndRole(Long userId, Integer year, Integer month, String role);
}
