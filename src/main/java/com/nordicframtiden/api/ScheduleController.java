package com.nordicframtiden.api;

import com.nordicframtiden.pharmacy.ScheduleService;
import com.nordicframtiden.pharmacy.ScheduleShift;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_USER','USER', 'ADMIN')") // safest
public class ScheduleController {

  private final ScheduleService service;

  public ScheduleController(ScheduleService service) {
    this.service = service;
  }

  public record EventDto(
      Long id,
      Long pharmacyId,
      String pharmacyName,
      Long userId,
      String userLabel,
      OffsetDateTime startAt,
      OffsetDateTime endAt,
      String note
  ) {
    static EventDto from(ScheduleShift s) {
      Long pid = (s.getPharmacy() == null) ? null : s.getPharmacy().getId();
      String pname = (s.getPharmacy() == null) ? null : s.getPharmacy().getName();
      return new EventDto(
          s.getId(),
          pid,
          pname,
          s.getUser().getId(),
          "@" + s.getUser().getUsername(),
          s.getStartAt(),
          s.getEndAt(),
          s.getNote()
      );
    }
  }

  // ✅ Pharmacist: get own schedule (no userId needed)
  @GetMapping("/me")
  public List<EventDto> mySchedules(
      @RequestParam OffsetDateTime start,
      @RequestParam OffsetDateTime end,
      Authentication auth
  ) {
    return service.listForCurrentUser(auth, start, end)
        .stream()
        .map(EventDto::from)
        .toList();
  }

  // ✅ Admin only list
  @GetMapping
  @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
  public List<EventDto> list(
      @RequestParam OffsetDateTime start,
      @RequestParam OffsetDateTime end,
      @RequestParam(required = false) Long pharmacyId,
      @RequestParam(required = false) Long userId
  ) {
    return service.listRange(start, end, pharmacyId, userId)
        .stream()
        .map(EventDto::from)
        .toList();
  }
}