package com.nordicframtiden.api;

import com.nordicframtiden.service.ScheduleWizardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin schedule wizard: pick period (day/week/month) + pharmacy +
 * pharmacists, review availability and booked days, then confirm to create
 * the shifts and notify the affected pharmacists.
 */
@RestController
@RequestMapping("/api/schedule-wizard")
@PreAuthorize("hasRole('ADMIN')")
public class ScheduleWizardController {

  private final ScheduleWizardService wizard;

  public ScheduleWizardController(ScheduleWizardService wizard) {
    this.wizard = wizard;
  }

  public record OptionsRequest(String period, LocalDate date, Long pharmacyId) {}

  /** Step 1–3: period + pharmacy chosen, returns pharmacists and booked days. */
  @PostMapping("/options")
  public ScheduleWizardService.WizardOptions options(@RequestBody OptionsRequest req) {
    return wizard.options(req.period(), req.date(), req.pharmacyId());
  }

  public record ConfirmRequest(String period, LocalDate date, Long pharmacyId,
      List<ScheduleWizardService.Assignment> assignments, boolean sendEmail) {}

  /** Step 5–6: create the shifts, then notify + email the schedule PDF. */
  @PostMapping("/confirm")
  public ScheduleWizardService.WizardConfirmResult confirm(@RequestBody ConfirmRequest req) {
    return wizard.confirm(req.period(), req.date(), req.pharmacyId(),
        req.assignments(), req.sendEmail());
  }
}
