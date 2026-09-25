package com.nordicframtiden.service.model;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SalaryAdjustmentRepository extends JpaRepository<SalaryAdjustment,Long> {
  List<SalaryAdjustment> findByUserIdAndYearAndMonthOrderById(Long userId,int year,int month);
  List<SalaryAdjustment> findByUserIdAndYear(Long userId,int year);
  void deleteByUserIdAndYearAndMonth(Long userId,int year,int month);
}
