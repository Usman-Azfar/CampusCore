package com.cms.controllers;

import com.cms.dao.AttendanceDAO;
import com.cms.models.Attendance;
import com.cms.models.User;
import jakarta.servlet.ServletException;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;

public class AttendanceServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private AttendanceDAO attendanceDAO;

    private com.cms.dao.EnrollmentDAO enrollmentDAO;

    public void init() {
        attendanceDAO = new AttendanceDAO();
        enrollmentDAO = new com.cms.dao.EnrollmentDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        // AuthenticationFilter handles login check
        User user = (User) session.getAttribute("user");

        String enrollmentIdParam = request.getParameter("enrollmentId");

        if (enrollmentIdParam != null && !enrollmentIdParam.isEmpty()) {
            // SHOW ATTENDANCE FOR SPECIFIC COURSE
            int enrollmentId = Integer.parseInt(enrollmentIdParam);
            List<Attendance> attendanceList = attendanceDAO.getAttendanceByEnrollment(enrollmentId);
            request.setAttribute("attendanceList", attendanceList);

            // Helpful to show which course we are looking at (optional, but good UX)
            // request.setAttribute("courseName", ...);

            request.getRequestDispatcher("attendance.jsp").forward(request, response);
        } else {
            // SHOW LIST OF COURSES TO SELECT
            List<com.cms.models.Enrollment> enrollments = enrollmentDAO.getEnrollmentsByStudent(user.getUserId());
            request.setAttribute("enrollments", enrollments);
            request.getRequestDispatcher("attendance.jsp").forward(request, response);
        }
    }
}
