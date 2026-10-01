<%@ page import="java.util.List, com.cms.models.Attendance" %>
    <!DOCTYPE html>
    <html lang="en">

    <head>
        <meta charset="UTF-8">
        <title>Attendance | CampusCore</title>
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
                        <h3 class="card-title">
                            <% if (request.getAttribute("attendanceList") !=null) { %>
                                Course Attendance Detail <a href="attendance" style="float:right; font-size:0.8em;">Back
                                    to Courses</a>
                                <% } else { %>
                                    Select Course for Attendance
                                    <% } %>
                        </h3>
                    </div>

                    <table class="styled-table">
                        <% if (request.getAttribute("attendanceList") !=null) { %>
                            <!-- SHOW ATTENDANCE DETAIL -->
                            <thead>
                                <tr>
                                    <th>Lecture #</th>
                                    <th>Date</th>
                                    <th>Status</th>
                                </tr>
                            </thead>
                            <tbody>
                                <% List<Attendance> attendanceList = (List<Attendance>)
                                        request.getAttribute("attendanceList");
                                        if (attendanceList != null && !attendanceList.isEmpty()) {
                                        for(Attendance att : attendanceList) { %>
                                        <tr>
                                            <td>Lecture <%= att.getLectureNumber() %>
                                            </td>
                                            <td>
                                                <%= att.getDate() %>
                                            </td>
                                            <td>
                                                <% if("Present".equalsIgnoreCase(att.getStatus())) { %>
                                                    <span
                                                        style="color: var(--success-color); font-weight:bold;">Present</span>
                                                    <% } else if("Absent".equalsIgnoreCase(att.getStatus())) { %>
                                                        <span
                                                            style="color: var(--danger-color); font-weight:bold;">Absent</span>
                                                        <% } else { %>
                                                            <span style="color: #666;">
                                                                <%= att.getStatus() %>
                                                            </span>
                                                            <% } %>
                                            </td>
                                        </tr>
                                        <% } } else { %>
                                            <tr>
                                                <td colspan="3" style="text-align:center;">No attendance records found.
                                                </td>
                                            </tr>
                                            <% } %>
                            </tbody>
                            <% } else { %>
                                <!-- SHOW COURSE SELECTION LIST -->
                                <thead>
                                    <tr>
                                        <th>Course Code</th>
                                        <th>Course Name</th>
                                        <th>Teacher</th>
                                        <th>Action</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <% java.util.List<com.cms.models.Enrollment> enrollments = (java.util.List
                                        <com.cms.models.Enrollment>) request.getAttribute("enrollments");
                                            if (enrollments != null && !enrollments.isEmpty()) {
                                            for(com.cms.models.Enrollment enr : enrollments) {
                                            %>
                                            <tr>
                                                <td>
                                                    <%= enr.getCourseAllocation().getCourse().getCourseCode() %>
                                                </td>
                                                <td>
                                                    <%= enr.getCourseAllocation().getCourse().getCourseName() %>
                                                </td>
                                                <td>
                                                    <%= enr.getCourseAllocation().getTeacher().getUsername() %>
                                                </td>
                                                <td>
                                                    <a href="attendance?enrollmentId=<%= enr.getEnrollmentId() %>"
                                                        class="btn btn-primary"
                                                        style="padding: 5px 10px; font-size: 0.8em;">View Attendance</a>
                                                </td>
                                            </tr>
                                            <% } } else { %>
                                                <tr>
                                                    <td colspan="4" style="text-align:center;">You are not enrolled in
                                                        any courses.</td>
                                                </tr>
                                                <% } %>
                                </tbody>
                                <% } %>
                    </table>
                </div>
            </div>
        </main>
    </body>

    </html>