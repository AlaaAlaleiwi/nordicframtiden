package com.nordicframtiden.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nordicframtiden.service.model.NetSalaryResponse;
import com.nordicframtiden.service.model.PayslipSnapshot;
import com.nordicframtiden.service.model.PayslipSnapshotRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Freezes ended salary months: once a month is over, its payslip is
 * calculated once and persisted. Later changes to the hourly cost (or any
 * other profile data) must never rewrite history — past months are served
 * verbatim from the stored snapshot, exactly like a printed payslip.
 *
 * Rules:
 * <ul>
 *   <li>Future/current month: always calculated live (nothing frozen yet).</li>
 *   <li>Past month: served from {@code payslip_snapshot} verbatim. The first
 *       read after the month has ended freezes it.</li>
 *   <li>Explicit save of adjustments for a past month (admin editing a closed
 *       month) refreshes the snapshot while preserving the historical hourly
 *       cost from the existing snapshot.</li>
 *   <li>Preview endpoint never freezes or reads frozen data — it always
 *       reflects unsaved edits.</li>
 * </ul>
 */
@Service
public class PayslipFreezeService {

  private static final Logger log = LoggerFactory.getLogger(PayslipFreezeService.class);

  private final PayslipSnapshotRepository snapshots;
  private final PayrollService payrollService;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public PayslipFreezeService(PayslipSnapshotRepository snapshots,
      PayrollService payrollService,
      ObjectMapper objectMapper) {
    this.snapshots = snapshots;
    this.payrollService = payrollService;
    this.objectMapper = objectMapper;
    this.clock = Clock.systemDefaultZone();
  }

  public boolean isMonthEnded(int year, int month) {
    YearMonth target = YearMonth.of(year, month);
    return target.isBefore(YearMonth.from(LocalDate.now(clock)));
  }

  /** Live (unfrozen) payslip; used for current/future months. */
  public NetSalaryResponse live(Long userId, int year, int month, String role) {
    return "STAFF".equalsIgnoreCase(role)
        ? payrollService.netSalaryForStaffMonth(userId, year, month)
        : payrollService.netSalaryForUserMonth(userId, year, month);
  }

  /**
   * Resolves the payslip honoring the freeze: past months come from the
   * snapshot (freezing on first read), current/future months compute live.
   */
  @Transactional
  public NetSalaryResponse resolve(Long userId, int year, int month, String role) {
    String normalizedRole = normalizeRole(role);
    if (!isMonthEnded(year, month)) {
      return live(userId, year, month, role);
    }

    OptionalSnapshot existing = find(userId, year, month, normalizedRole);
    if (existing.value != null) {
      return existing.value;
    }
    if (existing.corrupt) {
      // A broken snapshot must not brick the payslip: recompute and re-freeze.
      log.warn("Corrupt payslip snapshot for user {} {}-{} ({}); recomputing",
          userId, year, month, normalizedRole);
    }

    NetSalaryResponse computed = live(userId, year, month, role);
    freeze(userId, year, month, normalizedRole, computed);
    return computed;
  }

  /**
   * Explicit save of adjustments: refreshes the snapshot. For a past month,
   * the historical hourly cost from the existing snapshot is preserved so
   * corrections do not silently adopt today's rate.
   */
  @Transactional
  public NetSalaryResponse afterAdjustmentsSaved(Long userId, int year, int month, String role) {
    String normalizedRole = normalizeRole(role);
    NetSalaryResponse computed = live(userId, year, month, role);

    if (isMonthEnded(year, month)) {
      OptionalSnapshot existing = find(userId, year, month, normalizedRole);
      if (existing.value != null && existing.value.hourlyCost() != null) {
        computed = new NetSalaryResponse(
            computed.userId(), computed.monthKey(), existing.value.hourlyCost(),
            computed.totalHours(), computed.grossSalary(), computed.taxYear(),
            computed.municipalityCode(), computed.tableNumber(), computed.taxColumn(),
            computed.preliminaryTax(), computed.netSalary(), computed.regularTax(),
            computed.oneTimeTax(), computed.taxFreeAmount(), computed.projectedAnnualIncome(),
            computed.adjustments(), computed.baseHourlySalary(), computed.saturdayOb(),
            computed.sundayOb());
      }
    }
    freeze(userId, year, month, normalizedRole, computed);
    return computed;
  }

  private void freeze(Long userId, int year, int month, String role, NetSalaryResponse payslip) {
    try {
      String payload = objectMapper.writeValueAsString(payslip);
      PayslipSnapshot entity = snapshots
          .findByUserIdAndYearAndMonthAndRole(userId, year, month, role)
          .orElseGet(() -> {
            PayslipSnapshot created = new PayslipSnapshot();
            created.setUserId(userId);
            created.setYear(year);
            created.setMonth(month);
            created.setRole(role);
            return created;
          });
      entity.setPayload(payload);
      entity.touch();
      snapshots.save(entity);
    } catch (Exception e) {
      // Freezing is an optimization for stability of history; failing to
      // store it must not fail the request.
      log.warn("Could not persist payslip snapshot for user {} {}-{} ({}): {}",
          userId, year, month, role, e.getMessage());
    }
  }

  private record OptionalSnapshot(NetSalaryResponse value, boolean corrupt) {}

  private OptionalSnapshot find(Long userId, int year, int month, String role) {
    return snapshots.findByUserIdAndYearAndMonthAndRole(userId, year, month, role)
        .map(entity -> {
          try {
            return new OptionalSnapshot(
                objectMapper.readValue(entity.getPayload(), NetSalaryResponse.class), false);
          } catch (Exception e) {
            return new OptionalSnapshot(null, true);
          }
        })
        .orElse(new OptionalSnapshot(null, false));
  }

  private static String normalizeRole(String role) {
    return "STAFF".equalsIgnoreCase(role) ? "STAFF" : "USER";
  }
}
