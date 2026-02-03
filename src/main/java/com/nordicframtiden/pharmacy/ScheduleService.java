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
}