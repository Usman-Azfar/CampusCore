package com.cms.controllers;

import com.cms.dao.UserDAO;
import com.cms.models.User;
import jakarta.servlet.ServletException;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private UserDAO userDAO;

    public void init() {
        userDAO = new UserDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.getRequestDispatcher("login.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = request.getParameter("username"); // Roll Number
        String password = request.getParameter("password");

        User user = userDAO.authenticateUser(username, password);

        if (user != null) {
            HttpSession session = request.getSession();
            user.setPassword(null); // never keep the password in the session
            session.setAttribute("user", user);
            session.setAttribute("role", user.getRole());

            // Redirect to Dashboard
            response.sendRedirect("dashboard");
        } else {
            request.setAttribute("errorMessage", "Invalid Roll Number or Password");
            request.getRequestDispatcher("login.jsp").forward(request, response);
        }
    }
}
