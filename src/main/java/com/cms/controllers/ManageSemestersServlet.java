package com.cms.controllers;

import com.cms.dao.AcademicDAO;
import com.cms.dao.ClassSemesterDAO;
import com.cms.dao.SemesterDAO;
import com.cms.models.ClassSemester;
import com.cms.models.Semester;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin: terms (e.g. Fall 2024) and class semesters, i.e. which semester number each class is
 * in during a term (e.g. BS Computer Science-2022 in its 5th semester in Fall 2024). A term is
 * active while any class is currently in it, so several terms can be active at once.
 */
public class ManageSemestersServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = "semesters.flash.success";
    private static final String FLASH_ERROR = "semesters.flash.error";
    private static final String CLASS_SECTION = "manageSemesters#classSemesters";

    private SemesterDAO semesterDAO;
    private ClassSemesterDAO classSemesterDAO;
    private AcademicDAO academicDAO;

    public void init() {
        semesterDAO = new SemesterDAO();
        classSemesterDAO = new ClassSemesterDAO();
        academicDAO = new AcademicDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AdminSupport.requireAdmin(request, response) == null)
            return;
        SupportTicketServlet.moveFlash(request.getSession(), request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(request.getSession(), request, FLASH_ERROR, "errorMessage");

        // Active terms first, then newest
        List<Semester> semesters = new ArrayList<>(semesterDAO.getAllSemesters());
        semesters.sort(Comparator.comparing((Semester s) -> !s.isActive())
                .thenComparing(Semester::getStartDate, Comparator.nullsLast(Comparator.reverseOrder())));
        request.setAttribute("semesters", semesters);

        List<ClassSemester> classSemesters = classSemesterDAO.getAll();
        request.setAttribute("classSemesters", classSemesters);
        // Each class's current semester, shown next to the class in the "place classes" form
        Map<Integer, ClassSemester> currentByClass = new HashMap<>();
        for (ClassSemester cs : classSemesters)
            if (cs.isCurrent())
                currentByClass.put(cs.getClassId(), cs);
        request.setAttribute("currentByClass", currentByClass);
        request.setAttribute("classes", academicDAO.getAllClasses());
        request.setAttribute("departments", academicDAO.getAllDepartments());

        Semester edit = find(AdminSupport.parseInt(request.getParameter("editId")));
        request.setAttribute("semesterToEdit", edit);
        request.getRequestDispatcher("manage_semesters.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AdminSupport.requireAdmin(request, response) == null)
            return;
        String action = request.getParameter("action");
        String redirect = "manageSemesters";

        switch (action == null ? "" : action) {
            case "add":
            case "update": {
                Semester target = find(AdminSupport.parseInt(request.getParameter("semesterId")));
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
                } else if (semesterDAO.addSemester(s)) {
                    ok(request, "Semester " + name + " added. Place classes in it under Class Semesters to make it active for them.");
                } else {
                    fail(request, "Could not add the semester.");
                }
                break;
            }
            case "delete": {
                Semester target = find(AdminSupport.parseInt(request.getParameter("semesterId")));
                String error = target == null ? "Semester not found." : semesterDAO.deleteSemester(target.getSemesterId());
                if (error == null)
                    ok(request, "Semester " + target.getName() + " removed.");
                else
                    fail(request, "Semester not removed: " + error);
                break;
            }
            case "assignClasses":
                assignClasses(request);
                redirect = CLASS_SECTION;
                break;
            case "makeCurrent":
            case "endCurrent":
            case "changeNumber":
            case "removeClassSemester":
                changeClassSemester(request, action);
                redirect = CLASS_SECTION;
                break;
            default:
                fail(request, "Unknown action.");
        }
        response.sendRedirect(redirect);
    }

    // Places one or more classes in a term, with a chosen or automatic ("next") semester number
    private void assignClasses(HttpServletRequest request) {
        Semester term = find(AdminSupport.parseInt(request.getParameter("semesterId")));
        if (term == null) {
            fail(request, "Please choose a semester (term).");
            return;
        }
        Set<Integer> classIds = new LinkedHashSet<>();
        String[] raw = request.getParameterValues("classIds");
        if (raw != null)
            for (String v : raw) {
                Integer id = AdminSupport.parseInt(v);
                if (id != null)
                    classIds.add(id);
            }
        if (classIds.isEmpty()) {
            fail(request, "Please select at least one class.");
            return;
        }
        String numberParam = request.getParameter("semesterNumber");
        Integer number = "auto".equals(numberParam) ? null : AdminSupport.parseInt(numberParam);
        if (!"auto".equals(numberParam) && (number == null || number < 1 || number > ClassSemesterDAO.MAX_SEMESTER)) {
            fail(request, "Please choose a semester number from 1 to " + ClassSemesterDAO.MAX_SEMESTER + ", or Next semester.");
            return;
        }
        boolean makeCurrent = request.getParameter("makeCurrent") != null;

        ClassSemesterDAO.AssignResult r = classSemesterDAO.assign(new ArrayList<>(classIds), term.getSemesterId(), number, makeCurrent);
        if (!r.saved.isEmpty())
            ok(request, term.getName() + ": saved " + r.saved.size() + " class(es): " + String.join(", ", r.saved) + ".");
        if (!r.skipped.isEmpty())
            fail(request, "Not saved: " + String.join(" ", r.skipped));
    }

    private void changeClassSemester(HttpServletRequest request, String action) {
        Integer id = AdminSupport.parseInt(request.getParameter("classSemesterId"));
        ClassSemester row = id == null ? null : classSemesterDAO.getById(id);
        if (row == null) {
            fail(request, "Class semester not found.");
            return;
        }
        String label = row.getClassName() + " in " + row.getTermName();
        String error;
        String success;
        switch (action) {
            case "makeCurrent":
                error = classSemesterDAO.makeCurrent(id);
                success = label + " (" + row.getNumberLabel() + ") is now the class's current semester.";
                break;
            case "endCurrent":
                error = classSemesterDAO.endCurrent(id);
                success = label + " ended. The class has no current semester, so its students cannot submit add/drop requests until you set one.";
                break;
            case "changeNumber": {
                Integer n = AdminSupport.parseInt(request.getParameter("semesterNumber"));
                error = n == null ? "Please choose a semester number." : classSemesterDAO.changeNumber(id, n);
                success = n == null ? null : label + " is now the " + com.cms.models.ClassSemester.ordinal(n) + " semester.";
                break;
            }
            default: // removeClassSemester
                error = classSemesterDAO.delete(id);
                success = label + " removed.";
        }
        if (error == null)
            ok(request, success);
        else
            fail(request, error);
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
