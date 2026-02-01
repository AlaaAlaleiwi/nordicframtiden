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
@PreAuthorize("hasAnyRole('ADMIN','USER')")
public class SalaryController {

    private final ScheduleShiftRepository shiftRepo;
    private final UserProfileRepository profileRepo;

    public SalaryController(ScheduleShiftRepository shiftRepo, UserProfileRepository profileRepo) {
        this.shiftRepo = shiftRepo;
        this.profileRepo = profileRepo;
    }

    // ---------- Existing DTOs ----------
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
            BigDecimal cost) {
    }

    public record UserSummary(
            Long userId,
            String fullName,
            double hours,
            BigDecimal hourlyCost,
            BigDecimal totalCost) {
    }

    public record PharmacySummary(
            Long pharmacyId,
            String pharmacyName,
            double totalHours,
            BigDecimal totalCost,
            List<UserSummary> users) {
    }

    // ---------- NEW: Lazy-load DTOs ----------
    public record YearRow(int year) {
    }

    public record MonthRow(
            int year,
            int month, // 1..12
            double totalHours,
            BigDecimal totalCost) {
    }

    public record DayRow(
            String dayKey, // "YYYY-MM-DD"
            OffsetDateTime from,
            OffsetDateTime to,
            double totalHours,
            BigDecimal totalCost) {
    }

    // =========================================================
    // Existing endpoints (unchanged)
    // =========================================================

    // GET /api/salaries/month?start=...&end=...
    @GetMapping("/month")
    public List<PharmacySummary> monthly(@RequestParam OffsetDateTime start,
            @RequestParam OffsetDateTime end) {

        List<ScheduleShift> shifts = shiftRepo.findInRange(start, end, null, null);
        List<ShiftLine> lines = shifts.stream().map(this::toLine).toList();

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

            users.sort((a, b) -> b.totalCost().compareTo(a.totalCost()));

            out.add(new PharmacySummary(entry.getKey(), pharmacyName, totalHours, totalCost, users));
        }

        out.sort((a, b) -> b.totalCost().compareTo(a.totalCost()));
        return out;
    }

    // GET /api/salaries/report?start=...&end=...&pharmacyId=1&userId=2(optional)
    @GetMapping("/report")
    public List<ShiftLine> report(@RequestParam OffsetDateTime start,
            @RequestParam OffsetDateTime end,
            @RequestParam Long pharmacyId,
            @RequestParam(required = false) Long userId) {

        List<ScheduleShift> shifts = shiftRepo.findInRange(start, end, pharmacyId, userId);
        return shifts.stream().map(this::toLine).toList();
    }

    // =========================================================
    // NEW: Lazy salary endpoints (Year -> Months -> Days)
    // =========================================================

    // GET /api/salaries/user/years?userId=123
    @GetMapping("/user/years")
    public List<YearRow> userYears(@RequestParam Long userId) {
        // Wide range. You can tighten later with SQL distinct query.
        OffsetDateTime start = OffsetDateTime.parse("2000-01-01T00:00:00Z");
        OffsetDateTime end = OffsetDateTime.now().plusYears(2);

        List<ScheduleShift> shifts = shiftRepo.findInRange(start, end, null, userId);

        return shifts.stream()
                .map(s -> s.getStartAt().getYear())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .map(YearRow::new)
                .toList();
    }

    // GET /api/salaries/user/months?userId=123&year=2026
    @GetMapping("/user/months")
    public List<MonthRow> userMonths(@RequestParam Long userId, @RequestParam int year) {
        OffsetDateTime start = OffsetDateTime.parse(year + "-01-01T00:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse((year + 1) + "-01-01T00:00:00Z");

        List<ScheduleShift> shifts = shiftRepo.findInRange(start, end, null, userId);
        List<ShiftLine> lines = shifts.stream().map(this::toLine).toList();

        Map<Integer, List<ShiftLine>> byMonth = lines.stream()
                .collect(Collectors.groupingBy(l -> l.startAt().getMonthValue()));

        return byMonth.entrySet().stream()
                .map(e -> {
                    int month = e.getKey();
                    double hours = e.getValue().stream().mapToDouble(ShiftLine::hours).sum();
                    BigDecimal cost = e.getValue().stream().map(ShiftLine::cost).reduce(BigDecimal.ZERO,
                            BigDecimal::add);
                    return new MonthRow(year, month, hours, cost);
                })
                .sorted((a, b) -> Integer.compare(b.month(), a.month()))
                .toList();
    }

    // GET /api/salaries/user/month?userId=123&year=2026&month=1
    @GetMapping("/user/month")
    public List<DayRow> userMonthDays(@RequestParam Long userId,
            @RequestParam int year,
            @RequestParam int month) {

        if (month < 1 || month > 12)
            throw new IllegalArgumentException("month must be 1..12");

        String mm = String.format("%02d", month);
        OffsetDateTime start = OffsetDateTime.parse(year + "-" + mm + "-01T00:00:00Z");
        OffsetDateTime end = (month == 12)
                ? OffsetDateTime.parse((year + 1) + "-01-01T00:00:00Z")
                : OffsetDateTime.parse(year + "-" + String.format("%02d", month + 1) + "-01T00:00:00Z");

        List<ScheduleShift> shifts = shiftRepo.findInRange(start, end, null, userId);
        List<ShiftLine> lines = shifts.stream().map(this::toLine).toList();

        Map<String, List<ShiftLine>> byDay = lines.stream()
                .collect(Collectors.groupingBy(l -> l.startAt().toLocalDate().toString())); // "YYYY-MM-DD"

        return byDay.entrySet().stream()
                .map(e -> {
                    List<ShiftLine> dayLines = e.getValue();
                    OffsetDateTime from = dayLines.stream().map(ShiftLine::startAt).min(Comparator.naturalOrder())
                            .orElse(null);
                    OffsetDateTime to = dayLines.stream().map(ShiftLine::endAt).max(Comparator.naturalOrder())
                            .orElse(null);
                    double hours = dayLines.stream().mapToDouble(ShiftLine::hours).sum();
                    BigDecimal cost = dayLines.stream().map(ShiftLine::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new DayRow(e.getKey(), from, to, hours, cost);
                })
                .sorted((a, b) -> b.dayKey().compareTo(a.dayKey())) // newest first
                .toList();
    }

    // =========================================================
    // Helper
    // =========================================================
    private ShiftLine toLine(ScheduleShift s) {
        var p = s.getPharmacy();
        var u = s.getUser();

        // IMPORTANT: this endpoint is for pharmacy shifts, pharmacy must exist
        if (p == null)
            throw new IllegalStateException("Shift pharmacy is null (salary expects pharmacy shifts only)");

        var profile = profileRepo.findByUserId(u.getId()).orElse(null);

        BigDecimal hourly = (s.getHourlyCostSnapshot() != null)
                ? s.getHourlyCostSnapshot()
                : (profile != null && profile.getHourlyCost() != null ? profile.getHourlyCost() : BigDecimal.ZERO);

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
                cost);
    }
}