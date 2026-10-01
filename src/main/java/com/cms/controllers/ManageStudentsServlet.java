package com.cms.controllers;

import com.cms.dao.AcademicDAO;
import com.cms.dao.EnrollmentDAO;
import com.cms.dao.UserDAO;
import com.cms.models.Profile;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Admin: add, edit, activate/deactivate and delete student accounts. */
public class ManageStudentsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = "students.flash.success";
    private static final String FLASH_ERROR = "students.flash.error";
    private static final String DRAFT = "students.flash.draft";

    private UserDAO userDAO;
    private AcademicDAO academicDAO;
    private EnrollmentDAO enrollmentDAO;

    public void init() {
        userDAO = new UserDAO();
        academicDAO = new AcademicDAO();
        enrollmentDAO = new EnrollmentDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AdminSupport.requireAdmin(request, response) == null)
            return;
        SupportTicketServlet.moveFlash(request.getSession(), request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(request.getSession(), request, FLASH_ERROR, "errorMessage");
        SupportTicketServlet.moveFlash(request.getSession(), request, DRAFT, "draft");

        // Only student accounts can be edited here
        Integer editId = AdminSupport.parseInt(request.getParameter("editId"));
        if (editId != null) {
            User s = student(editId);
            if (s != null)
                request.setAttribute("studentToEdit", s);
            else
                request.setAttribute("errorMessage", "That account is not a student.");
        }

        request.setAttribute("students", userDAO.getUsersByRole("STUDENT"));
        request.setAttribute("classes", academicDAO.getAllClasses());
        request.setAttribute("summaries", enrollmentDAO.getStudentSummaries());
        request.getRequestDispatcher("manage_students.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AdminSupport.requireAdmin(request, response) == null)
            return;

        String action = request.getParameter("action");
        Integer userId = AdminSupport.parseInt(request.getParameter("userId"));
        String redirect = "manageStudents";

        if ("delete".equals(action) || "activate".equals(action) || "deactivate".equals(action)) {
            User s = userId != null ? student(userId) : null;
            if (s == null) {
                fail(request, "Student not found.");
            } else if ("delete".equals(action)) {
                EnrollmentDAO.StudentSummary sum = enrollmentDAO.getStudentSummaries().get(s.getUserId());
                if (sum != null && sum.hasAcademicRecords()) {
                    fail(request, s.getDisplayName() + " cannot be deleted: they have " + sum.enrollmentRecords
                            + " enrollment record(s)" + (sum.challans > 0 ? " and " + sum.challans + " fee challan(s)" : "")
                            + ". Deleting would erase their grades, attendance and transcript. Deactivate the account instead.");
                } else if (userDAO.deleteUser(s.getUserId())) {
                    ok(request, "Student " + s.getDisplayName() + " deleted.");
                } else {
                    fail(request, "Could not delete the student.");
                }
            } else {
                boolean activate = "activate".equals(action);
                if (userDAO.setActive(s.getUserId(), "STUDENT", activate)) {
                    String msg = s.getDisplayName() + (activate ? " activated. They can log in again." : " deactivated. They can no longer log in.");
                    EnrollmentDAO.StudentSummary sum = enrollmentDAO.getStudentSummaries().get(s.getUserId());
                    if (!activate && sum != null && !sum.currentCourses.isEmpty())
                        msg += " They remain enrolled in " + String.join(", ", sum.currentCourses)
                                + " this semester; drop them from those courses in Manage Courses if they have left.";
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
            user.setRole("STUDENT");
            user.setActive(true);
            profile = new Profile();
        } else {
            user = student(userId);
            if (user == null) {
                fail(request, "Student not found.");
                response.sendRedirect(redirect);
                return;
            }
            profile = user.getProfile();
            redirect = "manageStudents?editId=" + userId;
        }

        String error = UserFormSupport.read(request, user, profile, isAdd, userDAO);
        user.setProfile(profile); // so getDisplayName() shows the name in messages
        if (error == null) {
            user.setUsername(user.getUsername().toUpperCase()); // roll numbers are stored in capitals, e.g. BCSF22M512
            user.setClassId(selectedClassId(request));
            if (!isAdd && request.getParameter("isActive") != null)
                user.setActive("true".equals(request.getParameter("isActive")));

            boolean saved = isAdd ? userDAO.addUserWithProfile(user, profile) : userDAO.updateUserWithProfile(user, profile);
            if (saved) {
                ok(request, (isAdd ? "Student added: " : "Student updated: ") + user.getDisplayName()
                        + (isAdd ? ". They can now log in with roll number " + user.getUsername() + "." : "."));
                response.sendRedirect("manageStudents");
                return;
            }
            error = "Could not save the student. Please try again.";
        }
        fail(request, error);
        // Keep what was typed (never the password) so the admin only fixes the problem
        request.getSession().setAttribute(DRAFT, UserFormSupport.draft(request));
        response.sendRedirect(redirect + (redirect.contains("?") ? "&" : "?") + "draft=1#studentForm");
    }

    // The account, only if it is a student
    private User student(int id) {
        User u = userDAO.getUserById(id);
        return (u != null && "STUDENT".equals(u.getRole())) ? u : null;
    }

    // The chosen classId from the form, or null when none/invalid
    private Integer selectedClassId(HttpServletRequest request) {
        Integer id = AdminSupport.parseInt(request.getParameter("classId"));
        return (id != null && academicDAO.classExists(id)) ? id : null;
    }

    private static void ok(HttpServletRequest request, String msg) {
        AdminSupport.flash(request, FLASH_SUCCESS, msg);
    }

    private static void fail(HttpServletRequest request, String msg) {
        AdminSupport.flash(request, FLASH_ERROR, msg);
    }
}
