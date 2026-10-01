package com.cms.controllers;

import com.cms.dao.AcademicDAO;
import com.cms.dao.CourseRequestDAO;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Admin side of Add / Drop / Withdraw: lists pending requests and history, and
 * approves or rejects requests.
 */
public class AdminCourseRequestsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = "adminRequests.flash.success";
    private static final String FLASH_ERROR = "adminRequests.flash.error";

    private CourseRequestDAO courseRequestDAO;
    private AcademicDAO academicDAO;

    public void init() {
        courseRequestDAO = new CourseRequestDAO();
        academicDAO = new AcademicDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User admin = currentAdmin(request, response);
        if (admin == null)
            return;
        HttpSession session = request.getSession(false);
        SupportTicketServlet.moveFlash(session, request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(session, request, FLASH_ERROR, "errorMessage");

        request.setAttribute("pendingRequests", courseRequestDAO.getPendingRequests());
        request.setAttribute("processedRequests", courseRequestDAO.getProcessedRequests());
        request.setAttribute("classes", academicDAO.getAllClasses());
        request.getRequestDispatcher("admin_course_requests.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User admin = currentAdmin(request, response);
        if (admin == null)
            return;
        HttpSession session = request.getSession(false);

        int requestId;
        try {
            requestId = Integer.parseInt(request.getParameter("requestId"));
        } catch (NumberFormatException e) {
            session.setAttribute(FLASH_ERROR, "Invalid request.");
            response.sendRedirect("manageCourseRequests");
            return;
        }

        // Student, course and type are read from the database, never from the form
        String action = request.getParameter("action");
        if ("APPROVE".equals(action)) {
            String error = courseRequestDAO.approveRequest(requestId, admin.getUserId());
            if (error == null) {
                session.setAttribute(FLASH_SUCCESS, "Request #" + requestId + " approved and the student's enrollment updated.");
            } else {
                session.setAttribute(FLASH_ERROR, "Request #" + requestId + " was not approved: " + error);
            }
        } else if ("REJECT".equals(action)) {
            if (courseRequestDAO.rejectRequest(requestId, admin.getUserId())) {
                session.setAttribute(FLASH_SUCCESS, "Request #" + requestId + " rejected.");
            } else {
                session.setAttribute(FLASH_ERROR, "Request #" + requestId + " was not found or has already been processed.");
            }
        } else {
            session.setAttribute(FLASH_ERROR, "Unknown action.");
        }

        response.sendRedirect("manageCourseRequests");
    }

    private User currentAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.sendRedirect("login");
            return null;
        }
        User user = (User) session.getAttribute("user");
        if (!"ADMIN".equals(user.getRole())) {
            response.sendRedirect("dashboard");
            return null;
        }
        return user;
    }
}
