package com.nordicframtiden.api;

import com.nordicframtiden.pharmacy.ScheduleService;
import com.nordicframtiden.pharmacy.ScheduleShift;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;   // ✅ CORRECT
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@PreAuthorize("hasAnyRole('ADMIN','USER')") // ✅ pharmacists are USER
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

  /** ✅ pharmacists call this */
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

  /** ✅ admin listing */
  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
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

  public record CreateRequest(
      @NotNull Long pharmacyId,
      @NotNull Long userId,
      @NotNull OffsetDateTime startAt,
      @NotNull OffsetDateTime endAt,
      String note
  ) {}

  public record UpdateRequest(
      Long pharmacyId,
      Long userId,
      OffsetDateTime startAt,
      OffsetDateTime endAt,
      String note
  ) {}

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public EventDto create(@RequestBody CreateRequest req) {
    var created = service.create(
        req.pharmacyId(),
        req.userId(),
        req.startAt(),
        req.endAt(),
        req.note()
    );
    return EventDto.from(created);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public EventDto update(@PathVariable Long id, @RequestBody UpdateRequest req) {
    var updated = service.update(
        id,
        req.pharmacyId(),
        req.userId(),
        req.startAt(),
        req.endAt(),
        req.note()
    );
    return EventDto.from(updated);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}