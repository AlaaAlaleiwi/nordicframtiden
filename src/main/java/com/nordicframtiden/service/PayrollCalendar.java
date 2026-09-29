package com.nordicframtiden.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Payroll calendar rule for automatic payslip delivery: payslips for a work
 * month are "ready" on the 21st of the payment month — or, when the 21st is
 * not a working day (weekend or Swedish public holiday), on the previous
 * working day. Working days are Monday–Friday excluding Saturdays, Sundays
 * and the fixed + Easter/Ascension/Whit Swedish public holidays.
 */
public final class PayrollCalendar {

    /** Day of month the payslip becomes ready, when it is a working day. */
    static final int READY_DAY = 21;

    private PayrollCalendar() {
    }

    /**
     * Ready date for the work month of {@code payoutMonth}: the 21st of the
     * following month, rolled back to the previous working day when the 21st
     * falls on a weekend or Swedish public holiday.
     */
    public static LocalDate readyDateFor(YearMonth payoutMonth) {
        return previousOrSameWorkingDay(payoutMonth.plusMonths(1).atDay(READY_DAY));
    }

    /**
     * True when {@code today} is the ready date for the work month that pays
     * out this month (i.e. previous month): the 21st, or the previous working
     * day when the 21st is a weekend/holiday.
     */
    public static boolean isPayslipReadyDate(LocalDate today) {
        return today.equals(readyDateFor(YearMonth.from(today).minusMonths(1)));
    }

    /**
     * Work month whose payslip becomes ready on {@code today} (previous month
     * when today is a ready date); empty when today is not a ready date.
     */
    public static java.util.Optional<YearMonth> readyPayoutMonth(LocalDate today) {
        if (!isPayslipReadyDate(today)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(YearMonth.from(today).minusMonths(1));
    }

    /**
     * {@code date} itself when it is a working day, otherwise the closest
     * earlier working day. Walks back over weekends and holidays; a full
     * holiday cluster can never hide more than a handful of days, so 14 steps
     * are ample.
     */
    static LocalDate previousOrSameWorkingDay(LocalDate date) {
        LocalDate candidate = date;
        for (int i = 0; i < 14; i++) {
            if (isWorkingDay(candidate)) {
                return candidate;
            }
            candidate = candidate.minusDays(1);
    }
        return candidate;
    }

    /** Monday–Friday and not a Swedish public holiday. */
    static boolean isWorkingDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        return !isPublicHoliday(date);
    }

    /**
     * Swedish public holidays (alla helgons dag): the fixed-date holidays plus
     * Easter Sunday and the Easter-relative movable ones (Långfredagen,
     * Annandag påsk, Kristi himmelfärdsdag, Pingstdagen). Annandag pingst
     * (Whit Monday) was abolished as a Swedish holiday in 2005 and stays a
     * working day. Midsummer Eve and similar "de facto" days off are not
     * statutory holidays — payday follows the law, not custom.
     */
    static boolean isPublicHoliday(LocalDate date) {
        if (FIXED_HOLIDAYS.contains(date.getMonthValue() * 100 + date.getDayOfMonth())) {
            return true;
        }
        LocalDate easter = easterSunday(date.getYear());
        return date.equals(easter.minusDays(2))          // Långfredagen
            || date.equals(easter)                        // Påskdagen
            || date.equals(easter.plusDays(1))            // Annandag påsk
            || date.equals(easter.plusDays(39))           // Kristi himmelfärdsdag
            || date.equals(easter.plusDays(49));          // Pingstdagen
    }

    /**
     * Fixed-date Swedish public holidays as month*100 + day codes (a month can
     * hold several holidays, so a month-keyed map cannot represent them).
     */
    private static final java.util.Set<Integer> FIXED_HOLIDAYS = java.util.Set.of(
        1 * 100 + 1,    // Nyårsdagen
        1 * 100 + 6,    // Trettondag jul
        5 * 100 + 1,    // Första maj
        6 * 100 + 6,    // Sveriges nationaldag
        12 * 100 + 25,  // Juldagen
        12 * 100 + 26   // Annandag jul
    );

    /** Gregorian Easter (Anonymous Gregorian algorithm). */
    static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
