package com.nordicframtiden.contact;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContactRequestServiceTest {

  @Test
  void contactRequestRemainsSavedWhenNotificationEmailFails() {
    ContactRequestRepository repo = mock(ContactRequestRepository.class);
    ContactNotificationService notifications = mock(ContactNotificationService.class);
    ContactRequestService service = new ContactRequestService(repo, notifications);
    ContactRequest saved = new ContactRequest();

    when(repo.save(any(ContactRequest.class))).thenReturn(saved);
    doThrow(new MailAuthenticationException("bad credentials"))
        .when(notifications).sendNewContactRequestNotification(saved);

    ContactRequest result = service.create(new ContactRequestService.CreateContactRequest(
        "APOTEK", "Test User", null, "test@example.com", null,
        "KONSULTATION", "This message is long enough"));

    assertSame(saved, result);
    verify(repo).save(any(ContactRequest.class));
    verify(notifications).sendNewContactRequestNotification(saved);
  }

  @Test
  void adminUpdateRemainsSavedWhenReplyEmailFails() {
    ContactRequestRepository repo = mock(ContactRequestRepository.class);
    ContactNotificationService notifications = mock(ContactNotificationService.class);
    ContactRequestService service = new ContactRequestService(repo, notifications);
    ContactRequest saved = new ContactRequest();

    when(repo.findById(42L)).thenReturn(Optional.of(saved));
    when(repo.save(saved)).thenReturn(saved);
    doThrow(new MailAuthenticationException("bad credentials"))
        .when(notifications).sendAdminReplyNotification(saved, "We will contact you");

    ContactRequest result = service.markHandled(42L, true, "We will contact you");

    assertSame(saved, result);
    verify(repo).save(saved);
    verify(notifications).sendAdminReplyNotification(saved, "We will contact you");
  }
}
