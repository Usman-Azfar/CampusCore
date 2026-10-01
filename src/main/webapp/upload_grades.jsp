<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
    <%@ page import="java.util.List" %>
        <%@ page import="com.cms.models.Grade" %>
            <%@ page import="com.cms.models.CourseAllocation" %>
                <%@ page import="com.cms.models.Enrollment" %>
                    <%@ page import="com.cms.models.User" %>
                        <%@ page import="com.cms.models.Course" %>

                            <!DOCTYPE html>
                            <html lang="en">

                            <head>
                                <meta charset="UTF-8">
                                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                <title>Upload Grades | CampusCore</title>
                                <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
                                <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
                                <link rel="stylesheet" href="css/styles.css">
                                <style>
                                    .grade-table-container {
                                        background: white;
                                        padding: 2rem;
                                        border-radius: 12px;
                                        box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
                                        margin: 2rem;
                                    }

                                    .grade-table {
                                        width: 100%;
                                        border-collapse: collapse;
                                        margin-top: 1.5rem;
                                    }

                                    .grade-table th,
                                    .grade-table td {
                                        padding: 1rem;
                                        text-align: left;
                                        border-bottom: 1px solid #e2e8f0;
                                    }

                                    .grade-table th {
                                        background-color: #f8fafc;
                                        color: #64748b;
                                        font-weight: 600;
                                    }

                                    .mark-input {
                                        width: 70px;
                                        padding: 0.5rem;
                                        border: 1px solid #cbd5e1;
                                        border-radius: 4px;
                                        text-align: center;
                                    }

                                    .grade-input {
                                        width: 60px;
                                        padding: 0.5rem;
                                        border: 1px solid #cbd5e1;
                                        border-radius: 4px;
                                        text-align: center;
                                        text-transform: uppercase;
                                    }

                                    .btn-save {
                                        background: linear-gradient(135deg, var(--primary-color, #1e40af), var(--secondary-color, #3b82f6));
                                        color: white;
                                        padding: 0.75rem 1.5rem;
                                        border: none;
                                        border-radius: 6px;
                                        cursor: pointer;
                                        font-weight: 600;
                                        margin-top: 1rem;
                                        transition: opacity 0.2s;
                                    }

                                    .btn-save:hover {
                                        opacity: 0.9;
                                    }

                                    .success-msg {
                                        background-color: #dcfce7;
                                        color: #166534;
                                        padding: 1rem;
                                        border-radius: 6px;
                                        margin-bottom: 1rem;
                                        border: 1px solid #bbf7d0;
                                    }

                                    .error-msg {
                                        background-color: #fee2e2;
                                        color: #991b1b;
                                        padding: 1rem;
                                        border-radius: 6px;
                                        margin-bottom: 1rem;
                                        border: 1px solid #fecaca;
                                    }

                                    .total-cell {
                                        font-weight: bold;
                                        color: #1e293b;
                                    }
                                </style>
                            </head>

                            <body>
                                <jsp:include page="sidebar.jsp" />

                                <main class="main-content">
                                    <jsp:include page="header.jsp" />

                                    <div class="grade-table-container">
                                        <% CourseAllocation allocation=(CourseAllocation)
                                            request.getAttribute("allocation"); List<Grade> studentGrades = (List<Grade>
                                                ) request.getAttribute("studentGrades");

                                                if (allocation == null) {
                                                %>
                                                <div class="error-msg">
                                                    Course allocation information not found.
                                                    <a href="dashboard"
                                                        style="color: inherit; text-decoration: underline;">Return to
                                                        Dashboard</a>
                                                </div>
                                                <% return; } %>

                                                    <div
                                                        style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 2rem;">
                                                        <div>
                                                            <h2 style="margin: 0; color: #0f172a;">Upload Grades</h2>
                                                            <p style="color: #64748b; margin: 0.5rem 0 0 0;">
                                                                <%= allocation.getCourse() !=null ?
                                                                    allocation.getCourse().getCourseName()
                                                                    : "Unknown Course" %>
                                                                    (<%= allocation.getCourse() !=null ?
                                                                        allocation.getCourse().getCourseCode() : "N/A"
                                                                        %>)
                                                            </p>
                                                        </div>
                                                        <a href="dashboard" class="nav-item"
                                                            style="text-decoration: none; display: flex; align-items: center; gap: 0.5rem; color: #64748b;">
                                                            <span>&larr;</span> Back to Dashboard
                                                        </a>
                                                    </div>

                                                    <% if (request.getParameter("success") !=null) { %>
                                                        <div class="success-msg">Grades updated successfully!</div>
                                                        <% } %>
                                                            <% if (request.getParameter("error") !=null) { %>
                                                                <div class="error-msg">Failed to update some grades.
                                                                    Please check input values or server logs.</div>
                                                                <% } %>

                                                                    <form
                                                                        action="${pageContext.request.contextPath}/uploadGrades"
                                                                        method="POST">
                                                                        <input type="hidden" name="allocationId"
                                                                            value="<%= allocation.getAllocationId() %>">
                                                                        <table class="grade-table">
                                                                            <thead>
                                                                                <tr>
                                                                                    <th>Student (Roll No - Name)</th>
                                                                                    <th>Sessional (25)</th>
                                                                                    <th>Mid (35)</th>
                                                                                    <th>Final (40)</th>
                                                                                    <th>Total</th>
                                                                                    <th>Grade</th>
                                                                                    <th>Publish</th>
                                                                                </tr>
                                                                            </thead>
                                                                            <tbody>
                                                                                <% if (studentGrades !=null &&
                                                                                    !studentGrades.isEmpty()) { for
                                                                                    (Grade g : studentGrades) { %>
                                                                                    <tr>
                                                                                        <td>
                                                                                            <div
                                                                                                style="display: flex; flex-direction: column;">
                                                                                                <span
                                                                                                    style="font-weight: 500; color: #1e293b;">
                                                                                                    <%= g.getEnrollment()
                                                                                                        !=null &&
                                                                                                        g.getEnrollment().getStudent()
                                                                                                        !=null ?
                                                                                                        g.getEnrollment().getStudent().getUsername()
                                                                                                        : "Unknown Student"
                                                                                                        %>
                                                                                                </span>
                                                                                                <% if (g.getEnrollment() != null && g.getEnrollment().getStudent() != null && g.getEnrollment().getStudent().getClassName() != null) { %>
                                                                                                    <span class="tag" style="align-self:flex-start; margin-top:3px;"><%= com.cms.util.HtmlUtil.esc(g.getEnrollment().getStudent().getClassName()) %></span>
                                                                                                <% } %>
                                                                                            </div>
                                                                                            <input type="hidden"
                                                                                                name="enrollmentId"
                                                                                                value="<%= g.getEnrollmentId() %>">
                                                                                        </td>
                                                                                        <td>
                                                                                            <input type="number"
                                                                                                step="0.5" max="25"
                                                                                                min="0"
                                                                                                name="sessional_<%= g.getEnrollmentId() %>"
                                                                                                id="sessional_<%= g.getEnrollmentId() %>"
                                                                                                value="<%= g.getSessionalMarks() %>"
                                                                                                class="mark-input"
                                                                                                oninput="updateGrade(<%= g.getEnrollmentId() %>)"
                                                                                                required>
                                                                                        </td>
                                                                                        <td>
                                                                                            <input type="number"
                                                                                                step="0.5" max="35"
                                                                                                min="0"
                                                                                                name="mid_<%= g.getEnrollmentId() %>"
                                                                                                id="mid_<%= g.getEnrollmentId() %>"
                                                                                                value="<%= g.getMidMarks() %>"
                                                                                                class="mark-input"
                                                                                                oninput="updateGrade(<%= g.getEnrollmentId() %>)"
                                                                                                required>
                                                                                        </td>
                                                                                        <td>
                                                                                            <input type="number"
                                                                                                step="0.5" max="40"
                                                                                                min="0"
                                                                                                name="final_<%= g.getEnrollmentId() %>"
                                                                                                id="final_<%= g.getEnrollmentId() %>"
                                                                                                value="<%= g.getFinalMarks() %>"
                                                                                                class="mark-input"
                                                                                                oninput="updateGrade(<%= g.getEnrollmentId() %>)"
                                                                                                required>
                                                                                        </td>
                                                                                        <td class="total-cell"
                                                                                            id="total_<%= g.getEnrollmentId() %>">
                                                                                            <%= g.getTotalMarks() %>
                                                                                        </td>
                                                                                        <td>
                                                                                            <span class="grade-input"
                                                                                                id="grade_<%= g.getEnrollmentId() %>"
                                                                                                style="display:inline-block; min-width:30px; border:none; background:transparent;">
                                                                                                <%= g.getGradeLetter()
                                                                                                    !=null ?
                                                                                                    g.getGradeLetter()
                                                                                                    : "-" %>
                                                                                            </span>
                                                                                        </td>
                                                                                        <td>
                                                                                            <label class="switch">
                                                                                                <input type="checkbox"
                                                                                                    name="publish_<%= g.getEnrollmentId() %>"
                                                                                                    <%=g.isPublished()
                                                                                                    ? "checked" : "" %>>
                                                                                                <span
                                                                                                    class="slider round"></span>
                                                                                            </label>
                                                                                        </td>
                                                                                    </tr>
                                                                                    <% } } else { %>
                                                                                        <tr>
                                                                                            <td colspan="7"
                                                                                                style="text-align: center; padding: 2rem; color: #64748b;">
                                                                                                No students enrolled in
                                                                                                this
                                                                                                course yet.
                                                                                            </td>
                                                                                        </tr>
                                                                                        <% } %>
                                                                            </tbody>
                                                                        </table>
                                                                        <div
                                                                            style="margin-top: 2rem; display: flex; justify-content: flex-end;">
                                                                            <button type="submit" class="btn-save">Save
                                                                                All
                                                                                Grades</button>
                                                                        </div>
                                                                    </form>
                                    </div>
                                </main>

                                <script>
                                    function calculateLetterGrade(total) {
                                        if (total >= 85) return "A";
                                        if (total >= 80) return "A-";
                                        if (total >= 75) return "B+";
                                        if (total >= 70) return "B";
                                        if (total >= 65) return "B-";
                                        if (total >= 61) return "C+";
                                        if (total >= 58) return "C";
                                        if (total >= 55) return "C-";
                                        if (total >= 50) return "D";
                                        return "F";
                                    }

                                    function updateGrade(id) {
                                        const sessional = parseFloat(document.getElementById('sessional_' + id).value) || 0;
                                        const mid = parseFloat(document.getElementById('mid_' + id).value) || 0;
                                        const final = parseFloat(document.getElementById('final_' + id).value) || 0;

                                        const total = sessional + mid + final;
                                        document.getElementById('total_' + id).innerText = total.toFixed(1);
                                        document.getElementById('grade_' + id).innerText = calculateLetterGrade(total);
                                    }
                                </script>
                            </body>

                            </html>