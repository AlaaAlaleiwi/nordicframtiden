package com.nordicframtiden.api;

import com.nordicframtiden.pharmacy.ScheduleService;
import com.nordicframtiden.pharmacy.ScheduleShift;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class ScheduleController {

    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    public record EventDto(
            Long id,
            Long pharmacyId, // ✅ can be null for STAFF
            Long userId,
            String userLabel,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            String note) {
        static EventDto from(ScheduleShift s) {
            Long pid = (s.getPharmacy() == null) ? null : s.getPharmacy().getId();
            return new EventDto(
                    s.getId(),
                    pid,
                    s.getUser().getId(),
                    "@" + s.getUser().getUsername(),
                    s.getStartAt(),
                    s.getEndAt(),
                    s.getNote());
        }
    }

    public record CreateRequest(
            @NotNull Long pharmacyId,
            @NotNull Long userId,
            @NotNull OffsetDateTime startAt,
            @NotNull OffsetDateTime endAt,
            String note) {
    }

    public record UpdateRequest(
            Long pharmacyId, // optional
            Long userId,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            String note) {
    }

    @GetMapping
    public List<EventDto> list(
            @RequestParam OffsetDateTime start,
            @RequestParam OffsetDateTime end,
            @RequestParam(required = false) Long pharmacyId,
            @RequestParam(required = false) Long userId) {
        return service.listRange(start, end, pharmacyId, userId)
                .stream()
                .map(EventDto::from)
                .toList();
    }

    @PostMapping
    public EventDto create(@RequestBody CreateRequest req) {
        var created = service.create(
                req.pharmacyId(), // ✅ can be null now
                req.userId(),
                req.startAt(),
                req.endAt(),
                req.note());
        return EventDto.from(created);
    }

    @PutMapping("/{id}")
    public EventDto update(@PathVariable Long id, @RequestBody UpdateRequest req) {
        var updated = service.update(
                id,
                req.pharmacyId(),
                req.userId(),
                req.startAt(),
                req.endAt(),
                req.note());
        return EventDto.from(updated);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}