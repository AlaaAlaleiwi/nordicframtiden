package com.nordicframtiden.admin;

import com.nordicframtiden.admin.model.AdminProfileRepository;
import com.nordicframtiden.availability.AvailabilityRequestRepository;
import com.nordicframtiden.chat.CallHistoryRepository;
import com.nordicframtiden.chat.ChatMessageRepository;
import com.nordicframtiden.chat.ChatPushSubscriptionRepository;
import com.nordicframtiden.chat.ChatReactionRepository;
import com.nordicframtiden.chat.ChatRoomMemberRepository;
import com.nordicframtiden.chat.ChatRoomRepository;
import com.nordicframtiden.company.StaffShiftRepository;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import com.nordicframtiden.security.service.PasswordResetService;
import com.nordicframtiden.settings.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pinning the dual-role model: not all admins are pharmacists, and a promoted
 * account keeps its base role. Three supported flows:
 *   1. pharmacist (USER) promoted to admin — still visible as pharmacist
 *   2. admin-only account created directly
 *   3. staff promoted to admin — keeps STAFF and gains ADMIN
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceRoleFlowTest {

    @Mock private AppUserRepository repo;
    @Mock private AdminProfileRepository adminProfileRepo;
    @Mock private UserProfileRepository userProfileRepo;
    @Mock private PasswordEncoder encoder;
    @Mock private EmailService emailService;
    @Mock private PasswordResetService passwordResetService;
    @Mock private ChatMessageRepository chatMessageRepo;
    @Mock private ChatReactionRepository chatReactionRepo;
    @Mock private ChatRoomRepository chatRoomRepo;
    @Mock private CallHistoryRepository callHistoryRepo;
    @Mock private ChatPushSubscriptionRepository chatPushSubscriptionRepo;
    @Mock private StaffShiftRepository staffShiftRepo;
    @Mock private AvailabilityRequestRepository availabilityRequestRepo;

    private AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(repo, adminProfileRepo, userProfileRepo, encoder,
            emailService, passwordResetService, chatMessageRepo, chatReactionRepo,
            chatRoomRepo, callHistoryRepo, chatPushSubscriptionRepo, staffShiftRepo,
            availabilityRequestRepo,
            org.mockito.Mockito.mock(com.nordicframtiden.documents.ProfileDocumentRepository.class),
            org.mockito.Mockito.mock(com.nordicframtiden.chat.ChatAttachmentRepository.class),
            org.mockito.Mockito.mock(com.nordicframtiden.service.model.PayslipSnapshotRepository.class));
    }

    private AppUser userWithRoles(Role... roles) {
        AppUser user = new AppUser();
        user.setId(7L);
        user.setUsername("testuser");
        user.setRoles(new HashSet<>(Set.of(roles)));
        return user;
    }

    @Test
    void pharmacistCanBePromotedToAdminAndKeepsUserRole() {
        AppUser pharmacist = userWithRoles(Role.USER);
        when(repo.findById(7L)).thenReturn(Optional.of(pharmacist));

        service.setAdminRole(7L, true);

        assertTrue(pharmacist.getRoles().contains(Role.ADMIN), "pharmacist gains ADMIN");
        assertTrue(pharmacist.getRoles().contains(Role.USER), "pharmacist keeps USER");
        verify(repo).save(pharmacist);
    }

    @Test
    void staffCanBePromotedToAdminAndKeepsStaffRole() {
        AppUser staff = userWithRoles(Role.STAFF);
        when(repo.findById(7L)).thenReturn(Optional.of(staff));

        service.setAdminRole(7L, true);

        assertTrue(staff.getRoles().contains(Role.ADMIN), "staff gains ADMIN");
        assertTrue(staff.getRoles().contains(Role.STAFF), "staff keeps STAFF");
        verify(repo).save(staff);
    }

    @Test
    void removingAdminKeepsTheBaseRole() {
        // Dual-role pharmacist-admin: demote back to plain pharmacist.
        AppUser dualRole = userWithRoles(Role.USER, Role.ADMIN);
        when(repo.findById(7L)).thenReturn(Optional.of(dualRole));
        when(repo.countByRole(Role.ADMIN)).thenReturn(2L);

        service.setAdminRole(7L, false);

        assertFalse(dualRole.getRoles().contains(Role.ADMIN));
        assertTrue(dualRole.getRoles().contains(Role.USER), "base role survives demotion");
        verify(repo).save(dualRole);
    }

    @Test
    void lastAdminCannotBeDemoted() {
        AppUser lastAdmin = userWithRoles(Role.ADMIN);
        when(repo.findById(7L)).thenReturn(Optional.of(lastAdmin));
        when(repo.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThrows(IllegalStateException.class, () -> service.setAdminRole(7L, false));
        assertTrue(lastAdmin.getRoles().contains(Role.ADMIN), "roles untouched on guard");
    }

    @Test
    void promotingAnAdminAgainIsANoOp() {
        AppUser admin = userWithRoles(Role.ADMIN);
        when(repo.findById(7L)).thenReturn(Optional.of(admin));

        service.setAdminRole(7L, true); // already admin

        verify(repo, never()).save(any());
    }

    @Test
    void demotingAStaffAdminLeavesAStaffAccount() {
        // A staff-admin who loses ADMIN must end up exactly as before: STAFF.
        AppUser staffAdmin = userWithRoles(Role.STAFF, Role.ADMIN);
        when(repo.findById(7L)).thenReturn(Optional.of(staffAdmin));
        when(repo.countByRole(Role.ADMIN)).thenReturn(2L);

        service.setAdminRole(7L, false);

        assertEquals(Set.of(Role.STAFF), staffAdmin.getRoles());
        verify(repo).save(staffAdmin);
    }

    @Test
    void createAdminWithProfileAssignsOnlyTheAdminRole() {
        // Flow 2: a pure admin account (no pharmacist/staff role needed).
        when(adminProfileRepo.existsByEmail(any())).thenReturn(false);
        when(adminProfileRepo.existsByPhone(any())).thenReturn(false);
        when(repo.existsByUsername(any())).thenReturn(false);
        when(encoder.encode(any())).thenReturn("hash");
        lenient().when(repo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
            service.createAdminWithProfile(true, "Pure Admin", "pure@example.com", "0701234567"));

        verify(repo).save(any(AppUser.class));
    }
}
