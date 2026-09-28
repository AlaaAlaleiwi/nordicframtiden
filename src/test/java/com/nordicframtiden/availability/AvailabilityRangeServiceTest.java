package com.nordicframtiden.availability;

import com.nordicframtiden.api.AvailabilityController.AvailabilityRow;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.UserProfile;
import com.nordicframtiden.security.repo.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvailabilityRangeServiceTest {

  private final AvailabilityRequestRepository repo = Mockito.mock(AvailabilityRequestRepository.class);
  private final UserProfileRepository profileRepo = Mockito.mock(UserProfileRepository.class);
  private final AvailabilityService service = new AvailabilityService(repo, Mockito.mock(com.nordicframtiden.security.repo.AppUserRepository.class), profileRepo);

  private AvailabilityRequest request(long id, long userId, AvailabilityRequest.Status status,
                                      LocalDate start, LocalDate end,
                                      LocalTime startTime, LocalTime endTime) {
    AvailabilityRequest r = new AvailabilityRequest();
    r.setType(AvailabilityRequest.Type.DAY);
    r.setStartDate(start);
    r.setEndDate(end);
    r.setStartTime(startTime);
    r.setEndTime(endTime);
    r.setStatus(status);
    r.setNote("note " + id);
    AppUser u = new AppUser();
    u.setId(userId);
    u.setUsername("user" + userId);
    r.setUser(u);
    return r;
  }

  @Test
  void defaultStatusesExcludePendingAndRejected() {
    LocalDate day = LocalDate.parse("2026-10-05");
    when(repo.findApprovedOverlapping(day, day)).thenReturn(List.of(
        request(2L, 7L, AvailabilityRequest.Status.APPROVED, day, day, LocalTime.parse("09:00"), LocalTime.parse("17:00")),
        request(3L, 8L, AvailabilityRequest.Status.PENDING, day, day, null, null)
    ));

    List<AvailabilityRow> rows = service.getOverlapping(day, day, null);

    assertEquals(1, rows.size());
    assertEquals("APPROVED", rows.get(0).status());
    assertEquals(Long.valueOf(7L), rows.get(0).userId());
    verify(repo).findApprovedOverlapping(day, day);
    verify(repo, never()).findByStatusInAndOverlapping(any(), any(), any());
  }

  @Test
  void pendingAndApprovedAreBothIncludedWhenRequested() {
    LocalDate day = LocalDate.parse("2026-10-06");
    when(repo.findByStatusInAndOverlapping(List.of(AvailabilityRequest.Status.PENDING, AvailabilityRequest.Status.APPROVED), day, day))
        .thenReturn(List.of(
            request(1L, 5L, AvailabilityRequest.Status.PENDING, day, day, LocalTime.parse("08:00"), LocalTime.parse("16:00")),
            request(2L, 7L, AvailabilityRequest.Status.APPROVED, day, day, LocalTime.parse("09:00"), LocalTime.parse("17:00")),
            request(3L, 8L, AvailabilityRequest.Status.REJECTED, day, day, null, null)
        ));

    List<AvailabilityRow> rows = service.getOverlapping(day, day, "PENDING,APPROVED");

    assertEquals(2, rows.size());
    assertEquals("PENDING", rows.get(0).status());
    assertEquals("APPROVED", rows.get(1).status());
    assertEquals("user5", rows.get(0).username());
    verify(repo).findByStatusInAndOverlapping(List.of(AvailabilityRequest.Status.PENDING, AvailabilityRequest.Status.APPROVED), day, day);
    verify(repo, never()).findApprovedOverlapping(any(), any());
  }

  @Test
  void pendingOnlyRequestIncludesPendingRowWithMappedProfileName() {
    LocalDate day = LocalDate.parse("2026-10-07");
    AvailabilityRequest pending = request(9L, 11L, AvailabilityRequest.Status.PENDING, day, day, LocalTime.parse("10:00"), LocalTime.parse("18:00"));
    when(repo.findByStatusInAndOverlapping(List.of(AvailabilityRequest.Status.PENDING), day, day))
        .thenReturn(List.of(pending));
    UserProfile profile = new UserProfile();
    profile.setFullName("Anna Andersson");
    when(profileRepo.findByUserId(11L)).thenReturn(Optional.of(profile));

    List<AvailabilityRow> rows = service.getOverlapping(day, day, "PENDING");

    assertEquals(1, rows.size());
    AvailabilityRow row = rows.get(0);
    assertEquals("PENDING", row.status());
    assertEquals("Anna Andersson", row.userFullName());
    assertEquals("user11", row.username());
    assertEquals("10:00", row.startTime());
    assertEquals("18:00", row.endTime());
    assertEquals("2026-10-07", row.startDate());
  }

  @Test
  void rowMapsMissingProfileAndTimesToNulls() {
    LocalDate day = LocalDate.parse("2026-10-08");
    when(repo.findByStatusInAndOverlapping(List.of(AvailabilityRequest.Status.APPROVED), day, day)).thenReturn(List.of(
        request(4L, 6L, AvailabilityRequest.Status.APPROVED, day, day, null, null)
    ));
    when(profileRepo.findByUserId(6L)).thenReturn(Optional.empty());

    List<AvailabilityRow> rows = service.getOverlapping(day, day, "APPROVED");

    assertEquals(1, rows.size());
    assertNull(rows.get(0).userFullName());
    assertNull(rows.get(0).startTime());
    assertNull(rows.get(0).endTime());
  }

  @Test
  void unknownStatusNamesAreIgnoredRatherThanThrown() {
    LocalDate day = LocalDate.parse("2026-10-09");
    when(repo.findByStatusInAndOverlapping(List.of(AvailabilityRequest.Status.PENDING), day, day)).thenReturn(List.of());

    List<AvailabilityRow> rows = service.getOverlapping(day, day, "PENDING,NOT_A_STATUS");

    assertEquals(0, rows.size());
    verify(repo).findByStatusInAndOverlapping(List.of(AvailabilityRequest.Status.PENDING), day, day);
  }
}
