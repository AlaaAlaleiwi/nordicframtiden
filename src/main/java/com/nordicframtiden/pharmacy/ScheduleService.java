package com.nordicframtiden.pharmacy;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ScheduleService {

    private final ScheduleShiftRepository shiftRepo;
    private final PharmacyRepository pharmacyRepo;
    private final AppUserRepository userRepo;
    private final UserService userService; // ✅ use service

    public ScheduleService(
            ScheduleShiftRepository shiftRepo,
            PharmacyRepository pharmacyRepo,
            AppUserRepository userRepo,
            UserService userService) {
        this.shiftRepo = shiftRepo;
        this.pharmacyRepo = pharmacyRepo;
        this.userRepo = userRepo;
        this.userService = userService;
    }

    public List<ScheduleShift> listForUser(Long userId, Instant start, Instant end) {
        if (userId == null)
            throw new IllegalArgumentException("userId is required");
        if (start == null || end == null)
            throw new IllegalArgumentException("start/end are required");

        OffsetDateTime startAt = start.atOffset(ZoneOffset.UTC);
        OffsetDateTime endAt = end.atOffset(ZoneOffset.UTC);

        return shiftRepo.findInRange(startAt, endAt, null, userId);
    }

    public List<ScheduleShift> listRange(OffsetDateTime start, OffsetDateTime end, Long pharmacyId, Long userId) {
        return shiftRepo.findInRange(start, end, pharmacyId, userId);
    }

    public List<ScheduleShift> listForCurrentUser(Authentication auth, OffsetDateTime start, OffsetDateTime end) {
        String username = auth.getName();
        var user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return shiftRepo.findInRange(start, end, null, user.getId());
    }

    @Transactional
    public ScheduleShift create(Long pharmacyId, Long userId,
            OffsetDateTime startAt, OffsetDateTime endAt, String note) {
        validateRange(startAt, endAt);

        if (pharmacyId == null)
            throw new IllegalArgumentException("pharmacyId is required");

        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Pharmacy pharmacy = pharmacyRepo.findById(pharmacyId)
                .orElseThrow(() -> new IllegalArgumentException("Pharmacy not found"));

        // One shift per user per day: notify and reject the duplicate.
        assertNoShiftOnSameDay(user.getId(), startAt, endAt, null);

        // ✅ get hourlyCost through userService
        BigDecimal hourly = BigDecimal.ZERO;
        try {
            var profile = userService.getProfileByUserId(user.getId());
            if (profile.getHourlyCost() != null)
                hourly = profile.getHourlyCost();
        } catch (Exception ignored) {
        }

        ScheduleShift s = new ScheduleShift();
        s.setPharmacy(pharmacy);
        s.setUser(user);
        s.setStartAt(startAt);
        s.setEndAt(endAt);
        s.setNote(note);

        // ✅ snapshot at creation time
        s.setHourlyCostSnapshot(hourly);

        return shiftRepo.save(s);
    }

    @Transactional
    public ScheduleShift update(Long id,
            Long pharmacyId,
            Long userId,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            String note) {

        ScheduleShift s = shiftRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shift not found"));

        if (pharmacyId != null) {
            Pharmacy pharmacy = pharmacyRepo.findById(pharmacyId)
                    .orElseThrow(() -> new IllegalArgumentException("Pharmacy not found"));
            s.setPharmacy(pharmacy);
        }

        if (userId != null) {
            AppUser user = userRepo.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            s.setUser(user);
        }

        if (startAt != null)
            s.setStartAt(startAt);
        if (endAt != null)
            s.setEndAt(endAt);

        validateRange(s.getStartAt(), s.getEndAt());

        // One shift per user per day — ignore the shift being moved.
        assertNoShiftOnSameDay(s.getUser().getId(), s.getStartAt(), s.getEndAt(), id);

        if (note != null)
            s.setNote(note);

        return shiftRepo.save(s);
    }

    @Transactional
    public void delete(Long id) {
        shiftRepo.deleteById(id);
    }

    private static void validateRange(OffsetDateTime startAt, OffsetDateTime endAt) {
        if (startAt == null || endAt == null)
            throw new IllegalArgumentException("Start/end required");
        if (!startAt.isBefore(endAt))
            throw new IllegalArgumentException("Invalid time range");
    }

    /** Stockholm timezone for the "same day" definition. */
    private static final java.time.ZoneId SCHEDULE_ZONE = java.time.ZoneId.of("Europe/Stockholm");

    /**
     * Rejects the shift when the user already has another shift whose
     * [start, end) window intersects the Stockholm calendar day of the new
     * shift. {@code excludeShiftId} lets an update ignore itself.
     */
    private void assertNoShiftOnSameDay(Long userId, OffsetDateTime startAt, OffsetDateTime endAt, Long excludeShiftId) {
        var dayStart = startAt.atZoneSameInstant(SCHEDULE_ZONE).toLocalDate().atStartOfDay(SCHEDULE_ZONE).toInstant().atOffset(startAt.getOffset());
        var dayEnd = dayStart.plusDays(1);

        List<ScheduleShift> sameDay = shiftRepo.findByUserIdAndStartAtLessThanAndEndAtGreaterThan(userId, dayEnd, dayStart);
        // On create (excludeShiftId == null) any same-day shift conflicts;
        // on update the shift being moved is ignored.
        boolean conflict = sameDay.stream()
            .anyMatch(s -> excludeShiftId == null || !java.util.Objects.equals(s.getId(), excludeShiftId));
        if (conflict) {
            var day = startAt.atZoneSameInstant(SCHEDULE_ZONE).toLocalDate();
            throw new ShiftConflictException(
                "Användaren har redan ett arbetspass den " + day
                    + ". Endast ett arbetspass per användare och dag är tillåtet.");
        }
    }
}