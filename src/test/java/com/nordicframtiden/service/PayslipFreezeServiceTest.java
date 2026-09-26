package com.nordicframtiden.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nordicframtiden.service.model.NetSalaryResponse;
import com.nordicframtiden.service.model.PayslipSnapshot;
import com.nordicframtiden.service.model.PayslipSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayslipFreezeServiceTest {

  private static final int ENDED_YEAR = 2026;
  private static final int ENDED_MONTH = 8;      // August 2026 is over
  private static final int CURRENT_MONTH = 9;    // September is running

  private final PayslipSnapshotRepository snapshots = mock(PayslipSnapshotRepository.class);
  private final PayrollService payrollService = mock(PayrollService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private PayslipFreezeService service;

  @BeforeEach
  void setUp() {
    service = new PayslipFreezeService(snapshots, payrollService, objectMapper);
  }

  private NetSalaryResponse payslip(BigDecimal hourlyCost) {
    return new NetSalaryResponse(
        7L, "2026-08", hourlyCost, BigDecimal.valueOf(100), BigDecimal.valueOf(20000),
        2026, "0180", 30, 1, BigDecimal.valueOf(6000), BigDecimal.valueOf(14000),
        BigDecimal.valueOf(6000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(240000),
        java.util.List.of(), BigDecimal.valueOf(20000), BigDecimal.ZERO, BigDecimal.ZERO);
  }

  @Test
  void currentMonthIsAlwaysLive() {
    when(payrollService.netSalaryForUserMonth(7L, ENDED_YEAR, CURRENT_MONTH))
        .thenReturn(payslip(BigDecimal.valueOf(1500)));

    NetSalaryResponse result = service.resolve(7L, ENDED_YEAR, CURRENT_MONTH, "USER");

    assertThat(result.hourlyCost()).isEqualByComparingTo(BigDecimal.valueOf(1500));
    verify(snapshots, never()).findByUserIdAndYearAndMonthAndRole(any(), any(), any(), any());
    verify(snapshots, never()).save(any());
  }

  @Test
  void endedMonthFreezesOnFirstReadAndIsServedVerbatimAfterwards() throws Exception {
    NetSalaryResponse computed = payslip(BigDecimal.valueOf(1500));
    when(payrollService.netSalaryForUserMonth(7L, ENDED_YEAR, ENDED_MONTH)).thenReturn(computed);
    when(snapshots.findByUserIdAndYearAndMonthAndRole(7L, ENDED_YEAR, ENDED_MONTH, "USER"))
        .thenReturn(Optional.empty())
        .thenAnswer(invocation -> {
          PayslipSnapshot stored = new PayslipSnapshot();
          stored.setUserId(7L);
          stored.setYear(ENDED_YEAR);
          stored.setMonth(ENDED_MONTH);
          stored.setRole("USER");
          // Deliberately a different hourly cost than live computation would
          // produce (rate changed afterwards): history must come back frozen.
          stored.setPayload(objectMapper.writeValueAsString(payslip(BigDecimal.valueOf(999))));
          return Optional.of(stored);
        });

    NetSalaryResponse firstRead = service.resolve(7L, ENDED_YEAR, ENDED_MONTH, "USER");
    assertThat(firstRead.hourlyCost()).isEqualByComparingTo(BigDecimal.valueOf(1500));
    verify(snapshots).save(any(PayslipSnapshot.class));

    // The stored snapshot deliberately differs (rate changed afterwards):
    // history must come back exactly as frozen, without recalculating.
    NetSalaryResponse secondRead = service.resolve(7L, ENDED_YEAR, ENDED_MONTH, "USER");
    assertThat(secondRead.hourlyCost()).isEqualByComparingTo(BigDecimal.valueOf(999));
    verify(payrollService, times(1)).netSalaryForUserMonth(7L, ENDED_YEAR, ENDED_MONTH);
  }

  @Test
  void savingAdjustmentsForEndedMonthPreservesHistoricalHourlyCost() throws Exception {
    PayslipSnapshot stored = new PayslipSnapshot();
    stored.setUserId(7L);
    stored.setYear(ENDED_YEAR);
    stored.setMonth(ENDED_MONTH);
    stored.setRole("USER");
    stored.setPayload(objectMapper.writeValueAsString(payslip(BigDecimal.valueOf(100))));

    when(snapshots.findByUserIdAndYearAndMonthAndRole(7L, ENDED_YEAR, ENDED_MONTH, "USER"))
        .thenReturn(Optional.of(stored));
    when(payrollService.netSalaryForUserMonth(7L, ENDED_YEAR, ENDED_MONTH))
        .thenReturn(payslip(BigDecimal.valueOf(1500)));

    NetSalaryResponse saved = service.afterAdjustmentsSaved(7L, ENDED_YEAR, ENDED_MONTH, "USER");

    assertThat(saved.hourlyCost()).isEqualByComparingTo(BigDecimal.valueOf(100));
    verify(snapshots).save(any(PayslipSnapshot.class));
  }

  @Test
  void savingAdjustmentsForCurrentMonthKeepsLiveHourlyCost() {
    when(payrollService.netSalaryForUserMonth(7L, ENDED_YEAR, CURRENT_MONTH))
        .thenReturn(payslip(BigDecimal.valueOf(1500)));

    NetSalaryResponse saved = service.afterAdjustmentsSaved(7L, ENDED_YEAR, CURRENT_MONTH, "USER");

    assertThat(saved.hourlyCost()).isEqualByComparingTo(BigDecimal.valueOf(1500));
    verify(snapshots).save(any(PayslipSnapshot.class));
  }

  @Test
  void corruptSnapshotIsRecomputedAndRefrozen() {
    PayslipSnapshot corrupt = new PayslipSnapshot();
    corrupt.setUserId(7L);
    corrupt.setYear(ENDED_YEAR);
    corrupt.setMonth(ENDED_MONTH);
    corrupt.setRole("USER");
    corrupt.setPayload("this is not json");

    when(snapshots.findByUserIdAndYearAndMonthAndRole(7L, ENDED_YEAR, ENDED_MONTH, "USER"))
        .thenReturn(Optional.of(corrupt));
    when(payrollService.netSalaryForUserMonth(7L, ENDED_YEAR, ENDED_MONTH))
        .thenReturn(payslip(BigDecimal.valueOf(1500)));

    NetSalaryResponse result = service.resolve(7L, ENDED_YEAR, ENDED_MONTH, "USER");

    assertThat(result.hourlyCost()).isEqualByComparingTo(BigDecimal.valueOf(1500));
    verify(snapshots).save(any(PayslipSnapshot.class));
  }

  @Test
  void staffRoleUsesStaffPayrollForEndedMonth() {
    when(payrollService.netSalaryForStaffMonth(7L, ENDED_YEAR, ENDED_MONTH))
        .thenReturn(payslip(BigDecimal.valueOf(1200)));
    when(snapshots.findByUserIdAndYearAndMonthAndRole(7L, ENDED_YEAR, ENDED_MONTH, "STAFF"))
        .thenReturn(Optional.empty());

    service.resolve(7L, ENDED_YEAR, ENDED_MONTH, "STAFF");

    verify(payrollService).netSalaryForStaffMonth(7L, ENDED_YEAR, ENDED_MONTH);
    verify(payrollService, never()).netSalaryForUserMonth(eq(7L), any(Integer.class), any(Integer.class));
  }
}
