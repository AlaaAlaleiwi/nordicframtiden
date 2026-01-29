package com.nordicframtiden.api;

import com.nordicframtiden.pharmacy.ScheduleShift;
import com.nordicframtiden.pharmacy.ScheduleShiftRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/salaries")
@PreAuthorize("hasRole('ADMIN')")
public class SalaryController {

  private final ScheduleShiftRepository shiftRepo;
  private final UserProfileRepository profileRepo;

  public SalaryController(ScheduleShiftRepository shiftRepo, UserProfileRepository profileRepo) {
    this.shiftRepo = shiftRepo;
    this.profileRepo = profileRepo;
  }

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

  // GET /api/salaries/month?start=2026-01-01T00:00:00Z&end=2026-02-01T00:00:00Z
  @GetMapping("/month")
  public List<PharmacySummary> monthly(@RequestParam OffsetDateTime start,
                                       @RequestParam OffsetDateTime end) {

    List<ScheduleShift> shifts = shiftRepo.findInRange(start, end, null, null);

    // Build detailed lines (we’ll reuse same logic)
    List<ShiftLine> lines = shifts.stream().map(this::toLine).toList();

    // Group by pharmacy, then by user
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
        BigDecimal hourly = uLines.get(0).hourlyCost();

        double hours = uLines.stream().mapToDouble(ShiftLine::hours).sum();
        BigDecimal cost = uLines.stream().map(ShiftLine::cost).reduce(BigDecimal.ZERO, BigDecimal::add);

        users.add(new UserSummary(ue.getKey(), fullName, hours, hourly, cost));

        totalHours += hours;
        totalCost = totalCost.add(cost);
      }

      // sort users by cost desc
      users.sort((a, b) -> b.totalCost().compareTo(a.totalCost()));

      out.add(new PharmacySummary(entry.getKey(), pharmacyName, totalHours, totalCost, users));
    }

    // sort pharmacies by total cost desc
    out.sort((a, b) -> b.totalCost().compareTo(a.totalCost()));
    return out;
  }

  // GET /api/salaries/report?start=...&end=...&pharmacyId=1&userId=2 (userId optional)
  @GetMapping("/report")
  public List<ShiftLine> report(@RequestParam OffsetDateTime start,
                               @RequestParam OffsetDateTime end,
                               @RequestParam Long pharmacyId,
                               @RequestParam(required = false) Long userId) {

    List<ScheduleShift> shifts = shiftRepo.findInRange(start, end, pharmacyId, userId);
    return shifts.stream().map(this::toLine).toList();
  }

  private ShiftLine toLine(ScheduleShift s) {
    var p = s.getPharmacy();
    var u = s.getUser();

    var profile = profileRepo.findByUserId(u.getId()).orElse(null);

    BigDecimal hourly = (profile != null && profile.getHourlyCost() != null)
        ? profile.getHourlyCost()
        : BigDecimal.ZERO;

    OffsetDateTime start = s.getStartAt();
    OffsetDateTime end = s.getEndAt();

    double hours = Duration.between(start, end).toMinutes() / 60.0;

    BigDecimal cost = hourly.multiply(BigDecimal.valueOf(hours));

    return new ShiftLine(
        s.getId(),
        p.getId(),
        p.getName(),
        u.getId(),
        profile == null ? u.getUsername() : profile.getFullName(),
        start,
        end,
        hours,
        hourly,
        cost
    );
  }
}