package com.nordicframtiden.gdpr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nordicframtiden.availability.AvailabilityRequestRepository;
import com.nordicframtiden.chat.ChatMessageRepository;
import com.nordicframtiden.chat.ChatRoomRepository;
import com.nordicframtiden.chat.CallHistoryRepository;
import com.nordicframtiden.company.StaffShiftRepository;
import com.nordicframtiden.documents.ProfileDocumentRepository;
import com.nordicframtiden.pharmacy.ScheduleShiftRepository;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.model.UserProfile;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import com.nordicframtiden.security.service.UserService;
import com.nordicframtiden.service.model.PayslipSnapshotRepository;
import com.nordicframtiden.service.model.SalaryAdjustmentRepository;
import com.nordicframtiden.settings.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * TDD for the nightly email-delivery queue: confirming the request queues a
 * PENDING row (24h promise), the 03:00 job emails the JSON export, retries on
 * failure, and gives up after MAX_ATTEMPTS.
 */
@ExtendWith(MockitoExtension.class)
class GdprExportMailJobTest {

  @Mock private GdprExportRequestRepository requests;
  @Mock private AppUserRepository userRepo;
  @Mock private UserProfileRepository profileRepo;
  @Mock private GdprConsentRepository consentRepo;
  @Mock private ScheduleShiftRepository scheduleShifts;
  @Mock private StaffShiftRepository staffShifts;
  @Mock private AvailabilityRequestRepository availabilityRequests;
  @Mock private SalaryAdjustmentRepository salaryAdjustments;
  @Mock private PayslipSnapshotRepository payslipSnapshots;
  @Mock private ChatMessageRepository chatMessages;
  @Mock private ChatRoomRepository chatRooms;
  @Mock private com.nordicframtiden.chat.ChatRoomMemberRepository chatRoomMembers;
  @Mock private CallHistoryRepository callHistory;
  @Mock private ProfileDocumentRepository profileDocuments;
  @Mock private UserService userService;
  @Mock private EmailService emailService;

  private GdprService gdprService;
  private GdprExportMailJob job;
  private AppUser user;
  private UserProfile profile;

  @BeforeEach
  void setUp() {
    gdprService = new GdprService(userRepo, profileRepo, consentRepo, scheduleShifts,
        staffShifts, availabilityRequests, salaryAdjustments, payslipSnapshots,
        chatMessages, chatRooms, chatRoomMembers, callHistory, profileDocuments,
        mockExportRequests(), userService);
    job = new GdprExportMailJob(requests, gdprService, emailService, new ObjectMapper());

    user = new AppUser();
    user.setId(7L);
    user.setUsername("pharm");
    user.setRoles(new java.util.HashSet<>(Set.of(Role.USER)));
    profile = new UserProfile();
    profile.setUser(user);
    profile.setFullName("Anna Andersson");
    profile.setEmail("anna@example.com");
  }

  /** The GdprService under test shares the queue repository with the job. */
  private GdprExportRequestRepository mockExportRequests() {
    return requests;
  }

  @Test
  void requestExport_queues_pending_row_with_profile_email_and_audit_marker() {
    when(userRepo.findById(7L)).thenReturn(Optional.of(user));
    when(profileRepo.findByUserId(7L)).thenReturn(Optional.of(profile));
    when(requests.findByStatusOrderByCreatedAtAsc(GdprExportRequest.STATUS_PENDING))
        .thenReturn(List.of());
    when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));

    GdprExportRequest saved = gdprService.requestExportByEmail(7L);

    assertThat(saved.getUserId()).isEqualTo(7L);
    assertThat(saved.getEmail()).isEqualTo("anna@example.com");
    assertThat(saved.getStatus()).isEqualTo(GdprExportRequest.STATUS_PENDING);
    // Art. 7 audit marker recorded with the request.
    verify(consentRepo).save(any(GdprConsent.class));
  }

  @Test
  void requestExport_is_idempotent_while_a_pending_request_exists() {
    when(userRepo.findById(7L)).thenReturn(Optional.of(user));
    when(profileRepo.findByUserId(7L)).thenReturn(Optional.of(profile));
    GdprExportRequest existing =
        new GdprExportRequest(7L, "pharm", "anna@example.com");
    when(requests.findByStatusOrderByCreatedAtAsc(GdprExportRequest.STATUS_PENDING))
        .thenReturn(List.of(existing));

    GdprExportRequest result = gdprService.requestExportByEmail(7L);

    assertThat(result).isSameAs(existing);
    verify(requests, never()).save(any());
  }

  @Test
  void requestExport_requires_an_email_on_file() {
    when(userRepo.findById(7L)).thenReturn(Optional.of(user));
    profile.setEmail(" ");
    when(profileRepo.findByUserId(7L)).thenReturn(Optional.of(profile));

    assertThatThrownBy(() -> gdprService.requestExportByEmail(7L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void job_emails_pending_export_marks_it_sent() throws Exception {
    GdprExportRequest pending = new GdprExportRequest(7L, "pharm", "anna@example.com");
    when(requests.findByStatusOrderByCreatedAtAsc(GdprExportRequest.STATUS_PENDING))
        .thenReturn(List.of(pending));
    when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));
    stubExport();
    when(emailService.sendGdprExportEmail(anyString(), anyString(), any(byte[].class)))
        .thenReturn(true);

    job.processPendingRequests();

    ArgumentCaptor<byte[]> json = ArgumentCaptor.forClass(byte[].class);
    verify(emailService).sendGdprExportEmail(
        org.mockito.ArgumentMatchers.eq("anna@example.com"),
        org.mockito.ArgumentMatchers.eq("Anna Andersson"),
        json.capture());
    String payload = new String(json.getValue(), java.nio.charset.StandardCharsets.UTF_8);
    assertThat(payload).contains("pharm").contains("anna@example.com").contains("\"format\":\"json\"");
    ArgumentCaptor<GdprExportRequest> saved = ArgumentCaptor.forClass(GdprExportRequest.class);
    verify(requests).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(GdprExportRequest.STATUS_SENT);
    assertThat(saved.getValue().getSentAt()).isNotNull();
  }

  @Test
  void job_increments_attempts_on_failure_and_retries_later() throws Exception {
    GdprExportRequest pending = new GdprExportRequest(7L, "pharm", "anna@example.com");
    when(requests.findByStatusOrderByCreatedAtAsc(GdprExportRequest.STATUS_PENDING))
        .thenReturn(List.of(pending));
    when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));
    stubExport();
    when(emailService.sendGdprExportEmail(anyString(), anyString(), any(byte[].class)))
        .thenThrow(new IllegalStateException("smtp down"));

    job.processPendingRequests();

    ArgumentCaptor<GdprExportRequest> saved = ArgumentCaptor.forClass(GdprExportRequest.class);
    verify(requests).save(saved.capture());
    assertThat(saved.getValue().getAttempts()).isEqualTo(1);
    assertThat(saved.getValue().getStatus()).isEqualTo(GdprExportRequest.STATUS_PENDING);
    assertThat(saved.getValue().getLastError()).contains("smtp down");
  }

  @Test
  void job_gives_up_after_max_attempts() throws Exception {
    GdprExportRequest pending = new GdprExportRequest(7L, "pharm", "anna@example.com");
    pending.setAttempts(GdprExportMailJob.MAX_ATTEMPTS - 1);
    when(requests.findByStatusOrderByCreatedAtAsc(GdprExportRequest.STATUS_PENDING))
        .thenReturn(List.of(pending));
    when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));
    stubExport();
    when(emailService.sendGdprExportEmail(anyString(), anyString(), any(byte[].class)))
        .thenThrow(new IllegalStateException("smtp down again"));

    job.processPendingRequests();

    ArgumentCaptor<GdprExportRequest> saved = ArgumentCaptor.forClass(GdprExportRequest.class);
    verify(requests).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(GdprExportRequest.STATUS_FAILED);
  }

  @Test
  void job_leaves_pending_when_mail_is_disabled() throws Exception {
    GdprExportRequest pending = new GdprExportRequest(7L, "pharm", "anna@example.com");
    when(requests.findByStatusOrderByCreatedAtAsc(GdprExportRequest.STATUS_PENDING))
        .thenReturn(List.of(pending));
    stubExport();
    when(emailService.sendGdprExportEmail(anyString(), anyString(), any(byte[].class)))
        .thenReturn(false);

    job.processPendingRequests();

    verify(requests, never()).save(any());
    assertThat(pending.getStatus()).isEqualTo(GdprExportRequest.STATUS_PENDING);
  }

  private void stubExport() {
    lenient().when(userRepo.findById(7L)).thenReturn(Optional.of(user));
    lenient().when(profileRepo.findByUserId(7L)).thenReturn(Optional.of(profile));
    lenient().when(chatRooms.findVisibleTo(7L)).thenReturn(List.of());
    lenient().when(availabilityRequests.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());
    lenient().when(profileDocuments.findByUserIdOrderByIdDesc(7L)).thenReturn(List.of());
    lenient().when(scheduleShifts.findInRange(any(), any(), any(), any())).thenReturn(List.of());
    lenient().when(staffShifts.findInRange(any(), any(), any())).thenReturn(List.of());
    lenient().when(payslipSnapshots.findByUserId(7L)).thenReturn(List.of());
    lenient().when(salaryAdjustments.findByUserId(7L)).thenReturn(List.of());
  }
}
