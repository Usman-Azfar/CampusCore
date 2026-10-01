package com.cms.controllers;

import com.cms.dao.UserDAO;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

public class ChangePasswordServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = "password.flash.success";
    private static final String FLASH_ERROR = "password.flash.error";

    private UserDAO userDAO;

    public void init() {
        userDAO = new UserDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        SupportTicketServlet.moveFlash(session, request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(session, request, FLASH_ERROR, "errorMessage");
        request.getRequestDispatcher("change_password.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User user = session != null ? (User) session.getAttribute("user") : null;
        if (user == null) {
            response.sendRedirect("login");
            return;
        }

        String oldPassword = orEmpty(request.getParameter("oldPassword"));
        String newPassword = orEmpty(request.getParameter("newPassword"));
        String confirmPassword = orEmpty(request.getParameter("confirmPassword"));

        String error = null;
        if (oldPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            error = "Please fill in all three fields.";
        } else if (!newPassword.equals(confirmPassword)) {
            error = "The new passwords do not match.";
        } else if (newPassword.length() < UserFormSupport.MIN_PASSWORD) {
            error = "The new password must be at least " + UserFormSupport.MIN_PASSWORD + " characters.";
        } else if (newPassword.equals(oldPassword)) {
            error = "The new password must be different from the current one.";
        } else if (!userDAO.verifyPassword(user.getUserId(), oldPassword)) {
            error = "The current password is incorrect.";
        } else if (!userDAO.updatePassword(user.getUserId(), newPassword)) {
            error = "Could not change the password. Please try again.";
        }

        session.setAttribute(error == null ? FLASH_SUCCESS : FLASH_ERROR,
                error == null ? "Password changed successfully. Use the new password next time you log in." : error);
        // Post/Redirect/Get: refreshing the page does not resubmit the passwords
        response.sendRedirect("changePassword");
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
