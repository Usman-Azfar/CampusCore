package com.cms.controllers;

import com.cms.dao.GradeDAO;
import com.cms.models.Grade;
import com.cms.models.Semester;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Student: their marks and grades per course, grouped by term and semester (e.g.
 * "Fall 2024-3rd Semester", newest first), with filters for term, semester, course and result.
 */
public class GradebookServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    static final Set<String> RESULTS = Set.of("graded", "progress", "awaited", "failed", "withdrawn");

    private GradeDAO gradeDAO;

    public void init() {
        gradeDAO = new GradeDAO();
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

        List<Grade> all = gradeDAO.getGradesByStudent(user.getUserId());
        Filters f = Filters.from(request, true);
        addFilterOptions(request, all, true);
        request.setAttribute("filters", f);
        request.setAttribute("totalCourses", all.size());
        request.setAttribute("repeats", repeats(all));
        // CGPA is always over everything; a course taken again counts only with its latest graded attempt
        request.setAttribute("overall", com.cms.util.GradeRules.summarizeCumulative(all));
        request.setAttribute("replaced", com.cms.util.GradeRules.replaced(all));
        request.setAttribute("groups", groupByTerm(f.apply(all)));
        request.getRequestDispatcher("gradebook.jsp").forward(request, response);
    }

    // ---------------- shared with the transcript ----------------

    /** Results grouped by term and semester ("Fall 2024-3rd Semester"), keeping the order they came in. */
    static Map<String, List<Grade>> groupByTerm(List<Grade> grades) {
        Map<String, List<Grade>> groups = new LinkedHashMap<>();
        for (Grade g : grades)
            groups.computeIfAbsent(g.getEnrollment().getCourseAllocation().getSemesterId() + ":" + g.getEnrollment().getSemesterNumber(),
                    k -> new ArrayList<>()).add(g);
        return groups;
    }

    /**
     * Enrollment IDs of repeated attempts: the same course taken again in a later term (the first
     * attempt is not a repeat). All attempts are listed; each graded attempt counts towards CGPA.
     */
    static Set<Integer> repeats(List<Grade> grades) {
        Map<Integer, List<Grade>> byCourse = new HashMap<>();
        for (Grade g : grades)
            byCourse.computeIfAbsent(g.getEnrollment().getCourseAllocation().getCourse().getCourseId(), k -> new ArrayList<>()).add(g);
        Comparator<Grade> byTerm = Comparator
                .comparing((Grade g) -> { Semester s = g.getEnrollment().getCourseAllocation().getSemester();
                    return s.getStartDate() == null ? java.sql.Date.valueOf("1970-01-01") : s.getStartDate(); })
                .thenComparingInt(g -> g.getEnrollment().getCourseAllocation().getSemesterId());
        Set<Integer> repeats = new HashSet<>();
        for (List<Grade> attempts : byCourse.values()) {
            attempts.sort(byTerm);
            for (int i = 1; i < attempts.size(); i++)
                repeats.add(attempts.get(i).getEnrollmentId());
        }
        return repeats;
    }

    /** Filter choices from all of the student's results, in list order: terms, semester numbers, courses. */
    static void addFilterOptions(HttpServletRequest request, List<Grade> all, boolean withCourses) {
        Map<Integer, String> terms = new LinkedHashMap<>();
        TreeSet<Integer> semesters = new TreeSet<>();
        Map<String, String> courses = new TreeMap<>();
        for (Grade g : all) {
            terms.putIfAbsent(g.getEnrollment().getCourseAllocation().getSemesterId(), g.getEnrollment().getCourseAllocation().getSemester().getName());
            if (g.getEnrollment().getSemesterNumber() > 0)
                semesters.add(g.getEnrollment().getSemesterNumber());
            if (withCourses)
                courses.putIfAbsent(g.getEnrollment().getCourseAllocation().getCourse().getCourseCode(),
                        g.getEnrollment().getCourseAllocation().getCourse().getCourseName());
        }
        request.setAttribute("termOptions", terms);
        request.setAttribute("semesterOptions", semesters);
        request.setAttribute("courseOptions", courses);
    }

    /** Filters from the query string; unknown or invalid values are ignored. */
    public static final class Filters {
        public Integer term;      // semester (term) id
        public Integer semester;  // semester number
        public String course;     // course code (gradebook only)
        public String result;     // graded / progress / awaited / failed / withdrawn (gradebook only)

        static Filters from(HttpServletRequest request, boolean gradebook) {
            Filters f = new Filters();
            f.term = AdminSupport.parseInt(request.getParameter("term"));
            f.semester = AdminSupport.parseInt(request.getParameter("semester"));
            if (gradebook) {
                String course = request.getParameter("course");
                f.course = course == null || course.isBlank() ? null : course.trim();
                f.result = resultParam(request.getParameter("result"));
            }
            return f;
        }

        /** A known result filter, or null (missing or unknown; Set.of(...).contains(null) would throw). */
        static String resultParam(String value) {
            return value != null && RESULTS.contains(value) ? value : null;
        }

        public boolean isActive() {
            return term != null || semester != null || course != null || result != null;
        }

        boolean matches(Grade g) {
            if (term != null && g.getEnrollment().getCourseAllocation().getSemesterId() != term) return false;
            if (semester != null && g.getEnrollment().getSemesterNumber() != semester) return false;
            if (course != null && !course.equals(g.getEnrollment().getCourseAllocation().getCourse().getCourseCode())) return false;
            if (result != null) {
                String status = g.getResultStatus();
                switch (result) {
                    case "graded": return Grade.RESULT_GRADED.equals(status);
                    case "progress": return Grade.RESULT_IN_PROGRESS.equals(status);
                    case "awaited": return Grade.RESULT_AWAITED.equals(status);
                    case "withdrawn": return Grade.RESULT_WITHDRAWN.equals(status);
                    case "failed": return Grade.RESULT_GRADED.equals(status) && !com.cms.util.GradeRules.isPass(g.getGradeLetter());
                    default: return true;
                }
            }
            return true;
        }

        List<Grade> apply(List<Grade> all) {
            List<Grade> out = new ArrayList<>();
            for (Grade g : all)
                if (matches(g))
                    out.add(g);
            return out;
        }

        /** As a query string, e.g. "term=1&result=graded"; "" when none. */
        public String query() {
            StringBuilder q = new StringBuilder();
            if (term != null) q.append("&term=").append(term);
            if (semester != null) q.append("&semester=").append(semester);
            if (course != null) q.append("&course=").append(URLEncoder.encode(course, StandardCharsets.UTF_8));
            if (result != null) q.append("&result=").append(result);
            return q.length() == 0 ? "" : q.substring(1);
        }
    }
}
