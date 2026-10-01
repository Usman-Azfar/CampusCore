package com.cms.controllers;

import com.cms.dao.CourseAllocationDAO;
import com.cms.dao.CourseDAO;
import com.cms.dao.SemesterDAO;
import com.cms.dao.UserDAO;
import com.cms.models.Course;
import com.cms.models.CourseAllocation;
import com.cms.models.Semester;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Admin: assign a teacher to a course for a semester (a "course offering"). */
public class ManageAllocationsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = "allocations.flash.success";
    private static final String FLASH_ERROR = "allocations.flash.error";

    private CourseAllocationDAO allocationDAO;
    private CourseDAO courseDAO;
    private UserDAO userDAO;
    private SemesterDAO semesterDAO;

    public void init() {
        allocationDAO = new CourseAllocationDAO();
        courseDAO = new CourseDAO();
        userDAO = new UserDAO();
        semesterDAO = new SemesterDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AdminSupport.requireAdmin(request, response) == null)
            return;
        SupportTicketServlet.moveFlash(request.getSession(), request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(request.getSession(), request, FLASH_ERROR, "errorMessage");

        // Opened from "Assign Teacher" on a course: that course is fixed in the form
        Integer courseId = AdminSupport.parseInt(request.getParameter("courseId"));
        Course lockedCourse = courseId != null ? courseDAO.getCourseById(courseId) : null;

        request.setAttribute("lockedCourse", lockedCourse);
        request.setAttribute("courses", courseDAO.getAllCourses());
        request.setAttribute("teachers", activeTeachersSorted());
        request.setAttribute("semesters", semestersSorted());
        request.setAttribute("allocations", allocationDAO.getAllocationsDetailed(null));
        request.getRequestDispatcher("manage_allocations.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AdminSupport.requireAdmin(request, response) == null)
            return;

        String action = request.getParameter("action");
        Integer returnCourse = AdminSupport.parseInt(request.getParameter("returnCourseId"));
        String back = "manageAllocations" + (returnCourse != null ? "?courseId=" + returnCourse : "");

        if ("add".equals(action)) {
            Integer courseId = AdminSupport.parseInt(request.getParameter("courseId"));
            Integer teacherId = AdminSupport.parseInt(request.getParameter("teacherId"));
            Integer semesterId = AdminSupport.parseInt(request.getParameter("semesterId"));

            Course course = courseId != null ? courseDAO.getCourseById(courseId) : null;
            User teacher = teacherId != null ? userDAO.getUserById(teacherId) : null;
            Semester semester = findSemester(semesterId);

            if (course == null) {
                AdminSupport.flash(request, FLASH_ERROR, "Please select a valid course.");
            } else if (teacher == null || !"TEACHER".equals(teacher.getRole()) || !teacher.isActive()) {
                AdminSupport.flash(request, FLASH_ERROR, "Please select an active teacher.");
            } else if (semester == null) {
                AdminSupport.flash(request, FLASH_ERROR, "Please select a valid semester.");
            } else {
                String what = course.getCourseCode() + " in " + semester.getName();
                CourseAllocation existing = allocationDAO.getAllocationByCourseAndSemester(courseId, semesterId);

                CourseAllocation ca = new CourseAllocation();
                ca.setCourseId(courseId);
                ca.setTeacherId(teacherId);
                ca.setSemesterId(semesterId);

                if (existing == null) {
                    if (allocationDAO.addAllocation(ca)) {
                        AdminSupport.flash(request, FLASH_SUCCESS, teacher.getDisplayName() + " assigned to " + what + ".");
                    } else {
                        AdminSupport.flash(request, FLASH_ERROR, "Could not assign the teacher. Please try again.");
                    }
                } else if (existing.getTeacherId() == teacherId) {
                    AdminSupport.flash(request, FLASH_ERROR, teacher.getDisplayName() + " is already assigned to " + what + ".");
                } else {
                    // One teacher per course per semester: this replaces the current teacher, students stay enrolled
                    User previous = userDAO.getUserById(existing.getTeacherId());
                    if (allocationDAO.updateAllocation(ca)) {
                        AdminSupport.flash(request, FLASH_SUCCESS, what + " reassigned from "
                                + (previous != null ? previous.getDisplayName() : "the previous teacher")
                                + " to " + teacher.getDisplayName() + ". Enrolled students were kept.");
                    } else {
                        AdminSupport.flash(request, FLASH_ERROR, "Could not reassign the teacher. Please try again.");
                    }
                }
            }
        } else if ("delete".equals(action)) {
            Integer allocId = AdminSupport.parseInt(request.getParameter("allocationId"));
            CourseAllocation target = null;
            if (allocId != null) {
                for (CourseAllocation a : allocationDAO.getAllocationsDetailed(null)) {
                    if (a.getAllocationId() == allocId) {
                        target = a;
                        break;
                    }
                }
            }
            if (target == null) {
                AdminSupport.flash(request, FLASH_ERROR, "Assignment not found.");
            } else if (allocationDAO.deleteAllocation(allocId)) {
                AdminSupport.flash(request, FLASH_SUCCESS, "Removed " + target.getCourse().getCourseCode() + " in "
                        + target.getSemester().getName()
                        + (target.getEnrollmentRows() > 0
                                ? " and its " + target.getEnrollmentRows() + " enrollment record(s) with their grades and attendance."
                                : "."));
            } else {
                AdminSupport.flash(request, FLASH_ERROR, "Could not remove the assignment.");
            }
        } else {
            AdminSupport.flash(request, FLASH_ERROR, "Unknown action.");
        }
        response.sendRedirect(back);
    }

    // Active teachers, by department name then teacher name ("no department" last)
    private List<User> activeTeachersSorted() {
        List<User> teachers = new ArrayList<>();
        for (User t : userDAO.getUsersByRole("TEACHER"))
            if (t.isActive())
                teachers.add(t);
        teachers.sort(Comparator
                .comparing((User t) -> t.getDepartmentName() == null ? "￿" : t.getDepartmentName().toLowerCase())
                .thenComparing(t -> t.getDisplayName().toLowerCase()));
        return teachers;
    }

    // Active semester first, then newest start date
    private List<Semester> semestersSorted() {
        List<Semester> semesters = new ArrayList<>(semesterDAO.getAllSemesters());
        semesters.sort(Comparator.comparing((Semester s) -> !s.isActive())
                .thenComparing(Semester::getStartDate, Comparator.nullsLast(Comparator.reverseOrder())));
        return semesters;
    }

    private Semester findSemester(Integer id) {
        if (id == null)
            return null;
        for (Semester s : semesterDAO.getAllSemesters())
            if (s.getSemesterId() == id)
                return s;
        return null;
    }
}
