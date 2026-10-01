package com.cms.controllers;

import com.cms.models.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/** Small helpers shared by the admin servlets. */
final class AdminSupport {

    private AdminSupport() {
    }

    /**
     * Returns the logged-in admin, or null after redirecting (to login when not
     * logged in, to the dashboard for other roles). Use on GET and POST alike.
     */
    static User requireAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("user") : null;
        if (user == null) {
            response.sendRedirect("login");
            return null;
        }
        if (!"ADMIN".equals(user.getRole())) {
            response.sendRedirect("dashboard");
            return null;
        }
        return user;
    }

    static Integer parseInt(String s) {
        try {
            return (s == null || s.trim().isEmpty()) ? null : Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Distinct positive IDs from values like "5", "5,6,7" (possibly repeated)
    static Set<Integer> parseIds(String[] values) {
        return MessageServlet.parseIds(values);
    }

    static void flash(HttpServletRequest request, String key, String message) {
        request.getSession().setAttribute(key, message);
    }
}
