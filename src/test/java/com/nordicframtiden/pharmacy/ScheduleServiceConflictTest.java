package com.nordicframtiden.pharmacy;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One shift per user per day: creating (or moving) a shift that lands on a
 * day where the user already has one must be rejected with a clear message —
 * regardless of pharmacy, even when the times do not overlap.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceConflictTest {

    @Mock private ScheduleShiftRepository shiftRepo;
    @Mock private PharmacyRepository pharmacyRepo;
    @Mock private AppUserRepository userRepo;
    @Mock private UserService userService;

    private ScheduleService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleService(shiftRepo, pharmacyRepo, userRepo, userService);
    }

    private AppUser user() {
        AppUser u = new AppUser();
        u.setId(7L);
        u.setUsername("pharm");
        return u;
    }

    private OffsetDateTime utc(String iso) {
        return OffsetDateTime.parse(iso);
    }

    @Test
    void createRejectsWhenUserAlreadyHasAShiftThatDay() {
        AppUser u = user();
        when(userRepo.findById(7L)).thenReturn(Optional.of(u));
        when(pharmacyRepo.findById(1L)).thenReturn(Optional.of(new Pharmacy()));
        when(shiftRepo.findByUserIdAndStartAtLessThanAndEndAtGreaterThan(
            org.mockito.ArgumentMatchers.eq(7L), any(), any()))
            .thenReturn(List.of(new ScheduleShift()));

        ShiftConflictException ex = assertThrows(ShiftConflictException.class, () ->
            service.create(1L, 7L,
                utc("2026-02-15T09:00:00Z"), utc("2026-02-15T17:00:00Z"), null));

        assertTrue(ex.getMessage().contains("redan ett arbetspass"), "clear Swedish message");
        assertTrue(ex.getMessage().contains("2026"), "message mentions the day");
    }

    @Test
    void createSucceedsWhenTheDayIsFree() {
        AppUser u = user();
        when(userRepo.findById(7L)).thenReturn(Optional.of(u));
        when(pharmacyRepo.findById(1L)).thenReturn(Optional.of(new Pharmacy()));
        when(shiftRepo.findByUserIdAndStartAtLessThanAndEndAtGreaterThan(
            org.mockito.ArgumentMatchers.eq(7L), any(), any()))
            .thenReturn(List.of());
        when(shiftRepo.save(any(ScheduleShift.class))).thenAnswer(inv -> inv.getArgument(0));

        ScheduleShift created = service.create(1L, 7L,
            utc("2026-02-15T09:00:00Z"), utc("2026-02-15T17:00:00Z"), null);

        assertEquals(u, created.getUser());
    }

    @Test
    void conflictCheckUsesTheStockholmDayWindow() {
        // 09:00Z in February = 10:00 Stockholm time → day window must be
        // 2026-02-15T00:00+01:00 .. 2026-02-16T00:00+01:00 (i.e. 23:00Z bounds).
        AppUser u = user();
        when(userRepo.findById(7L)).thenReturn(Optional.of(u));
        when(pharmacyRepo.findById(1L)).thenReturn(Optional.of(new Pharmacy()));
        when(shiftRepo.findByUserIdAndStartAtLessThanAndEndAtGreaterThan(
            org.mockito.ArgumentMatchers.eq(7L), any(), any()))
            .thenReturn(List.of());
        lenient().when(shiftRepo.save(any(ScheduleShift.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(1L, 7L,
            utc("2026-02-15T09:00:00Z"), utc("2026-02-15T17:00:00Z"), null);

        ArgumentCaptor<OffsetDateTime> start = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> end = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(shiftRepo).findByUserIdAndStartAtLessThanAndEndAtGreaterThan(
            org.mockito.ArgumentMatchers.eq(7L), end.capture(), start.capture());
        assertEquals(utc("2026-02-14T23:00:00Z").toInstant(), start.getValue().toInstant());
        assertEquals(utc("2026-02-15T23:00:00Z").toInstant(), end.getValue().toInstant());
    }

    @Test
    void updateDoesNotConflictWithItself() {
        ScheduleShift existing = org.mockito.Mockito.mock(ScheduleShift.class);
        when(existing.getId()).thenReturn(42L);
        when(existing.getUser()).thenReturn(user());
        when(shiftRepo.findById(42L)).thenReturn(Optional.of(existing));
        when(pharmacyRepo.findById(1L)).thenReturn(Optional.of(new Pharmacy()));
        when(shiftRepo.findByUserIdAndStartAtLessThanAndEndAtGreaterThan(
            org.mockito.ArgumentMatchers.eq(7L), any(), any()))
            .thenReturn(List.of(existing));
        when(shiftRepo.save(any(ScheduleShift.class))).thenAnswer(inv -> inv.getArgument(0));

        ScheduleShift updated = service.update(42L, 1L, 7L,
            utc("2026-02-15T10:00:00Z"), utc("2026-02-15T18:00:00Z"), null);

        assertEquals(42L, updated.getId());
    }

    @Test
    void updateRejectsMovingOntoAnotherDaysShift() {
        ScheduleShift existing = org.mockito.Mockito.mock(ScheduleShift.class);
        when(existing.getId()).thenReturn(42L);
        when(existing.getUser()).thenReturn(user());
        when(shiftRepo.findById(42L)).thenReturn(Optional.of(existing));
        when(pharmacyRepo.findById(1L)).thenReturn(Optional.of(new Pharmacy()));
        when(shiftRepo.findByUserIdAndStartAtLessThanAndEndAtGreaterThan(
            org.mockito.ArgumentMatchers.eq(7L), any(), any()))
            .thenReturn(List.of(new ScheduleShift()));

        assertThrows(ShiftConflictException.class, () ->
            service.update(42L, 1L, 7L,
                utc("2026-03-01T10:00:00Z"), utc("2026-03-01T18:00:00Z"), null));
    }
}
