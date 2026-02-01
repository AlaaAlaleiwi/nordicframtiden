package com.nordicframtiden.availability;

import com.nordicframtiden.security.repo.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AvailabilityService {

  private final AvailabilityRequestRepository repo;
  private final AppUserRepository userRepo;

  public AvailabilityService(AvailabilityRequestRepository repo, AppUserRepository userRepo) {
    this.repo = repo;
    this.userRepo = userRepo;
  }

  public List<AvailabilityRequest> my(Authentication auth) {
    var user = userRepo.findByUsername(auth.getName())
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
    return repo.findByUserIdOrderByCreatedAtDesc(user.getId());
  }

  @Transactional
  public AvailabilityRequest createForMe(Authentication auth,
                                        AvailabilityRequest.Type type,
                                        LocalDate start,
                                        LocalDate end,
                                        String note) {

    var user = userRepo.findByUsername(auth.getName())
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (start == null || end == null) throw new IllegalArgumentException("start/end required");
    if (end.isBefore(start)) throw new IllegalArgumentException("end must be >= start");

    // optional safety: max 62 days
    if (start.plusDays(62).isBefore(end)) throw new IllegalArgumentException("range too large");

    var r = new AvailabilityRequest();
    r.setUser(user);
    r.setType(type);
    r.setStartDate(start);
    r.setEndDate(end);
    r.setNote(note == null ? null : note.trim());

    return repo.save(r);
  }

  public List<AvailabilityRequest> all() {
    return repo.findAllOrderByCreatedAtDesc();
  }

  @Transactional
  public AvailabilityRequest setStatus(Long id, AvailabilityRequest.Status status) {
    var r = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Request not found"));
    r.setStatus(status);
    return repo.save(r);
  }
}