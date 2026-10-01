package com.cms.controllers;

import com.cms.dao.AnnouncementDAO;
import com.cms.dao.CourseDAO;
import com.cms.models.Announcement;
import com.cms.models.Course;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;

@WebServlet("/manageAnnouncements")
public class CourseAnnouncementServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private AnnouncementDAO announcementDAO;
    private CourseDAO courseDAO;

    public void init() {
        announcementDAO = new AnnouncementDAO();
        courseDAO = new CourseDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String courseIdStr = request.getParameter("courseId");
        if (courseIdStr != null) {
            try {
                int courseId = Integer.parseInt(courseIdStr);
                Course course = courseDAO.getCourseById(courseId);
                List<Announcement> announcements = announcementDAO.getAnnouncementsByCourseId(courseId);

                request.setAttribute("course", course);
                request.setAttribute("announcements", announcements);
                request.getRequestDispatcher("/course_announcements.jsp").forward(request, response);
            } catch (NumberFormatException e) {
                response.sendRedirect("dashboard");
            }
        } else {
            response.sendRedirect("dashboard");
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User user = (User) session.getAttribute("user");

        String courseIdStr = request.getParameter("courseId");
        String title = request.getParameter("title");
        String content = request.getParameter("content");

        if (courseIdStr != null && title != null && content != null) {
            try {
                int courseId = Integer.parseInt(courseIdStr);
                Announcement ann = new Announcement();
                ann.setTitle(title);
                ann.setContent(content);
                ann.setCourseId(courseId);
                ann.setCreatedBy(user.getUserId());

                if (announcementDAO.createAnnouncement(ann)) {
                    response.sendRedirect("manageAnnouncements?courseId=" + courseId + "&success=true");
                } else {
                    response.sendRedirect("manageAnnouncements?courseId=" + courseId + "&error=true");
                }
            } catch (NumberFormatException e) {
                response.sendRedirect("dashboard");
            }
        } else {
            response.sendRedirect("dashboard");
        }
    }
}
