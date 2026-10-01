package com.cms.controllers;

import com.cms.dao.AcademicDAO;
import com.cms.dao.CourseAllocationDAO;
import com.cms.dao.UserDAO;
import com.cms.models.CourseAllocation;
import com.cms.models.Profile;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Admin: add, edit, activate/deactivate and delete teacher accounts. */
public class ManageTeachersServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = "teachers.flash.success";
    private static final String FLASH_ERROR = "teachers.flash.error";
    private static final String DRAFT = "teachers.flash.draft";

    private UserDAO userDAO;
    private AcademicDAO academicDAO;
    private CourseAllocationDAO allocationDAO;

    public void init() {
        userDAO = new UserDAO();
        academicDAO = new AcademicDAO();
        allocationDAO = new CourseAllocationDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AdminSupport.requireAdmin(request, response) == null)
            return;
        SupportTicketServlet.moveFlash(request.getSession(), request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(request.getSession(), request, FLASH_ERROR, "errorMessage");
        SupportTicketServlet.moveFlash(request.getSession(), request, DRAFT, "draft");

        Integer editId = AdminSupport.parseInt(request.getParameter("editId"));
        if (editId != null) {
            User t = teacher(editId);
            if (t != null)
                request.setAttribute("teacherToEdit", t);
            else
                request.setAttribute("errorMessage", "That account is not a teacher.");
        }

        request.setAttribute("teachers", userDAO.getUsersByRole("TEACHER"));
        request.setAttribute("departments", academicDAO.getAllDepartments());
        request.setAttribute("teaching", teachingByTeacher());
        request.getRequestDispatcher("manage_teachers.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AdminSupport.requireAdmin(request, response) == null)
            return;

        String action = request.getParameter("action");
        Integer userId = AdminSupport.parseInt(request.getParameter("userId"));
        String redirect = "manageTeachers";

        if ("delete".equals(action) || "activate".equals(action) || "deactivate".equals(action)) {
            User t = userId != null ? teacher(userId) : null;
            if (t == null) {
                fail(request, "Teacher not found.");
            } else if ("delete".equals(action)) {
                List<CourseAllocation> offerings = teachingByTeacher().getOrDefault(t.getUserId(), new ArrayList<>());
                if (!offerings.isEmpty()) {
                    fail(request, t.getDisplayName() + " cannot be deleted: they teach " + offerings.size()
                            + " course offering(s) (" + describe(offerings, false) + "). Deleting would also erase those students'"
                            + " enrollments, grades and attendance. Reassign the courses in Faculty Assignments, or deactivate the account instead.");
                } else if (userDAO.deleteUser(t.getUserId())) {
                    ok(request, "Teacher " + t.getDisplayName() + " deleted.");
                } else {
                    fail(request, "Could not delete the teacher.");
                }
            } else {
                boolean activate = "activate".equals(action);
                if (userDAO.setActive(t.getUserId(), "TEACHER", activate)) {
                    String msg = t.getDisplayName() + (activate ? " activated. They can log in again." : " deactivated. They can no longer log in.");
                    if (!activate) {
                        List<CourseAllocation> current = activeOfferings(t.getUserId());
                        if (!current.isEmpty())
                            msg += " Note: they are still assigned to " + describe(current, true)
                                    + ". Reassign these in Faculty Assignments so students have an active teacher.";
                    }
                    ok(request, msg);
                } else {
                    fail(request, "Could not change the account status.");
                }
            }
            response.sendRedirect(redirect);
            return;
        }

        // Add or update
        boolean isAdd = userId == null;
        User user;
        Profile profile;
        if (isAdd) {
            user = new User();
            user.setRole("TEACHER");
            user.setActive(true);
            profile = new Profile();
        } else {
            user = teacher(userId);
            if (user == null) {
                fail(request, "Teacher not found.");
                response.sendRedirect(redirect);
                return;
            }
            profile = user.getProfile();
            redirect = "manageTeachers?editId=" + userId;
        }

        String error = UserFormSupport.read(request, user, profile, isAdd, userDAO);
        user.setProfile(profile); // so getDisplayName() shows the name in messages
        if (error == null) {
            user.setDepartmentId(selectedDepartmentId(request));
            if (!isAdd && request.getParameter("isActive") != null)
                user.setActive("true".equals(request.getParameter("isActive")));

            boolean saved = isAdd ? userDAO.addUserWithProfile(user, profile) : userDAO.updateUserWithProfile(user, profile);
            if (saved) {
                ok(request, (isAdd ? "Teacher added: " : "Teacher updated: ") + user.getDisplayName()
                        + (isAdd ? ". They can now log in with username " + user.getUsername() + "." : "."));
                response.sendRedirect("manageTeachers");
                return;
            }
            error = "Could not save the teacher. Please try again.";
        }
        fail(request, error);
        // Keep what was typed (never the password) so the admin only fixes the problem
        request.getSession().setAttribute(DRAFT, UserFormSupport.draft(request));
        response.sendRedirect(redirect + (redirect.contains("?") ? "&" : "?") + "draft=1#teacherForm");
    }

    // The account, only if it is a teacher
    private User teacher(int id) {
        User u = userDAO.getUserById(id);
        return (u != null && "TEACHER".equals(u.getRole())) ? u : null;
    }

    // All offerings (any semester) per teacher
    private Map<Integer, List<CourseAllocation>> teachingByTeacher() {
        Map<Integer, List<CourseAllocation>> map = new HashMap<>();
        for (CourseAllocation a : allocationDAO.getAllocationsDetailed(null))
            map.computeIfAbsent(a.getTeacherId(), k -> new ArrayList<>()).add(a);
        return map;
    }

    private List<CourseAllocation> activeOfferings(int teacherId) {
        List<CourseAllocation> list = new ArrayList<>();
        for (CourseAllocation a : teachingByTeacher().getOrDefault(teacherId, new ArrayList<>()))
            if (a.getSemester().isActive())
                list.add(a);
        return list;
    }

    private static String describe(List<CourseAllocation> offerings, boolean codesOnly) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < offerings.size() && i < 5; i++) {
            if (i > 0)
                sb.append(", ");
            CourseAllocation a = offerings.get(i);
            sb.append(a.getCourse().getCourseCode());
            if (!codesOnly)
                sb.append(" ").append(a.getSemester().getName());
        }
        if (offerings.size() > 5)
            sb.append(" and ").append(offerings.size() - 5).append(" more");
        if (codesOnly && !offerings.isEmpty())
            sb.append(" in ").append(offerings.get(0).getSemester().getName());
        return sb.toString();
    }

    // The chosen departmentId from the form, or null when none/invalid
    private Integer selectedDepartmentId(HttpServletRequest request) {
        Integer id = AdminSupport.parseInt(request.getParameter("departmentId"));
        return (id != null && academicDAO.departmentExists(id)) ? id : null;
    }

    private static void ok(HttpServletRequest request, String msg) {
        AdminSupport.flash(request, FLASH_SUCCESS, msg);
    }

    private static void fail(HttpServletRequest request, String msg) {
        AdminSupport.flash(request, FLASH_ERROR, msg);
    }
}
