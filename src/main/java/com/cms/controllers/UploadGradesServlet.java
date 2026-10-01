package com.cms.controllers;

import com.cms.dao.GradeDAO;
import com.cms.dao.CourseAllocationDAO;
import com.cms.models.Grade;
import com.cms.models.CourseAllocation;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;

@WebServlet("/uploadGrades")
public class UploadGradesServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private GradeDAO gradeDAO;
    private CourseAllocationDAO courseAllocationDAO;

    @Override
    public void init() {
        gradeDAO = new GradeDAO();
        courseAllocationDAO = new CourseAllocationDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        String allocationIdStr = request.getParameter("allocationId");

        if (allocationIdStr != null) {
            try {
                int allocationId = Integer.parseInt(allocationIdStr);
                CourseAllocation allocation = courseAllocationDAO.getAllocationById(allocationId);

                if (allocation == null) {
                    response.sendRedirect(request.getContextPath() + "/dashboard?error=allocationNotFound");
                    return;
                }

                List<Grade> studentGrades = gradeDAO.getGradesByAllocation(allocationId);

                request.setAttribute("allocation", allocation);
                request.setAttribute("studentGrades", studentGrades);
                request.getRequestDispatcher("/upload_grades.jsp").forward(request, response);
            } catch (NumberFormatException e) {
                response.sendRedirect(request.getContextPath() + "/dashboard?error=invalidAllocationId");
            }
        } else {
            response.sendRedirect(request.getContextPath() + "/dashboard");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String allocationIdStr = request.getParameter("allocationId");
        String[] enrollmentIds = request.getParameterValues("enrollmentId");

        boolean anyError = false;
        if (enrollmentIds != null && allocationIdStr != null) {
            for (String enrollmentIdStr : enrollmentIds) {
                try {
                    int enrollmentId = Integer.parseInt(enrollmentIdStr);
                    String sessionalStr = request.getParameter("sessional_" + enrollmentId);
                    String midStr = request.getParameter("mid_" + enrollmentId);
                    String finalStr = request.getParameter("final_" + enrollmentId);

                    double sessional = (sessionalStr != null && !sessionalStr.isEmpty())
                            ? Double.parseDouble(sessionalStr)
                            : 0;
                    double mid = (midStr != null && !midStr.isEmpty()) ? Double.parseDouble(midStr) : 0;
                    double finals = (finalStr != null && !finalStr.isEmpty()) ? Double.parseDouble(finalStr) : 0;

                    boolean isPublished = request.getParameter("publish_" + enrollmentId) != null;

                    Grade grade = new Grade();
                    grade.setEnrollmentId(enrollmentId);
                    grade.setSessionalMarks(sessional);
                    grade.setMidMarks(mid);
                    grade.setFinalMarks(finals);
                    grade.setPublished(isPublished);

                    if (!gradeDAO.upsertGrade(grade)) {
                        anyError = true;
                    }
                } catch (Exception e) {
                    System.err.println(
                            "Error processing grade for enrollment " + enrollmentIdStr + ": " + e.getMessage());
                    anyError = true;
                }
            }
        }

        String redirectUrl = request.getContextPath() + "/uploadGrades?allocationId=" + allocationIdStr;
        if (anyError) {
            redirectUrl += "&error=true";
        } else {
            redirectUrl += "&success=true";
        }
        response.sendRedirect(redirectUrl);
    }
}
