package com.cms.util;

import com.cms.models.Semester;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Rules for marking attendance:
 * - a lecture date cannot be in the future;
 * - attendance can be marked, changed or deleted only within {@value #EDIT_WINDOW_DAYS} days of the
 *   lecture date (after that it is locked);
 * - the date must fall inside the term's start and end dates;
 * - a course offering has at most {@value #MAX_LECTURES} lectures.
 */
public final class AttendanceRules {

    public static final int EDIT_WINDOW_DAYS = 7;
    public static final int MAX_LECTURES = 32;
    /** Attendance below this percentage is highlighted as low. */
    public static final int LOW_ATTENDANCE_PERCENT = 75;

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private AttendanceRules() {
    }

    public static LocalDate today() {
        return LocalDate.now();
    }

    /** The earliest lecture date that can still be marked or changed today. */
    public static LocalDate earliestEditable() {
        return today().minusDays(EDIT_WINDOW_DAYS);
    }

    /** The last day a lecture held on {@code lectureDate} can still be changed. */
    public static LocalDate lockDate(LocalDate lectureDate) {
        return lectureDate.plusDays(EDIT_WINDOW_DAYS);
    }

    /** First date the date picker allows: the later of the term start and the edit window start. */
    public static LocalDate minDate(Semester term) {
        LocalDate min = earliestEditable();
        if (term != null && term.getStartDate() != null && term.getStartDate().toLocalDate().isAfter(min))
            min = term.getStartDate().toLocalDate();
        return min;
    }

    /** Last date the date picker allows: the earlier of today and the term end. */
    public static LocalDate maxDate(Semester term) {
        LocalDate max = today();
        if (term != null && term.getEndDate() != null && term.getEndDate().toLocalDate().isBefore(max))
            max = term.getEndDate().toLocalDate();
        return max;
    }

    /** True when the offering's term still allows any date to be marked (the picker range is not empty). */
    public static boolean isOpen(Semester term) {
        return !minDate(term).isAfter(maxDate(term));
    }

    /**
     * Returns null when attendance for {@code date} can be marked or changed today, otherwise a
     * message explaining why not.
     */
    public static String checkDate(LocalDate date, Semester term) {
        if (date == null)
            return "Please choose a valid date.";
        LocalDate today = today();
        if (date.isAfter(today))
            return "Attendance cannot be marked for a future date (" + format(date) + ").";
        if (term != null && term.getStartDate() != null && date.isBefore(term.getStartDate().toLocalDate()))
            return format(date) + " is before " + term.getName() + " started (" + format(term.getStartDate().toLocalDate()) + ").";
        if (term != null && term.getEndDate() != null && date.isAfter(term.getEndDate().toLocalDate()))
            return format(date) + " is after " + term.getName() + " ended (" + format(term.getEndDate().toLocalDate()) + ").";
        if (date.isBefore(earliestEditable()))
            return "Attendance for " + format(date) + " is locked: it can only be marked or changed within "
                    + EDIT_WINDOW_DAYS + " days of the lecture (until " + format(lockDate(date)) + ").";
        return null;
    }

    public static String checkDate(Date date, Semester term) {
        return checkDate(date == null ? null : date.toLocalDate(), term);
    }

    public static LocalDate parse(String s) {
        try {
            return s == null || s.trim().isEmpty() ? null : LocalDate.parse(s.trim());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    public static String format(LocalDate date) {
        return date == null ? "" : date.format(DISPLAY);
    }

    /** Whole-number percentage, or -1 when nothing was marked. */
    public static int percent(int present, int marked) {
        return marked == 0 ? -1 : (int) Math.round(present * 100.0 / marked);
    }
}
