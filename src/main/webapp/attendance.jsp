<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Map, java.util.Set, java.util.Collections, com.cms.models.Attendance, com.cms.models.CourseAttendance, com.cms.models.ClassSemester, com.cms.controllers.AttendanceServlet, com.cms.util.AttendanceRules, com.cms.util.HtmlUtil" %>
<%!
    static String range(CourseAttendance c) {
        if (c.getTermStart() == null || c.getTermEnd() == null) return "";
        return AttendanceRules.format(c.getTermStart().toLocalDate()) + " - " + AttendanceRules.format(c.getTermEnd().toLocalDate());
    }
    static String pct(int p) {
        return p < 0 ? "-" : p + "%";
    }
%>
<%
    CourseAttendance course = (CourseAttendance) request.getAttribute("course");
    List<Attendance> attendanceList = (List<Attendance>) request.getAttribute("attendanceList");
    Map<String, List<CourseAttendance>> groups = (Map<String, List<CourseAttendance>>) request.getAttribute("groups");
    if (groups == null) groups = Collections.emptyMap();
    Map<Integer, String> termOptions = (Map<Integer, String>) request.getAttribute("termOptions");
    Set<Integer> semesterOptions = (Set<Integer>) request.getAttribute("semesterOptions");
    Map<String, String> courseOptions = (Map<String, String>) request.getAttribute("courseOptions");
    AttendanceServlet.Filters f = (AttendanceServlet.Filters) request.getAttribute("filters");
    String filterQuery = (String) request.getAttribute("filterQuery");
    if (filterQuery == null) filterQuery = "";
    Integer totalCourses = (Integer) request.getAttribute("totalCourses");
    int low = AttendanceRules.LOW_ATTENDANCE_PERCENT;
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Attendance | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .s-present { color: #047857; font-weight: 700; }
        .s-absent { color: #b91c1c; font-weight: 700; }
        .s-leave { color: #b45309; font-weight: 700; }
        .low { color: #b91c1c; font-weight: 700; }
        .summary { display: flex; gap: 10px; flex-wrap: wrap; margin: 10px 0 4px; }
        .summary .tag { font-size: 0.85rem; padding: 3px 10px; }
        .head { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; align-items: flex-start; }
        .filters { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 10px; margin-top: 14px; align-items: end; }
        .filters .form-group { margin-bottom: 0; }
        .filter-bar { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; align-items: center; margin-top: 10px; }
        .term-label { white-space: nowrap; }
        .group-head { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; align-items: baseline; }
        .group-head h3 { margin: 0; }
    </style>
</head>

<body>
<jsp:include page="sidebar.jsp" />

<main class="main-content">
    <jsp:include page="header.jsp" />

    <div class="page-container">
<% if (course != null) {
       int pctValue = course.getPercent();
%>
        <!-- ONE COURSE: lecture by lecture -->
        <div class="card">
            <div class="head">
                <div>
                    <h2><%= HtmlUtil.esc(course.getCourseName()) %></h2>
                    <div class="summary">
                        <span class="tag"><%= HtmlUtil.esc(course.getCourseCode()) %></span>
                        <span class="tag tag-department term-label"><%= HtmlUtil.esc(course.getTermLabel()) %></span>
                        <span class="tag"><%= HtmlUtil.esc(course.getTeacherName()) %></span>
                    </div>
                    <% if (!range(course).isEmpty()) { %><p class="muted"><%= HtmlUtil.esc(range(course)) %></p><% } %>
                </div>
                <a href="attendance<%= filterQuery.isEmpty() ? "" : "?" + HtmlUtil.esc(filterQuery) %>" class="btn btn-secondary">All courses</a>
            </div>
            <div class="summary">
                <span class="tag">Lectures held: <%= course.getLecturesHeld() %></span>
                <span class="tag" style="background:#ecfdf5; color:#047857;">Present: <%= course.getPresent() %></span>
                <span class="tag" style="background:#fef2f2; color:#b91c1c;">Absent: <%= course.getAbsent() %></span>
                <span class="tag" style="background:#fffbeb; color:#b45309;">Leave: <%= course.getLeave() %></span>
                <% if (course.getNotMarked() > 0) { %><span class="tag tag-missing">Not marked: <%= course.getNotMarked() %></span><% } %>
                <span class="tag <%= course.isLow() ? "low" : "" %>">Attendance: <%= pct(pctValue) %></span>
            </div>
            <% if (course.isLow()) { %>
                <div class="alert alert-error" style="margin-top:10px;">Your attendance in this course is below <%= low %>%.</div>
            <% } %>
            <p class="muted">Percentage = present &divide; lectures you were marked in. Leave does not count as present.</p>

            <div class="table-scroll">
            <table class="styled-table">
                <thead><tr><th>Lecture</th><th>Date</th><th>Topic</th><th>Status</th></tr></thead>
                <tbody>
                <% if (attendanceList == null || attendanceList.isEmpty()) { %>
                    <tr><td colspan="4" style="text-align:center;">No lectures have been marked yet.</td></tr>
                <% } else for (Attendance a : attendanceList) { %>
                    <tr>
                        <td>Lecture <%= a.getLectureNumber() %></td>
                        <td style="white-space:nowrap;"><%= a.getDate() != null ? AttendanceRules.format(a.getDate().toLocalDate()) : "" %></td>
                        <td><%= a.getTopic() != null ? HtmlUtil.esc(a.getTopic()) : "<span class=\"muted\">-</span>" %></td>
                        <td><% if (a.getStatus() == null) { %><span class="muted">Not marked</span><% } else { %>
                            <span class="s-<%= a.getStatus().toLowerCase() %>"><%= HtmlUtil.esc(a.getStatus()) %></span><% } %></td>
                    </tr>
                <% } %>
                </tbody>
            </table>
            </div>
        </div>
<% } else {
       int shown = 0;
       for (List<CourseAttendance> g : groups.values()) shown += g.size();
       int total = totalCourses == null ? 0 : totalCourses;
%>
        <!-- ALL COURSES: filters, then one card per term and semester -->
        <div class="card">
            <h2>Attendance</h2>
            <p class="muted">Your attendance in each course, by term and semester (newest first). Below <%= low %>% is shown in red.</p>

            <% if (total > 0) { %>
            <form method="get" action="attendance" id="filterForm" class="filters" aria-label="Filter attendance">
                <div class="form-group">
                    <label class="form-label" for="fTerm">Term</label>
                    <select name="term" id="fTerm" class="form-control">
                        <option value="">All terms</option>
                        <% for (Map.Entry<Integer, String> t : termOptions.entrySet()) { %>
                            <option value="<%= t.getKey() %>" <%= t.getKey().equals(f.term) ? "selected" : "" %>><%= HtmlUtil.esc(t.getValue()) %></option>
                        <% } %>
                    </select>
                </div>
                <div class="form-group">
                    <label class="form-label" for="fSemester">Semester</label>
                    <select name="semester" id="fSemester" class="form-control">
                        <option value="">All semesters</option>
                        <% for (Integer n : semesterOptions) { %>
                            <option value="<%= n %>" <%= n.equals(f.semester) ? "selected" : "" %>><%= ClassSemester.ordinal(n) %> Semester</option>
                        <% } %>
                    </select>
                </div>
                <div class="form-group">
                    <label class="form-label" for="fCourse">Course</label>
                    <select name="course" id="fCourse" class="form-control">
                        <option value="">All courses</option>
                        <% for (Map.Entry<String, String> c : courseOptions.entrySet()) { %>
                            <option value="<%= HtmlUtil.esc(c.getKey()) %>" <%= c.getKey().equals(f.course) ? "selected" : "" %>><%= HtmlUtil.esc(c.getKey()) %> - <%= HtmlUtil.esc(c.getValue()) %></option>
                        <% } %>
                    </select>
                </div>
                <div class="form-group">
                    <label class="form-label" for="fLevel">Attendance</label>
                    <select name="level" id="fLevel" class="form-control">
                        <option value="">Any</option>
                        <option value="low" <%= "low".equals(f.level) ? "selected" : "" %>>Below <%= low %>%</option>
                        <option value="ok" <%= "ok".equals(f.level) ? "selected" : "" %>><%= low %>% or more</option>
                    </select>
                </div>
                <noscript><button type="submit" class="btn btn-primary">Apply</button></noscript>
            </form>
            <div class="filter-bar">
                <span class="muted" aria-live="polite">Showing <%= shown %> of <%= total %> course<%= total == 1 ? "" : "s" %></span>
                <% if (f.isActive()) { %><a href="attendance" class="btn btn-secondary btn-sm">Clear filters</a><% } %>
            </div>
            <% } %>
        </div>

        <% if (total == 0) { %>
            <div class="card"><p>You are not enrolled in any courses.</p></div>
        <% } else if (groups.isEmpty()) { %>
            <div class="card"><p>No courses match these filters. <a href="attendance">Clear filters</a></p></div>
        <% } %>

        <% for (List<CourseAttendance> g : groups.values()) {
               CourseAttendance first = g.get(0);
               int gPresent = 0, gMarked = 0, gHeld = 0;
               for (CourseAttendance c : g) { gPresent += c.getPresent(); gMarked += c.getMarked(); gHeld += c.getLecturesHeld(); }
               int gPct = AttendanceRules.percent(gPresent, gMarked);
        %>
        <div class="card">
            <div class="group-head">
                <h3 class="term-label"><%= HtmlUtil.esc(first.getTermLabel()) %></h3>
                <span class="muted"><%= HtmlUtil.esc(range(first)) %></span>
            </div>
            <div class="summary">
                <span class="tag"><%= g.size() %> course<%= g.size() == 1 ? "" : "s" %></span>
                <span class="tag"><%= gHeld %> lecture<%= gHeld == 1 ? "" : "s" %> held</span>
                <span class="tag <%= gPct >= 0 && gPct < low ? "low" : "" %>">Overall attendance: <%= pct(gPct) %></span>
            </div>
            <div class="table-scroll">
            <table class="styled-table">
                <thead><tr><th>Course</th><th>Term / Semester</th><th>Teacher</th><th>Lectures</th><th>Present</th><th>Absent</th><th>Leave</th><th>Attendance</th><th></th></tr></thead>
                <tbody>
                <% for (CourseAttendance c : g) { int p = c.getPercent(); %>
                    <tr>
                        <td><strong><%= HtmlUtil.esc(c.getCourseCode()) %></strong> <%= HtmlUtil.esc(c.getCourseName()) %></td>
                        <td class="term-label"><%= HtmlUtil.esc(c.getTermLabel()) %></td>
                        <td><%= HtmlUtil.esc(c.getTeacherName()) %></td>
                        <td><%= c.getLecturesHeld() %><% if (c.getNotMarked() > 0) { %> <span class="muted">(<%= c.getNotMarked() %> not marked)</span><% } %></td>
                        <td><%= c.getPresent() %></td>
                        <td><%= c.getAbsent() %></td>
                        <td><%= c.getLeave() %></td>
                        <td><% if (p < 0) { %><span class="muted">No lectures yet</span><% } else { %><span class="<%= c.isLow() ? "low" : "" %>"><%= p %>%</span><% } %></td>
                        <td><a href="attendance?enrollmentId=<%= c.getEnrollmentId() %><%= filterQuery.isEmpty() ? "" : "&amp;" + HtmlUtil.esc(filterQuery) %>" class="btn btn-primary btn-sm">Details</a></td>
                    </tr>
                <% } %>
                </tbody>
            </table>
            </div>
        </div>
        <% } %>
<% } %>
    </div>
</main>
<script>
(function () {
    // Filters apply as soon as one is changed
    var form = document.getElementById("filterForm");
    if (!form) return;
    form.querySelectorAll("select").forEach(function (s) {
        s.addEventListener("change", function () {
            // Leave empty filters out of the address
            form.querySelectorAll("select").forEach(function (o) { o.disabled = !o.value; });
            form.submit();
        });
    });
    // Coming back with the browser's Back button: undo the disabling above
    window.addEventListener("pageshow", function () {
        form.querySelectorAll("select").forEach(function (o) { o.disabled = false; });
    });
})();
</script>
</body>
</html>
