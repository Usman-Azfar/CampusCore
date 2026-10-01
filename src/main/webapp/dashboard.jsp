<%@ page contentType="text/html;charset=UTF-8" language="java"
    import="com.cms.models.User, com.cms.models.Profile, com.cms.models.Enrollment, com.cms.models.CourseAllocation, java.util.List"
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <title>Dashboard | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
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
            List<Enrollment> enrollments = (List<Enrollment>) request.getAttribute("enrollments");
            List<CourseAllocation> allocations = (List<CourseAllocation>) request.getAttribute("allocations");
            Integer semesterNum = (Integer) request.getAttribute("currentSemester");
        %>

        <div class="card">
            <h2 class="card-title">
                Welcome back,
                <%= profile != null ? profile.getFullName() : user.getUsername() %>!
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

            <% if (semesterNum != null) { %>
                <p style="margin-top: 5px; color: #666;">
                    Current Semester: <strong><%= semesterNum %></strong>
                </p>
            <% } %>
        </div>

        <% if ("STUDENT".equals(user.getRole())) { %>

        <!-- ================= STUDENT AREA ================= -->
        <% List<com.cms.models.Challan> unpaidChallans = (List<com.cms.models.Challan>) request.getAttribute("unpaidChallans");
           if (unpaidChallans != null && !unpaidChallans.isEmpty()) {
               java.math.BigDecimal due = java.math.BigDecimal.ZERO;
               int overdueCount = 0;
               for (com.cms.models.Challan c : unpaidChallans) {
                   if (c.getAmount() != null) due = due.add(c.getAmount());
                   if (c.isOverdue()) overdueCount++;
               }
               com.cms.models.Challan next = null; // the unpaid challan due soonest
               for (com.cms.models.Challan c : unpaidChallans)
                   if (c.getDueDate() != null && (next == null || next.getDueDate() == null || c.getDueDate().before(next.getDueDate()))) next = c;
               if (next == null) next = unpaidChallans.get(0);
        %>
            <div class="alert <%= overdueCount > 0 ? "alert-error" : "" %>" style="<%= overdueCount > 0 ? "" : "background:#fffbeb; color:#92400e;" %>">
                <strong>Fee reminder:</strong>
                <%= unpaidChallans.size() %> unpaid challan<%= unpaidChallans.size() == 1 ? "" : "s" %>
                (PKR <%= new java.text.DecimalFormat("#,##0.00").format(due) %>)<%= overdueCount > 0 ? ", " + overdueCount + " overdue" : "" %>.
                <% if (next.getDueDate() != null) { %>Next due <%= new java.text.SimpleDateFormat("dd MMM yyyy").format(next.getDueDate()) %>.<% } %>
                <a href="challans" style="color:inherit; font-weight:600;">View challans</a>
            </div>
        <% } %>
        <div class="card">
            <div class="card-header">
                <h3 class="card-title">My Courses (Enrolled)</h3>
            </div>

            <% if (enrollments != null && !enrollments.isEmpty()) { %>
            <div style="display: flex; flex-direction: column; gap: 20px;">

                <% for (Enrollment e : enrollments) { %>
                <div style="
                    background: white;
                    border: 1px solid #e5e7eb;
                    border-radius: 12px;
                    box-shadow: 0 4px 6px rgba(0,0,0,0.05);
                    overflow: hidden;
                ">

                    <!-- Course Header -->
                    <div style="
                        background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
                        padding: 20px 25px;
                        color: white;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    ">
                        <div>
                            <h3 style="margin: 0; font-size: 1.25rem; font-weight: 600;">
                                <%= e.getCourseAllocation().getCourse().getCourseName() %>
                            </h3>
                            <span style="opacity: 0.9; font-size: 0.9rem;">
                                <%= e.getCourseAllocation().getCourse().getCourseCode() %>
                            </span>
                        </div>
                        <div>
                            <span style="
                                background: rgba(255,255,255,0.2);
                                padding: 5px 12px;
                                border-radius: 20px;
                                font-size: 0.85rem;
                            ">
                                <%= e.getCourseAllocation().getTeacher().getUsername() %>
                            </span>
                        </div>
                    </div>

                    <!-- Course Actions -->
                    <div style="padding: 20px 25px; display: flex; gap: 15px;">

                        <a href="gradebook" class="btn"
                           style="flex:1; background-color:#fbbf24; color:#1f2937;">
                            <i class="fas fa-chart-bar"></i> Gradebook
                        </a>

                        <a href="attendance?enrollmentId=<%= e.getEnrollmentId() %>" class="btn"
                           style="flex:1; background-color:#10b981; color:#ffffff;">
                            <i class="fas fa-calendar-check"></i> Attendance
                        </a>

                        <!-- STEEL BLUE ANNOUNCEMENTS -->
                        <a href="announcements" class="btn"
                           style="flex:1; background-color:#4682B4; color:#ffffff;">
                            <i class="fas fa-bullhorn"></i> Announcements
                        </a>

                    </div>
                </div>
                <% } %>
            </div>
            <% } else { %>
                <p>No courses enrolled for this semester.</p>
            <% } %>
        </div>

        <% } else if ("TEACHER".equals(user.getRole())) { %>

        <!-- ================= TEACHER AREA ================= -->
        <div class="card">
            <div class="card-header">
                <h3 class="card-title">My Assigned Courses (Teaching)</h3>
            </div>

            <% if (allocations != null && !allocations.isEmpty()) { %>
            <div style="display: flex; flex-direction: column; gap: 20px;">

                <% for (CourseAllocation a : allocations) { %>
                <div style="
                    background: white;
                    border: 1px solid #e5e7eb;
                    border-radius: 12px;
                    box-shadow: 0 4px 6px rgba(0,0,0,0.05);
                    overflow: hidden;
                ">

                    <div style="
                        background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
                        padding: 20px 25px;
                        color: white;
                    ">
                        <h3 style="margin:0;">
                            <%= a.getCourse().getCourseName() %>
                        </h3>
                        <div style="opacity:0.9;">
                            <%= a.getCourse().getCourseCode() %> |
                            <%= a.getSemester() != null ? a.getSemester().getName() : "Unknown Semester" %>
                        </div>
                    </div>

                    <div style="padding:20px; display:flex; gap:15px;">

                        <a href="${pageContext.request.contextPath}/manageAttendance?allocationId=<%= a.getAllocationId() %>"
                           class="btn" style="flex:1; background-color:#10b981; color:#ffffff;">
                            Mark Attendance
                        </a>

                        <a href="${pageContext.request.contextPath}/uploadGrades?allocationId=<%= a.getAllocationId() %>"
                           class="btn" style="flex:1; background-color:#fbbf24; color:#1f2937;">
                            Upload Grades
                        </a>

                        <!-- STEEL BLUE ANNOUNCEMENTS -->
                        <a href="${pageContext.request.contextPath}/manageAnnouncements?courseId=<%= a.getCourse().getCourseId() %>"
                           class="btn" style="flex:1; background-color:#4682B4; color:#ffffff;">
                            Announcements
                        </a>

                        <a href="messages" class="btn"
                           style="flex:1; background-color:#0f172a; color:#ffffff;">
                            💬 Message Students
                        </a>

                    </div>
                </div>
                <% } %>
            </div>
            <% } else { %>
                <p>No courses assigned to you for this semester.</p>
            <% } %>
        </div>

        <% } %>

    </div>
</main>

</body>
</html>
