package com.cms.controllers;

import com.cms.dao.CourseDAO;
import com.cms.dao.CourseRequestDAO;
import com.cms.dao.EnrollmentDAO;
import com.cms.dao.UserDAO;
import com.cms.models.Course;
import com.cms.models.CourseRequest;
import com.cms.models.Enrollment;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Student side of Add / Drop / Withdraw: shows the forms and history, and validates
 * new requests before they reach the admin.
 */
public class CourseRequestServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = "courseRequests.flash.success";
    private static final String FLASH_ERROR = "courseRequests.flash.error";

    private CourseDAO courseDAO;
    private EnrollmentDAO enrollmentDAO;
    private CourseRequestDAO courseRequestDAO;
    private UserDAO userDAO;

    public void init() {
        courseDAO = new CourseDAO();
        enrollmentDAO = new EnrollmentDAO();
        courseRequestDAO = new CourseRequestDAO();
        userDAO = new UserDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = currentStudent(request, response);
        if (user == null)
            return;
        HttpSession session = request.getSession(false);
        SupportTicketServlet.moveFlash(session, request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(session, request, FLASH_ERROR, "errorMessage");

        List<Enrollment> currentEnrollments = enrollmentDAO.getActiveSemesterEnrollments(user.getUserId());
        List<CourseRequest> history = courseRequestDAO.getRequestsByStudent(user.getUserId());

        // Courses offered this semester that the student is not already taking
        Set<Integer> enrolledCourseIds = new HashSet<>();
        for (Enrollment e : enrollmentDAO.getEnrollmentsByStudent(user.getUserId())) {
            enrolledCourseIds.add(e.getCourseAllocation().getCourse().getCourseId());
        }
        List<Course> addable = courseDAO.getCoursesWithActiveAllocations();
        addable.removeIf(c -> enrolledCourseIds.contains(c.getCourseId()));

        // Courses that already have a pending request (any type) cannot get another
        Set<Integer> pendingCourseIds = new HashSet<>();
        for (CourseRequest r : history) {
            if ("PENDING".equals(r.getStatus()))
                pendingCourseIds.add(r.getCourseId());
        }

        request.setAttribute("addableCourses", addable);
        request.setAttribute("currentEnrollments", currentEnrollments);
        request.setAttribute("pendingCourseIds", pendingCourseIds);
        request.setAttribute("requestHistory", history);
        request.setAttribute("student", userDAO.getUserById(user.getUserId()));

        request.getRequestDispatcher("course_requests.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = currentStudent(request, response);
        if (user == null)
            return;
        HttpSession session = request.getSession(false);

        String type = request.getParameter("type");
        int courseId;
        try {
            courseId = Integer.parseInt(request.getParameter("courseId"));
        } catch (NumberFormatException e) {
            courseId = -1;
        }

        String error = validate(user.getUserId(), type, courseId);
        if (error != null) {
            session.setAttribute(FLASH_ERROR, error);
        } else {
            CourseRequest req = new CourseRequest();
            req.setStudentId(user.getUserId());
            req.setCourseId(courseId);
            req.setType(type);

            if (courseRequestDAO.createRequest(req)) {
                session.setAttribute(FLASH_SUCCESS, typeLabel(type) + " request submitted and is pending admin approval.");
            } else {
                session.setAttribute(FLASH_ERROR, "Could not submit the request. Please try again.");
            }
        }
        response.sendRedirect("courseRequests");
    }

    // Returns a user-facing error, or null when the request is valid
    private String validate(int studentId, String type, int courseId) {
        if (type == null || !CourseRequestDAO.TYPES.contains(type))
            return "Invalid request type.";
        if (courseId <= 0)
            return "Please select a course.";
        if (courseRequestDAO.hasPendingRequest(studentId, courseId))
            return "You already have a pending request for this course. Wait for the admin to process it.";

        if ("ADD".equals(type)) {
            boolean offered = courseDAO.getCoursesWithActiveAllocations().stream()
                    .anyMatch(c -> c.getCourseId() == courseId);
            if (!offered)
                return "That course is not offered in the current semester.";
            boolean enrolled = enrollmentDAO.getEnrollmentsByStudent(studentId).stream()
                    .anyMatch(e -> e.getCourseAllocation().getCourse().getCourseId() == courseId);
            if (enrolled)
                return "You are already enrolled in this course.";
        } else {
            boolean current = enrollmentDAO.getActiveSemesterEnrollments(studentId).stream()
                    .anyMatch(e -> e.getCourseAllocation().getCourse().getCourseId() == courseId);
            if (!current)
                return "You can only " + type.toLowerCase() + " a course you are currently enrolled in.";
        }
        return null;
    }

    static String typeLabel(String type) {
        if ("ADD".equals(type))
            return "Add";
        if ("DROP".equals(type))
            return "Drop";
        if ("WITHDRAW".equals(type))
            return "Withdraw";
        return type;
    }

    private User currentStudent(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.sendRedirect("login");
            return null;
        }
        User user = (User) session.getAttribute("user");
        if (!"STUDENT".equals(user.getRole())) {
            response.sendRedirect("dashboard");
            return null;
        }
        return user;
    }
}
