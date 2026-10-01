package com.cms.controllers;

import com.cms.dao.GradeDAO;
import com.cms.models.Grade;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class TranscriptServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private GradeDAO gradeDAO;

    public void init() {
        gradeDAO = new GradeDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.sendRedirect("login");
            return;
        }

        User user = (User) session.getAttribute("user");
        if (!"STUDENT".equals(user.getRole())) {
            response.sendRedirect("dashboard");
            return;
        }

        List<Grade> grades = gradeDAO.getPublishedGradesByStudent(user.getUserId());

        // Group grades by semester number
        Map<Integer, List<Grade>> semesterGrades = grades.stream()
                .collect(Collectors.groupingBy(
                        g -> g.getEnrollment().getSemesterNumber(),
                        TreeMap::new,
                        Collectors.toList()));

        request.setAttribute("semesterGrades", semesterGrades);
        // Full account (profile name + class) for the transcript header
        request.setAttribute("account", new com.cms.dao.UserDAO().getUserById(user.getUserId()));
        request.getRequestDispatcher("transcript.jsp").forward(request, response);
    }
}
