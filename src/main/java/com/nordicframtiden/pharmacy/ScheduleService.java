package com.nordicframtiden.pharmacy;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ScheduleService {

  private final ScheduleShiftRepository shiftRepo;
  private final PharmacyRepository pharmacyRepo;
  private final AppUserRepository userRepo;

  public ScheduleService(
      ScheduleShiftRepository shiftRepo,
      PharmacyRepository pharmacyRepo,
      AppUserRepository userRepo
  ) {
    this.shiftRepo = shiftRepo;
    this.pharmacyRepo = pharmacyRepo;
    this.userRepo = userRepo;
  }

  public List<ScheduleShift> listRange(OffsetDateTime start, OffsetDateTime end, Long pharmacyId, Long userId) {
    return shiftRepo.findInRange(start, end, pharmacyId, userId);
  }

@Transactional
public ScheduleShift create(Long pharmacyId, Long userId, OffsetDateTime startAt, OffsetDateTime endAt, String note) {
  validateRange(startAt, endAt);

  AppUser user = userRepo.findById(userId)
      .orElseThrow(() -> new IllegalArgumentException("User not found"));

  Pharmacy pharmacy = null;

  if (pharmacyId != null) {
    pharmacy = pharmacyRepo.findById(pharmacyId)
        .orElseThrow(() -> new IllegalArgumentException("Pharmacy not found"));
  } else {
    // ✅ pharmacyId is missing/null => allow ONLY if user is STAFF
    if (user.getRoles() == null || !user.getRoles().contains(com.nordicframtiden.security.model.Role.STAFF)) {
      throw new IllegalArgumentException("pharmacyId is required for non-STAFF shifts");
    }
  }

  ScheduleShift s = new ScheduleShift();
  s.setPharmacy(pharmacy); // ✅ can be null (for staff)
  s.setUser(user);
  s.setStartAt(startAt);
  s.setEndAt(endAt);
  s.setNote(note);

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

    // if provided -> update
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

    if (startAt != null) s.setStartAt(startAt);
    if (endAt != null) s.setEndAt(endAt);

    // validate after changes
    validateRange(s.getStartAt(), s.getEndAt());

    if (note != null) s.setNote(note);

    return shiftRepo.save(s);
  }

  @Transactional
  public void delete(Long id) {
    shiftRepo.deleteById(id);
  }

  private static void validateRange(OffsetDateTime startAt, OffsetDateTime endAt) {
    if (startAt == null || endAt == null) throw new IllegalArgumentException("Start/end required");
    if (!startAt.isBefore(endAt)) throw new IllegalArgumentException("Invalid time range");
  }
}