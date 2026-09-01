package com.nordicframtiden.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.DayOfWeek;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

import com.nordicframtiden.company.StaffScheduleService;
import com.nordicframtiden.pharmacy.ScheduleService;
import com.nordicframtiden.security.service.UserService;
import com.nordicframtiden.service.model.NetSalaryResponse;

@Service
public class PayrollService {

  private final UserService userService;
  private final TaxService taxService;

  private final ScheduleService scheduleService;      // your existing
  private final StaffScheduleService staffScheduleService; // your existing

    

  public PayrollService(UserService userService, TaxService taxService, ScheduleService scheduleService,
        StaffScheduleService staffScheduleService) {
    this.userService = userService;
    this.taxService = taxService;
    this.scheduleService = scheduleService;
    this.staffScheduleService = staffScheduleService;
}
public NetSalaryResponse netSalaryForUserMonth(Long userId, int year, int month, String role) {
  if ("STAFF".equalsIgnoreCase(role)) {
    return netSalaryForStaffMonth(userId, year, month);
  }
  return netSalaryForUserMonth(userId, year, month); // your existing USER one
}

public NetSalaryResponse netSalaryForStaffMonth(Long userId, int year, int month) {
  // Same logic as netSalaryForUserMonth but ONLY use staffScheduleService shifts
  var user = userService.getDetailedById(userId);
  var profile = userService.getProfileByUserId(userId);

  var range = monthRangeUTC(year, month);
  var shifts = staffScheduleService.listForUser(userId, range.start(), range.end());

  BigDecimal totalHours = BigDecimal.ZERO;
  BigDecimal gross = BigDecimal.ZERO;
  for (var s : shifts) {
    Instant start = s.getStartAt().toInstant();
    Instant end = s.getEndAt().toInstant();
    totalHours = totalHours.add(hoursBetween(start, end));
    gross = gross.add(shiftGross(start, end, profile.getHourlyCost()));
  }

  int taxYear = year;
  int taxColumn = taxService.resolveTaxColumn(profile.getYearOfBirth(), taxYear);
  int tableNumber = taxService.resolveTableNumber(profile.getMunicipalityCode(), taxYear);

  int grossInt = gross.setScale(0, RoundingMode.HALF_UP).intValue();
  int taxInt = taxService.lookupPreliminaryTax(taxYear, tableNumber, taxColumn, grossInt);

  BigDecimal tax = BigDecimal.valueOf(taxInt);
  BigDecimal net = gross.subtract(tax);

  return new NetSalaryResponse(
      userId,
      String.format("%04d-%02d", year, month),
      profile.getHourlyCost(),
      totalHours.setScale(2, RoundingMode.HALF_UP),
      gross.setScale(2, RoundingMode.HALF_UP),
      taxYear,
      profile.getMunicipalityCode(),
      tableNumber,
      taxColumn,
      tax.setScale(2, RoundingMode.HALF_UP),
      net.setScale(2, RoundingMode.HALF_UP)
  );
}
  public NetSalaryResponse netSalaryForUserMonth(Long userId, int year, int month) {

    var user = userService.getDetailedById(userId);
    var profile = userService.getProfileByUserId(userId); // implement helper that returns UserProfile

    if (profile.getHourlyCost() == null) {
      throw new IllegalArgumentException("Hourly cost missing for user " + userId);
    }

    var range = monthRangeUTC(year, month);
    var shifts = scheduleService.listForUser(userId, range.start(), range.end());

    BigDecimal totalHours = BigDecimal.ZERO;
    BigDecimal gross = BigDecimal.ZERO;
    for (var s : shifts) {
      Instant start = s.getStartAt().toInstant();
      Instant end = s.getEndAt().toInstant();
      totalHours = totalHours.add(hoursBetween(start, end));
      gross = gross.add(shiftGross(start, end, profile.getHourlyCost()));
    }

    int taxYear = year;
    int taxColumn = taxService.resolveTaxColumn(profile.getYearOfBirth(), taxYear);
    int tableNumber = taxService.resolveTableNumber(profile.getMunicipalityCode(), taxYear);

    int grossInt = gross.setScale(0, RoundingMode.HALF_UP).intValue(); // match Skatteverket granularity
    int taxInt = taxService.lookupPreliminaryTax(taxYear, tableNumber, taxColumn, grossInt);

    BigDecimal tax = BigDecimal.valueOf(taxInt);
    BigDecimal net = gross.subtract(tax);

    return new NetSalaryResponse(
        userId,
        String.format("%04d-%02d", year, month),
        profile.getHourlyCost(),
        totalHours.setScale(2, RoundingMode.HALF_UP),
        gross.setScale(2, RoundingMode.HALF_UP),
        taxYear,
        profile.getMunicipalityCode(),
        tableNumber,
        taxColumn,
        tax.setScale(2, RoundingMode.HALF_UP),
        net.setScale(2, RoundingMode.HALF_UP)
    );
  }

  private record UtcRange(Instant start, Instant end) {}

  private UtcRange monthRangeUTC(int year, int month) {
    var start = LocalDate.of(year, month, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
    var end = LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return new UtcRange(start, end);
  }

  private BigDecimal hoursBetween(Instant start, Instant end) {
    if (start == null || end == null) return BigDecimal.ZERO;
    long ms = Duration.between(start, end).toMillis();
    if (ms <= 0) return BigDecimal.ZERO;
    return BigDecimal.valueOf(ms).divide(BigDecimal.valueOf(3600000), 6, RoundingMode.HALF_UP);
  }

  /**
   * Compute gross pay for a shift, applying day-of-week multipliers:
   * - Saturday: 1.5x
   * - Sunday: 2.0x
   * Splits a shift across UTC midnights to apply correct multipliers per calendar day.
   */
  private BigDecimal shiftGross(Instant start, Instant end, BigDecimal hourlyCost) {
    if (start == null || end == null) return BigDecimal.ZERO;
    if (!end.isAfter(start)) return BigDecimal.ZERO;

    BigDecimal gross = BigDecimal.ZERO;
    Instant cursor = start;
    while (cursor.isBefore(end)) {
      ZonedDateTime z = cursor.atZone(ZoneOffset.UTC);
      Instant nextMidnight = z.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
      Instant segmentEnd = end.isBefore(nextMidnight) ? end : nextMidnight;

      long ms = Duration.between(cursor, segmentEnd).toMillis();
      BigDecimal hours = BigDecimal.valueOf(ms).divide(BigDecimal.valueOf(3600000), 6, RoundingMode.HALF_UP);

      DayOfWeek dow = z.getDayOfWeek();
      BigDecimal multiplier = BigDecimal.ONE;
      if (dow == DayOfWeek.SATURDAY) multiplier = BigDecimal.valueOf(1.5);
      else if (dow == DayOfWeek.SUNDAY) multiplier = BigDecimal.valueOf(2.0);

      gross = gross.add(hourlyCost.multiply(hours).multiply(multiplier));

      cursor = segmentEnd;
    }

    return gross;
  }
}
