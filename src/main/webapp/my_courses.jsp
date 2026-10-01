<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Collections, com.cms.models.CourseAllocation, com.cms.models.Semester, com.cms.util.AttendanceRules, com.cms.util.GradeRules, com.cms.util.HtmlUtil" %>
<%
    List<CourseAllocation> currentCourses = (List<CourseAllocation>) request.getAttribute("currentCourses");
    List<CourseAllocation> pastCourses = (List<CourseAllocation>) request.getAttribute("pastCourses");
    if (currentCourses == null) currentCourses = Collections.emptyList();
    if (pastCourses == null) pastCourses = Collections.emptyList();
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Course List | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .course-box { border: 1px solid #e5e7eb; border-radius: 12px; overflow: hidden; background: #fff; box-shadow: 0 4px 6px rgba(0,0,0,0.05); margin-top: 16px; }
        .course-box-head { background: linear-gradient(135deg, var(--primary-color), var(--secondary-color)); color: #fff; padding: 16px 20px; }
        .course-box-head h3 { margin: 0; font-size: 1.15rem; }
        .course-box-head .sub { opacity: 0.9; font-size: 0.9em; margin-top: 4px; }
        .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 10px; padding: 14px 20px 0; }
        .stat { background: #f8fafc; border: 1px solid #e5e7eb; border-radius: 8px; padding: 10px 12px; }
        .stat .label { font-size: 0.78em; text-transform: uppercase; letter-spacing: 0.03em; color: #6b7280; }
        .stat .value { font-size: 1.05rem; font-weight: 700; color: #111827; margin-top: 2px; }
        .stat .note { font-size: 0.8em; color: #6b7280; margin-top: 2px; }
        .actions { display: flex; flex-wrap: wrap; gap: 10px; padding: 14px 20px 18px; }
        .actions .btn { flex: 1 1 150px; text-align: center; }
        .closed { color: #b45309; }
    </style>
</head>

<body>
<jsp:include page="sidebar.jsp" />

<main class="main-content">
    <jsp:include page="header.jsp" />

    <div class="page-container">
        <% String errMsg = (String) request.getAttribute("errorMessage");
           if (errMsg != null) { %>
            <div class="alert alert-error"><%= HtmlUtil.esc(errMsg) %></div>
        <% } %>

        <div class="card">
            <h2>Course List</h2>
            <p class="muted">Every course assigned to you. Attendance can be marked within <%= AttendanceRules.EDIT_WINDOW_DAYS %> days of a lecture;
                grades can be changed until <%= GradeRules.EDIT_DAYS_AFTER_TERM %> days after the term ends.</p>

            <% if (currentCourses.isEmpty()) { %>
                <p style="margin-top:14px;">You have no courses in a current term.</p>
            <% } %>
            <% for (CourseAllocation ca : currentCourses) {
                   Semester term = ca.getSemester();
                   int id = ca.getAllocationId();
                   boolean attendanceOpen = AttendanceRules.isOpen(term);
                   boolean gradesOpen = GradeRules.isEditable(term);
                   java.time.LocalDate gradeLock = GradeRules.lockDate(term);
            %>
            <div class="course-box">
                <div class="course-box-head">
                    <h3><%= HtmlUtil.esc(ca.getCourse().getCourseName()) %></h3>
                    <div class="sub"><%= HtmlUtil.esc(ca.getCourse().getCourseCode()) %> &middot; <%= HtmlUtil.esc(term.getName()) %>
                        <%= term.getDateRange().isEmpty() ? "" : "&middot; " + HtmlUtil.esc(term.getDateRange()) %>
                        &middot; <%= ca.getCourse().getCreditHours() %> credit hour<%= ca.getCourse().getCreditHours() == 1 ? "" : "s" %></div>
                </div>
                <div class="stats">
                    <div class="stat"><div class="label">Students</div><div class="value"><%= ca.getEnrolledCount() %></div></div>
                    <div class="stat"><div class="label">Lectures</div><div class="value"><%= ca.getLectureCount() %>/<%= AttendanceRules.MAX_LECTURES %></div>
                        <div class="note <%= attendanceOpen ? "" : "closed" %>"><%= ca.getLastLectureDate() != null ? "Last: " + AttendanceRules.format(ca.getLastLectureDate().toLocalDate()) : "None marked yet" %><%= attendanceOpen ? "" : " &middot; closed" %></div></div>
                    <div class="stat"><div class="label">Grades</div><div class="value"><%= ca.getPublishedCount() %>/<%= ca.getEnrolledCount() %> published</div>
                        <div class="note <%= gradesOpen ? "" : "closed" %>"><%= ca.getCompleteCount() %> fully marked<%= gradeLock == null ? "" : gradesOpen ? " &middot; editable until " + GradeRules.formatDate(gradeLock) : " &middot; locked" %></div></div>
                    <div class="stat"><div class="label">Announcements</div><div class="value"><%= ca.getAnnouncementCount() %></div></div>
                </div>
                <div class="actions">
                    <a class="btn btn-success" href="manageAttendance?allocationId=<%= id %>"><%= attendanceOpen ? "Mark Attendance" : "View Attendance" %></a>
                    <a class="btn" style="background:#fbbf24; color:#1f2937;" href="uploadGrades?allocationId=<%= id %>"><%= gradesOpen ? "Upload Grades" : "View Grades" %></a>
                    <a class="btn" style="background:#4682B4; color:#fff;" href="manageAnnouncements?allocationId=<%= id %>">Announcements</a>
                    <a class="btn" style="background:#0f172a; color:#fff;" href="messages?allocationId=<%= id %>#compose">Message Students</a>
                </div>
            </div>
            <% } %>
        </div>

        <% if (!pastCourses.isEmpty()) { %>
        <div class="card">
            <h3>Past courses</h3>
            <p class="muted">Courses whose term has ended.</p>
            <div class="table-scroll">
            <table class="styled-table">
                <thead><tr><th>Course</th><th>Term</th><th>Students</th><th>Lectures</th><th>Published</th><th></th></tr></thead>
                <tbody>
                <% for (CourseAllocation ca : pastCourses) { int id = ca.getAllocationId(); boolean gradesOpen = GradeRules.isEditable(ca.getSemester()); %>
                    <tr>
                        <td><strong><%= HtmlUtil.esc(ca.getCourse().getCourseCode()) %></strong> <%= HtmlUtil.esc(ca.getCourse().getCourseName()) %></td>
                        <td><%= HtmlUtil.esc(ca.getSemester().getName()) %><br><span class="muted"><%= HtmlUtil.esc(ca.getSemester().getDateRange()) %></span></td>
                        <td><%= ca.getEnrolledCount() %></td>
                        <td><%= ca.getLectureCount() %></td>
                        <td><%= ca.getPublishedCount() %>/<%= ca.getEnrolledCount() %><% if (gradesOpen) { %><br><span class="muted">Editable until <%= GradeRules.formatDate(GradeRules.lockDate(ca.getSemester())) %></span><% } %></td>
                        <td style="white-space:nowrap;">
                            <a class="btn btn-secondary btn-sm" href="manageAttendance?allocationId=<%= id %>">Attendance</a>
                            <a class="btn <%= gradesOpen ? "btn-primary" : "btn-secondary" %> btn-sm" href="uploadGrades?allocationId=<%= id %>">Grades</a>
                            <a class="btn btn-secondary btn-sm" href="manageAnnouncements?allocationId=<%= id %>">Announcements</a>
                        </td>
                    </tr>
                <% } %>
                </tbody>
            </table>
            </div>
        </div>
        <% } %>
    </div>
</main>
</body>
</html>
