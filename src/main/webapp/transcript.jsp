<%@ page contentType="text/html;charset=UTF-8" language="java"
    import="java.util.*, com.cms.models.Grade, com.cms.dao.GradeDAO" %>
    <!DOCTYPE html>
    <html lang="en">

    <head>
        <meta charset="UTF-8">
        <title>Academic Transcript | CampusCore</title>
        <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
        <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
        <link rel="stylesheet" href="css/styles.css">
        <style>
            .transcript-container {
                max-width: 900px;
                margin: 20px auto;
                background: white;
                padding: 40px;
                box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
            }

            .transcript-header {
                text-align: center;
                border-bottom: 2px solid #333;
                margin-bottom: 30px;
                padding-bottom: 20px;
            }

            .semester-block {
                margin-bottom: 30px;
            }

            .semester-title {
                background: #f4f4f4;
                padding: 10px;
                font-weight: bold;
                border-left: 5px solid var(--primary-color);
                margin-bottom: 10px;
                display: flex;
                justify-content: space-between;
            }

            .summary-box {
                background: #eef2ff;
                padding: 15px;
                border-radius: 8px;
                margin-top: 20px;
                display: flex;
                justify-content: space-around;
                font-weight: bold;
            }

            @media print {

                .sidebar,
                .header,
                .btn-print {
                    display: none !important;
                }

                .main-content {
                    margin-left: 0 !important;
                    padding: 0 !important;
                }

                .transcript-container {
                    box-shadow: none;
                    width: 100%;
                    max-width: none;
                }
            }
        </style>
    </head>

    <body>

        <jsp:include page="sidebar.jsp" />

        <main class="main-content">
            <jsp:include page="header.jsp" />

            <div class="page-container">
                <div style="text-align: right; margin-bottom: 20px;">
                    <button onclick="window.print()" class="btn btn-primary btn-print">🖨️ Print Transcript</button>
                </div>

                <div class="transcript-container">
                    <div class="transcript-header">
                        <img src="${pageContext.request.contextPath}/images/campuscore-icon-192.png" alt="CampusCore logo"
                            style="height: 80px; margin-bottom: 10px; border-radius: 50%;">
                        <h1>Official Academic Transcript</h1>
                        <p>CampusCore &mdash; A Smart Campus Management System</p>
                        <div style="margin-top: 20px; display: grid; grid-template-columns: 1fr 1fr; text-align: left;">
                            <% com.cms.models.User acct = (com.cms.models.User) request.getAttribute("account"); %>
                            <div><strong>Student Name:</strong> <%= acct != null && acct.getProfile() != null ? com.cms.util.HtmlUtil.esc(acct.getProfile().getFullName()) : "" %></div>
                            <div><strong>Roll Number:</strong> <%= acct != null ? com.cms.util.HtmlUtil.esc(acct.getUsername()) : "" %></div>
                            <div><strong>Class:</strong> <%= acct != null && acct.getClassName() != null ? com.cms.util.HtmlUtil.esc(acct.getClassName()) : "Not assigned" %></div>
                            <div><strong>Date of Issue:</strong>
                                <%= new java.text.SimpleDateFormat("dd MMM yyyy").format(new java.util.Date()) %>
                            </div>
                        </div>
                    </div>

                    <% TreeMap<Integer, List<Grade>> semesterGrades = (TreeMap<Integer, List<Grade>>)
                            request.getAttribute("semesterGrades");
                            double totalPoints = 0;
                            int totalCredits = 0;

                            if (semesterGrades != null && !semesterGrades.isEmpty()) {
                            for (Map.Entry<Integer, List<Grade>> entry : semesterGrades.entrySet()) {
                                int semNum = entry.getKey();
                                List<Grade> grades = entry.getValue();

                                    double semTotalPoints = 0;
                                    int semTotalCredits = 0;
                                    %>
                                    <div class="semester-block">
                                        <div class="semester-title">
                                            <span>Semester <%= semNum %> (<%=
                                                        grades.get(0).getEnrollment().getCourseAllocation().getSemester().getName()
                                                        %>)</span>
                                        </div>
                                        <table class="styled-table" style="width: 100%;">
                                            <thead>
                                                <tr>
                                                    <th>Code</th>
                                                    <th>Course Title</th>
                                                    <th>Cr. Hrs</th>
                                                    <th>Grade</th>
                                                    <th>Points</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <% for (Grade g : grades) { int
                                                    credits=g.getEnrollment().getCourseAllocation().getCourse().getCreditHours();
                                                    double points=GradeDAO.getGradePoints(g.getGradeLetter()); double
                                                    qualityPoints=points * credits; semTotalPoints +=qualityPoints;
                                                    semTotalCredits +=credits; totalPoints +=qualityPoints; totalCredits
                                                    +=credits; %>
                                                    <tr>
                                                        <td>
                                                            <%= g.getEnrollment().getCourseAllocation().getCourse().getCourseCode()
                                                                %>
                                                        </td>
                                                        <td>
                                                            <%= g.getEnrollment().getCourseAllocation().getCourse().getCourseName()
                                                                %>
                                                        </td>
                                                        <td>
                                                            <%= credits %>
                                                        </td>
                                                        <td><strong>
                                                                <%= g.getGradeLetter() %>
                                                            </strong></td>
                                                        <td>
                                                            <%= String.format("%.2f", points) %>
                                                        </td>
                                                    </tr>
                                                    <% } %>
                                            </tbody>
                                        </table>
                                        <div
                                            style="text-align: right; margin-top: 5px; font-weight: bold; color: #555;">
                                            Semester GPA: <%= semTotalCredits> 0 ? String.format("%.2f",
                                                semTotalPoints/semTotalCredits) : "0.00" %>
                                        </div>
                                    </div>
                                    <% } } else { %>
                                        <div style="text-align: center; padding: 40px; color: #666;">
                                            <p>No published grades found yet. Your transcript will be available once
                                                your assessment results are finalized.</p>
                                        </div>
                                        <% } %>

                                            <% if (totalCredits> 0) { %>
                                                <div class="summary-box">
                                                    <span>Total Credit Hours: <%= totalCredits %></span>
                                                    <span>Cumulative GPA (CGPA): <%= String.format("%.2f",
                                                            totalPoints/totalCredits) %></span>
                                                </div>
                                                <% } %>

                                                    <div
                                                        style="margin-top: 50px; border-top: 1px solid #ddd; padding-top: 20px; font-size: 0.8em; color: #777; text-align: center;">
                                                        <p>This is a computer-generated document and does not require a
                                                            physical signature.</p>
                                                        <p>&copy; <%=
                                                                java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                                                                %> CampusCore</p>
                                                    </div>
                </div>
            </div>
        </main>

    </body>

    </html>