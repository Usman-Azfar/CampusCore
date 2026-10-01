package com.cms.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cms.models.Course;
import com.cms.models.CourseAllocation;
import com.cms.models.Enrollment;
import com.cms.models.Grade;
import com.cms.models.Semester;
import com.cms.util.GradeRules;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Gradebook and transcript: result status, totals, repeats, grouping and filters. */
class GradebookFlowTest {

    private static int nextId = 1;

    /** A result; marks null = not entered. */
    private static Grade result(int termId, String start, int semesterNumber, int courseId, String code, int credits,
            Double s, Double m, Double f, boolean published, String status) {
        Semester term = new Semester();
        term.setSemesterId(termId);
        term.setName("Term " + termId);
        term.setStartDate(java.sql.Date.valueOf(start));
        Course c = new Course();
        c.setCourseId(courseId);
        c.setCourseCode(code);
        c.setCreditHours(credits);
        CourseAllocation ca = new CourseAllocation();
        ca.setSemesterId(termId);
        ca.setSemester(term);
        ca.setCourse(c);
        Enrollment e = new Enrollment();
        e.setEnrollmentId(nextId++);
        e.setStatus(status);
        e.setSemesterNumber(semesterNumber);
        e.setCourseAllocation(ca);
        Grade g = new Grade();
        g.setEnrollmentId(e.getEnrollmentId());
        g.setSessionalMarks(s);
        g.setMidMarks(m);
        g.setFinalMarks(f);
        g.setPublished(published);
        g.setGradeLetter(GradeRules.letter(s, m, f));
        g.setEnrollment(e);
        return g;
    }

    // Fall 2024 (term 1, 3rd semester): OOP A- (83), DSA F (40) then repeated in Spring 2025 (term 2, 4th): B (70)
    private final Grade oop = result(1, "2024-09-01", 3, 10, "CS-201", 3, 20.0, 28.0, 35.0, true, "ENROLLED");
    private final Grade dsaF = result(1, "2024-09-01", 3, 11, "CS-301", 4, 10.0, 15.0, 15.0, true, "ENROLLED");
    private final Grade ict = result(1, "2024-09-01", 3, 12, "CS-101", 3, null, null, null, false, "ENROLLED");
    private final Grade dsaRepeat = result(2, "2025-02-01", 4, 11, "CS-301", 4, 20.0, 25.0, 25.0, true, "ENROLLED");
    private final Grade calc = result(2, "2025-02-01", 4, 13, "MATH-101", 3, 18.0, 25.0, null, true, "ENROLLED");
    private final Grade dropped = result(2, "2025-02-01", 4, 14, "ENG-101", 3, 20.0, 30.0, 30.0, true, "WITHDRAWN");

    @Test
    void resultStatus() {
        assertEquals(Grade.RESULT_GRADED, oop.getResultStatus());
        assertEquals(Grade.RESULT_AWAITED, ict.getResultStatus());
        assertEquals(Grade.RESULT_IN_PROGRESS, calc.getResultStatus());
        assertEquals(Grade.RESULT_WITHDRAWN, dropped.getResultStatus());
        assertFalse(dropped.isGraded()); // even with marks, a withdrawal never counts
    }

    @Test
    void summaryCountsGradedOnlyAndFailsAreNotEarned() {
        GradeRules.Summary s = GradeRules.summarize(List.of(oop, dsaF, ict, dsaRepeat, calc, dropped));
        assertEquals(3, s.graded);
        assertEquals(1, s.withdrawn);
        assertEquals(11, s.creditsAttempted); // 3 + 4 + 4
        assertEquals(7, s.creditsEarned);     // the F's 4 are not earned
        // (3.7*3 + 0*4 + 3.0*4) / 11 = 23.1 / 11 = 2.10
        assertEquals("2.10", s.getGpa());
        assertEquals("-", GradeRules.summarize(List.of(ict, dropped)).getGpa());
    }

    @Test
    void cgpaUsesOnlyTheLatestGradedAttemptOfARepeatedCourse() {
        List<Grade> all = List.of(oop, dsaF, ict, dsaRepeat, calc, dropped);
        assertEquals(Set.of(dsaF.getEnrollmentId()), GradeRules.replaced(all)); // the F was retaken and passed
        GradeRules.Summary cum = GradeRules.summarizeCumulative(all);
        assertEquals(2, cum.graded);
        assertEquals(1, cum.replaced);
        assertEquals(7, cum.creditsAttempted); // 3 (OOP) + 4 (DSA retake); the F's 4 no longer count
        assertEquals(7, cum.creditsEarned);
        // (3.7*3 + 3.0*4) / 7 = 23.1 / 7 = 3.30 (it was 2.10 with both attempts counted)
        assertEquals("3.30", cum.getGpa());
        assertEquals("3.30", GradeRules.cgpa(all));
        // A semester's own GPA stays as it was in that term
        assertEquals("1.59", GradeRules.summarize(List.of(oop, dsaF)).getGpa()); // (11.1 + 0) / 7
    }

    @Test
    void ungradedOrWithdrawnRetakeDoesNotReplaceTheEarlierGrade() {
        Grade retakeAwaited = result(3, "2025-09-01", 5, 11, "CS-301", 4, null, null, null, false, "ENROLLED");
        Grade retakeWithdrawn = result(3, "2025-09-01", 5, 11, "CS-301", 4, 20.0, 30.0, 30.0, true, "WITHDRAWN");
        assertTrue(GradeRules.replaced(List.of(dsaF, retakeAwaited, retakeWithdrawn)).isEmpty());
        assertEquals("0.00", GradeRules.cgpa(List.of(dsaF, retakeAwaited))); // the F still counts until the retake is graded
        // Order of the list does not matter: the later term wins
        assertEquals(Set.of(dsaF.getEnrollmentId()), GradeRules.replaced(List.of(dsaRepeat, dsaF)));
    }

    @Test
    void laterAttemptsOfTheSameCourseAreRepeats() {
        Set<Integer> repeats = GradebookServlet.repeats(List.of(dsaRepeat, oop, dsaF)); // any order in
        assertEquals(Set.of(dsaRepeat.getEnrollmentId()), repeats);
    }

    @Test
    void groupedByTermAndSemester() {
        Grade odd = result(1, "2024-09-01", 4, 15, "CS-401", 3, null, null, null, false, "ENROLLED"); // same term, other number
        Map<String, List<Grade>> groups = GradebookServlet.groupByTerm(List.of(oop, dsaF, odd, dsaRepeat));
        assertEquals(3, groups.size());
        assertEquals(List.of(oop, dsaF), groups.get("1:3"));
        assertEquals(List.of(odd), groups.get("1:4"));
    }

    @Test
    void missingOrUnknownResultFilterIsIgnored() {
        assertEquals(null, GradebookServlet.Filters.resultParam(null)); // used to throw (500 on /gradebook)
        assertEquals(null, GradebookServlet.Filters.resultParam("<x>"));
        assertEquals("failed", GradebookServlet.Filters.resultParam("failed"));
    }

    @Test
    void filters() {
        List<Grade> all = List.of(oop, dsaF, ict, dsaRepeat, calc, dropped);
        GradebookServlet.Filters f = new GradebookServlet.Filters();
        assertEquals(6, f.apply(all).size());
        f.term = 2;
        assertEquals(List.of(dsaRepeat, calc, dropped), f.apply(all));
        f.term = null;
        f.semester = 3;
        assertEquals(List.of(oop, dsaF, ict), f.apply(all));
        f.semester = null;
        f.course = "CS-301";
        assertEquals(List.of(dsaF, dsaRepeat), f.apply(all));
        f.course = null;
        for (String[] r : new String[][] { {"graded", "3"}, {"failed", "1"}, {"progress", "1"}, {"awaited", "1"}, {"withdrawn", "1"} }) {
            f.result = r[0];
            assertEquals(Integer.parseInt(r[1]), f.apply(all).size(), r[0]);
        }
        f.result = "failed";
        assertEquals(List.of(dsaF), f.apply(all));
        assertTrue(f.isActive());
        assertEquals("result=failed", f.query());
    }
}
