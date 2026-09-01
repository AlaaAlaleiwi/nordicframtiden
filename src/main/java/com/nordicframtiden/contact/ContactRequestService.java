package com.nordicframtiden.contact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ContactRequestService {

  private static final Logger log = LoggerFactory.getLogger(ContactRequestService.class);

  private final ContactRequestRepository repo;
  private final ContactNotificationService contactNotificationService;

  public ContactRequestService(ContactRequestRepository repo,
                              ContactNotificationService contactNotificationService) {
    this.repo = repo;
    this.contactNotificationService = contactNotificationService;
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

  @Transactional
  public ContactRequest markHandled(Long id, boolean handled, String adminNote) {
    ContactRequest c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Not found"));
    String previousNote = c.getAdminNote();
    String nextNote = safeOrNull(adminNote);

    c.setHandled(handled);
    c.setAdminNote(nextNote);
    c.setHandledAt(handled ? OffsetDateTime.now() : null);

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
