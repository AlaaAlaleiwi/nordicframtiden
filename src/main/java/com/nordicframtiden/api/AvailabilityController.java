package com.nordicframtiden.api;

import com.nordicframtiden.availability.AvailabilityRequest;
import com.nordicframtiden.availability.AvailabilityService;
import com.nordicframtiden.security.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {

  private final AvailabilityService service;
  private final UserService userService;

  public AvailabilityController(AvailabilityService service, UserService userService) {
    this.service = service;
    this.userService = userService;
  }

  public record CreateReq(
      String type, // "DAY" | "WEEK" | "RANGE"
      LocalDate startDate,
      LocalDate endDate,
      String note
  ) {}

  public record AvailabilityDto(
      Long id,
      Long userId,
      String userFullName,
      String username,
      String type,
      LocalDate startDate,
      LocalDate endDate,
      String status,
      String note
  ) {}

  public record StatusUpdateRequest(AvailabilityRequest.Status status) {}

  public record AvailabilityRow(
      Long id,
      Long userId,
      String username,
      String userFullName,
      String type,
      String startDate,
      String endDate,
      String status,
      String note
  ) {}

  private AvailabilityDto toDto(AvailabilityRequest r) {
    var u = r.getUser();
    var detailed = userService.getDetailedById(u.getId());
    return new AvailabilityDto(
        r.getId(),
        u.getId(),
        detailed.fullName(),
        u.getUsername(),
        r.getType().name(),
        r.getStartDate(),
        r.getEndDate(),
        r.getStatus().name(),
        r.getNote()
    );
  }

  @GetMapping("/me")
  @PreAuthorize("hasAnyRole('USER','STAFF','ADMIN')")
  public List<AvailabilityDto> my(Authentication auth) {
    return service.my(auth).stream().map(this::toDto).toList();
  }

  @PostMapping("/me")
  @PreAuthorize("hasAnyRole('USER','STAFF','ADMIN')")
  public AvailabilityDto create(Authentication auth, @RequestBody CreateReq req) {
    var type = AvailabilityRequest.Type.valueOf(req.type());
    var created = service.createForMe(auth, type, req.startDate(), req.endDate(), req.note());
    return toDto(created);
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public List<AvailabilityDto> all() {
    return service.all().stream().map(this::toDto).toList();
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasRole('ADMIN')") // ✅ simplest
  public void updateStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest req) {
    service.setStatus(id, req.status());   // ✅ enum type matches
  }

  @GetMapping("/range")
  @PreAuthorize("hasRole('ADMIN')")
  public List<AvailabilityRow> range(@RequestParam LocalDate start, @RequestParam LocalDate end) {
    return service.getApprovedOverlapping(start, end);
  }
}