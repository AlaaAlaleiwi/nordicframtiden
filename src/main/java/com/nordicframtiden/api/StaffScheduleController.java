package com.nordicframtiden.api;

import com.nordicframtiden.company.StaffScheduleService;
import com.nordicframtiden.company.StaffShift;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/staff-schedules")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class StaffScheduleController {

  private final StaffScheduleService service;

  public StaffScheduleController(StaffScheduleService service) {
    this.service = service;
  }

  public record EventDto(
      Long id,
      Long userId,
      String userLabel,
      OffsetDateTime startAt,
      OffsetDateTime endAt,
      String note
  ) {
    static EventDto from(StaffShift s) {
      return new EventDto(
          s.getId(),
          s.getUser().getId(),
          "@" + s.getUser().getUsername(),
          s.getStartAt(),
          s.getEndAt(),
          s.getNote()
      );
    }
  }

  public record CreateRequest(
      @NotNull Long userId,
      @NotNull OffsetDateTime startAt,
      @NotNull OffsetDateTime endAt,
      String note
  ) {}

  public record UpdateRequest(
      Long userId,
      OffsetDateTime startAt,
      OffsetDateTime endAt,
      String note
  ) {}

  @GetMapping
  public List<EventDto> list(
      @RequestParam OffsetDateTime start,
      @RequestParam OffsetDateTime end,
      @RequestParam(required = false) Long userId
  ) {
    return service.listRange(start, end, userId).stream().map(EventDto::from).toList();
  }

  @PostMapping
  public EventDto create(@RequestBody CreateRequest req) {
    var created = service.create(req.userId(), req.startAt(), req.endAt(), req.note());
    return EventDto.from(created);
  }

  @PutMapping("/{id}")
  public EventDto update(@PathVariable Long id, @RequestBody UpdateRequest req) {
    var updated = service.update(id, req.userId(), req.startAt(), req.endAt(), req.note());
    return EventDto.from(updated);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}