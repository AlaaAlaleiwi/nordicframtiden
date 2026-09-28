package com.nordicframtiden.gdpr;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.UserProfile;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import com.nordicframtiden.security.service.UserService;
import com.nordicframtiden.settings.EmailService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-reviewed erasure workflow (Art. 17 with business continuity):
 * deleting a user also deletes their schedule history, which payroll needs —
 * so the data subject files a request, an ADMIN approves it with an execution
 * date (at least {@link GdprDeletionRequest#MIN_GRACE_DAYS} days out), and the
 * nightly job performs the deletion after that date. The user is emailed at
 * request, approval and completion.
 */
@Service
public class GdprDeletionService {

  private static final Logger log = LoggerFactory.getLogger(GdprDeletionService.class);
  private static final ZoneId ZONE = ZoneId.of("Europe/Stockholm");
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

  private final GdprDeletionRequestRepository requests;
  private final AppUserRepository userRepo;
  private final UserProfileRepository profileRepo;
  private final UserService userService;
  private final EmailService emailService;
  private final GdprService gdprService;

  public GdprDeletionService(
      GdprDeletionRequestRepository requests,
      AppUserRepository userRepo,
      UserProfileRepository profileRepo,
      UserService userService,
      EmailService emailService,
      GdprService gdprService) {
    this.requests = requests;
    this.userRepo = userRepo;
    this.profileRepo = profileRepo;
    this.userService = userService;
    this.emailService = emailService;
    this.gdprService = gdprService;
  }

  // ---------- Data subject ----------

  /** Files a deletion request for the caller. Idempotent while one is open. */
  @Transactional
  public GdprDeletionRequest request(Long userId, String reason) {
    requests.findTopByUserIdAndStatusInOrderByCreatedAtDesc(
            userId,
            List.of(GdprDeletionRequest.STATUS_PENDING, GdprDeletionRequest.STATUS_APPROVED))
        .ifPresent(open -> {
          throw new IllegalStateException("A deletion request is already open");
        });

    AppUser user = userRepo.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
    if (user.getRoles() != null && user.getRoles().stream()
        .anyMatch(r -> r.name().contains("ADMIN"))) {
      throw new IllegalStateException("Admin accounts delete through the admin tooling");
    }
    String email = profileRepo.findByUserId(userId).map(UserProfile::getEmail)
        .filter(e -> e != null && !e.isBlank())
        .orElse(null);

    GdprDeletionRequest saved = requests.save(
        new GdprDeletionRequest(userId, user.getUsername(), email,
            reason == null ? null : reason.trim()));
    gdprService.recordConsent(userId, user.getUsername(), GdprConsent.TYPE_ERASURE_REQUEST, true);

    // Notice: request received and awaiting admin review (best-effort).
    try {
      if (email != null) {
        emailService.sendGdprDeletionReceivedEmail(email, displayName(userId, user.getUsername()));
      }
    } catch (Exception e) {
      log.warn("Deletion-request notice failed for user {}: {}", userId, e.getMessage());
    }
    return saved;
  }

  /** The requester withdraws their own open request (any pre-DELETED state). */
  @Transactional
  public GdprDeletionRequest cancel(Long userId, Long requestId) {
    GdprDeletionRequest request = requests.findById(requestId)
        .orElseThrow(() -> new IllegalArgumentException("Request not found"));
    if (!request.getUserId().equals(userId)) {
      throw new IllegalStateException("Not your request");
    }
    if (GdprDeletionRequest.STATUS_DELETED.equals(request.getStatus())
        || GdprDeletionRequest.STATUS_CANCELLED.equals(request.getStatus())) {
      throw new IllegalStateException("Request is already closed");
    }
    request.setStatus(GdprDeletionRequest.STATUS_CANCELLED);
    request.setCancelledAt(java.time.Instant.now());
    return requests.save(request);
  }

  // ---------- Admin ----------

  /** ADMIN approves the request and fixes the execution date. */
  @Transactional
  public GdprDeletionRequest approve(Long requestId, String adminUsername, LocalDate scheduledDate) {
    GdprDeletionRequest request = requests.findById(requestId)
        .orElseThrow(() -> new IllegalArgumentException("Request not found"));
    if (!GdprDeletionRequest.STATUS_PENDING.equals(request.getStatus())) {
      throw new IllegalStateException("Only pending requests can be approved");
    }
    LocalDate earliest = LocalDate.now(ZONE).plusDays(GdprDeletionRequest.MIN_GRACE_DAYS);
    if (scheduledDate == null || scheduledDate.isBefore(earliest)) {
      throw new IllegalArgumentException(
          "Scheduled date must be at least " + GdprDeletionRequest.MIN_GRACE_DAYS + " days out");
    }
    request.setStatus(GdprDeletionRequest.STATUS_APPROVED);
    request.setScheduledDate(scheduledDate);
    request.setApprovedBy(adminUsername);
    request.setApprovedAt(java.time.Instant.now());
    GdprDeletionRequest saved = requests.save(request);

    try {
      if (request.getEmail() != null) {
        emailService.sendGdprDeletionScheduledEmail(
            request.getEmail(),
            displayName(request.getUserId(), request.getUsername()),
            scheduledDate);
      }
    } catch (Exception e) {
      log.warn("Deletion-scheduled notice failed for user {}: {}", request.getUserId(), e.getMessage());
    }
    return saved;
  }

  /** ADMIN declines the request, with a reason shown to the requester. */
  @Transactional
  public GdprDeletionRequest reject(Long requestId, String adminUsername, String reason) {
    GdprDeletionRequest request = requests.findById(requestId)
        .orElseThrow(() -> new IllegalArgumentException("Request not found"));
    if (!GdprDeletionRequest.STATUS_PENDING.equals(request.getStatus())) {
      throw new IllegalStateException("Only pending requests can be rejected");
    }
    request.setStatus(GdprDeletionRequest.STATUS_REJECTED);
    request.setRejectedBy(adminUsername);
    request.setRejectedReason(reason == null ? null : reason.trim());
    return requests.save(request);
  }

  // ---------- Nightly execution ----------

  /**
   * Executes every approved request whose scheduled date has arrived.
   * Runs outside a class-level transaction on purpose: each deletion commits
   * independently and mail/audit failures must not roll back a deletion.
   */
  public int executeDue(LocalDate today) {
    List<GdprDeletionRequest> due =
        requests.findByStatusAndScheduledDateLessThanEqual(GdprDeletionRequest.STATUS_APPROVED, today);
    int executed = 0;
    for (GdprDeletionRequest request : due) {
      try {
        userService.deleteUser(request.getUserId());
        request.setStatus(GdprDeletionRequest.STATUS_DELETED);
        request.setDeletedAt(java.time.Instant.now());
        requests.save(request);
        executed++;
        try {
          if (request.getEmail() != null) {
            emailService.sendGdprDeletionCompletedEmail(
                request.getEmail(),
                request.getUsername() == null ? "user" : request.getUsername());
          }
        } catch (Exception mailFailure) {
          log.warn("Deletion-completed notice failed for user {}: {}",
              request.getUserId(), mailFailure.getMessage());
        }
      } catch (Exception e) {
        log.error("Scheduled deletion failed for user {}: {}",
            request.getUserId(), e.getMessage(), e);
      }
    }
    return executed;
  }

  // ---------- Queries ----------

  @Transactional(readOnly = true)
  public List<GdprDeletionRequest> allRequests() {
    return requests.findTop50ByOrderByCreatedAtDesc();
  }

  @Transactional(readOnly = true)
  public List<GdprDeletionRequest> myRequests(Long userId) {
    return requests.findByUserIdOrderByCreatedAtDesc(userId);
  }

  private String displayName(Long userId, String fallback) {
    return profileRepo.findByUserId(userId)
        .map(UserProfile::getFullName)
        .filter(n -> n != null && !n.isBlank())
        .orElse(fallback);
  }

  /** For the scheduled-email date formatting parity between services. */
  static String formatDate(LocalDate date) {
    return date.format(DATE_FMT.withLocale(Locale.ROOT));
  }
}
