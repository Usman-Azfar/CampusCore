<%@ page import="java.util.List, com.cms.models.Grade" %>
    <!DOCTYPE html>
    <html lang="en">

    <head>
        <meta charset="UTF-8">
        <title>Gradebook | CampusCore</title>
        <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
        <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
        <link rel="stylesheet" href="css/styles.css">
    </head>

    <body>
        <jsp:include page="sidebar.jsp" />

        <main class="main-content">
            <jsp:include page="header.jsp" />

            <div class="page-container">
                <div class="card">
                    <div class="card-header">
                        <h3 class="card-title">My Result Card</h3>
                    </div>

                    <table class="styled-table">
                        <thead>
                            <tr>
                                <th>Course</th>
                                <th>Instructor</th>
                                <th>Sessional (25)</th>
                                <th>Mids (35)</th>
                                <th>Finals (40)</th>
                                <th>Total (100)</th>
                                <th>Grade</th>
                            </tr>
                        </thead>
                        <tbody>
                            <% List<Grade> grades = (List<Grade>) request.getAttribute("grades");
                                    if (grades != null && !grades.isEmpty()) {
                                    for(Grade g : grades) {
                                    %>
                                    <tr>
                                        <td>
                                            <strong>
                                                <%= g.getEnrollment().getCourseAllocation().getCourse().getCourseName()
                                                    %>
                                            </strong>
                                        </td>
                                        <td>
                                            <%= g.getEnrollment().getCourseAllocation().getTeacher().getUsername() %>
                                        </td>
                                        <td>
                                            <%= g.isPublished() ? g.getSessionalMarks() : "-" %>
                                        </td>
                                        <td>
                                            <%= g.isPublished() ? g.getMidMarks() : "-" %>
                                        </td>
                                        <td>
                                            <%= g.isPublished() ? g.getFinalMarks() : "-" %>
                                        </td>
                                        <td><strong>
                                                <%= g.isPublished() ? g.getTotalMarks() : "-" %>
                                            </strong></td>
                                        <td>
                                            <% if (g.isPublished()) { %>
                                                <span
                                                    style="background:var(--primary-color); color:white; padding:2px 8px; border-radius:4px;">
                                                    <%= g.getGradeLetter() !=null ? g.getGradeLetter() : "-" %>
                                                </span>
                                                <% } else { %>
                                                    <span
                                                        style="color:#d97706; font-style:italic; font-size:0.9em;">Result
                                                        Awaited</span>
                                                    <% } %>
                                        </td>
                                    </tr>
                                    <% } } else { %>
                                        <tr>
                                            <td colspan="7" style="text-align:center;">No grades published yet.</td>
                                        </tr>
                                        <% } %>
                        </tbody>
                    </table>
                </div>
            </div>
        </main>
    </body>

    </html>