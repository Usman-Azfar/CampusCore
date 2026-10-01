package com.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cms.models.Semester;
import com.cms.util.AttendanceRules;
import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AttendanceRulesTest {

    private static final LocalDate TODAY = LocalDate.now();

    private static Semester term(LocalDate start, LocalDate end) {
        Semester s = new Semester();
        s.setName("Test Term");
        s.setStartDate(Date.valueOf(start));
        s.setEndDate(Date.valueOf(end));
        return s;
    }

    private static final Semester RUNNING = term(TODAY.minusDays(60), TODAY.plusDays(60));

    @Test
    void todayAndTheLastSevenDaysAreAllowed() {
        assertNull(AttendanceRules.checkDate(TODAY, RUNNING));
        assertNull(AttendanceRules.checkDate(TODAY.minusDays(1), RUNNING));
        assertNull(AttendanceRules.checkDate(TODAY.minusDays(AttendanceRules.EDIT_WINDOW_DAYS), RUNNING)); // boundary
    }

    @Test
    void olderThanSevenDaysIsLocked() {
        String error = AttendanceRules.checkDate(TODAY.minusDays(AttendanceRules.EDIT_WINDOW_DAYS + 1), RUNNING);
        assertNotNull(error);
        assertTrue(error.contains("locked"), error);
    }

    @Test
    void futureDatesAreRefused() {
        String error = AttendanceRules.checkDate(TODAY.plusDays(1), RUNNING);
        assertNotNull(error);
        assertTrue(error.contains("future"), error);
    }

    @Test
    void datesOutsideTheTermAreRefused() {
        Semester startedYesterday = term(TODAY.minusDays(1), TODAY.plusDays(90));
        assertTrue(AttendanceRules.checkDate(TODAY.minusDays(2), startedYesterday).contains("before"));
        Semester endedTwoDaysAgo = term(TODAY.minusDays(100), TODAY.minusDays(2));
        assertTrue(AttendanceRules.checkDate(TODAY.minusDays(1), endedTwoDaysAgo).contains("after"));
        assertNull(AttendanceRules.checkDate(TODAY.minusDays(3), endedTwoDaysAgo)); // last week of an ended term
    }

    @Test
    void pickerRangeFollowsTermAndWindow() {
        Semester endedTwoDaysAgo = term(TODAY.minusDays(100), TODAY.minusDays(2));
        assertEquals(TODAY.minusDays(2), AttendanceRules.maxDate(endedTwoDaysAgo));
        assertEquals(TODAY.minusDays(AttendanceRules.EDIT_WINDOW_DAYS), AttendanceRules.minDate(endedTwoDaysAgo));
        assertTrue(AttendanceRules.isOpen(endedTwoDaysAgo));

        Semester longOver = term(TODAY.minusDays(200), TODAY.minusDays(30));
        assertFalse(AttendanceRules.isOpen(longOver));

        Semester notStarted = term(TODAY.plusDays(5), TODAY.plusDays(100));
        assertFalse(AttendanceRules.isOpen(notStarted));
    }

    @Test
    void missingDateAndParsing() {
        assertNotNull(AttendanceRules.checkDate((LocalDate) null, RUNNING));
        assertNull(AttendanceRules.parse("not-a-date"));
        assertNull(AttendanceRules.parse(""));
        assertEquals(LocalDate.of(2026, 2, 28), AttendanceRules.parse("2026-02-28"));
        assertNull(AttendanceRules.parse("2026-02-30"));
    }

    @Test
    void percentages() {
        assertEquals(-1, AttendanceRules.percent(0, 0));
        assertEquals(75, AttendanceRules.percent(3, 4));
        assertEquals(67, AttendanceRules.percent(2, 3));
    }
}
