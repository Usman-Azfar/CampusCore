package com.cms.controllers;

import com.cms.dao.AnnouncementDAO;
import com.cms.models.Announcement;
import com.cms.models.User;
import jakarta.servlet.ServletException;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;

public class AnnouncementServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private AnnouncementDAO announcementDAO;

    public void init() {
        announcementDAO = new AnnouncementDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        // AuthenticationFilter handles login check
        User user = (User) session.getAttribute("user");

        List<Announcement> announcements = announcementDAO.getAnnouncementsForStudent(user.getUserId());
        request.setAttribute("announcements", announcements);
        request.getRequestDispatcher("announcements.jsp").forward(request, response);
    }
}
