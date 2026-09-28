package com.nordicframtiden.gdpr;

import com.nordicframtiden.security.service.UserService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * GDPR self-service endpoints:
 * - GET  /api/gdpr/me                        — current consent states (Art. 7).
 * - PUT  /api/gdpr/me/consents               — grant/withdraw a consent (Art. 7(3)).
 * - GET  /api/gdpr/me/consents               — full consent history (accountability).
 * - GET  /api/gdpr/me/export                 — machine-readable data export (Art. 15/20).
 * - POST /api/gdpr/me/export/email           — queue the export as an email (24h).
 * - POST /api/gdpr/me/deletion-request       — file an erasure request (Art. 17) for
 *                                              admin review; executed on a scheduled
 *                                              date by the nightly job (deleting a
 *                                              user also deletes the schedule and
 *                                              payment history tied to it, so it is
 *                                              never immediate).
 * - GET  /api/gdpr/deletion-requests         — ADMIN review queue.
 * Admins may act on behalf of a user via /api/gdpr/users/{id}/... .
 */
@RestController
@RequestMapping({"/api/gdpr"})
public class GdprController {

  private static final java.util.Set<String> CONSENT_TYPES = java.util.Set.of(
      GdprConsent.TYPE_TERMS,
      GdprConsent.TYPE_DATA_PROCESSING,
      GdprConsent.TYPE_EMAIL_NOTIFICATIONS,
      GdprConsent.TYPE_PUSH_NOTIFICATIONS);

  private final GdprService gdprService;
  private final GdprDeletionService deletionService;
  private final com.nordicframtiden.security.repo.AppUserRepository userRepo;
  private final UserService userService;

  public GdprController(
      GdprService gdprService,
      GdprDeletionService deletionService,
      com.nordicframtiden.security.repo.AppUserRepository userRepo,
      UserService userService) {
    this.gdprService = gdprService;
    this.deletionService = deletionService;
    this.userRepo = userRepo;
    this.userService = userService;
  }

  private Long currentUserId(Authentication auth) {
    return userRepo.findByUsername(auth.getName())
        .orElseThrow(() -> new IllegalArgumentException("User not found"))
        .getId();
  }

  private String currentUsername(Authentication auth) {
    return auth.getName();
  }

  // ---------- Consents ----------

  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public Map<String, Boolean> myConsents(Authentication auth) {
    return gdprService.currentConsents(currentUserId(auth));
  }

  @PutMapping("/me/consents")
  @PreAuthorize("isAuthenticated()")
  public Map<String, Boolean> setConsent(@RequestBody ConsentRequest request, Authentication auth) {
    if (request.type() == null || !CONSENT_TYPES.contains(request.type())) {
      throw new IllegalArgumentException("Unknown consent type");
    }
    GdprConsent saved = gdprService.recordConsent(
        currentUserId(auth), currentUsername(auth), request.type(), request.granted());
    return Map.of(saved.getConsentType(), saved.isGranted());
  }

  public record ConsentRequest(String type, boolean granted) {}

  @GetMapping("/me/consents")
  @PreAuthorize("isAuthenticated()")
  public List<GdprConsent> consentHistory(Authentication auth) {
    return gdprService.consentHistory(currentUserId(auth));
  }

  // ---------- Art. 15/20 export ----------

  @GetMapping("/me/export")
  @PreAuthorize("isAuthenticated()")
  public GdprService.GdprExport export(Authentication auth) {
    Long userId = currentUserId(auth);
    gdprService.recordConsent(
        userId, currentUsername(auth), GdprConsent.TYPE_ACCESS_REQUEST, true);
    return gdprService.export(userId);
  }

  /**
   * Email delivery of the export: the client asks the user to confirm ("the
   * export will be sent to your email within 24 hours") before calling this.
   * Queues the request; the 03:00 job emails the JSON. Idempotent while
   * pending.
   */
  @PostMapping("/me/export/email")
  @PreAuthorize("isAuthenticated()")
  public GdprExportRequest requestExportEmail(Authentication auth) {
    return gdprService.requestExportByEmail(currentUserId(auth));
  }

  /** Latest export-email request status, so the UI can show pending/sent. */
  @GetMapping("/me/export/email")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<GdprExportRequest> latestExportRequest(Authentication auth) {
    return gdprService.latestExportRequest(currentUserId(auth))
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  // ---------- Art. 17 erasure: admin-reviewed scheduled deletion ----------

  /**
   * Files a deletion request for review. Deleting a user also deletes their
   * schedule history (needed for payroll), so erasure is NOT immediate: an
   * admin approves and schedules it at least MIN_GRACE_DAYS out, the user is
   * emailed the date, and the nightly job executes after that date.
   */
  @PostMapping("/me/deletion-request")
  @PreAuthorize("isAuthenticated()")
  public GdprDeletionRequest requestDeletion(
      @RequestBody(required = false) DeletionRequestRequest body, Authentication auth) {
    return deletionService.request(currentUserId(auth), body == null ? null : body.reason());
  }

  public record DeletionRequestRequest(String reason) {}

  /** The requester withdraws their own request before execution. */
  @PostMapping("/me/deletion-request/{id}/cancel")
  @PreAuthorize("isAuthenticated()")
  public GdprDeletionRequest cancelDeletion(@PathVariable Long id, Authentication auth) {
    return deletionService.cancel(currentUserId(auth), id);
  }

  /** The caller's deletion requests, newest first. */
  @GetMapping("/me/deletion-request")
  @PreAuthorize("isAuthenticated()")
  public List<GdprDeletionRequest> myDeletionRequests(Authentication auth) {
    return deletionService.myRequests(currentUserId(auth));
  }

  /** ADMIN: all deletion requests (review queue). */
  @GetMapping("/deletion-requests")
  @PreAuthorize("hasRole('ADMIN')")
  public List<GdprDeletionRequest> allDeletionRequests() {
    return deletionService.allRequests();
  }

  /** ADMIN: approve with a scheduled execution date (>= 30 days out). */
  @PostMapping("/deletion-requests/{id}/approve")
  @PreAuthorize("hasRole('ADMIN')")
  public GdprDeletionRequest approveDeletion(
      @PathVariable Long id,
      @RequestBody ApproveDeletionRequest body,
      Authentication auth) {
    return deletionService.approve(id, auth.getName(), body.scheduledDate());
  }

  public record ApproveDeletionRequest(java.time.LocalDate scheduledDate) {}

  /** ADMIN: decline the request with a reason. */
  @PostMapping("/deletion-requests/{id}/reject")
  @PreAuthorize("hasRole('ADMIN')")
  public GdprDeletionRequest rejectDeletion(
      @PathVariable Long id,
      @RequestBody(required = false) RejectDeletionRequest body,
      Authentication auth) {
    return deletionService.reject(id, auth.getName(), body == null ? null : body.reason());
  }

  public record RejectDeletionRequest(String reason) {}

  // ---------- Admin: on behalf of a user ----------

  @GetMapping("/users/{id}/export")
  @PreAuthorize("hasRole('ADMIN')")
  public GdprService.GdprExport exportFor(@PathVariable Long id, Authentication auth) {
    gdprService.recordConsent(id, auth.getName(), GdprConsent.TYPE_ACCESS_REQUEST, true);
    return gdprService.export(id);
  }
}
