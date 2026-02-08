package com.nordicframtiden.api;

import com.nordicframtiden.company.StaffShift;
import com.nordicframtiden.company.StaffShiftRepository;
import com.nordicframtiden.pharmacy.ScheduleShift;
import com.nordicframtiden.pharmacy.ScheduleShiftRepository;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import com.nordicframtiden.service.PayrollService;
import com.nordicframtiden.service.model.NetSalaryResponse;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/salaries")
@PreAuthorize("hasAnyRole('ADMIN','USER','STAFF')")
public class SalariesController {

  private final ScheduleShiftRepository shiftRepo;
  private final StaffShiftRepository staffShiftRepo;
  private final UserProfileRepository profileRepo;
  private final AppUserRepository userRepo;
private final PayrollService payrollService;
  public SalariesController(
      ScheduleShiftRepository shiftRepo,
      StaffShiftRepository staffShiftRepo,
      UserProfileRepository profileRepo,
      AppUserRepository userRepo,
      PayrollService payrollService
  ) {
    this.shiftRepo = shiftRepo;
    this.staffShiftRepo = staffShiftRepo;
    this.profileRepo = profileRepo;
    this.userRepo = userRepo;
    this.payrollService = payrollService;
  }

  /* ===================== DTOs ===================== */

  public record ShiftLine(
      Long shiftId,
      Long pharmacyId,
      String pharmacyName,
      Long userId,
      String userFullName,
      OffsetDateTime startAt,
      OffsetDateTime endAt,
      double hours,
      BigDecimal hourlyCost,
      BigDecimal cost
  ) {}

  public record UserSummary(
      Long userId,
      String fullName,
      double hours,
      BigDecimal hourlyCost,
      BigDecimal totalCost
  ) {}

  public record PharmacySummary(
      Long pharmacyId,
      String pharmacyName,
      double totalHours,
      BigDecimal totalCost,
      List<UserSummary> users
  ) {}

  public record YearRow(int year) {}
  public record MonthRow(int year, int month, double totalHours, BigDecimal totalCost) {}
  public record DayRow(String dayKey, OffsetDateTime from, OffsetDateTime to, double totalHours, BigDecimal totalCost) {}
// ===== Payslip DTO (what frontend expects) =====


 
// ===== /api/salaries/payslip/me (USER/STAFF/ADMIN) =====
 
// ===== /api/salaries/payslip?userId= (ADMIN only) =====
@GetMapping("/payslip")
 
  public NetSalaryResponse payslipForUser(
      @RequestParam Long userId,
      @RequestParam int year,
      @RequestParam int month
  ) {
    return payrollService.netSalaryForUserMonth(userId, year, month);
  }
  /* ===================== PAYSLIP (ME) ===================== */
@GetMapping("/payslip/staff")
public NetSalaryResponse payslipForStaff(
    @RequestParam Long userId,
    @RequestParam int year,
    @RequestParam int month
) {
  return payrollService.netSalaryForStaffMonth(userId, year, month);
}
  // GET /api/salaries/payslip/me?year=2026&month=3
  @GetMapping("/payslip/me")
  public NetSalaryResponse payslipMe(
      @RequestParam int year,
      @RequestParam int month,
      Authentication auth
  ) {
    String username = auth.getName();
    Long userId = userRepo.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("User not found: " + username))
        .getId();

    return payrollService.netSalaryForUserMonth(userId, year, month);
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }

  /* ===================== MONTH SUMMARY ===================== */

  @GetMapping("/month")
  public List<PharmacySummary> monthly(
      @RequestParam OffsetDateTime start,
      @RequestParam OffsetDateTime end,
      @RequestParam(defaultValue = "USER") String role
  ) {
    List<ShiftLine> lines = "STAFF".equalsIgnoreCase(role)
        ? staffShiftRepo.findInRange(start, end, null).stream().map(this::toStaffLine).toList()
        : shiftRepo.findInRange(start, end, null, null).stream().map(this::toUserLine).toList();

    return summarizeByPharmacy(lines);
  }

  /* ===================== REPORT ===================== */

  @GetMapping("/report")
  public List<ShiftLine> report(
      @RequestParam OffsetDateTime start,
      @RequestParam OffsetDateTime end,
      @RequestParam(required = false) Long pharmacyId,
      @RequestParam(required = false) Long userId,
      @RequestParam(defaultValue = "USER") String role
  ) {
    if ("STAFF".equalsIgnoreCase(role)) {
      // staff has no pharmacy -> ignore pharmacyId
      return staffShiftRepo.findInRange(start, end, userId)
          .stream().map(this::toStaffLine).toList();
    }

    if (pharmacyId == null)
      throw new IllegalArgumentException("pharmacyId is required for USER report");

    return shiftRepo.findInRange(start, end, pharmacyId, userId)
        .stream().map(this::toUserLine).toList();
  }

  /* ===================== LAZY USER VIEW ===================== */

  @GetMapping("/user/years")
  public List<YearRow> userYears(@RequestParam Long userId) {
    return shiftRepo.findInRange(
            OffsetDateTime.parse("2000-01-01T00:00:00Z"),
            OffsetDateTime.now().plusYears(1),
            null,
            userId
        ).stream()
        .map(s -> s.getStartAt().getYear())
        .distinct()
        .sorted(Comparator.reverseOrder())
        .map(YearRow::new)
        .toList();
  }

  @GetMapping("/user/months")
  public List<MonthRow> userMonths(@RequestParam Long userId, @RequestParam int year) {
    OffsetDateTime start = OffsetDateTime.parse(year + "-01-01T00:00:00Z");
    OffsetDateTime end = OffsetDateTime.parse((year + 1) + "-01-01T00:00:00Z");

    return shiftRepo.findInRange(start, end, null, userId)
        .stream()
        .map(this::toUserLine)
        .collect(Collectors.groupingBy(l -> l.startAt().getMonthValue()))
        .entrySet().stream()
        .map(e -> new MonthRow(
            year,
            e.getKey(),
            e.getValue().stream().mapToDouble(ShiftLine::hours).sum(),
            e.getValue().stream().map(ShiftLine::cost).reduce(BigDecimal.ZERO, BigDecimal::add)
        ))
        .sorted((a, b) -> b.month() - a.month())
        .toList();
  }

  @GetMapping("/user/month")
  public List<DayRow> userMonthDays(@RequestParam Long userId, @RequestParam int year, @RequestParam int month) {
    String mm = String.format("%02d", month);
    OffsetDateTime start = OffsetDateTime.parse(year + "-" + mm + "-01T00:00:00Z");
    OffsetDateTime end = month == 12
        ? OffsetDateTime.parse((year + 1) + "-01-01T00:00:00Z")
        : OffsetDateTime.parse(year + "-" + String.format("%02d", month + 1) + "-01T00:00:00Z");

    return shiftRepo.findInRange(start, end, null, userId)
        .stream().map(this::toUserLine)
        .collect(Collectors.groupingBy(l -> l.startAt().toLocalDate().toString()))
        .entrySet().stream()
        .map(e -> new DayRow(
            e.getKey(),
            e.getValue().stream().map(ShiftLine::startAt).min(Comparator.naturalOrder()).orElse(null),
            e.getValue().stream().map(ShiftLine::endAt).max(Comparator.naturalOrder()).orElse(null),
            e.getValue().stream().mapToDouble(ShiftLine::hours).sum(),
            e.getValue().stream().map(ShiftLine::cost).reduce(BigDecimal.ZERO, BigDecimal::add)
        ))
        .sorted((a, b) -> b.dayKey().compareTo(a.dayKey()))
        .toList();
  }

  /* ===================== HELPERS ===================== */

  private ShiftLine toUserLine(ScheduleShift s) {
    var p = s.getPharmacy();
    var u = s.getUser();
    var profile = profileRepo.findByUserId(u.getId()).orElse(null);

    BigDecimal hourly = s.getHourlyCostSnapshot() != null
        ? s.getHourlyCostSnapshot()
        : (profile != null && profile.getHourlyCost() != null ? profile.getHourlyCost() : BigDecimal.ZERO);

    double hours = Duration.between(s.getStartAt(), s.getEndAt()).toMinutes() / 60.0;
    BigDecimal cost = hourly.multiply(BigDecimal.valueOf(hours));

    String fullName = (profile != null && profile.getFullName() != null && !profile.getFullName().isBlank())
        ? profile.getFullName()
        : u.getUsername();

    return new ShiftLine(
        s.getId(), p.getId(), p.getName(),
        u.getId(), fullName,
        s.getStartAt(), s.getEndAt(),
        hours, hourly, cost
    );
  }

  private ShiftLine toStaffLine(StaffShift s) {
    var u = s.getUser();
    var profile = profileRepo.findByUserId(u.getId()).orElse(null);

    BigDecimal hourly = (profile != null && profile.getHourlyCost() != null)
        ? profile.getHourlyCost()
        : BigDecimal.ZERO;

    double hours = Duration.between(s.getStartAt(), s.getEndAt()).toMinutes() / 60.0;
    BigDecimal cost = hourly.multiply(BigDecimal.valueOf(hours));

    String fullName = (profile != null && profile.getFullName() != null && !profile.getFullName().isBlank())
        ? profile.getFullName()
        : u.getUsername();

    return new ShiftLine(
        s.getId(), 0L, "Staff",
        u.getId(), fullName,
        s.getStartAt(), s.getEndAt(),
        hours, hourly, cost
    );
  }

  private List<PharmacySummary> summarizeByPharmacy(List<ShiftLine> lines) {
    Map<Long, List<ShiftLine>> byPharmacy = lines.stream()
        .collect(Collectors.groupingBy(ShiftLine::pharmacyId));

    List<PharmacySummary> out = new ArrayList<>();

    for (var entry : byPharmacy.entrySet()) {
      List<ShiftLine> pLines = entry.getValue();
      String pharmacyName = pLines.get(0).pharmacyName();

      Map<Long, List<ShiftLine>> byUser = pLines.stream()
          .collect(Collectors.groupingBy(ShiftLine::userId));

      List<UserSummary> users = new ArrayList<>();
      double totalHours = 0;
      BigDecimal totalCost = BigDecimal.ZERO;

      for (var ue : byUser.entrySet()) {
        List<ShiftLine> uLines = ue.getValue();
        String fullName = uLines.get(0).userFullName();

        double hours = uLines.stream().mapToDouble(ShiftLine::hours).sum();
        BigDecimal cost = uLines.stream().map(ShiftLine::cost).reduce(BigDecimal.ZERO, BigDecimal::add);

        // ✅ weighted hourly avg
        BigDecimal hourly = hours > 0
            ? cost.divide(BigDecimal.valueOf(hours), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        users.add(new UserSummary(ue.getKey(), fullName, hours, hourly, cost));

        totalHours += hours;
        totalCost = totalCost.add(cost);
      }

      users.sort((a, b) -> b.totalCost().compareTo(a.totalCost()));
      out.add(new PharmacySummary(entry.getKey(), pharmacyName, totalHours, totalCost, users));
    }

    out.sort((a, b) -> b.totalCost().compareTo(a.totalCost()));
    return out;
  }
}