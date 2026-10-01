package com.cms.controllers;

import com.cms.dao.AttendanceDAO;
import com.cms.models.CourseAttendance;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Student: their attendance per course, grouped by term and semester (e.g. "Fall 2024-3rd Semester",
 * newest first), with filters for term, semester, course and attendance level; and lecture by
 * lecture for one course.
 */
public class AttendanceServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    static final String LEVEL_LOW = "low";
    static final String LEVEL_OK = "ok";

    private AttendanceDAO attendanceDAO;

    public void init() {
        attendanceDAO = new AttendanceDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("user");
        if (user == null) {
            response.sendRedirect("login");
            return;
        }
        if (!"STUDENT".equals(user.getRole())) {
            response.sendRedirect("dashboard");
            return;
        }

        // Only the student's own courses are ever loaded, so a changed ID in the URL finds nothing
        List<CourseAttendance> all = attendanceDAO.getStudentCourses(user.getUserId());
        Filters f = Filters.from(request);
        request.setAttribute("filterQuery", f.query());

        Integer enrollmentId = AdminSupport.parseInt(request.getParameter("enrollmentId"));
        if (enrollmentId != null) {
            CourseAttendance course = null;
            for (CourseAttendance c : all)
                if (c.getEnrollmentId() == enrollmentId)
                    course = c;
            if (course == null) {
                response.sendRedirect("attendance");
                return;
            }
            request.setAttribute("course", course);
            request.setAttribute("attendanceList", attendanceDAO.getAttendanceByEnrollment(enrollmentId));
            request.getRequestDispatcher("attendance.jsp").forward(request, response);
            return;
        }

        // Filter choices come from all of the student's courses, in the same order as the list
        Map<Integer, String> terms = new LinkedHashMap<>();
        TreeSet<Integer> semesters = new TreeSet<>();
        Map<String, String> courses = new TreeMap<>();
        for (CourseAttendance c : all) {
            terms.putIfAbsent(c.getTermId(), c.getTermName());
            if (c.getSemesterNumber() > 0)
                semesters.add(c.getSemesterNumber());
            courses.putIfAbsent(c.getCourseCode(), c.getCourseName());
        }
        request.setAttribute("termOptions", terms);
        request.setAttribute("semesterOptions", semesters);
        request.setAttribute("courseOptions", courses);
        request.setAttribute("filters", f);
        request.setAttribute("totalCourses", all.size());
        request.setAttribute("groups", group(f.apply(all)));
        request.getRequestDispatcher("attendance.jsp").forward(request, response);
    }

    /** Courses grouped by term and semester ("Fall 2024-3rd Semester"), keeping the list's order. */
    static Map<String, List<CourseAttendance>> group(List<CourseAttendance> list) {
        Map<String, List<CourseAttendance>> groups = new LinkedHashMap<>();
        for (CourseAttendance c : list)
            groups.computeIfAbsent(c.getTermId() + ":" + c.getSemesterNumber(), k -> new ArrayList<>()).add(c);
        return groups;
    }

    /** The filters from the query string; unknown or invalid values are ignored. */
    public static final class Filters {
        public Integer term;      // semester (term) id
        public Integer semester;  // semester number, e.g. 3
        public String course;     // course code
        public String level;      // "low" (below the threshold) or "ok"

        static Filters from(HttpServletRequest request) {
            Filters f = new Filters();
            f.term = AdminSupport.parseInt(request.getParameter("term"));
            f.semester = AdminSupport.parseInt(request.getParameter("semester"));
            String course = request.getParameter("course");
            f.course = course == null || course.isBlank() ? null : course.trim();
            String level = request.getParameter("level");
            f.level = LEVEL_LOW.equals(level) || LEVEL_OK.equals(level) ? level : null;
            return f;
        }

        public boolean isActive() {
            return term != null || semester != null || course != null || level != null;
        }

        boolean matches(CourseAttendance c) {
            if (term != null && c.getTermId() != term) return false;
            if (semester != null && c.getSemesterNumber() != semester) return false;
            if (course != null && !course.equals(c.getCourseCode())) return false;
            if (LEVEL_LOW.equals(level) && !c.isLow()) return false;
            // "75% or more": courses with marked lectures that are not low
            if (LEVEL_OK.equals(level) && (c.getPercent() < 0 || c.isLow())) return false;
            return true;
        }

        List<CourseAttendance> apply(List<CourseAttendance> all) {
            List<CourseAttendance> out = new ArrayList<>();
            for (CourseAttendance c : all)
                if (matches(c))
                    out.add(c);
            return out;
        }

        /** The filters as a query string ("term=1&course=CS-201"), "" when none; for links back to the list. */
        String query() {
            StringBuilder q = new StringBuilder();
            if (term != null) q.append("&term=").append(term);
            if (semester != null) q.append("&semester=").append(semester);
            if (course != null) q.append("&course=").append(URLEncoder.encode(course, StandardCharsets.UTF_8));
            if (level != null) q.append("&level=").append(level);
            return q.length() == 0 ? "" : q.substring(1);
        }
    }
}
