package com.nordicframtiden.api;

import com.nordicframtiden.pharmacy.ScheduleService;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/payroll")
@PreAuthorize("hasRole('ADMIN')")
public class PayrollController {

  private final UserService userService;
  private final ScheduleService scheduleService;

  public PayrollController(UserService userService, ScheduleService scheduleService) {
    this.userService = userService;
    this.scheduleService = scheduleService;
  }

  public record SalaryRow(
      Long userId,
      String name,
      String email,
      BigDecimal hourlyCost,
      double totalHours,
      BigDecimal totalSalary
  ) {}

  /**
   * GET /api/payroll/salaries?start=2026-01-01T00:00:00Z&end=2026-02-01T00:00:00Z
   * Optional: &pharmacyId=1
   */
  @GetMapping("/salaries")
  public List<SalaryRow> salaries(
      @RequestParam OffsetDateTime start,
      @RequestParam OffsetDateTime end,
      @RequestParam(required = false) Long pharmacyId
  ) {

    // Pharmacists: you said "Users" should be pharmacists => Role.USER
    // If pharmacists are STAFF instead, change Role.USER -> Role.STAFF
    var pharmacists = userService.listDetailedByRole(Role.USER);

    // Load shifts in range (optionally filter by pharmacy)
    var shifts = scheduleService.listRange(start, end, pharmacyId, null);

    // Sum hours by userId
    Map<Long, Double> hoursByUser = new HashMap<>();
    for (var s : shifts) {
      if (s.getUser() == null || s.getUser().getId() == null) continue;
      if (s.getStartAt() == null || s.getEndAt() == null) continue;

      double hours = Duration.between(s.getStartAt(), s.getEndAt()).toMinutes() / 60.0;
      if (hours <= 0) continue;

      hoursByUser.merge(s.getUser().getId(), hours, Double::sum);
    }

    // Build rows
    List<SalaryRow> out = new ArrayList<>();
    for (var u : pharmacists) {
      double totalHours = round2(hoursByUser.getOrDefault(u.id(), 0.0));

      BigDecimal hourly = (u.hourlyCost() == null) ? BigDecimal.ZERO : u.hourlyCost();
      BigDecimal salary = hourly.multiply(BigDecimal.valueOf(totalHours)).setScale(2, RoundingMode.HALF_UP);

      String name = u.fullName() != null && !u.fullName().isBlank()
          ? u.fullName()
          : (u.username() != null ? u.username() : ("User #" + u.id()));

      out.add(new SalaryRow(
          u.id(),
          name,
          u.email(),
          hourly.setScale(2, RoundingMode.HALF_UP),
          totalHours,
          salary
      ));
    }

    // Optional: sort highest salary first
    out.sort(Comparator.comparing(SalaryRow::totalSalary).reversed());
    return out;
  }

  private static double round2(double x) {
    return Math.round(x * 100.0) / 100.0;
  }
}