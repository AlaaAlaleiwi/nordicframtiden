package com.nordicframtiden.api;

import com.nordicframtiden.pharmacy.ScheduleShift;
import com.nordicframtiden.pharmacy.ScheduleShiftRepository;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/payslips")
@PreAuthorize("hasAnyRole('USER','STAFF','ADMIN')")
public class PayslipController {

  private final ScheduleShiftRepository shiftRepo;
  private final AppUserRepository userRepo;
  private final UserProfileRepository profileRepo;

  public PayslipController(ScheduleShiftRepository shiftRepo,
                           AppUserRepository userRepo,
                           UserProfileRepository profileRepo) {
    this.shiftRepo = shiftRepo;
    this.userRepo = userRepo;
    this.profileRepo = profileRepo;
  }

  // -------- DTOs (simple Swedish-style) --------

  public record Period(int year, int month) {}

  public record Employee(Long id, String username, String fullName, String email, String phone) {}

  public record PayslipLine(
      String code,      // e.g. "TIMLON"
      String label,     // e.g. "Timlön"
      double qty,       // hours
      String unit,      // "h"
      BigDecimal rate,  // hourly
      BigDecimal amount // gross for this line
  ) {}

  public record Totals(double hours, BigDecimal gross, BigDecimal tax, BigDecimal net) {}

  public record Ytd(double hours, BigDecimal gross, BigDecimal tax, BigDecimal net) {}

  public record Meta(String payslipNo, LocalDate payDate) {}

  public record PayslipResponse(
      Period period,
      Employee employee,
      List<PayslipLine> lines,
      Totals totals,
      Ytd ytd,
      Meta meta
  ) {}

  // -------- Endpoint --------
  // GET /api/payslips/me?year=2026&month=2
  @GetMapping("/me")
  public PayslipResponse me(@RequestParam int year,
                            @RequestParam int month,
                            Authentication auth) {

    if (month < 1 || month > 12) throw new IllegalArgumentException("month must be 1..12");

    String username = auth.getName();
    AppUser user = userRepo.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

    var profile = profileRepo.findByUserId(user.getId()).orElse(null);

    String fullName = profile != null ? profile.getFullName() : user.getUsername();
    String email = profile != null ? profile.getEmail() : null;
    String phone = profile != null ? profile.getPhone() : null;

    BigDecimal hourly = (profile != null && profile.getHourlyCost() != null)
        ? profile.getHourlyCost()
        : BigDecimal.ZERO;

    // Month range (UTC-based)
    OffsetDateTime monthStart = OffsetDateTime.of(LocalDate.of(year, month, 1), LocalTime.MIDNIGHT, ZoneOffset.UTC);
    OffsetDateTime monthEnd = (month == 12)
        ? OffsetDateTime.of(LocalDate.of(year + 1, 1, 1), LocalTime.MIDNIGHT, ZoneOffset.UTC)
        : OffsetDateTime.of(LocalDate.of(year, month + 1, 1), LocalTime.MIDNIGHT, ZoneOffset.UTC);

    // YTD range (Jan -> end of selected month)
    OffsetDateTime ytdStart = OffsetDateTime.of(LocalDate.of(year, 1, 1), LocalTime.MIDNIGHT, ZoneOffset.UTC);
    OffsetDateTime ytdEnd = monthEnd;

    // Load shifts
    List<ScheduleShift> monthShifts = shiftRepo.findInRange(monthStart, monthEnd, null, user.getId());
    List<ScheduleShift> ytdShifts = shiftRepo.findInRange(ytdStart, ytdEnd, null, user.getId());

    // Compute hours
    double monthHours = monthShifts.stream()
        .filter(s -> s.getStartAt() != null && s.getEndAt() != null)
        .mapToDouble(s -> Duration.between(s.getStartAt(), s.getEndAt()).toMinutes() / 60.0)
        .sum();

    double ytdHours = ytdShifts.stream()
        .filter(s -> s.getStartAt() != null && s.getEndAt() != null)
        .mapToDouble(s -> Duration.between(s.getStartAt(), s.getEndAt()).toMinutes() / 60.0)
        .sum();

    // Money
    BigDecimal gross = hourly.multiply(BigDecimal.valueOf(monthHours)).setScale(2, RoundingMode.HALF_UP);
    BigDecimal tax = gross.multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP);
    BigDecimal net = gross.subtract(tax).setScale(2, RoundingMode.HALF_UP);

    BigDecimal ytdGross = hourly.multiply(BigDecimal.valueOf(ytdHours)).setScale(2, RoundingMode.HALF_UP);
    BigDecimal ytdTax = ytdGross.multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP);
    BigDecimal ytdNet = ytdGross.subtract(ytdTax).setScale(2, RoundingMode.HALF_UP);

    // Swedish-like payslip number & payday (example: 25th)
    String payslipNo = String.format("%04d-%02d-%06d", year, month, user.getId());
    LocalDate payDate = LocalDate.of(year, month, Math.min(25, Month.of(month).length(Year.isLeap(year))));

    // One salary row for now (you can add OB/helg later)
    List<PayslipLine> lines = List.of(
        new PayslipLine("TIMLON", "Timlön", round2(monthHours), "h", hourly.setScale(2, RoundingMode.HALF_UP), gross)
    );

    return new PayslipResponse(
        new Period(year, month),
        new Employee(user.getId(), user.getUsername(), fullName, email, phone),
        lines,
        new Totals(round2(monthHours), gross, tax, net),
        new Ytd(round2(ytdHours), ytdGross, ytdTax, ytdNet),
        new Meta(payslipNo, payDate)
    );
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }
}