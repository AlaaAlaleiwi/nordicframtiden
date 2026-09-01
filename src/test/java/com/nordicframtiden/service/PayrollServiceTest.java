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
  void pharmacistPayslipCountsOnlyPharmacistScheduleHours() {
    UserService userService = mock(UserService.class);
    TaxService taxService = mock(TaxService.class);
    ScheduleService scheduleService = mock(ScheduleService.class);
    StaffScheduleService staffScheduleService = mock(StaffScheduleService.class);
    PayrollService payrollService = new PayrollService(
        userService, taxService, scheduleService, staffScheduleService);

    UserProfile profile = new UserProfile();
    profile.setHourlyCost(BigDecimal.valueOf(200));
    profile.setYearOfBirth(1990);
    profile.setMunicipalityCode("0180");
    when(userService.getProfileByUserId(7L)).thenReturn(profile);

    ScheduleShift pharmacistShift = new ScheduleShift();
    pharmacistShift.setStartAt(OffsetDateTime.parse("2026-08-03T08:00:00Z"));
    pharmacistShift.setEndAt(OffsetDateTime.parse("2026-08-03T16:00:00Z"));
    when(scheduleService.listForUser(eq(7L), any(), any()))
        .thenReturn(List.of(pharmacistShift));

    when(taxService.resolveTaxColumn(1990, 2026)).thenReturn(1);
    when(taxService.resolveTableNumber("0180", 2026)).thenReturn(30);
    when(taxService.lookupPreliminaryTax(2026, 30, 1, 1600)).thenReturn(0);

    var payslip = payrollService.netSalaryForUserMonth(7L, 2026, 8);

    assertEquals(new BigDecimal("8.00"), payslip.totalHours());
    assertEquals(new BigDecimal("1600.00"), payslip.grossSalary());
    verify(staffScheduleService, never()).listForUser(eq(7L), any(), any());
  }
}
