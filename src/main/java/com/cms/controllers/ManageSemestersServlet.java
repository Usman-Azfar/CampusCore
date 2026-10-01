package com.cms.controllers;

import com.cms.dao.SemesterDAO;
import com.cms.models.Semester;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Admin: add, edit, activate/deactivate and remove semesters. */
public class ManageSemestersServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = "semesters.flash.success";
    private static final String FLASH_ERROR = "semesters.flash.error";

    private SemesterDAO semesterDAO;

    public void init() {
        semesterDAO = new SemesterDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AdminSupport.requireAdmin(request, response) == null)
            return;
        SupportTicketServlet.moveFlash(request.getSession(), request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(request.getSession(), request, FLASH_ERROR, "errorMessage");

        // Active semester first, then newest
        List<Semester> semesters = new ArrayList<>(semesterDAO.getAllSemesters());
        semesters.sort(Comparator.comparing((Semester s) -> !s.isActive())
                .thenComparing(Semester::getStartDate, Comparator.nullsLast(Comparator.reverseOrder())));
        request.setAttribute("semesters", semesters);

        Semester edit = find(AdminSupport.parseInt(request.getParameter("editId")));
        request.setAttribute("semesterToEdit", edit);
        request.getRequestDispatcher("manage_semesters.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AdminSupport.requireAdmin(request, response) == null)
            return;
        String action = request.getParameter("action");
        Semester target = find(AdminSupport.parseInt(request.getParameter("semesterId")));
        String redirect = "manageSemesters";

        switch (action == null ? "" : action) {
            case "add":
            case "update": {
                boolean isUpdate = "update".equals(action);
                if (isUpdate && target == null) {
                    fail(request, "Semester not found.");
                    break;
                }
                String name = request.getParameter("name") == null ? "" : request.getParameter("name").trim().replaceAll("\\s+", " ");
                Date start = parseDate(request.getParameter("startDate"));
                Date end = parseDate(request.getParameter("endDate"));
                String error = validate(name, start, end, isUpdate ? target.getSemesterId() : null);
                if (error != null) {
                    fail(request, error);
                    if (isUpdate)
                        redirect = "manageSemesters?editId=" + target.getSemesterId();
                    break;
                }
                Semester s = new Semester();
                s.setName(name);
                s.setStartDate(start);
                s.setEndDate(end);
                if (isUpdate) {
                    s.setSemesterId(target.getSemesterId());
                    if (semesterDAO.updateSemester(s))
                        ok(request, "Semester " + name + " updated.");
                    else
                        fail(request, "Could not update the semester.");
                } else {
                    s.setActive(false); // new semesters start inactive
                    if (semesterDAO.addSemester(s))
                        ok(request, "Semester " + name + " added (inactive). Activate it when it starts.");
                    else
                        fail(request, "Could not add the semester.");
                }
                break;
            }
            case "activate":
                // Checked first: activating an unknown ID would otherwise deactivate everything
                if (target == null) {
                    fail(request, "Semester not found.");
                } else if (target.isActive()) {
                    fail(request, target.getName() + " is already the active semester.");
                } else if (semesterDAO.activateSemester(target.getSemesterId())) {
                    ok(request, target.getName() + " is now the active semester. Add/drop requests, teacher course lists and new enrollments use it.");
                } else {
                    fail(request, "Could not activate that semester.");
                }
                break;
            case "deactivate":
                if (target == null) {
                    fail(request, "Semester not found.");
                } else if (semesterDAO.deactivateSemester(target.getSemesterId())) {
                    ok(request, target.getName() + " deactivated. No semester is active now: students cannot submit add/drop requests until you activate one.");
                } else {
                    fail(request, target.getName() + " is not the active semester.");
                }
                break;
            case "delete": {
                String error = target == null ? "Semester not found." : semesterDAO.deleteSemester(target.getSemesterId());
                if (error == null)
                    ok(request, "Semester " + target.getName() + " removed.");
                else
                    fail(request, "Semester not removed: " + error);
                break;
            }
            default:
                fail(request, "Unknown action.");
        }
        response.sendRedirect(redirect);
    }

    private String validate(String name, Date start, Date end, Integer exceptId) {
        if (name.isEmpty() || name.length() > 50)
            return "Semester name is required (up to 50 characters), e.g. Spring 2025.";
        if (start == null || end == null)
            return "Please enter valid start and end dates.";
        if (!end.after(start))
            return "End date must be after the start date.";
        for (Semester s : semesterDAO.getAllSemesters()) {
            if ((exceptId == null || s.getSemesterId() != exceptId) && s.getName().equalsIgnoreCase(name))
                return "A semester named " + s.getName() + " already exists.";
        }
        return null;
    }

    private Semester find(Integer id) {
        if (id == null)
            return null;
        for (Semester s : semesterDAO.getAllSemesters())
            if (s.getSemesterId() == id)
                return s;
        return null;
    }

    private static Date parseDate(String s) {
        try {
            return s == null ? null : Date.valueOf(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void ok(HttpServletRequest request, String msg) {
        AdminSupport.flash(request, FLASH_SUCCESS, msg);
    }

    private static void fail(HttpServletRequest request, String msg) {
        AdminSupport.flash(request, FLASH_ERROR, msg);
    }
}
