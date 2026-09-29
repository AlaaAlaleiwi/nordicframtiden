package com.nordicframtiden.api;

import com.nordicframtiden.company.StaffShift;
import com.nordicframtiden.company.StaffShiftRepository;
import com.nordicframtiden.pharmacy.ScheduleShift;
import com.nordicframtiden.pharmacy.ScheduleShiftRepository;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import com.nordicframtiden.service.PayrollService;
import com.nordicframtiden.service.PayslipDeliveryService;
import com.nordicframtiden.service.PayslipFreezeService;
import com.nordicframtiden.service.SalaryAdjustmentService;
import com.nordicframtiden.service.model.NetSalaryResponse;
import com.nordicframtiden.settings.EmailService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
public class SalariesController {

  private static final String CAN_MANAGE_SALARIES =
      "hasRole('ADMIN') or hasAuthority('PERM_SALARIES')";

  private final ScheduleShiftRepository shiftRepo;
  private final StaffShiftRepository staffShiftRepo;
  private final UserProfileRepository profileRepo;
  private final AppUserRepository userRepo;
  private final PayrollService payrollService;
  private final PayslipFreezeService payslipFreezeService;
  private final EmailService emailService;
  private final SalaryAdjustmentService adjustmentService;
  private final PayslipDeliveryService payslipDeliveryService;

  public SalariesController(
      ScheduleShiftRepository shiftRepo,
      StaffShiftRepository staffShiftRepo,
      UserProfileRepository profileRepo,
      AppUserRepository userRepo,
      PayrollService payrollService,
      PayslipFreezeService payslipFreezeService,
      EmailService emailService, SalaryAdjustmentService adjustmentService,
      PayslipDeliveryService payslipDeliveryService
  ) {
    this.shiftRepo = shiftRepo;
    this.staffShiftRepo = staffShiftRepo;
    this.profileRepo = profileRepo;
    this.userRepo = userRepo;
    this.payrollService = payrollService;
    this.payslipFreezeService = payslipFreezeService;
    this.emailService = emailService;
    this.adjustmentService = adjustmentService;
    this.payslipDeliveryService = payslipDeliveryService;
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
  public record AdjustmentRequest(List<SalaryAdjustmentService.AdjustmentInput> adjustments) {}

  @PutMapping("/payslip/adjustments")
  @PreAuthorize(CAN_MANAGE_SALARIES)
  public NetSalaryResponse saveAdjustments(@RequestParam Long userId,@RequestParam int year,@RequestParam int month,@RequestParam(defaultValue="USER") String role,@RequestBody AdjustmentRequest request){
    return payslipFreezeService.saveAdjustments(userId, year, month, role, request.adjustments());
  }
  /** Live preview: computes the payslip with unsaved hourly cost / adjustment overrides. Nothing is persisted. */
  @PostMapping("/payslip/preview")
  @PreAuthorize(CAN_MANAGE_SALARIES)
  public NetSalaryResponse previewPayslip(
      @RequestParam Long userId,
      @RequestParam int year,
      @RequestParam int month,
      @RequestParam(defaultValue = "USER") String role,
      @RequestBody(required = false) PayrollService.PreviewRequest request
  ) {
    PayrollService.PreviewRequest body =
        request == null ? new PayrollService.PreviewRequest(null, null) : request;
    return payslipFreezeService.preview(userId, year, month, role, body);
  }
  @GetMapping("/payslip/revisions")
  @PreAuthorize(CAN_MANAGE_SALARIES)
  public List<PayslipFreezeService.Revision> revisions(@RequestParam Long userId, @RequestParam int year,
      @RequestParam int month, @RequestParam(defaultValue = "USER") String role) {
    return payslipFreezeService.history(userId, year, month, role);
  }

  @PostMapping("/payslip/finalize")
  @PreAuthorize(CAN_MANAGE_SALARIES)
  public PayslipFreezeService.Revision finalizePayslip(@RequestParam Long userId, @RequestParam int year,
      @RequestParam int month, @RequestParam(defaultValue = "USER") String role, Authentication auth) {
    return payslipFreezeService.finalizePayslip(userId, year, month, role, auth.getName());
  }

  /**
   * Reopen an accidentally finalized payslip as a draft (removes the snapshot
   * + revisions). Allowed only for the current month, or the previous month
   * through the 20th — the same windows as editing.
   */
  @DeleteMapping("/payslip/finalize")
  @PreAuthorize(CAN_MANAGE_SALARIES)
  public ResponseEntity<Void> unfinalizePayslip(@RequestParam Long userId,
      @RequestParam int year, @RequestParam int month, @RequestParam(defaultValue = "USER") String role) {
    payslipFreezeService.unfinalize(userId, year, month, role);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/payslip/corrections")
  @PreAuthorize(CAN_MANAGE_SALARIES)
  public PayslipFreezeService.Revision correctPayslip(@RequestParam Long userId, @RequestParam int year,
      @RequestParam int month, @RequestParam(defaultValue = "USER") String role,
      @RequestBody PayslipFreezeService.Correction correction, Authentication auth) {
    return payslipFreezeService.correct(userId, year, month, role, correction, auth.getName());
  }

  public record MonthRow(int year, int month, double totalHours, BigDecimal totalCost) {}
  public record DayRow(String dayKey, OffsetDateTime from, OffsetDateTime to, double totalHours, BigDecimal totalCost) {}
// ===== Payslip DTO (what frontend expects) =====


 
// ===== /api/salaries/payslip/me (USER/STAFF/ADMIN) =====
 
// ===== /api/salaries/payslip?userId= (ADMIN only) =====
@GetMapping("/payslip")
  @PreAuthorize(CAN_MANAGE_SALARIES)
  public NetSalaryResponse payslipForUser(
      @RequestParam Long userId,
      @RequestParam int year,
      @RequestParam int month
  ) {
    // Finalized months resolve to their latest immutable revision.
    return payslipFreezeService.resolve(userId, year, month, "USER");
  }
  /* ===================== PAYSLIP (ME) ===================== */
@GetMapping("/payslip/staff")
@PreAuthorize(CAN_MANAGE_SALARIES)
public NetSalaryResponse payslipForStaff(
    @RequestParam Long userId,
    @RequestParam int year,
    @RequestParam int month
) {
  return payslipFreezeService.resolve(userId, year, month, "STAFF");
}
  // GET /api/salaries/payslip/me?year=2026&month=3
  @GetMapping("/payslip/me")
  @PreAuthorize("isAuthenticated()")
  public NetSalaryResponse payslipMe(
      @RequestParam int year,
      @RequestParam int month,
      Authentication auth
  ) {
    return payslipFreezeService.resolve(currentUserId(auth), year, month, "USER");
  }

  /**
   * Self-service readiness info for the salary screens: when the previous
   * work month's payslip was automatically emailed/announced (the 21st or
   * previous working day), and which work month was delivered last.
 */
  @GetMapping("/payslip/ready-status")
  @PreAuthorize("isAuthenticated()")
  public Map<String, Object> payslipReadyStatus(Authentication auth) {
    java.time.LocalDate today = java.time.LocalDate.now();
    java.time.YearMonth payoutMonth = java.time.YearMonth.from(today);
    java.time.YearMonth workMonth = payoutMonth.minusMonths(1);
    java.time.LocalDate readyDate = payslipDeliveryService.readyDateFor(payoutMonth);

    boolean ready = !today.isBefore(readyDate);
    var delivered = payslipDeliveryService.lastDelivered(currentUserId(auth), "USER");

    return Map.of(
        "ready", ready,
        "readyDate", readyDate.toString(),
        "workMonth", workMonth.toString(),
        "lastDeliveredMonth", delivered.map(java.time.YearMonth::toString).orElse(null)
    );
  }

  /* ===================== Admin audit + resend ===================== */

  /** Audit listing: every auto-delivery row for one work month. */
  @GetMapping("/payslip/deliveries")
  @PreAuthorize(CAN_MANAGE_SALARIES)
  public List<PayslipDeliveryService.DeliveryRow> payslipDeliveries(
      @RequestParam int year, @RequestParam int month) {
    return payslipDeliveryService.deliveriesForMonth(year, month);
  }

  /** Re-queues one finished/failed delivery and sends it immediately. */
  @PostMapping("/payslip/deliveries/{id}/resend")
  @PreAuthorize(CAN_MANAGE_SALARIES)
  public Map<String, Object> resendPayslipDelivery(@PathVariable Long id) {
    int queued = payslipDeliveryService.resend(id);
    if (queued == 0) {
      return Map.of("queued", false, "reason",
          "Already queued or currently being delivered");
    }
    boolean sent = payslipDeliveryService.deliverPending() > 0;
    return Map.of("queued", true, "sent", sent);
  }

  /** Queues rows for every eligible account missing one for the month. */
  @PostMapping("/payslip/deliveries/queue-missing")
  @PreAuthorize(CAN_MANAGE_SALARIES)
  public Map<String, Object> queueMissingPayslipDeliveries(
      @RequestParam int year, @RequestParam int month) {
    YearMonth.of(year, month); // validates the month range
    int queued = payslipDeliveryService.queueMissing(java.time.YearMonth.of(year, month));
    boolean sent = payslipDeliveryService.deliverPending() > 0;
    return Map.of("queued", queued, "sent", sent);
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }

  /* ===================== MONTH SUMMARY ===================== */

  @GetMapping("/month")
  @PreAuthorize(CAN_MANAGE_SALARIES)
  public List<PharmacySummary> monthly(
      @RequestParam OffsetDateTime start,
      @RequestParam OffsetDateTime end,
      @RequestParam(defaultValue = "USER") String role
  ) {
    List<ShiftLine> lines = "STAFF".equalsIgnoreCase(role)
        ? staffShiftRepo.findInRange(start, end, null).stream().map(this::toStaffLine).toList()
        : shiftRepo.findInRange(start, end, null, null).stream().map(this::toUserLine).toList();

    List<PharmacySummary> summaries = summarizeByPharmacy(lines);

    // Payslip adjustment fields (bonuses, one-time pay, tax-free
    // reimbursements) are part of the month's employer cost but live outside
    // the shifts. Fold the work month's adjustment totals into the matching
    // person so the salary lists agree with the payslip gross on all
    // platforms. Tax-free adjustments count toward the cost too — they are
    // pay, only the tax treatment differs.
    if (adjustmentService != null) {
      summaries = withPayslipAdjustments(summaries, lines, start, end);
    }
    return summaries;
  }

  /**
   * Adds each person's payslip adjustment total for the work month(s) covered
   * by [start, end) to their user summary (and the pharmacy totals).
   */
  private List<PharmacySummary> withPayslipAdjustments(
      List<PharmacySummary> summaries, List<ShiftLine> lines,
      OffsetDateTime start, OffsetDateTime end) {
    // Work months covered by the requested window (the end is exclusive —
    // clients request exactly one calendar month; multiple are handled anyway).
    java.util.Set<java.time.YearMonth> months = new java.util.HashSet<>();
    java.time.OffsetDateTime cursor = start;
    while (cursor.isBefore(end) && months.size() < 24) {
      months.add(java.time.YearMonth.from(cursor));
      cursor = cursor.plusMonths(1);
    }
    if (months.isEmpty()) return summaries;

    // userId -> adjustment total for the covered work months. Adjustments
    // are role-less (shared per user + work month); the peopleInRole check
    // below keeps them out of the other role's view.
    Map<Long, BigDecimal> adjustmentTotals = new java.util.HashMap<>();
    for (java.time.YearMonth ym : months) {
      for (var adjustment : adjustmentService.forMonth(ym.getYear(), ym.getMonthValue())) {
        BigDecimal amount = adjustment.getAmount() == null ? BigDecimal.ZERO : adjustment.getAmount();
        adjustmentTotals.merge(adjustment.getUserId(), amount, BigDecimal::add);
      }
    }
    if (adjustmentTotals.isEmpty()) return summaries;

    // Restrict to people who actually appear in the requested role's shifts.
    java.util.Set<Long> peopleInRole = lines.stream()
        .map(ShiftLine::userId).collect(java.util.stream.Collectors.toSet());

    List<PharmacySummary> updated = new ArrayList<>(summaries.size());
    for (PharmacySummary pharmacy : summaries) {
      List<UserSummary> users = new ArrayList<>(pharmacy.users().size());
      double totalHours = 0;
      BigDecimal totalCost = BigDecimal.ZERO;
      for (UserSummary user : pharmacy.users()) {
        BigDecimal extra = peopleInRole.contains(user.userId())
            ? adjustmentTotals.getOrDefault(user.userId(), BigDecimal.ZERO)
            : BigDecimal.ZERO;
        double hours = user.hours();
        BigDecimal cost = user.totalCost().add(extra);
        BigDecimal hourly = hours > 0
            ? cost.divide(BigDecimal.valueOf(hours), 2, RoundingMode.HALF_UP)
            : user.hourlyCost();
        users.add(new UserSummary(user.userId(), user.fullName(), hours, hourly, cost));
        totalHours += hours;
        totalCost = totalCost.add(cost);
      }
      users.sort((a, b) -> b.totalCost().compareTo(a.totalCost()));
      updated.add(new PharmacySummary(pharmacy.pharmacyId(), pharmacy.pharmacyName(), totalHours, totalCost, users));
    }
    updated.sort((a, b) -> b.totalCost().compareTo(a.totalCost()));
    return updated;
  }

  /* ===================== REPORT ===================== */

  @GetMapping("/report")
  @PreAuthorize(CAN_MANAGE_SALARIES)
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

  @PostMapping("/send-pdf-email")
  @PreAuthorize(CAN_MANAGE_SALARIES)
  public ResponseEntity<Map<String, Object>> sendSalaryPdfEmail(
      @RequestBody SalaryEmailRequest request
  ) {
    Long userId = request.userId();
    Integer year = request.year();
    Integer month = request.month();
    String role = request.role() == null ? "USER" : request.role();
    String pdfBase64 = request.pdfBase64() == null ? "" : request.pdfBase64().trim();

    if (userId == null || year == null || month == null || pdfBase64.isBlank()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Map.of("error", "Missing required fields to send the salary PDF email."));
    }

    var profile = profileRepo.findByUserId(userId).orElse(null);
    String email = profile == null || profile.getEmail() == null ? "" : profile.getEmail().trim();
    String employeeName = profile == null || profile.getFullName() == null ? "" : profile.getFullName().trim();
    if (email.isBlank() || !email.contains("@")) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Map.of("error", "The employee does not have a valid profile email."));
    }

    byte[] pdfBytes;
    try {
      pdfBytes = Base64.getDecoder().decode(pdfBase64);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Map.of("error", "Invalid PDF payload."));
    }

    var payslip = payslipFreezeService.resolve(userId, year, month, role);

    String monthLabel = String.format("%04d-%02d", year, month);
    boolean sent = emailService.sendSalaryPdfEmail(email, employeeName.isBlank() ? "Employee" : employeeName, pdfBytes, monthLabel);

    return ResponseEntity.ok(Map.of(
        "sent", sent,
        "recipient", email,
        "employeeName", employeeName,
        "month", monthLabel,
        "filename", "salary-" + monthLabel + ".pdf",
        "payslip", payslip
    ));
  }

  public record SalaryEmailRequest(
      Long userId,
      Integer year,
      Integer month,
      String role,
      String pdfBase64
  ) {}

  /* ===================== LAZY USER VIEW ===================== */

  @GetMapping("/user/years")
  @PreAuthorize(CAN_MANAGE_SALARIES)
  public List<YearRow> userYears(@RequestParam Long userId) {
    return yearsForUser(userId);
  }

  @GetMapping("/me/years")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<YearRow>> myYears(Authentication auth) {
    return ResponseEntity.ok()
        .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(1)).cachePrivate())
        .body(yearsForUser(currentUserId(auth)));
  }

  private List<YearRow> yearsForUser(Long userId) {
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
  @PreAuthorize(CAN_MANAGE_SALARIES)
  public List<MonthRow> userMonths(@RequestParam Long userId, @RequestParam int year) {
    return monthsForUser(userId, year);
  }

  @GetMapping("/me/months")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<MonthRow>> myMonths(@RequestParam int year, Authentication auth) {
    return ResponseEntity.ok()
        .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePrivate())
        .body(monthsForUser(currentUserId(auth), year));
  }

  private List<MonthRow> monthsForUser(Long userId, int year) {
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
  @PreAuthorize(CAN_MANAGE_SALARIES)
  public List<DayRow> userMonthDays(@RequestParam Long userId, @RequestParam int year, @RequestParam int month) {
    return monthDaysForUser(userId, year, month);
  }

  @GetMapping("/me/month")
  @PreAuthorize("isAuthenticated()")
  public List<DayRow> myMonthDays(
      @RequestParam int year,
      @RequestParam int month,
      Authentication auth
  ) {
    return monthDaysForUser(currentUserId(auth), year, month);
  }

  private List<DayRow> monthDaysForUser(Long userId, int year, int month) {
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

  private Long currentUserId(Authentication auth) {
    String username = auth.getName();
    return userRepo.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("User not found: " + username))
        .getId();
  }

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
