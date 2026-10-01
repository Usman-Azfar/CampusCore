package com.cms.controllers;

import com.cms.dao.ProfileDAO;
import com.cms.dao.UserDAO;
import com.cms.models.Profile;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * A user's own profile. Students and teachers may change their contact details;
 * their full name and father name are official records changed only by the admin.
 */
public class ProfileServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = "profile.flash.success";
    private static final String FLASH_ERROR = "profile.flash.error";
    private static final String DRAFT = "profile.flash.draft";

    private ProfileDAO profileDAO;
    private UserDAO userDAO;

    public void init() {
        profileDAO = new ProfileDAO();
        userDAO = new UserDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = currentUser(request, response);
        if (user == null)
            return;
        HttpSession session = request.getSession();
        SupportTicketServlet.moveFlash(session, request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(session, request, FLASH_ERROR, "errorMessage");
        SupportTicketServlet.moveFlash(session, request, DRAFT, "draft");

        request.setAttribute("profile", profileDAO.getProfileByUserId(user.getUserId()));
        // Account details incl. class (students) / department (teachers)
        request.setAttribute("account", userDAO.getUserById(user.getUserId()));
        request.setAttribute("nameLocked", nameLocked(user));
        request.getRequestDispatcher("update_profile.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = currentUser(request, response);
        if (user == null)
            return;
        HttpSession session = request.getSession();

        // Start from the saved profile so nothing the form does not send is lost
        Profile profile = profileDAO.getProfileByUserId(user.getUserId());
        String error = null;
        if (profile == null) {
            error = "Your profile record is missing. Please contact the admin through the Help Desk.";
        } else {
            if (!nameLocked(user)) {
                String fullName = UserFormSupport.clean(request.getParameter("fullName"));
                String fatherName = UserFormSupport.clean(request.getParameter("fatherName"));
                if (fullName.isEmpty() || fullName.length() > 100)
                    error = "Full name is required (up to 100 characters).";
                else if (fatherName.length() > 100)
                    error = "Father name can be at most 100 characters.";
                else {
                    profile.setFullName(fullName);
                    profile.setFatherName(fatherName.isEmpty() ? null : fatherName);
                }
            }
            if (error == null)
                error = UserFormSupport.readContact(request, profile, userDAO, user.getUserId());
        }

        if (error == null) {
            if (profileDAO.updateProfile(profile)) {
                session.setAttribute(FLASH_SUCCESS, "Profile updated successfully.");
            } else {
                error = "Could not update your profile. Please try again.";
            }
        }
        if (error != null) {
            session.setAttribute(FLASH_ERROR, error);
            session.setAttribute(DRAFT, UserFormSupport.draft(request));
            response.sendRedirect("updateProfile?draft=1");
            return;
        }
        // Post/Redirect/Get: refreshing the page does not resubmit
        response.sendRedirect("updateProfile");
    }

    // Official names are admin-managed for students and teachers
    private static boolean nameLocked(User user) {
        return "STUDENT".equals(user.getRole()) || "TEACHER".equals(user.getRole());
    }

    private User currentUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        User user = session != null ? (User) session.getAttribute("user") : null;
        if (user == null)
            response.sendRedirect("login");
        return user;
    }
}
