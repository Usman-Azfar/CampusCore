package com.cms.controllers;

import com.cms.dao.CourseAllocationDAO;
import com.cms.models.CourseAllocation;
import com.cms.models.Semester;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Teacher: Course List. Every course offering assigned to them, current terms first, with
 * students, attendance, grading and announcements at a glance and links to each task.
 */
public class TeacherCoursesServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private CourseAllocationDAO allocationDAO;

    public void init() {
        allocationDAO = new CourseAllocationDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("user");
        if (user == null) {
            response.sendRedirect("login");
            return;
        }
        if (!"TEACHER".equals(user.getRole())) {
            response.sendRedirect("dashboard");
            return;
        }
        SupportTicketServlet.moveFlash(session, request, CourseAnnouncementServlet.FLASH_ERROR, "errorMessage");

        List<CourseAllocation> current = new ArrayList<>(), past = new ArrayList<>();
        for (CourseAllocation ca : allocationDAO.getTeachingOverview(user.getUserId()))
            (isCurrentTerm(ca.getSemester(), LocalDate.now()) ? current : past).add(ca);
        request.setAttribute("currentCourses", current);
        request.setAttribute("pastCourses", past);
        request.getRequestDispatcher("my_courses.jsp").forward(request, response);
    }

    /** A term that has not ended yet, or that a class is still placed in. */
    static boolean isCurrentTerm(Semester term, LocalDate today) {
        return term == null || term.isActive() || term.getEndDate() == null
                || !term.getEndDate().toLocalDate().isBefore(today);
    }
}
