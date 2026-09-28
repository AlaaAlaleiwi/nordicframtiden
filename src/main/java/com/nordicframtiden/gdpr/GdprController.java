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
 * - GET  /api/gdpr/me              — current consent states (Art. 7).
 * - PUT  /api/gdpr/me/consents     — grant/withdraw a consent (Art. 7(3)).
 * - GET  /api/gdpr/me/consents     — full consent history (accountability).
 * - GET  /api/gdpr/me/export       — machine-readable data export (Art. 15/20).
 * - DELETE /api/gdpr/me            — erasure (Art. 17): deletes the account
 *                                    and every referencing row.
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
  private final com.nordicframtiden.security.repo.AppUserRepository userRepo;
  private final UserService userService;

  public GdprController(
      GdprService gdprService,
      com.nordicframtiden.security.repo.AppUserRepository userRepo,
      UserService userService) {
    this.gdprService = gdprService;
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

  // ---------- Art. 17 erasure ----------

  @DeleteMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> eraseMe(Authentication auth) {
    Long userId = currentUserId(auth);
    String username = currentUsername(auth);
    gdprService.recordConsent(userId, username, GdprConsent.TYPE_ERASURE_REQUEST, true);
    // The account itself (and the ADMIN guard from UserService) still applies:
    // admins erase through the admin endpoints or another admin does it.
    userService.deleteUser(userId);
    return ResponseEntity.ok(Map.of("erased", true, "username", username));
  }

  // ---------- Admin: on behalf of a user ----------

  @GetMapping("/users/{id}/export")
  @PreAuthorize("hasRole('ADMIN')")
  public GdprService.GdprExport exportFor(@PathVariable Long id, Authentication auth) {
    gdprService.recordConsent(id, auth.getName(), GdprConsent.TYPE_ACCESS_REQUEST, true);
    return gdprService.export(id);
  }

  @DeleteMapping("/users/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Map<String, Object>> eraseFor(@PathVariable Long id, Authentication auth) {
    gdprService.recordConsent(id, auth.getName(), GdprConsent.TYPE_ERASURE_REQUEST, true);
    userService.deleteUser(id);
    return ResponseEntity.ok(Map.of("erased", true, "userId", id));
  }
}
