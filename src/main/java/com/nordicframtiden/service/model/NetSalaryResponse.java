package com.nordicframtiden.service.model;

import java.math.BigDecimal;
import java.util.List;

public record NetSalaryResponse(
    Long userId,
    String monthKey,           // "2026-02"
    BigDecimal hourlyCost,
    BigDecimal totalHours,
    BigDecimal grossSalary,
    Integer taxYear,
    String municipalityCode,
    Integer tableNumber,
    Integer taxColumn,
    BigDecimal preliminaryTax,
    BigDecimal netSalary,
    BigDecimal regularTax,
    BigDecimal oneTimeTax,
    BigDecimal taxFreeAmount,
    BigDecimal projectedAnnualIncome,
    List<AdjustmentLine> adjustments
) {
  public NetSalaryResponse(Long userId,String monthKey,BigDecimal hourlyCost,BigDecimal totalHours,BigDecimal grossSalary,Integer taxYear,String municipalityCode,Integer tableNumber,Integer taxColumn,BigDecimal preliminaryTax,BigDecimal netSalary) {
    this(userId,monthKey,hourlyCost,totalHours,grossSalary,taxYear,municipalityCode,tableNumber,taxColumn,preliminaryTax,netSalary,preliminaryTax,BigDecimal.ZERO,BigDecimal.ZERO,grossSalary.multiply(BigDecimal.valueOf(12)),List.of());
  }
  public record AdjustmentLine(Long id,String name,BigDecimal amount,SalaryAdjustment.TaxTreatment taxTreatment){}
}
