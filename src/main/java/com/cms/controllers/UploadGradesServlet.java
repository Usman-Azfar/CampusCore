package com.cms.controllers;

import com.cms.dao.GradeDAO;
import com.cms.models.CourseAllocation;
import com.cms.models.Grade;
import com.cms.models.User;
import com.cms.util.GradeRules;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Teacher: enter marks for their course offerings and publish results to students.
 * A blank mark means "not entered yet"; the grade letter is given once all three marks are
 * entered (see GradeRules). Invalid input saves nothing and re-shows the sheet with what was typed.
 * A sheet can be changed until 10 days after its term ends; after that it is view only.
 */
public class UploadGradesServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = "grades.flash.success";
    private static final String FLASH_ERROR = "grades.flash.error";
    private static final int MAX_ERRORS_SHOWN = 5;

    private GradeDAO gradeDAO;

    @Override
    public void init() {
        gradeDAO = new GradeDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User teacher = requireTeacher(request, response);
        if (teacher == null)
            return;
        HttpSession session = request.getSession();
        SupportTicketServlet.moveFlash(session, request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(session, request, FLASH_ERROR, "errorMessage");

        Integer allocationId = AdminSupport.parseInt(request.getParameter("allocationId"));
        if (allocationId == null) {
            // The teacher's courses: open for grading first, locked (view only) below
            List<CourseAllocation> open = new ArrayList<>(), locked = new ArrayList<>();
            for (CourseAllocation ca : gradeDAO.getOfferingsByTeacher(teacher.getUserId()))
                (GradeRules.isEditable(ca.getSemester()) ? open : locked).add(ca);
            request.setAttribute("openOfferings", open);
            request.setAttribute("lockedOfferings", locked);
            request.getRequestDispatcher("upload_grades.jsp").forward(request, response);
            return;
        }

        CourseAllocation offering = ownOffering(allocationId, teacher);
        if (offering == null) {
            AdminSupport.flash(request, FLASH_ERROR, "That course is not assigned to you.");
            response.sendRedirect("uploadGrades");
            return;
        }
        showSheet(request, response, offering, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User teacher = requireTeacher(request, response);
        if (teacher == null)
            return;
        Integer allocationId = AdminSupport.parseInt(request.getParameter("allocationId"));
        CourseAllocation offering = allocationId == null ? null : ownOffering(allocationId, teacher);
        if (offering == null) {
            AdminSupport.flash(request, FLASH_ERROR, "That course is not assigned to you.");
            response.sendRedirect("uploadGrades");
            return;
        }
        String base = "uploadGrades?allocationId=" + allocationId;
        String locked = GradeRules.lockReason(offering.getSemester());
        if (locked != null) {
            AdminSupport.flash(request, FLASH_ERROR, "Grades not saved. " + locked);
            response.sendRedirect(base);
            return;
        }

        String[] ids = request.getParameterValues("enrollmentId");
        if (ids == null || ids.length == 0) {
            AdminSupport.flash(request, FLASH_ERROR, "Nothing to save: no students were submitted.");
            response.sendRedirect(base);
            return;
        }

        // Roll numbers for error messages; students no longer enrolled are skipped by the DAO
        Map<Integer, String> rollNos = new HashMap<>();
        for (Grade g : gradeDAO.getSheet(allocationId))
            rollNos.put(g.getEnrollmentId(), g.getEnrollment().getStudent().getUsername());

        List<Grade> rows = new ArrayList<>();
        Map<Integer, String[]> typed = new HashMap<>(); // what was typed, to refill the sheet on error
        List<String> errors = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (String idValue : ids) {
            Integer enrollmentId = AdminSupport.parseInt(idValue);
            if (enrollmentId == null || !seen.add(enrollmentId))
                continue;
            String sessional = request.getParameter("sessional_" + enrollmentId);
            String mid = request.getParameter("mid_" + enrollmentId);
            String fin = request.getParameter("final_" + enrollmentId);
            boolean publish = request.getParameter("publish_" + enrollmentId) != null;
            typed.put(enrollmentId, new String[] { sessional, mid, fin, publish ? "1" : "" });

            Grade row = new Grade();
            row.setEnrollmentId(enrollmentId);
            row.setPublished(publish);
            List<String> problems = new ArrayList<>();
            row.setSessionalMarks(mark(sessional, GradeRules.SESSIONAL_MAX, "Sessional", problems));
            row.setMidMarks(mark(mid, GradeRules.MID_MAX, "Mid", problems));
            row.setFinalMarks(mark(fin, GradeRules.FINAL_MAX, "Final", problems));
            if (problems.isEmpty())
                rows.add(row);
            else
                errors.add(rollNos.getOrDefault(enrollmentId, "A student") + ": " + String.join("; ", problems) + ".");
        }

        if (!errors.isEmpty()) {
            StringBuilder msg = new StringBuilder("Grades not saved. Fix the highlighted marks: ");
            msg.append(String.join(" ", errors.subList(0, Math.min(errors.size(), MAX_ERRORS_SHOWN))));
            if (errors.size() > MAX_ERRORS_SHOWN)
                msg.append(" (and ").append(errors.size() - MAX_ERRORS_SHOWN).append(" more)");
            request.setAttribute("errorMessage", msg.toString());
            showSheet(request, response, offering, typed);
            return;
        }

        GradeDAO.SaveResult r = gradeDAO.saveSheet(allocationId, rows, teacher.getUserId());
        if (r.error != null) {
            request.setAttribute("errorMessage", "Grades not saved: " + r.error);
            showSheet(request, response, offering, typed);
            return;
        }

        StringBuilder msg = new StringBuilder();
        if (r.changed == 0)
            msg.append("No changes to save.");
        else
            msg.append("Grades saved for ").append(r.changed).append(r.changed == 1 ? " student." : " students.");
        if (r.skipped > 0)
            msg.append(" ").append(r.skipped).append(r.skipped == 1 ? " student was" : " students were")
                    .append(" skipped because they are no longer enrolled in this course.");
        int partial = 0;
        if (r.changed > 0)
            for (Grade g : rows)
                if (g.isPublished() && !g.isComplete() && rollNos.containsKey(g.getEnrollmentId()))
                    partial++;
        if (partial > 0)
            msg.append(" ").append(partial).append(partial == 1 ? " published result is" : " published results are")
                    .append(" incomplete: the student sees the marks entered so far, and the grade appears once all three marks are entered.");
        AdminSupport.flash(request, FLASH_SUCCESS, msg.toString());
        response.sendRedirect(base);
    }

    // ---------------- helpers ----------------

    // A typed mark (null when blank); an invalid one is added to problems and returns null
    private static Double mark(String value, double maximum, String label, List<String> problems) {
        try {
            return GradeRules.parseMark(value, maximum, label);
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
            return null;
        }
    }

    private void showSheet(HttpServletRequest request, HttpServletResponse response, CourseAllocation offering,
            Map<Integer, String[]> typed) throws ServletException, IOException {
        request.setAttribute("offering", offering);
        request.setAttribute("sheetLocked", GradeRules.lockReason(offering.getSemester()));
        request.setAttribute("sheet", gradeDAO.getSheet(offering.getAllocationId()));
        request.setAttribute("typed", typed);
        request.getRequestDispatcher("upload_grades.jsp").forward(request, response);
    }

    private CourseAllocation ownOffering(int allocationId, User teacher) {
        CourseAllocation offering = gradeDAO.getOffering(allocationId);
        return canManageAllocation(teacher, offering) ? offering : null;
    }

    /** Only the teacher assigned to the offering may grade it. */
    static boolean canManageAllocation(User user, CourseAllocation allocation) {
        return user != null && "TEACHER".equals(user.getRole()) && allocation != null
                && user.getUserId() == allocation.getTeacherId();
    }

    private static User requireTeacher(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("user");
        if (user == null) {
            response.sendRedirect("login");
            return null;
        }
        if (!"TEACHER".equals(user.getRole())) {
            response.sendRedirect("dashboard");
            return null;
        }
        return user;
    }
}
