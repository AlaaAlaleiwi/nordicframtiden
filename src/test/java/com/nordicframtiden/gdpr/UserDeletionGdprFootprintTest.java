package com.nordicframtiden.gdpr;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.nordicframtiden.admin.model.AdminProfileRepository;
import com.nordicframtiden.availability.AvailabilityRequestRepository;
import com.nordicframtiden.chat.CallHistoryRepository;
import com.nordicframtiden.chat.ChatAttachmentRepository;
import com.nordicframtiden.chat.ChatMessageRepository;
import com.nordicframtiden.chat.ChatPushSubscriptionRepository;
import com.nordicframtiden.chat.ChatReactionRepository;
import com.nordicframtiden.chat.ChatRoomMemberRepository;
import com.nordicframtiden.chat.ChatRoomRepository;
import com.nordicframtiden.company.StaffShiftRepository;
import com.nordicframtiden.documents.ProfileDocumentRepository;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.PasswordResetTokenRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import com.nordicframtiden.security.service.PasswordResetService;
import com.nordicframtiden.security.service.UserService;
import com.nordicframtiden.service.model.PayslipSnapshotRepository;
import com.nordicframtiden.settings.EmailService;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * GDPR Art. 17 completeness for the pharmacist account path: erasure must
 * clean EVERY table referencing the account. The original path missed chat
 * memberships, reactions, push subscriptions, reset tokens and profile
 * documents — rows that either blocked the delete or kept personal data
 * alive after erasure.
 */
@ExtendWith(MockitoExtension.class)
class UserDeletionGdprFootprintTest {

  @Mock private AppUserRepository repo;
  @Mock private UserProfileRepository userProfileRepo;
  @Mock private AdminProfileRepository adminProfileRepo;
  @Mock private PasswordEncoder encoder;
  @Mock private EmailService emailService;
  @Mock private PasswordResetService passwordResetService;
  @Mock private ChatMessageRepository chatMessageRepo;
  @Mock private ChatReactionRepository chatReactionRepo;
  @Mock private ChatRoomRepository chatRoomRepo;
  @Mock private ChatRoomMemberRepository chatRoomMemberRepo;
  @Mock private CallHistoryRepository callHistoryRepo;
  @Mock private ChatPushSubscriptionRepository pushSubscriptionRepo;
  @Mock private StaffShiftRepository staffShiftRepo;
  @Mock private AvailabilityRequestRepository availabilityRequestRepo;
  @Mock private ChatAttachmentRepository chatAttachmentRepo;
  @Mock private PayslipSnapshotRepository payslipSnapshotRepo;
  @Mock private PasswordResetTokenRepository resetTokenRepo;
  @Mock private ProfileDocumentRepository profileDocumentRepo;

  private UserService service;
  private AppUser pharmacist;

  @BeforeEach
  void setUp() {
    service = new UserService(repo, userProfileRepo, encoder, emailService,
        staffShiftRepo, availabilityRequestRepo, callHistoryRepo, chatRoomRepo,
        chatMessageRepo, chatAttachmentRepo, payslipSnapshotRepo,
        chatRoomMemberRepo, chatReactionRepo, pushSubscriptionRepo,
        resetTokenRepo, profileDocumentRepo);

    pharmacist = new AppUser();
    pharmacist.setId(7L);
    pharmacist.setUsername("pharm");
    pharmacist.setRoles(new java.util.HashSet<>(Set.of(Role.USER)));
    lenient().when(repo.findById(7L)).thenReturn(Optional.of(pharmacist));
    lenient().when(userProfileRepo.findByUserId(7L)).thenReturn(Optional.empty());
  }

  @Test
  void erasing_a_pharmacist_cleans_every_referencing_table() {
    service.deleteUser(7L);

    org.mockito.InOrder inOrder = inOrder(
        chatRoomRepo, chatMessageRepo, chatReactionRepo, chatRoomMemberRepo,
        pushSubscriptionRepo, resetTokenRepo, profileDocumentRepo,
        staffShiftRepo, availabilityRequestRepo, callHistoryRepo,
        chatAttachmentRepo, payslipSnapshotRepo, repo);

    inOrder.verify(chatRoomRepo).deleteByCreatedBy(pharmacist);
    inOrder.verify(chatMessageRepo).deleteBySender(pharmacist);
    inOrder.verify(chatReactionRepo).deleteByUser(pharmacist);
    inOrder.verify(chatRoomMemberRepo).deleteByUserId(7L);
    inOrder.verify(pushSubscriptionRepo).deleteByUser(pharmacist);
    inOrder.verify(resetTokenRepo).deleteByUserId(7L);
    inOrder.verify(profileDocumentRepo).deleteByUserId(7L);
    inOrder.verify(staffShiftRepo).deleteByUser(pharmacist);
    inOrder.verify(availabilityRequestRepo).deleteByUser(pharmacist);
    inOrder.verify(callHistoryRepo).deleteByCaller(pharmacist);
    inOrder.verify(chatAttachmentRepo).deleteByUploaderId(7L);
    inOrder.verify(payslipSnapshotRepo).deleteByUserId(7L);
    inOrder.verify(repo).delete(pharmacist);
  }

  @Test
  void erasing_an_unknown_account_fails_cleanly() {
    when(repo.findById(999L)).thenReturn(Optional.empty());
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> service.deleteUser(999L));
    org.mockito.Mockito.verify(repo, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.any());
  }
}
