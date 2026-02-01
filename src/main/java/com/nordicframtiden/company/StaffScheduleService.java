package com.nordicframtiden.company;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class StaffScheduleService {

  private final StaffShiftRepository repo;
  private final AppUserRepository userRepo;

  public StaffScheduleService(StaffShiftRepository repo, AppUserRepository userRepo) {
    this.repo = repo;
    this.userRepo = userRepo;
  }

  public List<StaffShift> listRange(OffsetDateTime start, OffsetDateTime end, Long userId) {
    return repo.findInRange(start, end, userId);
  }
public List<StaffShift> listForCurrentUser(Authentication auth, OffsetDateTime start, OffsetDateTime end) {
  if (auth == null || auth.getName() == null) {
    throw new IllegalArgumentException("Not authenticated");
  }

  AppUser u = userRepo.findByUsername(auth.getName())
      .orElseThrow(() -> new IllegalArgumentException("User not found"));

  return repo.findInRange(start, end, u.getId());
}
  @Transactional
  public StaffShift create(Long userId, OffsetDateTime startAt, OffsetDateTime endAt, String note) {
    if (userId == null) throw new IllegalArgumentException("userId is required");
    if (startAt == null || endAt == null) throw new IllegalArgumentException("startAt/endAt are required");
    if (!startAt.isBefore(endAt)) throw new IllegalArgumentException("startAt must be before endAt");

    var user = userRepo.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

    var s = new StaffShift();
    s.setUser(user);
    s.setStartAt(startAt);
    s.setEndAt(endAt);
    s.setNote(note);

    return repo.save(s);
  }

  @Transactional
  public StaffShift update(Long id, Long userId, OffsetDateTime startAt, OffsetDateTime endAt, String note) {
    var s = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Shift not found"));

    if (userId != null) {
      var user = userRepo.findById(userId)
          .orElseThrow(() -> new IllegalArgumentException("User not found"));
      s.setUser(user);
    }
    if (startAt != null) s.setStartAt(startAt);
    if (endAt != null) s.setEndAt(endAt);
    if (startAt != null || endAt != null) {
      if (!s.getStartAt().isBefore(s.getEndAt())) {
        throw new IllegalArgumentException("startAt must be before endAt");
      }
    }
    s.setNote(note);

    return repo.save(s);
  }

  @Transactional
  public void delete(Long id) {
    repo.deleteById(id);
  }
}