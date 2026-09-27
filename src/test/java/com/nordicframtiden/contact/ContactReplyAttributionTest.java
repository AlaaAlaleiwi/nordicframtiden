package com.nordicframtiden.contact;

import com.nordicframtiden.admin.model.AdminProfile;
import com.nordicframtiden.admin.model.AdminProfileRepository;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import com.nordicframtiden.security.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reply attribution: when an admin/staff replies (markHandled), the request
 * records WHO replied (display name + username). A pure-admin account has no
 * user_profile, so the admin_profile is consulted; username is the fallback.
 * Re-replies keep the first replier.
 */
class ContactReplyAttributionTest {

  private ContactRequestRepository repo;
  private ContactNotificationService notifications;
  private AppUserRepository appUserRepository;
  private UserProfileRepository userProfileRepository;
  private AdminProfileRepository adminProfileRepository;
  private ContactRequestService service;

  private AppUser actor;
  private ContactRequest saved;

  @BeforeEach
  void setUp() {
    repo = mock(ContactRequestRepository.class);
    notifications = mock(ContactNotificationService.class);
    appUserRepository = mock(AppUserRepository.class);
    userProfileRepository = mock(UserProfileRepository.class);
    adminProfileRepository = mock(AdminProfileRepository.class);
    service = new ContactRequestService(repo, notifications, appUserRepository, userProfileRepository, adminProfileRepository);

    actor = new AppUser();
    actor.setId(9L);
    actor.setUsername("alaa.admin");
    when(appUserRepository.findByUsername("alaa.admin")).thenReturn(Optional.of(actor));

    saved = new ContactRequest();
    when(repo.findById(42L)).thenReturn(Optional.of(saved));
    when(repo.save(any(ContactRequest.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void replyRecordsDisplayNameFromUserProfile() {
    UserProfile profile = new UserProfile();
    profile.setFullName("Alaa Alaleiwi");
    when(userProfileRepository.findByUserId(9L)).thenReturn(Optional.of(profile));

    service.markHandled(42L, true, "We will contact you tomorrow.", "alaa.admin");

    assertEquals("Alaa Alaleiwi", saved.getHandledByName());
    assertEquals("alaa.admin", saved.getHandledByUsername());
  }

  @Test
  void pureAdminFallsBackToAdminProfileName() {
    when(userProfileRepository.findByUserId(9L)).thenReturn(Optional.empty());
    AdminProfile adminProfile = new AdminProfile();
    adminProfile.setFullName("Pure Admin");
    when(adminProfileRepository.findByUserId(9L)).thenReturn(Optional.of(adminProfile));

    service.markHandled(42L, true, "Thanks for reaching out.", "alaa.admin");

    assertEquals("Pure Admin", saved.getHandledByName());
    assertEquals("alaa.admin", saved.getHandledByUsername());
  }

  @Test
  void unknownProfileFallsBackToUsername() {
    when(userProfileRepository.findByUserId(9L)).thenReturn(Optional.empty());
    when(adminProfileRepository.findByUserId(9L)).thenReturn(Optional.empty());

    service.markHandled(42L, true, "Thanks!", "alaa.admin");

    assertEquals("alaa.admin", saved.getHandledByName());
    assertEquals("alaa.admin", saved.getHandledByUsername());
  }

  @Test
  void handledAtIsSetWhenHandling() {
    service.markHandled(42L, true, "Closing this.", "alaa.admin");
    assertEquals(saved.getHandledAt() != null, saved.isHandled());
  }

  @Test
  void reReplyKeepsTheFirstReplier() {
    saved.setHandledByName("First Responder");
    saved.setHandledByUsername("first.admin");

    when(userProfileRepository.findByUserId(9L)).thenReturn(Optional.empty());
    when(adminProfileRepository.findByUserId(9L)).thenReturn(Optional.empty());

    service.markHandled(42L, true, "One more note.", "alaa.admin");

    assertEquals("First Responder", saved.getHandledByName());
    assertEquals("first.admin", saved.getHandledByUsername());
  }

  @Test
  void plainHandledWithoutNoteStillAttributes() {
    when(userProfileRepository.findByUserId(9L)).thenReturn(Optional.empty());
    when(adminProfileRepository.findByUserId(9L)).thenReturn(Optional.empty());

    service.markHandled(42L, true, null, "alaa.admin");

    assertEquals("alaa.admin", saved.getHandledByName());
  }

  @Test
  void mailFailureDoesNotBreakAttribution() {
    when(userProfileRepository.findByUserId(9L)).thenReturn(Optional.empty());
    when(adminProfileRepository.findByUserId(9L)).thenReturn(Optional.empty());
    doThrow(new MailAuthenticationException("bad credentials"))
        .when(notifications).sendAdminReplyNotification(any(), any());

    service.markHandled(42L, true, "We will contact you.", "alaa.admin");

    assertEquals("alaa.admin", saved.getHandledByName());
  }

  @Test
  void systemCallsWithoutActorSkipAttribution() {
    // Backwards compatibility: null actor must not crash or attribute.
    service.markHandled(42L, true, "note", null);

    assertNull(saved.getHandledByName());
    assertNull(saved.getHandledByUsername());
  }
}
