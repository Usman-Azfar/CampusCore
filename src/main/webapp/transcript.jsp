<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Map, java.util.Set, java.util.Collections, com.cms.models.Grade, com.cms.models.Course, com.cms.models.Semester, com.cms.models.CourseAttendance, com.cms.models.ClassSemester, com.cms.models.User, com.cms.controllers.GradebookServlet, com.cms.util.GradeRules, com.cms.util.HtmlUtil" %>
<%
    Map<String, List<Grade>> terms = (Map<String, List<Grade>>) request.getAttribute("terms");
    if (terms == null) terms = Collections.emptyMap();
    Map<Integer, String> termOptions = (Map<Integer, String>) request.getAttribute("termOptions");
    Set<Integer> semesterOptions = (Set<Integer>) request.getAttribute("semesterOptions");
    GradebookServlet.Filters f = (GradebookServlet.Filters) request.getAttribute("filters");
    Set<Integer> repeats = (Set<Integer>) request.getAttribute("repeats");
    if (repeats == null) repeats = Collections.emptySet();
    Set<Integer> replaced = (Set<Integer>) request.getAttribute("replaced");
    if (replaced == null) replaced = Collections.emptySet();
    GradeRules.Summary overall = (GradeRules.Summary) request.getAttribute("overall");
    GradeRules.Summary selected = (GradeRules.Summary) request.getAttribute("selected");
    boolean hasResults = Boolean.TRUE.equals(request.getAttribute("hasResults"));
    boolean filtered = f != null && f.isActive();
    User acct = (User) request.getAttribute("account");
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Transcript | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .toolbar { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; align-items: flex-end; margin-bottom: 20px; }
        .filters { display: flex; gap: 10px; flex-wrap: wrap; align-items: flex-end; }
        .filters .form-group { margin-bottom: 0; min-width: 170px; }
        .transcript-container { max-width: 900px; margin: 0 auto; background: white; padding: 40px; box-shadow: 0 0 10px rgba(0, 0, 0, 0.1); border-radius: 8px; }
        .transcript-header { text-align: center; border-bottom: 2px solid #333; margin-bottom: 30px; padding-bottom: 20px; }
        .student-facts { margin-top: 20px; display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 6px 20px; text-align: left; }
        .semester-block { margin-bottom: 30px; break-inside: avoid; }
        .semester-title { background: #f4f4f4; padding: 10px; font-weight: bold; border-left: 5px solid var(--primary-color); margin-bottom: 10px; display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
        .semester-foot { text-align: right; margin-top: 5px; font-weight: bold; color: #555; }
        .summary-box { background: #eef2ff; padding: 15px; border-radius: 8px; margin-top: 20px; display: flex; justify-content: space-around; gap: 10px; flex-wrap: wrap; font-weight: bold; }
        .partial-note { background: #fffbeb; color: #92400e; border: 1px solid #fde68a; padding: 8px 12px; border-radius: 6px; margin-bottom: 20px; font-size: 0.9em; }
        .fine-print { margin-top: 50px; border-top: 1px solid #ddd; padding-top: 20px; font-size: 0.8em; color: #777; text-align: center; }
        .repeat { font-size: 0.75em; color: #92400e; font-weight: 600; margin-left: 4px; }
        @media (max-width: 768px) { .transcript-container { padding: 18px; } }
        @media print {
            .sidebar, .header, .top-header, .no-print { display: none !important; }
            .main-content { margin-left: 0 !important; padding: 0 !important; }
            .transcript-container { box-shadow: none; width: 100%; max-width: none; padding: 0; }
        }
    </style>
</head>

<body>
<jsp:include page="sidebar.jsp" />

<main class="main-content">
    <jsp:include page="header.jsp" />

    <div class="page-container">
        <div class="toolbar no-print">
            <% if (hasResults) { %>
            <form method="get" action="transcript" id="filterForm" class="filters" aria-label="Filter transcript">
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
                <noscript><button type="submit" class="btn btn-secondary">Apply</button></noscript>
                <% if (filtered) { %><a href="transcript" class="btn btn-secondary btn-sm">Show all semesters</a><% } %>
            </form>
            <% } else { %><span></span><% } %>
            <button type="button" onclick="window.print()" class="btn btn-primary">Print transcript</button>
        </div>

        <div class="transcript-container">
            <div class="transcript-header">
                <img src="images/campuscore-icon-192.png" alt="CampusCore logo" style="height: 80px; margin-bottom: 10px; border-radius: 50%;">
                <h1>Academic Transcript</h1>
                <p>CampusCore &mdash; A Smart Campus Management System</p>
                <div class="student-facts">
                    <div><strong>Student Name:</strong> <%= acct != null && acct.getProfile() != null ? HtmlUtil.esc(acct.getProfile().getFullName()) : "" %></div>
                    <div><strong>Roll Number:</strong> <%= acct != null ? HtmlUtil.esc(acct.getUsername()) : "" %></div>
                    <div><strong>Class:</strong> <%= acct != null && acct.getClassName() != null ? HtmlUtil.esc(acct.getClassName()) : "Not assigned" %></div>
                    <div><strong>Date of Issue:</strong> <%= new java.text.SimpleDateFormat("dd MMM yyyy").format(new java.util.Date()) %></div>
                </div>
            </div>

            <% if (filtered && !terms.isEmpty()) { %>
                <div class="partial-note">Partial transcript: only the selected semester<%= terms.size() == 1 ? " is" : "s are" %> shown. The CGPA below is over all semesters.</div>
            <% } %>

            <% for (List<Grade> grades : terms.values()) {
                   Grade first = grades.get(0);
                   Semester term = first.getEnrollment().getCourseAllocation().getSemester();
                   GradeRules.Summary sem = GradeRules.summarize(grades);
            %>
            <div class="semester-block">
                <div class="semester-title">
                    <span><%= HtmlUtil.esc(CourseAttendance.label(term.getName(), first.getEnrollment().getSemesterNumber())) %></span>
                    <span class="muted" style="font-weight:400;"><%= HtmlUtil.esc(term.getDateRange()) %></span>
                </div>
                <div class="table-scroll">
                <table class="styled-table" style="width: 100%;">
                    <thead>
                        <tr><th>Code</th><th>Course Title</th><th>Cr. Hrs</th><th>Marks</th><th>Grade</th><th>Grade Points</th><th>Quality Points</th></tr>
                    </thead>
                    <tbody>
                    <% for (Grade g : grades) {
                           Course c = g.getEnrollment().getCourseAllocation().getCourse();
                           int credits = c.getCreditHours();
                           boolean w = g.isWithdrawn();
                           double points = GradeRules.points(g.getGradeLetter());
                    %>
                        <tr>
                            <td><%= HtmlUtil.esc(c.getCourseCode()) %></td>
                            <td><%= HtmlUtil.esc(c.getCourseName()) %><% if (repeats.contains(g.getEnrollmentId())) { %><span class="repeat">(Repeat)</span><% } %><% if (replaced.contains(g.getEnrollmentId())) { %><span class="repeat">(Replaced: not in CGPA)</span><% } %></td>
                            <td><%= credits %></td>
                            <td><%= w ? "-" : GradeRules.format(g.getTotalMarks()) %></td>
                            <td><strong><%= w ? "W" : HtmlUtil.esc(g.getGradeLetter()) %></strong></td>
                            <td><%= w ? "-" : String.format("%.2f", points) %></td>
                            <td><%= w ? "-" : String.format("%.2f", points * credits) %></td>
                        </tr>
                    <% } %>
                    </tbody>
                </table>
                </div>
                <div class="semester-foot">Semester GPA: <%= sem.getGpa() %>
                    <span style="font-weight:400;">&middot; Credit hours: <%= sem.creditsEarned %> earned of <%= sem.creditsAttempted %> attempted</span></div>
            </div>
            <% } %>

            <% if (!hasResults) { %>
                <div style="text-align: center; padding: 40px; color: #666;">
                    <p>No results on your transcript yet. A course appears here once your teacher has entered all its marks and published the result.</p>
                </div>
            <% } else if (terms.isEmpty()) { %>
                <div style="text-align: center; padding: 40px; color: #666;">
                    <p>No results for the selected semester. <a href="transcript" class="no-print">Show all semesters</a></p>
                </div>
            <% } else { %>
                <div class="summary-box">
                    <% if (filtered) { %>
                        <span>Selected: GPA <%= selected.getGpa() %> (<%= selected.creditsAttempted %> credit hours)</span>
                    <% } %>
                    <span>Credit hours attempted: <%= overall.creditsAttempted %></span>
                    <span>Credit hours earned: <%= overall.creditsEarned %></span>
                    <span>CGPA: <%= overall.getGpa() %></span>
                    <% if (overall.replaced > 0) { %><span style="font-weight:400;"><%= overall.replaced %> repeated course result<%= overall.replaced == 1 ? "" : "s" %> replaced by a later attempt</span><% } %>
                </div>
            <% } %>

            <div class="fine-print">
                <p>Only published results with all marks entered are shown; W = withdrawn (no credit hours or grade points). A repeated course is listed in each term it was taken; only its latest result counts towards the CGPA and credit hours (semester GPAs show each term as it was).
                    Courses in progress are listed in the Gradebook.</p>
                <p>This is a computer-generated document and does not require a physical signature.</p>
                <p>&copy; <%= java.time.Year.now() %> CampusCore</p>
            </div>
        </div>
    </div>
</main>
<script>
(function () {
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
