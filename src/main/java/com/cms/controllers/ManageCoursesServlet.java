package com.cms.controllers;

import com.cms.dao.AcademicDAO;
import com.cms.dao.CourseDAO;
import com.cms.models.AcademicClass;
import com.cms.models.Course;
import com.cms.models.Department;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Admin: add, edit and delete courses, with their department and classes. */
public class ManageCoursesServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = "courses.flash.success";
    private static final String FLASH_ERROR = "courses.flash.error";
    private static final Pattern CODE = Pattern.compile("[A-Za-z]{2,10}-?[0-9]{2,4}[A-Za-z]?");

    private CourseDAO courseDAO;
    private AcademicDAO academicDAO;

    public void init() {
        courseDAO = new CourseDAO();
        academicDAO = new AcademicDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AdminSupport.requireAdmin(request, response) == null)
            return;
        SupportTicketServlet.moveFlash(request.getSession(), request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(request.getSession(), request, FLASH_ERROR, "errorMessage");

        request.setAttribute("courses", courseDAO.getAllCourses());
        request.setAttribute("departments", academicDAO.getAllDepartments());
        request.setAttribute("classes", academicDAO.getAllClasses());

        Integer editId = AdminSupport.parseInt(request.getParameter("editId"));
        if (editId != null) {
            request.setAttribute("courseToEdit", courseDAO.getCourseById(editId));
        }
        request.getRequestDispatcher("manage_courses.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AdminSupport.requireAdmin(request, response) == null)
            return;

        String action = request.getParameter("action");
        if ("delete".equals(action)) {
            Integer id = AdminSupport.parseInt(request.getParameter("courseId"));
            String error = (id == null) ? "Invalid course." : courseDAO.deleteCourse(id);
            AdminSupport.flash(request, error == null ? FLASH_SUCCESS : FLASH_ERROR,
                    error == null ? "Course deleted." : "Course not deleted: " + error);
            response.sendRedirect("manageCourses");
            return;
        }

        Integer courseId = AdminSupport.parseInt(request.getParameter("courseId")); // set when editing
        String code = clean(request.getParameter("courseCode")).toUpperCase();
        String name = clean(request.getParameter("courseName"));
        String description = clean(request.getParameter("description"));
        Integer credits = AdminSupport.parseInt(request.getParameter("creditHours"));
        Integer deptId = AdminSupport.parseInt(request.getParameter("departmentId"));
        Set<Integer> classIds = AdminSupport.parseIds(request.getParameterValues("classIds"));

        String error = null;
        if (!CODE.matcher(code).matches()) {
            error = "Course code must look like CS-101 or MATH101 (letters, optional dash, digits).";
        } else if (name.isEmpty() || name.length() > 100) {
            error = "Course name is required (up to 100 characters).";
        } else if (credits == null || credits < 1 || credits > 6) {
            error = "Credit hours must be a whole number from 1 to 6.";
        } else if (courseDAO.codeExists(code, courseId)) {
            error = "Course code " + code + " is already used by another course.";
        } else if (deptId != null && !isDepartment(deptId)) {
            error = "Selected department does not exist.";
        } else if (!classesExist(classIds)) {
            error = "One or more selected classes do not exist.";
        }

        if (error == null) {
            Course c = new Course();
            c.setCourseCode(code);
            c.setCourseName(name);
            c.setCreditHours(credits);
            c.setDepartmentId(deptId);
            c.setDescription(description.isEmpty() ? null : description);
            boolean ok;
            if (courseId == null) {
                ok = courseDAO.addCourse(c, classIds);
            } else {
                c.setCourseId(courseId);
                ok = courseDAO.updateCourse(c, classIds);
            }
            if (ok) {
                AdminSupport.flash(request, FLASH_SUCCESS, (courseId == null ? "Course added: " : "Course updated: ") + code + " - " + name);
                response.sendRedirect("manageCourses");
                return;
            }
            error = "Could not save the course. Please try again.";
        }

        AdminSupport.flash(request, FLASH_ERROR, error);
        response.sendRedirect(courseId == null ? "manageCourses" : "manageCourses?editId=" + courseId);
    }

    private boolean isDepartment(int id) {
        for (Department d : academicDAO.getAllDepartments())
            if (d.getDepartmentId() == id)
                return true;
        return false;
    }

    private boolean classesExist(Set<Integer> ids) {
        if (ids.isEmpty())
            return true;
        Set<Integer> known = new HashSet<>();
        List<AcademicClass> all = academicDAO.getAllClasses();
        for (AcademicClass c : all)
            known.add(c.getClassId());
        return known.containsAll(ids);
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }
}
