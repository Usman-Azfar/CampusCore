package com.cms.controllers;

import com.cms.dao.AcademicDAO;
import com.cms.dao.CourseAllocationDAO;
import com.cms.dao.CourseDAO;
import com.cms.dao.EnrollmentDAO;
import com.cms.dao.UserDAO;
import com.cms.models.Course;
import com.cms.models.CourseAllocation;
import com.cms.models.Enrollment;
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
import java.util.Set;

/** Admin: enroll students (one, many or all matching) into a course offering. */
public class ManageEnrollmentServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = "enrollment.flash.success";
    private static final String FLASH_ERROR = "enrollment.flash.error";
    private static final int MAX_BULK = 5000;

    private EnrollmentDAO enrollmentDAO;
    private CourseAllocationDAO allocationDAO;
    private CourseDAO courseDAO;
    private UserDAO userDAO;
    private AcademicDAO academicDAO;

    public void init() {
        enrollmentDAO = new EnrollmentDAO();
        allocationDAO = new CourseAllocationDAO();
        courseDAO = new CourseDAO();
        userDAO = new UserDAO();
        academicDAO = new AcademicDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AdminSupport.requireAdmin(request, response) == null)
            return;

        Integer courseId = AdminSupport.parseInt(request.getParameter("courseId"));
        Course course = courseId != null ? courseDAO.getCourseById(courseId) : null;
        if (course == null) {
            response.sendRedirect("manageCourses");
            return;
        }
        SupportTicketServlet.moveFlash(request.getSession(), request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(request.getSession(), request, FLASH_ERROR, "errorMessage");

        // Offerings of this course (active semester first); default to the first
        List<CourseAllocation> allocations = allocationDAO.getAllocationsDetailed(courseId);
        Integer requested = AdminSupport.parseInt(request.getParameter("allocationId"));
        CourseAllocation selected = null;
        for (CourseAllocation a : allocations) {
            if (requested != null && a.getAllocationId() == requested)
                selected = a;
        }
        if (selected == null && !allocations.isEmpty())
            selected = allocations.get(0);

        // Enrollment records of the course, and who is currently ENROLLED (and where)
        List<Enrollment> records = enrollmentDAO.getCourseEnrollmentsAllStatuses(courseId);
        Map<Integer, String> enrolledIn = new HashMap<>();
        for (Enrollment e : records) {
            if ("ENROLLED".equals(e.getStatus()))
                enrolledIn.put(e.getStudentId(), e.getCourseAllocation().getSemester().getName());
        }

        List<User> students = new ArrayList<>();
        for (User s : userDAO.getUsersByRole("STUDENT"))
            if (s.isActive())
                students.add(s);

        request.setAttribute("course", course);
        request.setAttribute("allocations", allocations);
        request.setAttribute("selectedAllocation", selected);
        request.setAttribute("students", students);
        request.setAttribute("enrolledIn", enrolledIn);
        request.setAttribute("records", records);
        request.setAttribute("classes", academicDAO.getAllClasses());
        request.getRequestDispatcher("manage_enrollment.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AdminSupport.requireAdmin(request, response) == null)
            return;

        Integer courseId = AdminSupport.parseInt(request.getParameter("courseId"));
        Integer allocationId = AdminSupport.parseInt(request.getParameter("allocationId"));
        String back = "manageEnrollment?courseId=" + courseId + (allocationId != null ? "&allocationId=" + allocationId : "");
        if (courseId == null) {
            response.sendRedirect("manageCourses");
            return;
        }

        String action = request.getParameter("action");
        if ("drop".equals(action)) {
            Integer enrollmentId = AdminSupport.parseInt(request.getParameter("enrollmentId"));
            if (enrollmentId != null && enrollmentDAO.dropEnrollment(enrollmentId, courseId)) {
                AdminSupport.flash(request, FLASH_SUCCESS, "Student dropped from the course. Their grades and attendance are kept.");
            } else {
                AdminSupport.flash(request, FLASH_ERROR, "Could not drop: the enrollment was not found or is not active.");
            }
            response.sendRedirect(back + "#enrolled");
            return;
        }

        // Enroll: the offering must belong to this course
        CourseAllocation target = null;
        if (allocationId != null) {
            for (CourseAllocation a : allocationDAO.getAllocationsDetailed(courseId))
                if (a.getAllocationId() == allocationId)
                    target = a;
        }
        // One comma-separated field from the picker; repeated fields without JavaScript
        Set<Integer> ids = AdminSupport.parseIds(request.getParameterValues("studentIds"));
        ids.addAll(AdminSupport.parseIds(request.getParameterValues("studentId")));

        if (target == null) {
            AdminSupport.flash(request, FLASH_ERROR, "Please choose a valid semester offering of this course.");
        } else if (ids.isEmpty()) {
            AdminSupport.flash(request, FLASH_ERROR, "Please select at least one student.");
        } else if (ids.size() > MAX_BULK) {
            AdminSupport.flash(request, FLASH_ERROR, "You can enroll at most " + MAX_BULK + " students at once.");
        } else {
            EnrollmentDAO.BulkResult r = enrollmentDAO.enrollStudents(allocationId, ids);
            if (!r.ok) {
                AdminSupport.flash(request, FLASH_ERROR, "Enrollment failed; nothing was changed. Please try again.");
            } else {
                int added = r.enrolled + r.reactivated;
                StringBuilder msg = new StringBuilder();
                msg.append(added).append(added == 1 ? " student" : " students").append(" enrolled in ")
                        .append(target.getCourse().getCourseCode()).append(" (").append(target.getSemester().getName()).append(")");
                if (r.reactivated > 0)
                    msg.append(", including ").append(r.reactivated).append(" re-enrolled after dropping");
                msg.append('.');
                if (r.alreadyEnrolled > 0)
                    msg.append(" Skipped ").append(r.alreadyEnrolled).append(" already enrolled.");
                if (r.invalid > 0)
                    msg.append(" Skipped ").append(r.invalid).append(" not active students.");
                AdminSupport.flash(request, added > 0 ? FLASH_SUCCESS : FLASH_ERROR, msg.toString());
            }
        }
        response.sendRedirect(back);
    }
}
