package com.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cms.util.GradeRules;
import org.junit.jupiter.api.Test;

class GradeRulesTest {

    @Test
    void marksMustRespectComponentMaximumAndHalfMarkStep() {
        assertTrue(GradeRules.isValidMark(0, 25));
        assertTrue(GradeRules.isValidMark(12.5, 25));
        assertTrue(GradeRules.isValidMark(25, 25));
        assertFalse(GradeRules.isValidMark(-0.5, 25));
        assertFalse(GradeRules.isValidMark(25.5, 25));
        assertFalse(GradeRules.isValidMark(12.25, 25));
        assertFalse(GradeRules.isValidMark(Double.NaN, 25));
        assertFalse(GradeRules.isValidMark(Double.POSITIVE_INFINITY, 25));
    }

    @Test
    void blankMeansNotEnteredNotZero() {
        assertNull(GradeRules.parseMark(null, 25, "Sessional"));
        assertNull(GradeRules.parseMark("", 25, "Sessional"));
        assertNull(GradeRules.parseMark("   ", 25, "Sessional"));
        assertEquals(0.0, GradeRules.parseMark("0", 25, "Sessional"));
        assertEquals(12.5, GradeRules.parseMark(" 12.5 ", 25, "Sessional"));
    }

    @Test
    void onlyPlainNumbersInRangeAreAccepted() {
        for (String bad : new String[] { "abc", "NaN", "Infinity", "1e1", "0x1p3", "-1", "36", "12.25", "1,5" }) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> GradeRules.parseMark(bad, 35, "Mid"), bad);
            assertEquals("Mid must be between 0 and 35 in steps of 0.5", e.getMessage());
        }
    }

    @Test
    void letterOnlyWhenAllThreeMarksAreEntered() {
        assertNull(GradeRules.letter(18.0, 25.0, null)); // final not held yet: no grade (used to be an F)
        assertEquals("A-", GradeRules.letter(20.0, 28.0, 35.0)); // 83
        assertEquals("F", GradeRules.letter(0.0, 0.0, 0.0));     // real zeros are a real F
        assertEquals(43.0, GradeRules.total(18.0, 25.0, null));  // total so far
        assertNull(GradeRules.total(null, null, null));
    }

    @Test
    void letterBoundaries() {
        assertEquals("A", GradeRules.letter(100));
        assertEquals("A", GradeRules.letter(85));
        assertEquals("A-", GradeRules.letter(84.5));
        assertEquals("C+", GradeRules.letter(61));
        assertEquals("C", GradeRules.letter(60.5));
        assertEquals("D", GradeRules.letter(50));
        assertEquals("F", GradeRules.letter(49.5));
    }

    @Test
    void pointsAndGpa() {
        assertEquals(4.0, GradeRules.points("A"));
        assertEquals(3.7, GradeRules.points("A-"));
        assertEquals(0.0, GradeRules.points("F"));
        assertEquals(0.0, GradeRules.points(null));
        assertTrue(GradeRules.isPass("D"));
        assertFalse(GradeRules.isPass("F"));
        assertFalse(GradeRules.isPass(null));
        // 3 credits of A- and 4 credits of B: (11.1 + 12) / 7 = 3.30
        assertEquals("3.30", GradeRules.gpa(3.7 * 3 + 3.0 * 4, 7));
        assertEquals("-", GradeRules.gpa(0, 0));
    }

    @Test
    void scaleMatchesLetters() {
        String[][] scale = GradeRules.scale();
        assertEquals("A", scale[0][0]);
        assertEquals("85 - 100", scale[0][1]);
        assertEquals("80 - 84.5", scale[1][1]);
        assertEquals("F", scale[scale.length - 1][0]);
        assertEquals("0 - 49.5", scale[scale.length - 1][1]);
        assertTrue(GradeRules.scaleJs().startsWith("[[85,\"A\"],[80,\"A-\"]"));
    }

    private static com.cms.models.Semester endingOn(java.time.LocalDate end) {
        com.cms.models.Semester term = new com.cms.models.Semester();
        term.setName("Fall 2024");
        term.setEndDate(end == null ? null : java.sql.Date.valueOf(end));
        return term;
    }

    @Test
    void gradesCanBeChangedUntilTenDaysAfterTheTermEnds() {
        java.time.LocalDate end = java.time.LocalDate.of(2025, 1, 15);
        com.cms.models.Semester term = endingOn(end);
        assertEquals(java.time.LocalDate.of(2025, 1, 25), GradeRules.lockDate(term));
        assertTrue(GradeRules.isEditable(term, end.minusDays(30)));  // during the term
        assertTrue(GradeRules.isEditable(term, end));                // last day of term
        assertTrue(GradeRules.isEditable(term, end.plusDays(10)));   // 10th day after: still allowed
        assertFalse(GradeRules.isEditable(term, end.plusDays(11)));  // locked
    }

    @Test
    void lockReasonNamesTheDates() {
        java.time.LocalDate today = java.time.LocalDate.now();
        assertNull(GradeRules.lockReason(endingOn(today.minusDays(GradeRules.EDIT_DAYS_AFTER_TERM))));
        String reason = GradeRules.lockReason(endingOn(java.time.LocalDate.of(2025, 1, 15)));
        assertTrue(reason.contains("15 Jan 2025") && reason.contains("25 Jan 2025"), reason);
        assertTrue(GradeRules.isEditable(endingOn(null)));           // no end date: never locks
        assertNull(GradeRules.lockDate(null));
    }

    @Test
    void formatting() {
        assertEquals("-", GradeRules.format(null));
        assertEquals("20", GradeRules.format(20.0));
        assertEquals("12.5", GradeRules.format(12.5));
        assertEquals("", GradeRules.inputValue(null));
    }
}
