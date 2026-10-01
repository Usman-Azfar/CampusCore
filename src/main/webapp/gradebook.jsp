<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Map, java.util.Set, java.util.Collections, com.cms.models.Grade, com.cms.models.Semester, com.cms.models.CourseAttendance, com.cms.models.ClassSemester, com.cms.controllers.GradebookServlet, com.cms.util.GradeRules, com.cms.util.HtmlUtil" %>
<%
    Map<String, List<Grade>> groups = (Map<String, List<Grade>>) request.getAttribute("groups");
    if (groups == null) groups = Collections.emptyMap();
    Map<Integer, String> termOptions = (Map<Integer, String>) request.getAttribute("termOptions");
    Set<Integer> semesterOptions = (Set<Integer>) request.getAttribute("semesterOptions");
    Map<String, String> courseOptions = (Map<String, String>) request.getAttribute("courseOptions");
    GradebookServlet.Filters f = (GradebookServlet.Filters) request.getAttribute("filters");
    Set<Integer> repeats = (Set<Integer>) request.getAttribute("repeats");
    if (repeats == null) repeats = Collections.emptySet();
    Set<Integer> replaced = (Set<Integer>) request.getAttribute("replaced");
    if (replaced == null) replaced = Collections.emptySet();
    GradeRules.Summary overall = (GradeRules.Summary) request.getAttribute("overall");
    Integer totalCourses = (Integer) request.getAttribute("totalCourses");
    int total = totalCourses == null ? 0 : totalCourses;
    int shown = 0;
    for (List<Grade> g : groups.values()) shown += g.size();
    String maxS = GradeRules.format(GradeRules.SESSIONAL_MAX), maxM = GradeRules.format(GradeRules.MID_MAX),
           maxF = GradeRules.format(GradeRules.FINAL_MAX), maxT = GradeRules.format(GradeRules.TOTAL_MAX);
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Gradebook | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .head { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; align-items: flex-start; }
        .letter { display: inline-block; min-width: 34px; text-align: center; padding: 2px 8px; border-radius: 4px; font-weight: 700; background: var(--primary-color); color: #fff; }
        .letter.fail { background: #b91c1c; }
        .letter.withdrawn { background: #6b7280; }
        .pending { color: #b45309; font-size: 0.9em; font-style: italic; white-space: nowrap; }
        .summary { display: flex; gap: 10px; flex-wrap: wrap; margin: 10px 0 4px; }
        .summary .tag { font-size: 0.85rem; padding: 3px 10px; }
        .filters { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 10px; margin-top: 14px; align-items: end; }
        .filters .form-group { margin-bottom: 0; }
        .filter-bar { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; align-items: center; margin-top: 10px; }
        .group-head { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; align-items: baseline; }
        .group-head h3 { margin: 0; white-space: nowrap; }
        .tag-repeat { background: #fef3c7; color: #92400e; }
        tr.withdrawn-row td { color: #6b7280; }
    </style>
</head>

<body>
<jsp:include page="sidebar.jsp" />

<main class="main-content">
    <jsp:include page="header.jsp" />

    <div class="page-container">
        <div class="card">
            <div class="head">
                <div>
                    <h2>Gradebook</h2>
                    <p class="muted">Your marks in each course by term and semester (newest first): Sessional (<%= maxS %>), Mid (<%= maxM %>) and Final (<%= maxF %>), out of <%= maxT %>.
                        Marks appear once your teacher publishes them; the grade appears once all three marks are in. W = withdrawn (no grade points).
                        For a course you took more than once, only the latest result counts towards your CGPA.</p>
                </div>
                <a href="transcript" class="btn btn-secondary">View transcript</a>
            </div>
            <% if (overall != null && total > 0) { %>
            <div class="summary">
                <span class="tag">CGPA: <strong><%= overall.getGpa() %></strong></span>
                <span class="tag">Credit hours earned: <%= overall.creditsEarned %> of <%= overall.creditsAttempted %> attempted</span>
                <span class="tag"><%= overall.graded %> of <%= total %> course<%= total == 1 ? "" : "s" %> graded</span>
                <% if (overall.withdrawn > 0) { %><span class="tag"><%= overall.withdrawn %> withdrawn</span><% } %>
                <% if (overall.replaced > 0) { %><span class="tag tag-repeat"><%= overall.replaced %> earlier result<%= overall.replaced == 1 ? "" : "s" %> replaced by a retake</span><% } %>
            </div>

            <form method="get" action="gradebook" id="filterForm" class="filters" aria-label="Filter grades">
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
                    <label class="form-label" for="fResult">Result</label>
                    <select name="result" id="fResult" class="form-control">
                        <option value="">Any</option>
                        <% for (String[] o : new String[][] { {"graded", "Graded"}, {"failed", "Failed (F)"}, {"progress", "In progress"}, {"awaited", "Result awaited"}, {"withdrawn", "Withdrawn (W)"} }) { %>
                            <option value="<%= o[0] %>" <%= o[0].equals(f.result) ? "selected" : "" %>><%= o[1] %></option>
                        <% } %>
                    </select>
                </div>
                <noscript><button type="submit" class="btn btn-primary">Apply</button></noscript>
            </form>
            <div class="filter-bar">
                <span class="muted" aria-live="polite">Showing <%= shown %> of <%= total %> course<%= total == 1 ? "" : "s" %></span>
                <% if (f.isActive()) { %><a href="gradebook" class="btn btn-secondary btn-sm">Clear filters</a><% } %>
            </div>
            <% } %>
        </div>

        <% if (total == 0) { %>
            <div class="card"><p>You are not enrolled in any courses yet.</p></div>
        <% } else if (groups.isEmpty()) { %>
            <div class="card"><p>No courses match these filters. <a href="gradebook">Clear filters</a></p></div>
        <% } %>

        <% for (List<Grade> grades : groups.values()) {
               Grade first = grades.get(0);
               Semester term = first.getEnrollment().getCourseAllocation().getSemester();
               GradeRules.Summary termSummary = GradeRules.summarize(grades);
        %>
        <div class="card">
            <div class="group-head">
                <h3><%= HtmlUtil.esc(CourseAttendance.label(term.getName(), first.getEnrollment().getSemesterNumber())) %></h3>
                <span class="muted"><%= HtmlUtil.esc(term.getDateRange()) %></span>
            </div>
            <div class="table-scroll">
            <table class="styled-table">
                <thead>
                    <tr>
                        <th>Course</th><th>Instructor</th><th>Cr. Hrs</th>
                        <th>Sessional (<%= maxS %>)</th><th>Mid (<%= maxM %>)</th><th>Final (<%= maxF %>)</th>
                        <th>Total (<%= maxT %>)</th><th>Grade</th>
                    </tr>
                </thead>
                <tbody>
                <% for (Grade g : grades) {
                       com.cms.models.Course c = g.getEnrollment().getCourseAllocation().getCourse();
                       Grade rg = g;
                       boolean showMarks = g.isPublished();
                %>
                    <tr class="<%= g.isWithdrawn() ? "withdrawn-row" : "" %>">
                        <td><strong><%= HtmlUtil.esc(c.getCourseCode()) %></strong> <%= HtmlUtil.esc(c.getCourseName()) %>
                            <% if (repeats.contains(g.getEnrollmentId())) { %><span class="tag tag-repeat">Repeat</span><% } %>
                            <% if (replaced.contains(g.getEnrollmentId())) { %><span class="tag tag-missing" title="You took this course again; the later result counts in your CGPA">Replaced &middot; not in CGPA</span><% } %></td>
                        <td><%= HtmlUtil.esc(g.getEnrollment().getCourseAllocation().getTeacher().getUsername()) %></td>
                        <td><%= c.getCreditHours() %></td>
                        <td><%= showMarks ? GradeRules.format(g.getSessionalMarks()) : "-" %></td>
                        <td><%= showMarks ? GradeRules.format(g.getMidMarks()) : "-" %></td>
                        <td><%= showMarks ? GradeRules.format(g.getFinalMarks()) : "-" %></td>
                        <td><% if (showMarks) { %><strong><%= GradeRules.format(g.getTotalMarks()) %></strong><%= g.isComplete() ? "" : " <span class=\"muted\">so far</span>" %><% } else { %>-<% } %></td>
                        <td><%@ include file="/WEB-INF/jspf/result_cell.jspf" %></td>
                    </tr>
                <% } %>
                </tbody>
            </table>
            </div>
            <div class="summary" style="justify-content:flex-end;">
                <span class="tag">Term GPA: <strong><%= termSummary.getGpa() %></strong></span>
                <span class="tag">Credit hours: <%= termSummary.creditsEarned %> earned of <%= termSummary.creditsAttempted %> graded</span>
            </div>
        </div>
        <% } %>
    </div>
</main>
<script>
(function () {
    // Filters apply as soon as one is changed; empty ones are left out of the address
    var form = document.getElementById("filterForm");
    if (!form) return;
    form.querySelectorAll("select").forEach(function (s) {
        s.addEventListener("change", function () {
            form.querySelectorAll("select").forEach(function (o) { o.disabled = !o.value; });
            form.submit();
        });
    });
    window.addEventListener("pageshow", function () {
        form.querySelectorAll("select").forEach(function (o) { o.disabled = false; });
    });
})();
</script>
</body>
</html>
