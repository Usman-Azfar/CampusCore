<%@ page contentType="text/html;charset=UTF-8" language="java"
    import="com.cms.models.User, com.cms.models.Profile, com.cms.models.Enrollment, com.cms.models.CourseAllocation, java.util.List"
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Dashboard | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <style>
        .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 14px; margin-bottom: 20px; }
        .tile { display: flex; flex-direction: column; gap: 4px; background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 16px 18px; text-decoration: none; color: inherit; box-shadow: 0 2px 4px rgba(0,0,0,0.04); transition: transform 0.2s; }
        .tile:hover { transform: translateY(-2px); }
        .tile-value { font-size: 1.6rem; font-weight: 700; color: var(--primary-color); }
        .tile-label { color: #6b7280; font-size: 0.9em; }
        .todo { list-style: none; padding: 0; margin: 0; }
        .todo li { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; padding: 10px 0; border-top: 1px solid #e5e7eb; }
        .todo li:first-child { border-top: none; }
        .todo li > span { flex: 1 1 320px; }
        .todo li.urgent span { color: #b91c1c; font-weight: 600; }
        .tile-bad { color: #b91c1c !important; }
        .tile-warn { color: #b45309 !important; font-size: 1.3rem !important; }
        .low { color: #b91c1c; font-weight: 700; }
        .pending { color: #b45309; font-size: 0.9em; font-style: italic; white-space: nowrap; }
        .letter { display: inline-block; min-width: 34px; text-align: center; padding: 2px 8px; border-radius: 4px; font-weight: 700; background: var(--primary-color); color: #fff; }
        .letter.fail { background: #b91c1c; }
        .letter.withdrawn { background: #6b7280; }
        .row-actions { white-space: nowrap; }
        .row-actions .btn { margin: 2px 0; }
        .admin-grid { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); gap: 20px; align-items: start; }
        .admin-grid > div > .card + .card { margin-top: 20px; }
        @media (max-width: 1100px) { .admin-grid { grid-template-columns: minmax(0, 1fr); } }
        .progress { height: 10px; background: #e5e7eb; border-radius: 999px; overflow: hidden; }
        .progress span { display: block; height: 100%; background: #10b981; }
        .facts-list { display: grid; grid-template-columns: 1fr auto; gap: 8px 16px; margin: 0; }
        .facts-list dt { color: #6b7280; }
        .facts-list dd { margin: 0; font-weight: 600; text-align: right; }
        .facts-list dd a { color: inherit; text-decoration: none; }
        .facts-list dd a:hover { color: var(--primary-color); text-decoration: underline; }
        .nowrap { white-space: nowrap; }
        .ann-mini { padding: 10px 0; border-top: 1px solid #e5e7eb; }
        .ann-mini:first-of-type { border-top: none; }
        .ann-mini-text { color: #4b5563; margin-top: 4px; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; white-space: pre-wrap; }
    </style>
</head>

<body>

<!-- Sidebar -->
<jsp:include page="sidebar.jsp" />

<!-- Main Content -->
<main class="main-content">

    <!-- Header -->
    <jsp:include page="header.jsp" />

    <div class="page-container">

        <%
            Profile profile = (Profile) request.getAttribute("profile");
            User user = (User) session.getAttribute("user");
            com.cms.models.ClassSemester currentTerm = (com.cms.models.ClassSemester) request.getAttribute("currentTerm");
        %>

        <div class="card">
            <h2 class="card-title">
                Welcome back,
                <%= com.cms.util.HtmlUtil.esc(profile != null ? profile.getFullName() : user.getUsername()) %>!
            </h2>

            <% User account = (User) request.getAttribute("account");
               if (account != null && !"ADMIN".equals(account.getRole())) { %>
                <p style="margin-top: 8px;">
                    <% if (account.getAffiliation() != null) { %>
                        <span class="tag <%= "TEACHER".equals(account.getRole()) ? "tag-department" : "" %>">
                            <%= "TEACHER".equals(account.getRole()) ? "Department: " : "Class: " %><%= com.cms.util.HtmlUtil.esc(account.getAffiliation()) %>
                        </span>
                    <% } else { %>
                        <span class="tag tag-missing"><%= "TEACHER".equals(account.getRole()) ? "No department assigned" : "No class assigned" %></span>
                    <% } %>
                </p>
            <% } %>

            <% if ("STUDENT".equals(user.getRole())) { %>
                <p style="margin-top: 5px; color: #666;">
                    <% if (currentTerm != null) { %>
                        Current Semester: <strong><%= com.cms.util.HtmlUtil.esc(com.cms.models.CourseAttendance.label(currentTerm.getTermName(), currentTerm.getSemesterNumber())) %></strong>
                        <% if (!currentTerm.getTermDateRange().isEmpty()) { %><span class="muted">(<%= currentTerm.getTermDateRange() %>)</span><% } %>
                    <% } else { %>
                        Current Semester: <strong>not set</strong> &mdash; your class is not in a semester right now.
                    <% } %>
                </p>
            <% } %>
        </div>

        <% if ("STUDENT".equals(user.getRole())) { %>

        <!-- ================= STUDENT AREA ================= -->
        <%
            List<com.cms.models.CourseAttendance> termCourses = (List<com.cms.models.CourseAttendance>) request.getAttribute("termCourses");
            if (termCourses == null) termCourses = java.util.Collections.emptyList();
            java.util.Map<Integer, com.cms.models.Grade> results = (java.util.Map<Integer, com.cms.models.Grade>) request.getAttribute("results");
            if (results == null) results = java.util.Collections.emptyMap();
            Integer termId = (Integer) request.getAttribute("termId");
            Integer termAttendance = (Integer) request.getAttribute("termAttendance");
            int termPct = termAttendance == null ? -1 : termAttendance;
            String cgpa = (String) request.getAttribute("cgpa");
            Integer unpaidCount = (Integer) request.getAttribute("unpaidCount");
            java.math.BigDecimal feesDue = (java.math.BigDecimal) request.getAttribute("feesDue");
            Integer unreadStudent = (Integer) request.getAttribute("unreadMessages");
            Integer otherCourseCount = (Integer) request.getAttribute("otherCourseCount");
            List<String[]> attention = (List<String[]>) request.getAttribute("attention");
            if (attention == null) attention = java.util.Collections.emptyList();
            String attentionEmpty = "All caught up: no fees due, your attendance is fine and nothing is waiting for you.";
            String attentionLink = null, attentionLinkLabel = null;
            int unpaidN = unpaidCount == null ? 0 : unpaidCount, unreadN = unreadStudent == null ? 0 : unreadStudent;
            boolean lowTerm = termPct >= 0 && termPct < com.cms.util.AttendanceRules.LOW_ATTENDANCE_PERCENT;
            String termQuery = termId == null ? "" : "?term=" + termId;
        %>
        <div class="tiles">
            <a class="tile" href="attendance<%= termQuery %>"><span class="tile-value"><%= termCourses.size() %></span><span class="tile-label">Course<%= termCourses.size() == 1 ? "" : "s" %> this semester</span></a>
            <a class="tile" href="attendance<%= termQuery %>"><span class="tile-value <%= lowTerm ? "tile-bad" : "" %>"><%= termPct < 0 ? "-" : termPct + "%" %></span><span class="tile-label">Attendance this semester</span></a>
            <a class="tile" href="transcript"><span class="tile-value"><%= cgpa == null ? "-" : cgpa %></span><span class="tile-label">CGPA</span></a>
            <a class="tile" href="challans"><span class="tile-value <%= unpaidN > 0 ? "tile-warn" : "" %>"><%= unpaidN == 0 ? "None" : "PKR " + new java.text.DecimalFormat("#,##0").format(feesDue) %></span><span class="tile-label"><%= unpaidN == 0 ? "Fees due" : unpaidN + " unpaid challan" + (unpaidN == 1 ? "" : "s") %></span></a>
            <a class="tile" href="messages"><span class="tile-value"><%= unreadN %></span><span class="tile-label">Unread message<%= unreadN == 1 ? "" : "s" %></span></a>
        </div>

        <%@ include file="/WEB-INF/jspf/dashboard_attention.jspf" %>

        <div class="card">
            <div class="card-header" style="display:flex; justify-content:space-between; align-items:center; gap:10px; flex-wrap:wrap;">
                <h3 class="card-title">This semester at a glance<%= termCourses.isEmpty() ? "" : ": " + com.cms.util.HtmlUtil.esc(termCourses.get(0).getTermLabel()) %></h3>
                <span style="display:flex; gap:8px; flex-wrap:wrap;">
                    <a href="gradebook" class="btn btn-secondary btn-sm">Gradebook</a>
                    <a href="attendance" class="btn btn-secondary btn-sm">All attendance</a>
                </span>
            </div>
            <% if (termCourses.isEmpty()) { %>
                <p>You are not enrolled in any courses yet.</p>
            <% } else { %>
            <div class="table-scroll">
            <table class="styled-table">
                <thead><tr><th>Course</th><th>Teacher</th><th>Lectures</th><th>Attendance</th><th>Result</th><th></th></tr></thead>
                <tbody>
                <% for (com.cms.models.CourseAttendance c : termCourses) {
                       int p = c.getPercent();
                       com.cms.models.Grade g = results.get(c.getEnrollmentId());
                %>
                    <tr>
                        <td><strong><%= com.cms.util.HtmlUtil.esc(c.getCourseCode()) %></strong> <%= com.cms.util.HtmlUtil.esc(c.getCourseName()) %>
                            <br><span class="muted"><%= com.cms.util.HtmlUtil.esc(c.getTermLabel()) %></span></td>
                        <td><%= com.cms.util.HtmlUtil.esc(c.getTeacherName()) %></td>
                        <td><%= c.getLecturesHeld() %></td>
                        <td><% if (p < 0) { %><span class="muted">No lectures yet</span><% } else { %><span class="<%= c.isLow() ? "low" : "" %>"><%= p %>%</span> <span class="muted">(<%= c.getPresent() %>/<%= c.getMarked() %>)</span><% } %></td>
                        <td><% com.cms.models.Grade rg = g; %><%@ include file="/WEB-INF/jspf/result_cell.jspf" %><% if (g != null && com.cms.models.Grade.RESULT_IN_PROGRESS.equals(g.getResultStatus())) { %> <span class="muted">(<%= com.cms.util.GradeRules.format(g.getTotalMarks()) %> so far)</span><% } %></td>
                        <td class="row-actions">
                            <a class="btn btn-sm btn-success" href="attendance?enrollmentId=<%= c.getEnrollmentId() %>">Attendance</a>
                            <a class="btn btn-sm" style="background:#4682B4; color:#fff;" href="announcements?allocationId=<%= c.getAllocationId() %>">Announcements</a>
                            <% if (c.getTeacherId() > 0) { %><a class="btn btn-sm" style="background:#0f172a; color:#fff;" href="messages?to=<%= c.getTeacherId() %>#compose">Message Teacher</a><% } %>
                        </td>
                    </tr>
                <% } %>
                </tbody>
            </table>
            </div>
            <% if (otherCourseCount != null && otherCourseCount > 0) { %>
                <p class="muted" style="margin-top:8px;">Courses from other terms (<%= otherCourseCount %>) are in your <a href="attendance">Attendance</a> and <a href="gradebook">Gradebook</a>.</p>
            <% } %>
            <% } %>
        </div>

        <%@ include file="/WEB-INF/jspf/dashboard_announcements.jspf" %>

        <% } else if ("TEACHER".equals(user.getRole())) { %>

        <!-- ================= TEACHER AREA ================= -->
        <%
            List<CourseAllocation> currentCourses = (List<CourseAllocation>) request.getAttribute("currentCourses");
            List<String[]> attention = (List<String[]>) request.getAttribute("attention");
            Integer studentCount = (Integer) request.getAttribute("studentCount");
            Integer unreadMessages = (Integer) request.getAttribute("unreadMessages");
            if (currentCourses == null) currentCourses = java.util.Collections.emptyList();
            if (attention == null) attention = java.util.Collections.emptyList();
            int unread = unreadMessages == null ? 0 : unreadMessages;
        %>
        <div class="tiles">
            <a class="tile" href="myCourses"><span class="tile-value"><%= currentCourses.size() %></span><span class="tile-label">Current course<%= currentCourses.size() == 1 ? "" : "s" %></span></a>
            <a class="tile" href="myCourses"><span class="tile-value"><%= studentCount == null ? 0 : studentCount %></span><span class="tile-label">Students enrolled</span></a>
            <a class="tile" href="messages"><span class="tile-value"><%= unread %></span><span class="tile-label">Unread message<%= unread == 1 ? "" : "s" %></span></a>
            <a class="tile" href="announcements"><span class="tile-value"><i class="fas fa-bullhorn"></i></span><span class="tile-label">Announcements</span></a>
        </div>

        <% String attentionEmpty = "All caught up: attendance is marked and no grades are waiting to be published.";
           String attentionLink = "myCourses", attentionLinkLabel = "Open Course List"; %>
        <%@ include file="/WEB-INF/jspf/dashboard_attention.jspf" %>

        <% if (!currentCourses.isEmpty()) { %>
        <div class="card">
            <h3 class="card-title">This term at a glance</h3>
            <div class="table-scroll">
            <table class="styled-table">
                <thead><tr><th>Course</th><th>Term</th><th>Students</th><th>Lectures</th><th>Grades published</th></tr></thead>
                <tbody>
                <% for (CourseAllocation ca : currentCourses) { %>
                    <tr>
                        <td><strong><%= com.cms.util.HtmlUtil.esc(ca.getCourse().getCourseCode()) %></strong> <%= com.cms.util.HtmlUtil.esc(ca.getCourse().getCourseName()) %></td>
                        <td><%= com.cms.util.HtmlUtil.esc(ca.getSemester().getName()) %></td>
                        <td><%= ca.getEnrolledCount() %></td>
                        <td><%= ca.getLectureCount() %><% if (ca.getLastLectureDate() != null) { %> <span class="muted">(last <%= com.cms.util.AttendanceRules.format(ca.getLastLectureDate().toLocalDate()) %>)</span><% } %></td>
                        <td><%= ca.getPublishedCount() %>/<%= ca.getEnrolledCount() %></td>
                    </tr>
                <% } %>
                </tbody>
            </table>
            </div>
            <p class="muted" style="margin-top:8px;">Mark attendance, upload grades, post announcements and message students from the <a href="myCourses">Course List</a>.</p>
        </div>
        <% } %>

        <%@ include file="/WEB-INF/jspf/dashboard_announcements.jspf" %>

        <% } else if ("ADMIN".equals(user.getRole())) { %>

        <!-- ================= ADMIN AREA ================= -->
        <%
            com.cms.dao.AdminOverviewDAO.Overview ov = (com.cms.dao.AdminOverviewDAO.Overview) request.getAttribute("overview");
            if (ov == null) ov = new com.cms.dao.AdminOverviewDAO.Overview();
            List<com.cms.dao.AdminOverviewDAO.ClassRow> classRows = (List<com.cms.dao.AdminOverviewDAO.ClassRow>) request.getAttribute("classRows");
            if (classRows == null) classRows = java.util.Collections.emptyList();
            List<String[]> attention = (List<String[]>) request.getAttribute("attention");
            if (attention == null) attention = java.util.Collections.emptyList();
            String attentionEmpty = "All caught up: no requests, payment proofs or tickets are waiting, and every class and course is set up.";
            String attentionLink = null, attentionLinkLabel = null;
            Integer unreadAdmin = (Integer) request.getAttribute("unreadMessages");
            int unreadA = unreadAdmin == null ? 0 : unreadAdmin;
            java.text.DecimalFormat pkr = new java.text.DecimalFormat("#,##0");
            int paidPct = ov.termChallans == 0 ? 0 : (int) Math.round(ov.termPaid * 100.0 / ov.termChallans);
        %>
        <div class="tiles">
            <a class="tile" href="manageStudents"><span class="tile-value"><%= ov.activeStudents %></span><span class="tile-label">Active students</span></a>
            <a class="tile" href="manageTeachers"><span class="tile-value"><%= ov.activeTeachers %></span><span class="tile-label">Active teachers</span></a>
            <a class="tile" href="manageCourses"><span class="tile-value"><%= ov.currentOfferings %></span><span class="tile-label">Course offerings this term</span></a>
            <a class="tile" href="uploadChallan"><span class="tile-value <%= ov.termOutstanding.signum() > 0 ? "tile-warn" : "" %>"><%= ov.termOutstanding.signum() == 0 ? "None" : "PKR " + pkr.format(ov.termOutstanding) %></span><span class="tile-label">Fees outstanding this term</span></a>
            <a class="tile" href="messages"><span class="tile-value"><%= unreadA %></span><span class="tile-label">Unread message<%= unreadA == 1 ? "" : "s" %></span></a>
        </div>

        <%@ include file="/WEB-INF/jspf/dashboard_attention.jspf" %>

        <div class="admin-grid">
            <div class="card">
                <div class="card-header" style="display:flex; justify-content:space-between; align-items:center; gap:10px; flex-wrap:wrap;">
                    <h3 class="card-title">Classes this semester</h3>
                    <a href="manageSemesters" class="btn btn-secondary btn-sm">Manage semesters</a>
                </div>
                <% if (classRows.isEmpty()) { %>
                    <p class="muted">No classes yet. <a href="manageClasses">Add a class</a>.</p>
                <% } else { %>
                <div class="table-scroll">
                <table class="styled-table">
                    <thead><tr><th>Class</th><th>Current semester</th><th>Students</th><th title="Courses offered to the class that have a teacher this term">Courses</th></tr></thead>
                    <tbody>
                    <% for (com.cms.dao.AdminOverviewDAO.ClassRow c : classRows) { %>
                        <tr>
                            <td><strong><%= com.cms.util.HtmlUtil.esc(c.className) %></strong></td>
                            <td class="nowrap"><% if (c.termName != null) { %><%= com.cms.util.HtmlUtil.esc(com.cms.models.CourseAttendance.label(c.termName, c.semesterNumber)) %><% } else { %><span class="tag tag-missing">Not in a semester</span><% } %></td>
                            <td><%= c.students %></td>
                            <td><%= c.termName == null ? "-" : String.valueOf(c.offerings) %></td>
                        </tr>
                    <% } %>
                    </tbody>
                </table>
                </div>
                <% } %>
            </div>

            <div>
                <div class="card">
                    <div class="card-header" style="display:flex; justify-content:space-between; align-items:center; gap:10px; flex-wrap:wrap;">
                        <h3 class="card-title">Fee collection this term</h3>
                        <a href="uploadChallan" class="btn btn-secondary btn-sm">Accounts / Challan</a>
                    </div>
                    <% if (ov.termChallans == 0) { %>
                        <p class="muted">No challans issued for the current term yet.</p>
                    <% } else { %>
                        <div class="progress" role="img" aria-label="<%= paidPct %>% of challans paid"><span style="width:<%= paidPct %>%"></span></div>
                        <p class="muted" style="margin:6px 0 12px;"><%= ov.termPaid %> of <%= ov.termChallans %> challans paid (<%= paidPct %>%)</p>
                        <dl class="facts-list">
                            <dt>Collected</dt><dd>PKR <%= pkr.format(ov.termCollected) %></dd>
                            <dt>Outstanding</dt><dd>PKR <%= pkr.format(ov.termOutstanding) %></dd>
                            <dt>Payment proofs to review</dt><dd><%= ov.proofsToReview %></dd>
                            <dt>Overdue (all terms)</dt><dd class="<%= ov.overdueChallans > 0 ? "low" : "" %>"><%= ov.overdueChallans %><%= ov.overdueChallans > 0 ? " &middot; PKR " + pkr.format(ov.overdueAmount) : "" %></dd>
                        </dl>
                    <% } %>
                </div>

                <div class="card">
                    <h3 class="card-title">Campus at a glance</h3>
                    <dl class="facts-list">
                        <dt>Departments</dt><dd><a href="manageClasses"><%= ov.departments %></a></dd>
                        <dt>Classes</dt><dd><a href="manageClasses"><%= ov.classes %></a></dd>
                        <dt>Courses</dt><dd><a href="manageCourses"><%= ov.courses %></a></dd>
                        <dt>Enrollments this term</dt><dd><%= ov.currentEnrollments %></dd>
                        <dt>Inactive accounts</dt><dd><%= ov.inactiveAccounts %></dd>
                    </dl>
                </div>
            </div>
        </div>

        <%@ include file="/WEB-INF/jspf/dashboard_announcements.jspf" %>

        <% } %>

    </div>
</main>

</body>
</html>
