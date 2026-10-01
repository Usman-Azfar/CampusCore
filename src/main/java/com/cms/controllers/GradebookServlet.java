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

public class GradebookServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private GradeDAO gradeDAO;

    public void init() {
        gradeDAO = new GradeDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        // AuthenticationFilter handles login check
        User user = (User) session.getAttribute("user");

        if ("STUDENT".equals(user.getRole())) {
            List<Grade> grades = gradeDAO.getGradesByStudent(user.getUserId());
            request.setAttribute("grades", grades);
            request.getRequestDispatcher("gradebook.jsp").forward(request, response);
        } else {
            // Teacher/Admin Logic to view/add grades
            response.sendRedirect("dashboard");
        }
    }
}
