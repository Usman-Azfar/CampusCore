package com.cms.controllers;

import com.cms.dao.AnnouncementDAO;
import com.cms.dao.EnrollmentDAO;
import com.cms.models.Announcement;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Announcements page for everyone.
 * Students: general announcements for students plus those of the courses they are enrolled in
 * (optionally one course). Teachers: general announcements for teachers. Admin: posts, edits and
 * deletes general announcements and chooses who sees them (everyone, students or teachers).
 */
public class AnnouncementServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = CourseAnnouncementServlet.FLASH_SUCCESS;
    private static final String FLASH_ERROR = CourseAnnouncementServlet.FLASH_ERROR;

    private AnnouncementDAO announcementDAO;
    private EnrollmentDAO enrollmentDAO;

    public void init() {
        announcementDAO = new AnnouncementDAO();
        enrollmentDAO = new EnrollmentDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("user");
        if (user == null) {
            response.sendRedirect("login");
            return;
        }
        SupportTicketServlet.moveFlash(session, request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(session, request, FLASH_ERROR, "errorMessage");

        if ("STUDENT".equals(user.getRole())) {
            Integer allocationId = AdminSupport.parseInt(request.getParameter("allocationId"));
            request.setAttribute("enrollments", enrollmentDAO.getEnrollmentsByStudent(user.getUserId()));
            request.setAttribute("selectedAllocationId", allocationId);
            request.setAttribute("announcements", announcementDAO.getForStudent(user.getUserId(), allocationId));
        } else {
            request.setAttribute("announcements", announcementDAO.getGeneral(user.getRole()));
            if ("ADMIN".equals(user.getRole())) {
                Integer editId = AdminSupport.parseInt(request.getParameter("edit"));
                if (editId != null) {
                    Announcement editing = announcementDAO.getById(editId);
                    if (editing != null && editing.isGeneral())
                        request.setAttribute("editing", editing);
                    else
                        request.setAttribute("errorMessage", "Announcement not found.");
                }
            }
        }
        request.getRequestDispatcher("announcements.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User admin = AdminSupport.requireAdmin(request, response);
        if (admin == null)
            return;
        String action = request.getParameter("action");

        Announcement target = null;
        if ("update".equals(action) || "delete".equals(action)) {
            Integer id = AdminSupport.parseInt(request.getParameter("announcementId"));
            target = id == null ? null : announcementDAO.getById(id);
            if (target == null || !target.isGeneral()) {
                AdminSupport.flash(request, FLASH_ERROR, "Announcement not found.");
                response.sendRedirect("announcements");
                return;
            }
        }

        if ("delete".equals(action)) {
            if (announcementDAO.delete(target.getAnnouncementId()))
                AdminSupport.flash(request, FLASH_SUCCESS, "Announcement \"" + target.getTitle() + "\" deleted.");
            else
                AdminSupport.flash(request, FLASH_ERROR, "The announcement could not be deleted. Please try again.");
            response.sendRedirect("announcements");
            return;
        }
        if (!"create".equals(action) && !"update".equals(action)) {
            AdminSupport.flash(request, FLASH_ERROR, "Unknown action.");
            response.sendRedirect("announcements");
            return;
        }

        String title = CourseAnnouncementServlet.clean(request.getParameter("title"));
        String content = AnnouncementDAO.cleanContent(request.getParameter("content"));
        String audience = request.getParameter("audience");
        String error = AnnouncementDAO.validate(title, content);
        if (error == null && (audience == null || !AnnouncementDAO.AUDIENCES.contains(audience))) // Set.of(...).contains(null) throws
            error = "Please choose who should see the announcement.";
        if (error != null) {
            request.setAttribute("errorMessage", error);
            request.setAttribute("draftTitle", title);
            request.setAttribute("draftContent", content);
            request.setAttribute("draftAudience", audience);
            if (target != null)
                request.setAttribute("editing", target);
            request.setAttribute("announcements", announcementDAO.getGeneral(admin.getRole()));
            request.getRequestDispatcher("announcements.jsp").forward(request, response);
            return;
        }

        boolean ok;
        if (target != null) {
            ok = announcementDAO.update(target.getAnnouncementId(), title, content, audience);
        } else {
            Announcement ann = new Announcement();
            ann.setTitle(title);
            ann.setContent(content);
            ann.setAudience(audience);
            ann.setCreatedBy(admin.getUserId());
            ok = announcementDAO.create(ann);
        }
        Announcement shown = new Announcement();
        shown.setAudience(audience);
        if (ok)
            AdminSupport.flash(request, FLASH_SUCCESS, (target != null ? "Announcement updated" : "Announcement posted")
                    + " (visible to: " + shown.getAudienceLabel().toLowerCase() + ").");
        else
            AdminSupport.flash(request, FLASH_ERROR, "The announcement could not be saved. Please try again.");
        response.sendRedirect("announcements");
    }
}
