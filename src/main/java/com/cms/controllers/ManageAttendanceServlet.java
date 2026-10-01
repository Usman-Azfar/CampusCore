package com.cms.controllers;

import com.cms.dao.CourseAllocationDAO;
import com.cms.dao.EnrollmentDAO;
import com.cms.dao.AttendanceDAO;
import com.cms.models.CourseAllocation;
import com.cms.models.Enrollment;
import com.cms.models.Attendance;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.List;
import java.sql.Date;

@WebServlet("/manageAttendance")
public class ManageAttendanceServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private CourseAllocationDAO courseAllocationDAO;
    private EnrollmentDAO enrollmentDAO;
    private AttendanceDAO attendanceDAO;

    public void init() {
        courseAllocationDAO = new CourseAllocationDAO();
        enrollmentDAO = new EnrollmentDAO();
        attendanceDAO = new AttendanceDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.sendRedirect("login");
            return;
        }

        User user = (User) session.getAttribute("user");
        if (!"TEACHER".equals(user.getRole())) {
            response.sendRedirect("dashboard");
            return;
        }

        String allocationIdStr = request.getParameter("allocationId");

        try {
            if (allocationIdStr == null || allocationIdStr.isEmpty()) {
                // Step 1: List all courses allocated to this teacher
                List<CourseAllocation> allocations = courseAllocationDAO.getAllocationsByTeacher(user.getUserId());
                request.setAttribute("allocations", allocations);
                request.getRequestDispatcher("manage_attendance.jsp").forward(request, response);
            } else {
                // Step 2: Show student list for selected course
                int allocationId = Integer.parseInt(allocationIdStr);
                CourseAllocation allocation = courseAllocationDAO.getAllocationById(allocationId);

                // Security check: Ensure this allocation belongs to the logged-in teacher
                if (allocation == null || allocation.getTeacherId() != user.getUserId()) {
                    response.sendRedirect("manageAttendance"); // or error page
                    return;
                }

                List<Enrollment> enrollments = enrollmentDAO.getEnrollmentsByAllocationId(allocationId);

                request.setAttribute("selectedAllocation", allocation);
                request.setAttribute("enrollments", enrollments);
                request.getRequestDispatcher("manage_attendance.jsp").forward(request, response);
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("error.jsp");
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.sendRedirect("login");
            return;
        }

        try {
            int allocationId = Integer.parseInt(request.getParameter("allocationId"));
            String dateStr = request.getParameter("date");
            Date date = Date.valueOf(dateStr);
            int lectureNumber = Integer.parseInt(request.getParameter("lectureNumber")); // Assume manual input for now

            // Fetch students to iterate through parameters
            List<Enrollment> enrollments = enrollmentDAO.getEnrollmentsByAllocationId(allocationId);

            int count = 0;
            for (Enrollment enrollment : enrollments) {
                String statusParam = "status_" + enrollment.getEnrollmentId(); // e.g., status_101=Present
                String status = request.getParameter(statusParam);

                if (status != null && !status.isEmpty()) {
                    Attendance att = new Attendance();
                    att.setEnrollmentId(enrollment.getEnrollmentId());
                    att.setDate(date);
                    att.setLectureNumber(lectureNumber);
                    att.setStatus(status);

                    if (attendanceDAO.addAttendance(att)) {
                        count++;
                    }
                }
            }

            // Redirect with success message
            response.sendRedirect(request.getContextPath() + "/manageAttendance?allocationId=" + allocationId + "&success=true&count=" + count);

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(request.getContextPath() + "/manageAttendance?error=true");
        }
    }
}
