package com.cms.controllers;

import com.cms.dao.EnrollmentDAO;
import com.cms.dao.ProfileDAO;
import com.cms.dao.UserDAO;
import com.cms.models.Enrollment;
import com.cms.models.Profile;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;

public class DashboardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private EnrollmentDAO enrollmentDAO;
    private ProfileDAO profileDAO;
    private com.cms.dao.CourseAllocationDAO allocationDAO;

    public void init() {
        enrollmentDAO = new EnrollmentDAO();
        profileDAO = new ProfileDAO();
        allocationDAO = new com.cms.dao.CourseAllocationDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.sendRedirect("login.jsp");
            return;
        }

        User user = (User) session.getAttribute("user");
        Profile profile = profileDAO.getProfileByUserId(user.getUserId());
        request.setAttribute("profile", profile);
        // Account details incl. class (students) / department (teachers)
        request.setAttribute("account", new com.cms.dao.UserDAO().getUserById(user.getUserId()));

        if ("STUDENT".equals(user.getRole())) {
            // Unpaid challans for the dashboard reminder
            java.util.List<com.cms.models.Challan> unpaidChallans = new java.util.ArrayList<>();
            for (com.cms.models.Challan c : new com.cms.dao.ChallanDAO().getChallansByStudent(user.getUserId()))
                if (!c.isPaid()) unpaidChallans.add(c);
            request.setAttribute("unpaidChallans", unpaidChallans);
            List<Enrollment> enrollments = enrollmentDAO.getEnrollmentsByStudent(user.getUserId());
            request.setAttribute("enrollments", enrollments);
            // Assuming current semester is 1 for now or derived from enrollments
            int semesterNum = !enrollments.isEmpty() ? enrollments.get(0).getSemesterNumber() : 1;
            request.setAttribute("currentSemester", semesterNum);
        } else if ("TEACHER".equals(user.getRole())) {
            List<com.cms.models.CourseAllocation> allocations = allocationDAO.getAllocationsByTeacher(user.getUserId());
            request.setAttribute("allocations", allocations);
        }

        request.getRequestDispatcher("dashboard.jsp").forward(request, response);
    }
}
