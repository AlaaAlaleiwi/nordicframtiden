package com.nordicframtiden.service;

import com.nordicframtiden.company.StaffScheduleService;
import com.nordicframtiden.pharmacy.ScheduleService;
import com.nordicframtiden.pharmacy.ScheduleShift;
import com.nordicframtiden.security.model.UserProfile;
import com.nordicframtiden.security.service.UserService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayrollServiceTest {

  @Test
  void pharmacistPayslipShowsSaturdayAndSundayObSeparately() {
    UserService userService = mock(UserService.class);
    TaxService taxService = mock(TaxService.class);
    ScheduleService scheduleService = mock(ScheduleService.class);
    StaffScheduleService staffScheduleService = mock(StaffScheduleService.class);
    SalaryAdjustmentService adjustmentService = mock(SalaryAdjustmentService.class);
    OneTimeTaxService oneTimeTaxService = mock(OneTimeTaxService.class);
    PayrollService payrollService = new PayrollService(
        userService, taxService, scheduleService, staffScheduleService, adjustmentService, oneTimeTaxService);

    UserProfile profile = new UserProfile();
    profile.setHourlyCost(BigDecimal.valueOf(200));
    profile.setYearOfBirth(1990);
    profile.setMunicipalityCode("0180");
    when(userService.getProfileByUserId(7L)).thenReturn(profile);

    ScheduleShift pharmacistShift = new ScheduleShift();
    pharmacistShift.setStartAt(OffsetDateTime.parse("2026-08-03T08:00:00Z"));
    pharmacistShift.setEndAt(OffsetDateTime.parse("2026-08-03T16:00:00Z"));
    ScheduleShift saturday = new ScheduleShift();
    saturday.setStartAt(OffsetDateTime.parse("2026-08-08T08:00:00Z"));
    saturday.setEndAt(OffsetDateTime.parse("2026-08-08T16:00:00Z"));
    ScheduleShift sunday = new ScheduleShift();
    sunday.setStartAt(OffsetDateTime.parse("2026-08-09T08:00:00Z"));
    sunday.setEndAt(OffsetDateTime.parse("2026-08-09T16:00:00Z"));
    when(scheduleService.listForUser(eq(7L), any(), any()))
        .thenReturn(List.of(pharmacistShift, saturday, sunday));
    when(adjustmentService.forMonth(7L, 2026, 8)).thenReturn(List.of());
    when(adjustmentService.annualOneTimeTotal(7L, 2026)).thenReturn(BigDecimal.ZERO);

    when(taxService.resolveTaxColumn(1990, 2026)).thenReturn(1);
    when(taxService.resolveTableNumber("0180", 2026)).thenReturn(30);
    when(taxService.lookupPreliminaryTax(2026, 30, 1, 7200)).thenReturn(0);

    var payslip = payrollService.netSalaryForUserMonth(7L, 2026, 8);

    assertEquals(new BigDecimal("24.00"), payslip.totalHours());
    assertEquals(new BigDecimal("4800.00"), payslip.baseHourlySalary());
    assertEquals(new BigDecimal("800.00"), payslip.saturdayOb());
    assertEquals(new BigDecimal("1600.00"), payslip.sundayOb());
    assertEquals(new BigDecimal("7200.00"), payslip.grossSalary());
    verify(staffScheduleService, never()).listForUser(eq(7L), any(), any());
  }
}
