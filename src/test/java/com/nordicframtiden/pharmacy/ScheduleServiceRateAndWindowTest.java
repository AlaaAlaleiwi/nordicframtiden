package com.nordicframtiden.pharmacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.UserProfile;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.service.UserService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Review item 3 + 7: the pay-rate snapshot must follow the employee
 * (reassignment reprices, same-person edits keep the frozen rate), a missing
 * profile/rate must be refused instead of silently priced at zero, and the
 * same-day conflict window must use Stockholm local midnights so it stays
 * correct across DST transitions and covers overnight shifts.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceRateAndWindowTest {

    @Mock private ScheduleShiftRepository shiftRepo;
    @Mock private PharmacyRepository pharmacyRepo;
    @Mock private AppUserRepository userRepo;
    @Mock private UserService userService;

    private ScheduleService service;

    @BeforeEach
    void setUp() {
        // Fixed clock before every fixture date: the past-shift lock is inert here.
        service = new ScheduleService(shiftRepo, pharmacyRepo, userRepo, userService, null,
            java.time.Clock.fixed(java.time.Instant.parse("2027-01-15T12:00:00Z"),
                java.time.ZoneId.of("Europe/Stockholm")));
    }

    private AppUser user(long id, String name) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setUsername(name);
        return u;
    }

    private UserProfile profileWithRate(BigDecimal rate) {
        UserProfile p = new UserProfile();
        p.setFullName("Anna Andersson");
        p.setHourlyCost(rate);
        return p;
    }

    private ScheduleShift existingShift(long id, long userId, String snapshotRate) {
        ScheduleShift s = org.mockito.Mockito.mock(ScheduleShift.class);
        org.mockito.Mockito.lenient().doReturn(id).when(s).getId();
        org.mockito.Mockito.lenient().doReturn(user(userId, "u" + userId)).when(s).getUser();
        org.mockito.Mockito.lenient().doReturn(OffsetDateTime.parse("2027-03-01T09:00:00Z")).when(s).getStartAt();
        org.mockito.Mockito.lenient().doReturn(OffsetDateTime.parse("2027-03-01T17:00:00Z")).when(s).getEndAt();
        org.mockito.Mockito.lenient().doReturn(new BigDecimal(snapshotRate)).when(s).getHourlyCostSnapshot();
        return s;
    }

    @Test
    void reassigningTheShiftRepricesAtTheNewEmployeesRate() {
        ScheduleShift s = existingShift(42L, 7L, "200");
        when(shiftRepo.findById(42L)).thenReturn(Optional.of(s));
        AppUser newcomer = user(8L, "bosse");
        when(userRepo.findById(8L)).thenReturn(Optional.of(newcomer));
        UserProfile rich = profileWithRate(new BigDecimal("300"));
        when(userService.getProfileByUserId(8L)).thenReturn(rich);
        lenient().when(pharmacyRepo.findById(any())).thenReturn(Optional.of(new Pharmacy()));
        when(shiftRepo.findByUserIdAndStartAtLessThanAndEndAtGreaterThan(any(), any(), any()))
            .thenReturn(List.of());
        when(shiftRepo.save(any(ScheduleShift.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(42L, null, 8L, null, null, null);

        // Mocked entity: assert the snapshot setter saw the new rate.
        verify(s).setHourlyCostSnapshot(new BigDecimal("300"));
    }

    @Test
    void editingForTheSameEmployeeKeepsTheFrozenRate() {
        ScheduleShift s = existingShift(42L, 7L, "200");
        when(shiftRepo.findById(42L)).thenReturn(Optional.of(s));
        when(shiftRepo.findByUserIdAndStartAtLessThanAndEndAtGreaterThan(any(), any(), any()))
            .thenReturn(List.of());
        when(shiftRepo.save(any(ScheduleShift.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(42L, null, null, null, null, "note only");

        // The frozen snapshot must not be touched; no profile lookup either.
        verify(s, never()).setHourlyCostSnapshot(any());
        verify(userService, never()).getProfileByUserId(any());
    }

    @Test
    void createRefusesWhenTheProfileIsMissing() {
        AppUser u = user(7L, "pharm");
        when(userRepo.findById(7L)).thenReturn(Optional.of(u));
        when(pharmacyRepo.findById(1L)).thenReturn(Optional.of(new Pharmacy()));
        when(userService.getProfileByUserId(7L))
            .thenThrow(new IllegalArgumentException("Profile not found"));

        assertThrows(IllegalArgumentException.class, () ->
            service.create(1L, 7L,
                OffsetDateTime.parse("2027-03-01T09:00:00Z"),
                OffsetDateTime.parse("2027-03-01T17:00:00Z"), null));
    }

    @Test
    void createRefusesWhenTheRateIsMissing() {
        AppUser u = user(7L, "pharm");
        when(userRepo.findById(7L)).thenReturn(Optional.of(u));
        when(pharmacyRepo.findById(1L)).thenReturn(Optional.of(new Pharmacy()));
        when(userService.getProfileByUserId(7L)).thenReturn(profileWithRate(null));

        assertThrows(IllegalArgumentException.class, () ->
            service.create(1L, 7L,
                OffsetDateTime.parse("2027-03-01T09:00:00Z"),
                OffsetDateTime.parse("2027-03-01T17:00:00Z"), null));
    }

    @Test
    void overnightShiftProtectsEveryDayItTouches() {
        AppUser u = user(7L, "pharm");
        when(userRepo.findById(7L)).thenReturn(Optional.of(u));
        when(pharmacyRepo.findById(1L)).thenReturn(Optional.of(new Pharmacy()));
        when(userService.getProfileByUserId(7L)).thenReturn(profileWithRate(new BigDecimal("100")));
        when(shiftRepo.findByUserIdAndStartAtLessThanAndEndAtGreaterThan(any(), any(), any()))
            .thenReturn(List.of());
        lenient().when(shiftRepo.save(any(ScheduleShift.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mon 2027-02-15 23:00 CET -> Tue 2027-02-16 11:00 CET (overnight).
        service.create(1L, 7L,
            OffsetDateTime.parse("2027-02-15T22:00:00Z"),
            OffsetDateTime.parse("2027-02-16T10:00:00Z"), null);

        ArgumentCaptor<OffsetDateTime> start = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> end = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(shiftRepo).findByUserIdAndStartAtLessThanAndEndAtGreaterThan(any(), end.capture(), start.capture());
        // Window must span Mon 00:00 .. Wed 00:00 Stockholm (both midnights).
        assertEquals(OffsetDateTime.parse("2027-02-14T23:00:00Z").toInstant(), start.getValue().toInstant());
        assertEquals(OffsetDateTime.parse("2027-02-16T23:00:00Z").toInstant(), end.getValue().toInstant());
    }

    @Test
    void dstTransitionDaysUseLocalMidnights() {
        AppUser u = user(7L, "pharm");
        when(userRepo.findById(7L)).thenReturn(Optional.of(u));
        when(pharmacyRepo.findById(1L)).thenReturn(Optional.of(new Pharmacy()));
        when(userService.getProfileByUserId(7L)).thenReturn(profileWithRate(new BigDecimal("100")));
        when(shiftRepo.findByUserIdAndStartAtLessThanAndEndAtGreaterThan(any(), any(), any()))
            .thenReturn(List.of());
        lenient().when(shiftRepo.save(any(ScheduleShift.class))).thenAnswer(inv -> inv.getArgument(0));

        // Spring-forward day (2027-03-28, 23 hours long): 01:30 CET -> 20:00 CEST.
        service.create(1L, 7L,
            OffsetDateTime.parse("2027-03-28T00:30:00Z"),
            OffsetDateTime.parse("2027-03-28T19:00:00Z"), null);

        ArgumentCaptor<OffsetDateTime> start = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> end = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(shiftRepo).findByUserIdAndStartAtLessThanAndEndAtGreaterThan(any(), end.capture(), start.capture());
        // Local midnights: Sat 24:00 CET (23:00Z) .. Sun 24:00 CEST (22:00Z) —
        // a plain 24h addition would produce the wrong right bound.
        assertEquals(OffsetDateTime.parse("2027-03-27T23:00:00Z").toInstant(), start.getValue().toInstant());
        assertEquals(OffsetDateTime.parse("2027-03-28T22:00:00Z").toInstant(), end.getValue().toInstant());
    }
}
