package com.cms.controllers;

import com.cms.dao.AcademicDAO;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Admin page for Classes (e.g. "BS Computer Science-2026") and Departments
 * (e.g. "Computer Science").
 */
public class ManageClassesServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = "classes.flash.success";
    private static final String FLASH_ERROR = "classes.flash.error";

    // Degree like BS, MS, BBA, M.Phil, Ph.D; names allow letters, digits, spaces and & . , ( ) -
    private static final Pattern DEGREE = Pattern.compile("[A-Za-z][A-Za-z. ]{0,19}");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 &.,()'-]{0,99}");

    private AcademicDAO academicDAO;

    public void init() {
        academicDAO = new AcademicDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!isAdmin(request, response))
            return;
        HttpSession session = request.getSession(false);
        SupportTicketServlet.moveFlash(session, request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(session, request, FLASH_ERROR, "errorMessage");

        request.setAttribute("classes", academicDAO.getAllClasses());
        request.setAttribute("departments", academicDAO.getAllDepartments());
        request.getRequestDispatcher("manage_classes.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!isAdmin(request, response))
            return;
        HttpSession session = request.getSession(false);
        String action = request.getParameter("action");
        String error;
        String success;

        switch (action == null ? "" : action) {
            case "saveClass": {
                String degree = clean(request.getParameter("degree"));
                String program = clean(request.getParameter("programName"));
                Integer year = parseInt(request.getParameter("batchYear"));
                Integer id = parseInt(request.getParameter("classId"));
                error = validateClass(degree, program, year);
                if (error == null) {
                    AcademicDAO.Result r = (id == null)
                            ? academicDAO.addClass(degree, program, year)
                            : academicDAO.updateClass(id, degree, program, year);
                    error = r.error;
                }
                success = (id == null ? "Class added: " : "Class updated: ") + degree + " " + program + "-" + year;
                break;
            }
            case "deleteClass": {
                Integer id = parseInt(request.getParameter("classId"));
                error = (id == null) ? "Invalid class." : academicDAO.deleteClass(id).error;
                success = "Class deleted.";
                break;
            }
            case "saveDepartment": {
                String name = clean(request.getParameter("name"));
                Integer id = parseInt(request.getParameter("departmentId"));
                error = (name.isEmpty() || !NAME.matcher(name).matches())
                        ? "Department name is required (letters, digits, spaces and & . , ( ) - only, up to 100 characters)."
                        : null;
                if (error == null) {
                    AcademicDAO.Result r = (id == null)
                            ? academicDAO.addDepartment(name)
                            : academicDAO.updateDepartment(id, name);
                    error = r.error;
                }
                success = (id == null ? "Department added: " : "Department updated: ") + name;
                break;
            }
            case "deleteDepartment": {
                Integer id = parseInt(request.getParameter("departmentId"));
                error = (id == null) ? "Invalid department." : academicDAO.deleteDepartment(id).error;
                success = "Department deleted.";
                break;
            }
            default:
                error = "Unknown action.";
                success = null;
        }

        session.setAttribute(error == null ? FLASH_SUCCESS : FLASH_ERROR, error == null ? success : error);
        response.sendRedirect("manageClasses");
    }

    private static String validateClass(String degree, String program, Integer year) {
        if (degree.isEmpty() || !DEGREE.matcher(degree).matches())
            return "Degree is required, e.g. BS, MS, BBA (letters and dots only, up to 20 characters).";
        if (program.isEmpty() || !NAME.matcher(program).matches())
            return "Program name is required, e.g. Computer Science (up to 100 characters).";
        if (year == null || year < 1950 || year > 2100)
            return "Year must be a 4-digit year between 1950 and 2100, e.g. 2026.";
        return null;
    }

    // Trim and collapse repeated spaces
    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static Integer parseInt(String s) {
        try {
            return (s == null || s.trim().isEmpty()) ? null : Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("user") : null;
        if (user == null) {
            response.sendRedirect("login");
            return false;
        }
        if (!"ADMIN".equals(user.getRole())) {
            response.sendRedirect("dashboard");
            return false;
        }
        return true;
    }
}
