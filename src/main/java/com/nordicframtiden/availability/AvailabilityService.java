package com.nordicframtiden.availability;

import com.nordicframtiden.api.AvailabilityController;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AvailabilityService {

  private final AvailabilityRequestRepository repo;
  private final AppUserRepository userRepo;
  private final UserProfileRepository profileRepo;

  public AvailabilityService(
      AvailabilityRequestRepository repo,
      AppUserRepository userRepo,
      UserProfileRepository profileRepo
  ) {
    this.repo = repo;
    this.userRepo = userRepo;
    this.profileRepo = profileRepo;
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
    if (start.plusDays(62).isBefore(end)) throw new IllegalArgumentException("range too large");

    var r = new AvailabilityRequest();
    r.setUser(user);
    r.setType(type);
    r.setStartDate(start);
    r.setEndDate(end);
    r.setNote(note == null ? null : note.trim());

    // ✅ default status if your entity doesn’t do it
    if (r.getStatus() == null) r.setStatus(AvailabilityRequest.Status.PENDING);

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

  @Transactional(readOnly = true)
  public List<AvailabilityController.AvailabilityRow> getApprovedOverlapping(LocalDate start, LocalDate end) {
    var rows = repo.findApprovedOverlapping(start, end);

    return rows.stream().map(a -> {
      var u = a.getUser();                 // ✅ use relation
      var userId = u.getId();

      var p = profileRepo.findByUserId(userId).orElse(null);

      String username = u.getUsername();
      String fullName = (p != null ? p.getFullName() : null);

      return new AvailabilityController.AvailabilityRow(
          a.getId(),
          userId,
          username,
          fullName,
          a.getType().name(),
          a.getStartDate().toString(),
          a.getEndDate().toString(),
          a.getStatus().name(),
          a.getNote()
      );
    }).toList();
  }
}