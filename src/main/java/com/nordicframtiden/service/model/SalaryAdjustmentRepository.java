package com.nordicframtiden.service.model;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SalaryAdjustmentRepository extends JpaRepository<SalaryAdjustment,Long> {
  List<SalaryAdjustment> findByUserId(Long userId);
  List<SalaryAdjustment> findByUserIdAndYearAndMonthOrderById(Long userId,int year,int month);
  /** Every adjustment in one work month, across users (monthly salary totals). */
  List<SalaryAdjustment> findByYearAndMonthOrderById(int year,int month);
  List<SalaryAdjustment> findByUserIdAndYear(Long userId,int year);
  void deleteByUserIdAndYearAndMonth(Long userId,int year,int month);
  List<SalaryAdjustment> findByUserIdAndYearAndMonthNot(Long userId,int year,int month);
}
