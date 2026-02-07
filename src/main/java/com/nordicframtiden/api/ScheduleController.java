package com.nordicframtiden.api;

import com.nordicframtiden.pharmacy.ScheduleService;
import com.nordicframtiden.pharmacy.ScheduleShift;
import com.nordicframtiden.security.repo.UserProfileRepository;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class ScheduleController {

  private final ScheduleService service;
  private final UserProfileRepository userProfileRepository;

  public ScheduleController(ScheduleService service, UserProfileRepository userProfileRepository) {
    this.service = service;
    this.userProfileRepository = userProfileRepository;
  }

  public record EventDto(
      Long id,
      Long pharmacyId,
      String pharmacyName,
      Long userId,
      String userLabel,
      OffsetDateTime startAt,
      OffsetDateTime endAt,
      String note) {
    static EventDto from(ScheduleShift s, String username) {
      Long pid = s.getPharmacy() == null ? null : s.getPharmacy().getId();
      String pname = s.getPharmacy() == null ? null : s.getPharmacy().getName();

      return new EventDto(
          s.getId(),
          pid,
          pname,
          s.getUser().getId(),
          username,
          s.getStartAt(),
          s.getEndAt(),
          s.getNote());
    }
  }

  // ✅ pharmacist gets own schedule
  @GetMapping("/me")
  public List<EventDto> mySchedules(@RequestParam OffsetDateTime start,
      @RequestParam OffsetDateTime end,
      Authentication auth) {

    var res = service
        .listForCurrentUser(auth, start, end)
        .stream()
        .map(s -> {
          Long pid = s.getPharmacy() == null ? null : s.getPharmacy().getId();
          String pname = s.getPharmacy() == null ? null : s.getPharmacy().getName();
          var u = userProfileRepository.findByUserId(s.getUser().getId());
          return new EventDto(
              s.getId(),
              pid,
              pname,
              s.getUser().getId(),
              u.get().getFullName(),
              s.getStartAt(),
              s.getEndAt(),
              s.getNote());

        })
        .toList();

    return res;
  }

  // ✅ admin listing
  @GetMapping
  public List<EventDto> list(@RequestParam OffsetDateTime start,
      @RequestParam OffsetDateTime end,
      @RequestParam(required = false) Long pharmacyId,
      @RequestParam(required = false) Long userId) {
    return service.listRange(start, end, pharmacyId, userId)
        .stream().map(s -> {
          Long pid = s.getPharmacy() == null ? null : s.getPharmacy().getId();
          String pname = s.getPharmacy() == null ? null : s.getPharmacy().getName();
          var u = userProfileRepository.findByUserId(s.getUser().getId());
          return new EventDto(
              s.getId(),
              pid,
              pname,
              s.getUser().getId(),
              u.get().getFullName(),
              s.getStartAt(),
              s.getEndAt(),
              s.getNote());

        }).toList();
  }

  public record CreateRequest(
      Long pharmacyId,
      Long userId,
      OffsetDateTime startAt,
      OffsetDateTime endAt,
      String note) {
  }

  // ✅ THIS is what your UI is calling
  @PostMapping
  public EventDto create(@RequestBody CreateRequest req) {
    var created = service.create(
        req.pharmacyId(),
        req.userId(),
        req.startAt(),
        req.endAt(),
        req.note());

    Long pid = created.getPharmacy() == null ? null : created.getPharmacy().getId();
    String pname = created.getPharmacy() == null ? null : created.getPharmacy().getName();
    var u = userProfileRepository.findByUserId(created.getUser().getId());
    return new EventDto(
        created.getId(),
        pid,
        pname,
        created.getUser().getId(),
        u.get().getFullName(),
        created.getStartAt(),
        created.getEndAt(),
        created.getNote());

  }
}