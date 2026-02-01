package com.nordicframtiden.pharmacy;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
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
public List<ScheduleShift> listForCurrentUser(Authentication auth, OffsetDateTime start, OffsetDateTime end) {
    if (auth == null || auth.getName() == null) {
      throw new IllegalArgumentException("Not authenticated");
    }

    AppUser u = userRepo.findByUsername(auth.getName())
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

    return shiftRepo.findInRange(start, end, null, u.getId());
  }
@Transactional
public ScheduleShift create(Long pharmacyId, Long userId,
                            OffsetDateTime startAt, OffsetDateTime endAt, String note) {
  validateRange(startAt, endAt);

  if (pharmacyId == null) {
    throw new IllegalArgumentException("pharmacyId is required");
  }

  AppUser user = userRepo.findById(userId)
      .orElseThrow(() -> new IllegalArgumentException("User not found"));

  Pharmacy pharmacy = pharmacyRepo.findById(pharmacyId)
      .orElseThrow(() -> new IllegalArgumentException("Pharmacy not found"));

  ScheduleShift s = new ScheduleShift();
  s.setPharmacy(pharmacy);   // ✅ never null here
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