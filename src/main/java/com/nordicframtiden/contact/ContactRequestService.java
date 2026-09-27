package com.nordicframtiden.contact;

import com.nordicframtiden.admin.model.AdminProfileRepository;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ContactRequestService {

  private static final Logger log = LoggerFactory.getLogger(ContactRequestService.class);

  private final ContactRequestRepository repo;
  private final ContactNotificationService contactNotificationService;
  private final AppUserRepository appUserRepository;
  private final UserProfileRepository userProfileRepository;
  private final AdminProfileRepository adminProfileRepository;

  public ContactRequestService(ContactRequestRepository repo,
                              ContactNotificationService contactNotificationService,
                              AppUserRepository appUserRepository,
                              UserProfileRepository userProfileRepository,
                              AdminProfileRepository adminProfileRepository) {
    this.repo = repo;
    this.contactNotificationService = contactNotificationService;
    this.appUserRepository = appUserRepository;
    this.userProfileRepository = userProfileRepository;
    this.adminProfileRepository = adminProfileRepository;
  }

  public List<ContactRequest> list() {
    return repo.findAllNewestFirst();
  }

  public long unreadCount() {
    return repo.countByHandledFalse();
  }

  @Transactional
  public ContactRequest create(CreateContactRequest req) {
    String name = safe(req.name());
    String email = safe(req.email());
    String msg = safe(req.message());

    if (name.length() < 2) throw new IllegalArgumentException("Name is required");
    if (!email.contains("@") || email.length() < 5) throw new IllegalArgumentException("Valid email is required");
    if (msg.length() < 10) throw new IllegalArgumentException("Message too short");

    ContactRequest c = new ContactRequest();
    c.setType(safe(req.type()));
    c.setName(name);
    c.setOrganization(safeOrNull(req.organization()));
    c.setEmail(email);
    c.setPhone(safeOrNull(req.phone()));
    c.setTopic(safe(req.topic()));
    c.setMessage(msg);

    ContactRequest saved = repo.save(c);
    try {
      contactNotificationService.sendNewContactRequestNotification(saved);
    } catch (MailException e) {
      log.error("Contact request {} was saved, but its notification email could not be sent",
          saved.getId(), e);
    }
    return saved;
  }

  /**
   * Marks the request handled/unhandled and records WHO replied. The actor
   * is resolved to a display name via user_profile, then admin_profile
   * (pure admins), then the username. Re-replies keep the first replier.
   * {@code actorUsername} may be null for system calls — attribution is
   * skipped in that case.
   */
  @Transactional
  public ContactRequest markHandled(Long id, boolean handled, String adminNote, String actorUsername) {
    ContactRequest c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Not found"));
    String previousNote = c.getAdminNote();
    String nextNote = safeOrNull(adminNote);

    c.setHandled(handled);
    c.setAdminNote(nextNote);
    c.setHandledAt(handled ? OffsetDateTime.now() : null);

    if (actorUsername != null) {
      applyActorAttribution(c, actorUsername);
    }

    ContactRequest saved = repo.save(c);

    if (nextNote != null && !nextNote.isBlank() && !nextNote.equals(previousNote)) {
      try {
        contactNotificationService.sendAdminReplyNotification(saved, nextNote);
      } catch (MailException e) {
        log.error("Contact request {} was updated, but its reply email could not be sent",
            saved.getId(), e);
      }
    }

    return saved;
  }

  /** Resolves the actor's display name and stamps it on the request. */
  private void applyActorAttribution(ContactRequest c, String actorUsername) {
    if (c.getHandledByUsername() == null) {
      // No replier recorded yet: attribute to this actor. Re-replies keep
      // the first replier.
      c.setHandledByUsername(actorUsername);
      c.setHandledByName(resolveDisplayName(actorUsername));
    }
  }

  private String resolveDisplayName(String username) {
    Optional<AppUser> user = appUserRepository.findByUsername(username);
    if (user.isPresent()) {
      String viaUserProfile = userProfileRepository.findByUserId(user.get().getId())
          .map(p -> p.getFullName())
          .filter(n -> n != null && !n.isBlank())
          .orElse(null);
      if (viaUserProfile != null) return viaUserProfile;
      String viaAdminProfile = adminProfileRepository.findByUserId(user.get().getId())
          .map(p -> p.getFullName())
          .filter(n -> n != null && !n.isBlank())
          .orElse(null);
      if (viaAdminProfile != null) return viaAdminProfile;
    }
    return username;
  }

  @Transactional
  public void delete(Long id) {
    ContactRequest c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Not found"));
    repo.delete(c);
  }

  public record CreateContactRequest(
      String type,
      String name,
      String organization,
      String email,
      String phone,
      String topic,
      String message
  ) {}

  private static String safe(String s) {
    return s == null ? "" : s.trim();
  }
  private static String safeOrNull(String s) {
    String x = safe(s);
    return x.isEmpty() ? null : x;
  }
}
