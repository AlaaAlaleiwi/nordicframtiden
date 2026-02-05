package com.nordicframtiden.contact;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ContactRequestService {

  private final ContactRequestRepository repo;

  public ContactRequestService(ContactRequestRepository repo) {
    this.repo = repo;
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

    return repo.save(c);
  }

  @Transactional
  public ContactRequest markHandled(Long id, boolean handled, String adminNote) {
    ContactRequest c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Not found"));
    c.setHandled(handled);
    c.setAdminNote(safeOrNull(adminNote));
    c.setHandledAt(handled ? OffsetDateTime.now() : null);
    return repo.save(c);
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