package com.cms.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cms.dao.AdminOverviewDAO;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The admin dashboard's "Needs attention" list. */
class AdminDashboardTest {

    private static AdminOverviewDAO.ClassRow cls(String name, String term, int students) {
        AdminOverviewDAO.ClassRow c = new AdminOverviewDAO.ClassRow();
        c.className = name;
        c.termName = term;
        c.semesterNumber = term == null ? 0 : 3;
        c.students = students;
        return c;
    }

    private static AdminOverviewDAO.CourseGap gap(int courseId, String code, String className) {
        AdminOverviewDAO.CourseGap g = new AdminOverviewDAO.CourseGap();
        g.courseId = courseId;
        g.allocationId = 40 + courseId;
        g.courseCode = code;
        g.courseName = "Course " + code;
        g.termName = "Fall 2026";
        g.className = className;
        return g;
    }

    @Test
    void nothingToDoMeansAnEmptyList() {
        List<String[]> items = DashboardServlet.adminAttention(new AdminOverviewDAO.Overview(),
                List.of(cls("BS CS-2024", "Fall 2026", 30), cls("BS CS-2019", null, 0)), // a finished class with no students is fine
                Collections.emptyList(), Collections.emptyList());
        assertTrue(items.isEmpty());
    }

    @Test
    void setupGapsFirstThenWaitingWork() {
        AdminOverviewDAO.Overview o = new AdminOverviewDAO.Overview();
        o.proofsToReview = 3;
        o.pendingRequests = 1;
        o.openTickets = 4;
        o.overdueChallans = 2;
        o.overdueAmount = new BigDecimal("90000");
        o.studentsWithoutClass = 1;
        List<String[]> items = DashboardServlet.adminAttention(o,
                List.of(cls("BS IT-2022", null, 2), cls("BS CS-2024", "Fall 2026", 30)),
                List.of(gap(9, "CS-401", "BS CS-2022")), List.of(gap(12, "IT-202", null)));

        assertEquals("error", items.get(0)[3]);
        assertTrue(items.get(0)[0].contains("BS IT-2022 (2 students)"), items.get(0)[0]);
        assertEquals("manageSemesters", items.get(0)[1]);
        assertTrue(items.get(1)[0].startsWith("CS-401 Course CS-401 has no teacher for Fall 2026"), items.get(1)[0]);
        assertEquals("manageAllocations?courseId=9", items.get(1)[1]);
        assertEquals("3 payment proofs are waiting for review.", items.get(2)[0]);
        assertEquals("1 add/drop request is waiting for approval.", items.get(3)[0]);
        assertEquals("4 help desk tickets are open.", items.get(4)[0]);
        assertTrue(items.get(5)[0].contains("PKR 90,000"), items.get(5)[0]);
        assertEquals("manageEnrollment?courseId=12&allocationId=52", items.get(6)[1]);
        assertEquals("1 active student has no class assigned.", items.get(7)[0]);
        assertEquals(8, items.size());
    }

    @Test
    void longListsAreShortened() {
        List<AdminOverviewDAO.CourseGap> many = new ArrayList<>();
        for (int i = 1; i <= 8; i++)
            many.add(gap(i, "CS-10" + i, "BS CS-2026"));
        List<String[]> items = DashboardServlet.adminAttention(new AdminOverviewDAO.Overview(), Collections.emptyList(), many, many);
        assertEquals(5 + 1 + 1, items.size()); // five courses, "3 more", one line for empty offerings
        assertEquals("3 more courses for this term have no teacher.", items.get(5)[0]);
        assertTrue(items.get(6)[0].startsWith("8 course offerings have no students enrolled") && items.get(6)[0].contains("and 3 more"), items.get(6)[0]);
    }
}
