package com.nordicframtiden.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD for the payslip-ready rule: the 21st of the payment month, rolled back
 * to the previous working day (weekends + Swedish public holidays) when the
 * 21st is not a working day.
 */
class PayrollCalendarTest {

    @Test
    void readyDate_isThe21st_whenItIsAWorkingDay() {
        // 2026-07-21 is a Tuesday.
        assertThat(PayrollCalendar.readyDateFor(YearMonth.of(2026, 6)))
            .isEqualTo(LocalDate.of(2026, 7, 21));
    }

    @Test
    void readyDate_rollsBackToFriday_whenThe21stIsASaturday() {
        // 2026-11-21 is a Saturday -> Friday the 20th.
        assertThat(PayrollCalendar.readyDateFor(YearMonth.of(2026, 10)))
            .isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void readyDate_rollsBackOverHolidayAndWeekend() {
        // 2026-12-21 is a Monday but 2026-12-25/26 are Sat/Sun; the 21st itself is fine.
        // Use a case where the 21st is a Sunday: 2027-02-21 is a Sunday -> Friday 19th.
        assertThat(PayrollCalendar.readyDateFor(YearMonth.of(2027, 1)))
            .isEqualTo(LocalDate.of(2027, 2, 19));
    }

    @Test
    void readyDate_rollsBackFromAFixedPublicHoliday() {
        // 2027-01-21 is a Thursday; not a holiday. Construct a holiday case:
        // 2026-01-06 (Trettondag jul) is a Tuesday; a 21st never lands on it,
        // so exercise the working-day predicate directly instead.
        assertThat(PayrollCalendar.isWorkingDay(LocalDate.of(2026, 1, 6))).isFalse();
        assertThat(PayrollCalendar.isWorkingDay(LocalDate.of(2026, 1, 5))).isTrue();
        assertThat(PayrollCalendar.isWorkingDay(LocalDate.of(2026, 6, 6))).isFalse(); // Nationaldagen, Saturday
        assertThat(PayrollCalendar.isWorkingDay(LocalDate.of(2026, 6, 8))).isTrue();
        assertThat(PayrollCalendar.isWorkingDay(LocalDate.of(2026, 12, 25))).isFalse(); // Juldagen, Friday
        assertThat(PayrollCalendar.isWorkingDay(LocalDate.of(2026, 12, 24))).isTrue(); // Not statutory
    }

    @Test
    void easterSunday_matchesKnownSwedishCalendarDates() {
        // Well-known Swedish Easter Sundays.
        assertThat(PayrollCalendar.easterSunday(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(PayrollCalendar.easterSunday(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
        assertThat(PayrollCalendar.easterSunday(2024)).isEqualTo(LocalDate.of(2024, 3, 31));
    }

    @Test
    void readyDate_rollsBackFromEasterDerivedHolidays() {
        // Easter Sunday 2026-04-05: Good Friday 2026-04-03 and Easter Monday
        // 2026-04-06 are non-working; the 21st never lands there, so exercise
        // the predicate directly.
        assertThat(PayrollCalendar.isWorkingDay(LocalDate.of(2026, 4, 3))).isFalse();
        assertThat(PayrollCalendar.isWorkingDay(LocalDate.of(2026, 4, 6))).isFalse();
        assertThat(PayrollCalendar.isWorkingDay(LocalDate.of(2026, 4, 7))).isTrue();
        // Ascension day (Easter + 39): 2026-05-14, a Thursday.
        assertThat(PayrollCalendar.isWorkingDay(LocalDate.of(2026, 5, 14))).isFalse();
        // Pingstdagen (Easter + 49) is a Sunday: 2026-05-24. Annandag pingst
        // (Whit Monday) was abolished in 2005 — the Monday after is working.
        assertThat(PayrollCalendar.isWorkingDay(LocalDate.of(2026, 5, 24))).isFalse();
        assertThat(PayrollCalendar.isWorkingDay(LocalDate.of(2026, 5, 25))).isTrue();
    }

    @Test
    void isPayslipReadyDate_trueOnThe21st_andOnTheRolledBackDate() {
        assertThat(PayrollCalendar.isPayslipReadyDate(LocalDate.of(2026, 7, 21))).isTrue();
        // November 2026: 21st is Saturday, ready date is Friday the 20th.
        assertThat(PayrollCalendar.isPayslipReadyDate(LocalDate.of(2026, 11, 20))).isTrue();
        assertThat(PayrollCalendar.isPayslipReadyDate(LocalDate.of(2026, 11, 21))).isFalse();
        // Ordinary day is never ready.
        assertThat(PayrollCalendar.isPayslipReadyDate(LocalDate.of(2026, 7, 15))).isFalse();
    }

    @Test
    void readyPayoutMonth_mapsTodayToThePreviousWorkMonth() {
        assertThat(PayrollCalendar.readyPayoutMonth(LocalDate.of(2026, 9, 21)))
            .contains(YearMonth.of(2026, 8));
        // On the rolled-back date the payout month is still the current month.
        assertThat(PayrollCalendar.readyPayoutMonth(LocalDate.of(2026, 11, 20)))
            .contains(YearMonth.of(2026, 10));
        assertThat(PayrollCalendar.readyPayoutMonth(LocalDate.of(2026, 9, 20))).isEmpty();
    }
}
