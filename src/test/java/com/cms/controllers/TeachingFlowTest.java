package com.cms.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cms.dao.AnnouncementDAO;
import com.cms.models.Announcement;
import com.cms.models.Semester;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Course List grouping and announcement input rules. */
class TeachingFlowTest {

    @Test
    void currentTermsAreThoseNotOverOrStillActive() {
        LocalDate today = LocalDate.of(2026, 10, 1);
        Semester term = new Semester();
        term.setEndDate(java.sql.Date.valueOf(today.minusDays(1)));
        assertFalse(TeacherCoursesServlet.isCurrentTerm(term, today));
        term.setActive(true); // a class is still placed in it
        assertTrue(TeacherCoursesServlet.isCurrentTerm(term, today));
        term.setActive(false);
        term.setEndDate(java.sql.Date.valueOf(today));
        assertTrue(TeacherCoursesServlet.isCurrentTerm(term, today));
    }

    @Test
    void announcementTitleAndTextAreRequiredAndLimited() {
        assertNull(AnnouncementDAO.validate("Quiz 2", "On Monday"));
        assertEquals("Please enter a title.", AnnouncementDAO.validate("", "x"));
        assertEquals("Please enter the announcement text.", AnnouncementDAO.validate("Quiz", ""));
        assertTrue(AnnouncementDAO.validate("x".repeat(AnnouncementDAO.MAX_TITLE + 1), "x").contains("at most"));
        assertNull(AnnouncementDAO.validate("x".repeat(AnnouncementDAO.MAX_TITLE), "x".repeat(AnnouncementDAO.MAX_CONTENT)));
        assertTrue(AnnouncementDAO.validate("Quiz", "x".repeat(AnnouncementDAO.MAX_CONTENT + 1)).contains("at most"));
    }

    @Test
    void textIsCleanedBeforeChecking() {
        assertEquals("Quiz 2 on Monday", CourseAnnouncementServlet.clean("  Quiz 2 \n on   Monday "));
        // Windows line breaks count as one character, like the browser's counter
        assertEquals("a\nb", AnnouncementDAO.cleanContent(" a\r\nb "));
        assertEquals("", AnnouncementDAO.cleanContent(null));
    }

    @Test
    void audienceLabels() {
        Announcement a = new Announcement();
        assertTrue(a.isGeneral());
        assertEquals("Everyone", a.getAudienceLabel());
        a.setAudience(Announcement.AUDIENCE_STUDENTS);
        assertEquals("Students", a.getAudienceLabel());
        a.setAudience(Announcement.AUDIENCE_TEACHERS);
        assertEquals("Teachers", a.getAudienceLabel());
        a.setAllocationId(4);
        assertFalse(a.isGeneral());
        assertTrue(AnnouncementDAO.AUDIENCES.contains("ALL") && !AnnouncementDAO.AUDIENCES.contains("ADMIN"));
    }
}
