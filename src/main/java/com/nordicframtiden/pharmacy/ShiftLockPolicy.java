package com.nordicframtiden.pharmacy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Past shifts are locked: a shift whose Stockholm start day is before today
 * can no longer be changed or removed (payroll/audit integrity), and new
 * shifts cannot be created on days that have already passed. Today's and
 * future shifts stay editable. Mapped to HTTP 409 via ApiExceptionHandler.
 */
public final class ShiftLockPolicy {

    public static final ZoneId ZONE = ZoneId.of("Europe/Stockholm");

    private ShiftLockPolicy() {}

    /** True when the shift's Stockholm start day is before {@code today}. */
    public static boolean isLocked(OffsetDateTime startAt, LocalDate today) {
        if (startAt == null || today == null) return false;
        return startAt.atZoneSameInstant(ZONE).toLocalDate().isBefore(today);
    }

    /** Human-readable Swedish refusal for a locked (past) shift. */
    public static String lockedMessage(OffsetDateTime startAt) {
        LocalDate day = startAt.atZoneSameInstant(ZONE).toLocalDate();
        return "Arbetspass den " + day + " har redan genomförts och kan inte "
                + "ändras eller tas bort. Historik är låst för löne- och revisjonsändamål.";
    }

    /** Human-readable Swedish refusal for creating a shift in the past. */
    public static String pastCreationMessage(OffsetDateTime startAt) {
        LocalDate day = startAt.atZoneSameInstant(ZONE).toLocalDate();
        return "Nya arbetspass kan inte läggas till på en dag som redan har passerat (" + day + ").";
    }
}
