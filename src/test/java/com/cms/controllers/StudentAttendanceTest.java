package com.cms.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cms.models.CourseAttendance;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The student Attendance page: term label, filters and grouping. */
class StudentAttendanceTest {

    private static CourseAttendance course(int termId, String term, int semester, String code, int present, int absent) {
        CourseAttendance c = new CourseAttendance();
        c.setTermId(termId);
        c.setTermName(term);
        c.setSemesterNumber(semester);
        c.setCourseCode(code);
        c.setPresent(present);
        c.setAbsent(absent);
        c.setLecturesHeld(present + absent + 1);
        return c;
    }

    @Test
    void termLabelLooksLikeFall2024Dash3rdSemester() {
        assertEquals("Fall 2024-3rd Semester", course(1, "Fall 2024", 3, "CS-201", 0, 0).getTermLabel());
        assertEquals("Spring 2025-1st Semester", CourseAttendance.label("Spring 2025", 1));
        assertEquals("Fall 2024", CourseAttendance.label("Fall 2024", 0)); // number unknown
    }

    @Test
    void lectureCounts() {
        CourseAttendance c = course(1, "Fall 2024", 3, "CS-201", 4, 1); // 6 held, 5 marked
        assertEquals(5, c.getMarked());
        assertEquals(1, c.getNotMarked());
        assertEquals(80, c.getPercent());
        assertFalse(c.isLow());
    }

    @Test
    void filtersByTermSemesterCourseAndLevel() {
        CourseAttendance a = course(2, "Spring 2025", 4, "CS-301", 2, 3); // 40%: low
        CourseAttendance b = course(1, "Fall 2024", 3, "CS-201", 4, 1);   // 80%
        CourseAttendance none = course(1, "Fall 2024", 3, "CS-101", 0, 0); // no lectures marked
        AttendanceServlet.Filters f = new AttendanceServlet.Filters();
        assertFalse(f.isActive());
        assertEquals(3, f.apply(List.of(a, b, none)).size());

        f.term = 1;
        assertEquals(List.of(b, none), f.apply(List.of(a, b, none)));
        f.term = null;
        f.semester = 4;
        assertEquals(List.of(a), f.apply(List.of(a, b, none)));
        f.semester = null;
        f.course = "CS-201";
        assertEquals(List.of(b), f.apply(List.of(a, b, none)));
        f.course = null;
        f.level = AttendanceServlet.LEVEL_LOW;
        assertEquals(List.of(a), f.apply(List.of(a, b, none)));
        f.level = AttendanceServlet.LEVEL_OK; // courses with no marked lectures are neither
        assertEquals(List.of(b), f.apply(List.of(a, b, none)));
        assertTrue(f.isActive());
        assertEquals("level=ok", f.query());
        f.course = "CS 201&x";
        assertEquals("course=CS+201%26x&level=ok", f.query());
    }

    @Test
    void groupsByTermAndSemesterInListOrder() {
        CourseAttendance spring1 = course(2, "Spring 2025", 4, "CS-301", 0, 0);
        CourseAttendance spring2 = course(2, "Spring 2025", 4, "MATH-201", 0, 0);
        CourseAttendance fall = course(1, "Fall 2024", 3, "CS-201", 0, 0);
        Map<String, List<CourseAttendance>> groups = AttendanceServlet.group(List.of(spring1, spring2, fall));
        assertEquals(2, groups.size());
        List<List<CourseAttendance>> values = List.copyOf(groups.values());
        assertEquals(List.of(spring1, spring2), values.get(0)); // newest term first, as loaded
        assertEquals(List.of(fall), values.get(1));
    }
}
