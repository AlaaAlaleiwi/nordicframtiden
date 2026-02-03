package com.nordicframtiden.service.model;

import java.math.BigDecimal;

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
    BigDecimal netSalary
) {}
