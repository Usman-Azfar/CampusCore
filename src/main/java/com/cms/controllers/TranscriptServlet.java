package com.cms.controllers;

import com.cms.dao.GradeDAO;
import com.cms.dao.UserDAO;
import com.cms.models.Grade;
import com.cms.models.User;
import com.cms.util.GradeRules;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;

/**
 * Student: transcript of published, complete results and withdrawals, term by term (oldest first),
 * with semester GPA and CGPA. Filters (term, semester) narrow what is listed; the CGPA is always
 * over the whole record.
 */
public class TranscriptServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
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

        List<Grade> all = gradeDAO.getTranscriptGrades(user.getUserId());
        GradebookServlet.Filters f = GradebookServlet.Filters.from(request, false);
        List<Grade> shown = f.apply(all);
        GradebookServlet.addFilterOptions(request, all, false);
        request.setAttribute("filters", f);
        // Repeats from every attempt (as in the Gradebook), not only those on the transcript
        request.setAttribute("repeats", GradebookServlet.repeats(gradeDAO.getGradesByStudent(user.getUserId())));
        // CGPA and overall credit hours: only the latest graded attempt of a repeated course counts
        request.setAttribute("overall", GradeRules.summarizeCumulative(all));
        request.setAttribute("replaced", GradeRules.replaced(all));
        request.setAttribute("selected", GradeRules.summarize(shown));
        request.setAttribute("terms", GradebookServlet.groupByTerm(shown));
        request.setAttribute("hasResults", !all.isEmpty());
        // Full account (profile name + class) for the transcript header
        request.setAttribute("account", new UserDAO().getUserById(user.getUserId()));
        request.getRequestDispatcher("transcript.jsp").forward(request, response);
    }
}
