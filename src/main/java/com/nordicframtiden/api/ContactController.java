package com.nordicframtiden.api;

import com.nordicframtiden.contact.ContactRequest;
import com.nordicframtiden.contact.ContactRequestService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/contact")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class ContactController {

  private final ContactRequestService service;

  public ContactController(ContactRequestService service) {
    this.service = service;
  }

  // ---------- Public POST ----------
  public record CreateContactRequest(
      @NotBlank String type,
      @NotBlank String name,
      String organization,
      @NotBlank String email,
      String phone,
      @NotBlank String topic,
      @NotBlank String message
  ) {}

  public record ContactResponse(
      Long id,
      String type,
      String name,
      String organization,
      String email,
      String phone,
      String topic,
      String message,
      boolean handled,
      String adminNote,
      OffsetDateTime createdAt,
      OffsetDateTime handledAt
  ) {
    static ContactResponse from(ContactRequest c) {
      return new ContactResponse(
          c.getId(),
          c.getType(),
          c.getName(),
          c.getOrganization(),
          c.getEmail(),
          c.getPhone(),
          c.getTopic(),
          c.getMessage(),
          c.isHandled(),
          c.getAdminNote(),
          c.getCreatedAt(),
          c.getHandledAt()
      );
    }
  }

  @PostMapping
  @PreAuthorize("permitAll()") // IMPORTANT: allow unauthenticated
  public ContactResponse create(@RequestBody CreateContactRequest req) {
    var created = service.create(new ContactRequestService.CreateContactRequest(
        req.type(), req.name(), req.organization(), req.email(), req.phone(), req.topic(), req.message()
    ));
    return ContactResponse.from(created);
  }

  // ---------- Admin endpoints ----------
  @GetMapping
   
  public List<ContactResponse> list() {
    return service.list().stream().map(ContactResponse::from).toList();
  }

  @GetMapping("/unhandled-count")
 
  public long unhandledCount() {
    return service.unreadCount();
  }

  public record HandleRequest(Boolean handled, String adminNote) {}

  @PutMapping("/{id}/handled")
  @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
  public ContactResponse markHandled(@PathVariable Long id, @RequestBody HandleRequest req) {
    boolean handled = req.handled() != null && req.handled();
    return ContactResponse.from(service.markHandled(id, handled, req.adminNote()));
  }

  @DeleteMapping("/{id}")
 
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}