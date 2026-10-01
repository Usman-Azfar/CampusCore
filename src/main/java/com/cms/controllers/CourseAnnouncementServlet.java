package com.cms.controllers;

import com.cms.dao.AnnouncementDAO;
import com.cms.dao.CourseAllocationDAO;
import com.cms.models.Announcement;
import com.cms.models.CourseAllocation;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Teacher: post, edit and delete announcements for one of their course offerings. Students
 * currently enrolled in that offering see them on their Announcements page.
 */
public class CourseAnnouncementServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    static final String FLASH_SUCCESS = "announcements.flash.success";
    static final String FLASH_ERROR = "announcements.flash.error";

    private AnnouncementDAO announcementDAO;
    private CourseAllocationDAO allocationDAO;

    public void init() {
        announcementDAO = new AnnouncementDAO();
        allocationDAO = new CourseAllocationDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User teacher = requireTeacher(request, response);
        if (teacher == null)
            return;
        HttpSession session = request.getSession();
        SupportTicketServlet.moveFlash(session, request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(session, request, FLASH_ERROR, "errorMessage");

        Integer allocationId = AdminSupport.parseInt(request.getParameter("allocationId"));
        if (allocationId == null) {
            // Courses are chosen on the Course List
            response.sendRedirect("myCourses");
            return;
        }
        CourseAllocation offering = ownOffering(allocationId, teacher);
        if (offering == null) {
            AdminSupport.flash(request, FLASH_ERROR, "That course is not assigned to you.");
            response.sendRedirect("myCourses");
            return;
        }

        Integer editId = AdminSupport.parseInt(request.getParameter("edit"));
        if (editId != null) {
            Announcement editing = announcementDAO.getById(editId);
            if (editing != null && allocationId.equals(editing.getAllocationId()))
                request.setAttribute("editing", editing);
            else
                request.setAttribute("errorMessage", "Announcement not found.");
        }
        show(request, response, offering);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User teacher = requireTeacher(request, response);
        if (teacher == null)
            return;
        Integer allocationId = AdminSupport.parseInt(request.getParameter("allocationId"));
        CourseAllocation offering = allocationId == null ? null : ownOffering(allocationId, teacher);
        if (offering == null) {
            AdminSupport.flash(request, FLASH_ERROR, "That course is not assigned to you.");
            response.sendRedirect("myCourses");
            return;
        }
        String base = "manageAnnouncements?allocationId=" + allocationId;
        String action = request.getParameter("action");

        // Editing and deleting: only announcements of this offering
        Announcement target = null;
        if ("update".equals(action) || "delete".equals(action)) {
            Integer id = AdminSupport.parseInt(request.getParameter("announcementId"));
            target = id == null ? null : announcementDAO.getById(id);
            if (target == null || !allocationId.equals(target.getAllocationId())) {
                AdminSupport.flash(request, FLASH_ERROR, "Announcement not found.");
                response.sendRedirect(base);
                return;
            }
        }

        if ("delete".equals(action)) {
            if (announcementDAO.delete(target.getAnnouncementId()))
                AdminSupport.flash(request, FLASH_SUCCESS, "Announcement \"" + target.getTitle() + "\" deleted.");
            else
                AdminSupport.flash(request, FLASH_ERROR, "The announcement could not be deleted. Please try again.");
            response.sendRedirect(base);
            return;
        }
        if (!"create".equals(action) && !"update".equals(action)) {
            AdminSupport.flash(request, FLASH_ERROR, "Unknown action.");
            response.sendRedirect(base);
            return;
        }

        String title = clean(request.getParameter("title"));
        String content = AnnouncementDAO.cleanContent(request.getParameter("content"));
        String error = AnnouncementDAO.validate(title, content);
        if (error != null) {
            // Keep what was typed
            request.setAttribute("errorMessage", error);
            request.setAttribute("draftTitle", title);
            request.setAttribute("draftContent", content);
            if (target != null)
                request.setAttribute("editing", target);
            show(request, response, offering);
            return;
        }

        boolean ok;
        if (target != null) {
            ok = announcementDAO.update(target.getAnnouncementId(), title, content, null);
        } else {
            Announcement ann = new Announcement();
            ann.setTitle(title);
            ann.setContent(content);
            ann.setAllocationId(allocationId);
            ann.setCreatedBy(teacher.getUserId());
            ok = announcementDAO.create(ann);
        }
        if (ok)
            AdminSupport.flash(request, FLASH_SUCCESS, target != null ? "Announcement updated."
                    : "Announcement posted to the " + offering.getEnrolledCount() + " student"
                            + (offering.getEnrolledCount() == 1 ? "" : "s") + " of " + offering.getCourse().getCourseCode() + ".");
        else
            AdminSupport.flash(request, FLASH_ERROR, "The announcement could not be saved. Please try again.");
        response.sendRedirect(base);
    }

    private void show(HttpServletRequest request, HttpServletResponse response, CourseAllocation offering)
            throws ServletException, IOException {
        request.setAttribute("offering", offering);
        request.setAttribute("announcements", announcementDAO.getByAllocation(offering.getAllocationId()));
        request.getRequestDispatcher("course_announcements.jsp").forward(request, response);
    }

    private CourseAllocation ownOffering(int allocationId, User teacher) {
        CourseAllocation offering = allocationDAO.getOverview(allocationId);
        return offering != null && offering.getTeacherId() == teacher.getUserId() ? offering : null;
    }

    /** Title as typed, trimmed, with runs of whitespace (incl. newlines) turned into one space. */
    static String clean(String title) {
        return title == null ? "" : title.trim().replaceAll("\\s+", " ");
    }

    private static User requireTeacher(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("user");
        if (user == null) {
            response.sendRedirect("login");
            return null;
        }
        if (!"TEACHER".equals(user.getRole())) {
            response.sendRedirect("dashboard");
            return null;
        }
        return user;
    }
}
