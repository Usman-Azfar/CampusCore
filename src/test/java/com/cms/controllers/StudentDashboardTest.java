package com.cms.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cms.models.Challan;
import com.cms.models.ClassSemester;
import com.cms.models.CourseAttendance;
import com.cms.models.Course;
import com.cms.models.CourseAllocation;
import com.cms.models.Enrollment;
import com.cms.models.Grade;
import com.cms.util.GradeRules;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The student dashboard's "Needs attention" list and CGPA tile. */
class StudentDashboardTest {

    private static Challan challan(int id, LocalDate due) {
        Challan c = new Challan();
        c.setChallanId(id);
        c.setStatus("UNPAID");
        c.setTitle("Semester fee");
        c.setAmount(new java.math.BigDecimal("45000"));
        c.setDueDate(java.sql.Date.valueOf(due));
        return c;
    }

    private static CourseAttendance course(String code, int present, int absent) {
        CourseAttendance c = new CourseAttendance();
        c.setCourseCode(code);
        c.setEnrollmentId(7);
        c.setPresent(present);
        c.setAbsent(absent);
        return c;
    }

    @Test
    void urgentItemsComeFirst() {
        ClassSemester term = new ClassSemester();
        Challan dueLater = challan(1, LocalDate.now().plusDays(20));
        Challan overdue = challan(2, LocalDate.now().minusDays(3));
        Challan rejected = challan(3, LocalDate.now().plusDays(5));
        rejected.setProofStatus("REJECTED");
        rejected.setProofReviewNote("Receipt is unreadable");

        List<String[]> items = DashboardServlet.studentAttention(term,
                List.of(course("CS-201", 3, 2), course("CS-301", 4, 0)), // 60% (low) and 100%
                List.of(dueLater, overdue, rejected), List.of(rejected), 2);

        assertTrue(items.get(0)[0].contains("CH-000002") && items.get(0)[0].contains("overdue"), items.get(0)[0]);
        assertTrue(items.get(1)[0].contains("not accepted: Receipt is unreadable"), items.get(1)[0]);
        assertTrue(items.get(2)[0].contains("CS-201 is 60%"), items.get(2)[0]);
        assertEquals("attendance?enrollmentId=7", items.get(2)[1]);
        // then the non-urgent ones: the two challans not yet due, the pending requests
        assertEquals("", items.get(3)[3]);
        assertTrue(items.get(items.size() - 1)[0].startsWith("2 add/drop requests are waiting"));
        assertEquals(6, items.size());
        for (String[] i : items)
            assertTrue(!i[0].contains("CS-301"), "100% attendance is not flagged");
    }

    @Test
    void pendingProofIsNotNaggedAndNoSemesterIsShown() {
        Challan pending = challan(4, LocalDate.now().plusDays(5));
        pending.setProofStatus("SUBMITTED");
        List<String[]> items = DashboardServlet.studentAttention(null, Collections.emptyList(), List.of(pending),
                Collections.emptyList(), 0);
        assertEquals(1, items.size());
        assertTrue(items.get(0)[0].contains("not placed in a semester"), items.get(0)[0]);
    }

    private static int nextId = 1;

    // Each result is a different course and enrollment (same IDs would look like retakes of one course)
    private static Grade grade(int credits, double s, double m, Double f, boolean published) {
        int id = nextId++;
        Grade g = new Grade();
        g.setEnrollmentId(id);
        g.setSessionalMarks(s);
        g.setMidMarks(m);
        g.setFinalMarks(f);
        g.setGradeLetter(GradeRules.letter(g.getSessionalMarks(), g.getMidMarks(), g.getFinalMarks()));
        g.setPublished(published);
        Course c = new Course();
        c.setCourseId(id);
        c.setCreditHours(credits);
        CourseAllocation ca = new CourseAllocation();
        ca.setCourse(c);
        Enrollment e = new Enrollment();
        e.setEnrollmentId(id);
        e.setCourseAllocation(ca);
        g.setEnrollment(e);
        return g;
    }

    @Test
    void cgpaCountsOnlyPublishedCompleteResults() {
        assertEquals("-", GradeRules.cgpa(Collections.emptyList()));
        // 3 credits A- (83) + 4 credits B (71); an unpublished A and an incomplete result are ignored
        String cgpa = GradeRules.cgpa(List.of(grade(3, 20, 28, 35.0, true), grade(4, 20, 21, 30.0, true),
                grade(3, 25, 35, 40.0, false), grade(3, 18, 25, null, true)));
        assertEquals("3.30", cgpa);
    }
}
