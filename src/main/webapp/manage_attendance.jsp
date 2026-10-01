<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Map, java.util.Collections, java.time.LocalDate, com.cms.models.CourseAllocation, com.cms.models.Lecture, com.cms.models.AttendanceSummary, com.cms.models.Semester, com.cms.util.AttendanceRules, com.cms.util.HtmlUtil" %>
<%!
    // "01 Sep 2024 - 15 Jan 2025" or ""
    static String range(Semester s) {
        return s == null ? "" : s.getDateRange();
    }
    static String fmt(java.sql.Date d) {
        return d == null ? "" : AttendanceRules.format(d.toLocalDate());
    }
    static String fmt(java.sql.Timestamp t) {
        return t == null ? "" : new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a").format(t);
    }
%>
<%
    List<CourseAllocation> openOfferings = (List<CourseAllocation>) request.getAttribute("openOfferings");
    List<CourseAllocation> closedOfferings = (List<CourseAllocation>) request.getAttribute("closedOfferings");
    CourseAllocation offering = (CourseAllocation) request.getAttribute("offering");
    List<Lecture> lectures = (List<Lecture>) request.getAttribute("lectures");
    List<AttendanceSummary> summaries = (List<AttendanceSummary>) request.getAttribute("summaries");
    Lecture sheetLecture = (Lecture) request.getAttribute("sheetLecture");
    LocalDate sheetDate = (LocalDate) request.getAttribute("sheetDate");
    String sheetNotice = (String) request.getAttribute("sheetNotice");
    String sheetLocked = (String) request.getAttribute("sheetLocked");
    List<Lecture> sameDay = (List<Lecture>) request.getAttribute("sameDayLectures");
    Map<Integer, String> statuses = (Map<Integer, String>) request.getAttribute("statuses");
    boolean extra = Boolean.TRUE.equals(request.getAttribute("extra"));
    if (lectures == null) lectures = Collections.emptyList();
    if (summaries == null) summaries = Collections.emptyList();
    if (sameDay == null) sameDay = Collections.emptyList();
    if (statuses == null) statuses = Collections.emptyMap();
    boolean editing = sheetLecture != null;
    boolean locked = sheetLocked != null;
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Mark Attendance | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .course-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; margin-top: 16px; }
        .course-card { display: block; background: linear-gradient(135deg, var(--primary-color), #0e7490); color: #fff; padding: 18px; border-radius: 8px; text-decoration: none; transition: transform 0.2s; }
        .course-card:hover { transform: translateY(-3px); }
        .course-card h3 { font-size: 1.05rem; margin-bottom: 4px; }
        .course-card .meta { font-size: 0.85em; opacity: 0.9; margin-top: 8px; line-height: 1.5; }
        .page-head { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; align-items: flex-start; }
        .facts { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
        .date-bar { display: flex; gap: 10px; flex-wrap: wrap; align-items: end; }
        .date-bar .form-group { margin-bottom: 0; }
        .sheet-head { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; align-items: center; margin: 16px 0 10px; }
        .bulk { display: flex; gap: 6px; flex-wrap: wrap; }
        .status-group { display: inline-flex; position: relative; border: 1px solid #d1d5db; border-radius: 6px; overflow: hidden; }
        .status-group label { cursor: pointer; }
        .status-group input { position: absolute; opacity: 0; width: 1px; height: 1px; }
        .status-group span { display: inline-block; padding: 5px 12px; font-size: 0.85rem; font-weight: 600; color: #4b5563; background: #fff; border-left: 1px solid #e5e7eb; }
        .status-group label:first-child span { border-left: none; }
        .status-group input:focus-visible + span { outline: 2px solid var(--primary-color); outline-offset: -2px; }
        .status-group input:checked + span.s-present { background: #047857; color: #fff; }
        .status-group input:checked + span.s-absent { background: #b91c1c; color: #fff; }
        .status-group input:checked + span.s-leave { background: #b45309; color: #fff; }
        .status-group input:disabled + span { cursor: not-allowed; opacity: 0.75; }
        .tally { font-size: 0.9em; color: #374151; }
        .tally b.p { color: #047857; } .tally b.a { color: #b91c1c; } .tally b.l { color: #b45309; }
        .low { color: #b91c1c; font-weight: 700; }
        .unmarked-row { background: #fffbeb !important; }
        .current-row { background: #eff6ff !important; }
        .sheet-actions { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; margin-top: 16px; align-items: center; }
        .lock { color: #6b7280; font-size: 0.85em; white-space: nowrap; }
    </style>
</head>

<body>
<jsp:include page="sidebar.jsp" />

<main class="main-content">
    <jsp:include page="header.jsp" />

    <div class="page-container">
        <% String okMsg = (String) request.getAttribute("successMessage");
           if (okMsg != null) { %>
            <div class="alert alert-success"><%= HtmlUtil.esc(okMsg) %></div>
        <% } %>
        <% String errMsg = (String) request.getAttribute("errorMessage");
           if (errMsg != null) { %>
            <div class="alert alert-error"><%= HtmlUtil.esc(errMsg) %></div>
        <% } %>

<% if (offering == null) { %>
        <!-- STEP 1: choose a course -->
        <div class="card">
            <h2>Mark Attendance</h2>
            <p class="muted">Choose a course. Attendance can be marked or changed within <%= AttendanceRules.EDIT_WINDOW_DAYS %> days of each lecture, for dates inside the term.</p>
            <% if (openOfferings == null || openOfferings.isEmpty()) { %>
                <p style="margin-top:14px;">You have no courses open for attendance right now.</p>
            <% } else { %>
            <div class="course-grid">
                <% for (CourseAllocation ca : openOfferings) { %>
                    <a href="manageAttendance?allocationId=<%= ca.getAllocationId() %>" class="course-card">
                        <h3><%= HtmlUtil.esc(ca.getCourse().getCourseName()) %></h3>
                        <div><%= HtmlUtil.esc(ca.getCourse().getCourseCode()) %> &middot; <%= HtmlUtil.esc(ca.getSemester().getName()) %></div>
                        <div class="meta">
                            <%= ca.getEnrolledCount() %> student<%= ca.getEnrolledCount() == 1 ? "" : "s" %>
                            &middot; <%= ca.getLectureCount() %>/<%= AttendanceRules.MAX_LECTURES %> lectures
                            <% if (ca.getLastLectureDate() != null) { %><br>Last lecture: <%= fmt(ca.getLastLectureDate()) %><% } %>
                        </div>
                    </a>
                <% } %>
            </div>
            <% } %>
        </div>
        <% if (closedOfferings != null && !closedOfferings.isEmpty()) { %>
        <div class="card">
            <h3>Past courses (view only)</h3>
            <p class="muted">Their term has ended, so attendance can no longer be changed.</p>
            <div class="table-scroll">
            <table class="styled-table">
                <thead><tr><th>Course</th><th>Term</th><th>Students</th><th>Lectures</th><th></th></tr></thead>
                <tbody>
                <% for (CourseAllocation ca : closedOfferings) { %>
                    <tr>
                        <td><strong><%= HtmlUtil.esc(ca.getCourse().getCourseCode()) %></strong> <%= HtmlUtil.esc(ca.getCourse().getCourseName()) %></td>
                        <td><%= HtmlUtil.esc(ca.getSemester().getName()) %><br><span class="muted"><%= HtmlUtil.esc(range(ca.getSemester())) %></span></td>
                        <td><%= ca.getEnrolledCount() %></td>
                        <td><%= ca.getLectureCount() %></td>
                        <td><a class="btn btn-secondary btn-sm" href="manageAttendance?allocationId=<%= ca.getAllocationId() %>">View</a></td>
                    </tr>
                <% } %>
                </tbody>
            </table>
            </div>
        </div>
        <% } %>

<% } else {
       Semester term = offering.getSemester();
       boolean termOpen = AttendanceRules.isOpen(term);
%>
        <!-- STEP 2: one course -->
        <div class="card">
            <div class="page-head">
                <div>
                    <h2><%= HtmlUtil.esc(offering.getCourse().getCourseName()) %></h2>
                    <div class="facts">
                        <span class="tag"><%= HtmlUtil.esc(offering.getCourse().getCourseCode()) %></span>
                        <span class="tag tag-department"><%= HtmlUtil.esc(term.getName()) %><%= range(term).isEmpty() ? "" : " &middot; " + HtmlUtil.esc(range(term)) %></span>
                        <span class="tag"><%= summaries.size() %> student<%= summaries.size() == 1 ? "" : "s" %></span>
                        <span class="tag"><%= lectures.size() %>/<%= AttendanceRules.MAX_LECTURES %> lectures</span>
                    </div>
                </div>
                <a href="manageAttendance" class="btn btn-secondary">All courses</a>
            </div>

            <% if (termOpen) { %>
            <form method="get" action="manageAttendance" class="date-bar" id="dateForm" style="margin-top:16px;">
                <input type="hidden" name="allocationId" value="<%= offering.getAllocationId() %>">
                <div class="form-group">
                    <label class="form-label" for="date">Lecture date</label>
                    <input type="date" name="date" id="date" class="form-control" required
                        min="<%= AttendanceRules.minDate(term) %>" max="<%= AttendanceRules.maxDate(term) %>"
                        value="<%= sheetDate != null ? sheetDate.toString() : "" %>">
                </div>
                <button type="submit" class="btn btn-primary">Open</button>
                <span class="muted">You can mark or change dates from <strong><%= AttendanceRules.format(AttendanceRules.minDate(term)) %></strong>
                    to <strong><%= AttendanceRules.format(AttendanceRules.maxDate(term)) %></strong>. Older lectures are locked after <%= AttendanceRules.EDIT_WINDOW_DAYS %> days.</span>
            </form>
            <% } else { %>
                <div class="alert alert-error" style="margin-top:16px;">
                    Attendance for this course is closed: <%= HtmlUtil.esc(term.getName()) %> runs <%= HtmlUtil.esc(range(term)) %>,
                    and changes are allowed only within <%= AttendanceRules.EDIT_WINDOW_DAYS %> days of a lecture. You can still view past lectures below.
                </div>
            <% } %>
        </div>

        <% if (sheetDate != null) { %>
        <!-- ATTENDANCE SHEET -->
        <div class="card" id="sheet">
            <% if (sameDay.size() > 1) { %>
                <p class="muted" style="margin-bottom:8px;">Lectures on <%= AttendanceRules.format(sheetDate) %>:
                    <% for (Lecture l : sameDay) { %>
                        <a href="manageAttendance?allocationId=<%= offering.getAllocationId() %>&lectureId=<%= l.getLectureId() %>#sheet"
                           class="tag <%= editing && l.getLectureId() == sheetLecture.getLectureId() ? "tag-department" : "" %>">Lecture <%= l.getNumber() %></a>
                    <% } %>
                </p>
            <% } %>
            <% if (sheetNotice != null) { %><div class="alert alert-success" style="background:#eff6ff; color:#1e3a8a; border-color:#bfdbfe;"><%= HtmlUtil.esc(sheetNotice) %></div><% } %>
            <% if (locked) { %><div class="alert alert-error"><%= HtmlUtil.esc(sheetLocked) %></div><% } %>

            <%
                int newNumber = 1;
                if (!editing) for (Lecture l : lectures) if (!l.getDate().toLocalDate().isAfter(sheetDate)) newNumber++;
            %>
            <div class="sheet-head">
                <div>
                    <h3>
                        <% if (editing) { %>
                            Lecture <%= sheetLecture.getNumber() %> &middot; <%= AttendanceRules.format(sheetDate) %>
                        <% } else { %>
                            New lecture &middot; <%= AttendanceRules.format(sheetDate) %>
                            <span class="muted" style="font-weight:400;">(will be Lecture <%= newNumber %><%= extra ? ", another lecture on this date" : "" %>)</span>
                        <% } %>
                    </h3>
                    <% if (editing) { %>
                        <div class="muted">
                            Marked<%= sheetLecture.getCreatedByName() != null ? " by " + HtmlUtil.esc(sheetLecture.getCreatedByName()) : "" %> on <%= fmt(sheetLecture.getCreatedAt()) %>
                            <% if (sheetLecture.getUpdatedAt() != null) { %> &middot; last changed<%= sheetLecture.getUpdatedByName() != null ? " by " + HtmlUtil.esc(sheetLecture.getUpdatedByName()) : "" %> on <%= fmt(sheetLecture.getUpdatedAt()) %><% } %>
                            <% if (!locked) { %> &middot; can be changed until <strong><%= AttendanceRules.format(AttendanceRules.lockDate(sheetDate)) %></strong><% } %>
                        </div>
                    <% } %>
                </div>
                <div class="tally" id="tally" aria-live="polite"></div>
            </div>

            <% if (summaries.isEmpty()) { %>
                <p>No students are enrolled in this course yet.</p>
            <% } else { %>
            <form method="post" action="manageAttendance" id="sheetForm">
                <input type="hidden" name="action" value="save">
                <input type="hidden" name="allocationId" value="<%= offering.getAllocationId() %>">
                <% if (editing) { %>
                    <input type="hidden" name="lectureId" value="<%= sheetLecture.getLectureId() %>">
                <% } else { %>
                    <input type="hidden" name="date" value="<%= sheetDate %>">
                    <% if (extra) { %><input type="hidden" name="extra" value="1"><% } %>
                <% } %>

                <div class="date-bar" style="margin-bottom:10px;">
                    <div class="form-group" style="flex:1; min-width:220px;">
                        <label class="form-label" for="topic">Topic <span class="muted">(optional)</span></label>
                        <input type="text" name="topic" id="topic" class="form-control" maxlength="200" <%= locked ? "disabled" : "" %>
                            placeholder="e.g. Inheritance and polymorphism" value="<%= editing && sheetLecture.getTopic() != null ? HtmlUtil.esc(sheetLecture.getTopic()) : "" %>">
                    </div>
                    <% if (!locked) { %>
                    <div class="bulk" role="group" aria-label="Mark everyone">
                        <button type="button" class="btn btn-sm btn-success js-all" data-status="Present">All present</button>
                        <button type="button" class="btn btn-sm btn-danger js-all" data-status="Absent">All absent</button>
                        <button type="button" class="btn btn-sm btn-secondary js-all" data-status="Leave">All on leave</button>
                    </div>
                    <% } %>
                </div>

                <div class="table-scroll">
                <table class="styled-table" style="margin-top:0;">
                    <thead>
                        <tr><th>Roll No</th><th>Student</th><th>Class</th><th>Status</th><th>Attendance so far</th></tr>
                    </thead>
                    <tbody>
                    <% for (AttendanceSummary s : summaries) {
                           String current = statuses.get(s.getEnrollmentId());
                           boolean hasRecord = current != null;
                           String chosen = editing ? current : "Present"; // new lectures start as Present
                           boolean required = !editing || hasRecord;
                           int pct = s.getPercent();
                    %>
                        <tr class="<%= editing && !hasRecord ? "unmarked-row" : "" %>">
                            <td><strong><%= HtmlUtil.esc(s.getRollNo()) %></strong></td>
                            <td><%= HtmlUtil.esc(s.getName() != null ? s.getName() : "-") %>
                                <% if (editing && !hasRecord) { %><br><span class="muted">Not marked in this lecture<%= locked ? "" : " (optional)" %></span><% } %></td>
                            <td><% if (s.getClassName() != null) { %><span class="tag"><%= HtmlUtil.esc(s.getClassName()) %></span><% } else { %><span class="tag tag-missing">Not assigned</span><% } %></td>
                            <td>
                                <div class="status-group" role="radiogroup" aria-label="Status for <%= HtmlUtil.esc(s.getRollNo()) %>">
                                    <% for (String st : new String[] {"Present", "Absent", "Leave"}) { %>
                                        <label><input type="radio" name="status_<%= s.getEnrollmentId() %>" value="<%= st %>"
                                            <%= st.equals(chosen) ? "checked" : "" %> <%= required ? "required" : "" %> <%= locked ? "disabled" : "" %>><span class="s-<%= st.toLowerCase() %>"><%= st %></span></label>
                                    <% } %>
                                </div>
                            </td>
                            <td><% if (pct < 0) { %><span class="muted">-</span><% } else { %>
                                <span class="<%= s.isLow() ? "low" : "" %>"><%= pct %>%</span>
                                <span class="muted">(<%= s.getPresent() %>/<%= s.getMarked() %>)</span><% } %></td>
                        </tr>
                    <% } %>
                    </tbody>
                </table>
                </div>

                <div class="sheet-actions">
                    <div>
                        <% if (editing && !locked && !extra) { %>
                            <a class="muted" href="manageAttendance?allocationId=<%= offering.getAllocationId() %>&date=<%= sheetDate %>&extra=1#sheet">+ Another lecture on this date</a>
                        <% } %>
                    </div>
                    <% if (!locked) { %>
                        <button type="submit" class="btn btn-primary"><%= editing ? "Update attendance" : "Save attendance" %></button>
                    <% } %>
                </div>
            </form>
            <% if (editing && !locked) { %>
                <form method="post" action="manageAttendance" style="margin-top:10px; text-align:right;"
                      data-confirm="Delete Lecture <%= sheetLecture.getNumber() %> (<%= AttendanceRules.format(sheetDate) %>) and its attendance for all students? Later lectures will be renumbered."
                      onsubmit="return confirm(this.dataset.confirm);">
                    <input type="hidden" name="action" value="delete">
                    <input type="hidden" name="allocationId" value="<%= offering.getAllocationId() %>">
                    <input type="hidden" name="lectureId" value="<%= sheetLecture.getLectureId() %>">
                    <button type="submit" class="btn btn-danger btn-sm">Delete this lecture</button>
                </form>
            <% } %>
            <% } %>
        </div>
        <% } %>

        <!-- LECTURE HISTORY -->
        <div class="card">
            <h3>Lectures</h3>
            <% if (lectures.isEmpty()) { %>
                <p class="muted" style="margin-top:8px;">No lectures marked yet.</p>
            <% } else { %>
            <div class="table-scroll">
            <table class="styled-table">
                <thead><tr><th>#</th><th>Date</th><th>Topic</th><th>Present</th><th>Absent</th><th>Leave</th><th>Changes</th><th></th></tr></thead>
                <tbody>
                <% for (int i = lectures.size() - 1; i >= 0; i--) {
                       Lecture l = lectures.get(i);
                       boolean open = AttendanceRules.checkDate(l.getDate(), term) == null; %>
                    <tr class="<%= editing && l.getLectureId() == sheetLecture.getLectureId() ? "current-row" : "" %>">
                        <td><strong><%= l.getNumber() %></strong></td>
                        <td style="white-space:nowrap;"><%= fmt(l.getDate()) %></td>
                        <td><%= l.getTopic() != null ? HtmlUtil.esc(l.getTopic()) : "<span class=\"muted\">-</span>" %></td>
                        <td><%= l.getPresentCount() %></td>
                        <td><%= l.getAbsentCount() %></td>
                        <td><%= l.getLeaveCount() %></td>
                        <td><% if (open) { %><span class="lock">Editable until <%= AttendanceRules.format(AttendanceRules.lockDate(l.getDate().toLocalDate())) %></span><% } else { %><span class="lock">Locked</span><% } %></td>
                        <td><a class="btn btn-sm <%= open ? "btn-primary" : "btn-secondary" %>" href="manageAttendance?allocationId=<%= offering.getAllocationId() %>&lectureId=<%= l.getLectureId() %>#sheet"><%= open ? "Edit" : "View" %></a></td>
                    </tr>
                <% } %>
                </tbody>
            </table>
            </div>
            <% } %>
        </div>

        <!-- STUDENT TOTALS -->
        <div class="card">
            <h3>Student attendance</h3>
            <p class="muted">Percentage = present &divide; lectures the student was marked in. Leave does not count as present. Below <%= AttendanceRules.LOW_ATTENDANCE_PERCENT %>% is shown in red.</p>
            <% if (summaries.isEmpty()) { %>
                <p style="margin-top:8px;">No students are enrolled in this course yet.</p>
            <% } else { %>
            <div class="table-scroll">
            <table class="styled-table">
                <thead><tr><th>Roll No</th><th>Student</th><th>Present</th><th>Absent</th><th>Leave</th><th>Attendance</th></tr></thead>
                <tbody>
                <% for (AttendanceSummary s : summaries) { int pct = s.getPercent(); %>
                    <tr>
                        <td><strong><%= HtmlUtil.esc(s.getRollNo()) %></strong></td>
                        <td><%= HtmlUtil.esc(s.getName() != null ? s.getName() : "-") %></td>
                        <td><%= s.getPresent() %></td>
                        <td><%= s.getAbsent() %></td>
                        <td><%= s.getLeave() %></td>
                        <td><% if (pct < 0) { %><span class="muted">No lectures yet</span><% } else { %><span class="<%= s.isLow() ? "low" : "" %>"><%= pct %>%</span><% } %></td>
                    </tr>
                <% } %>
                </tbody>
            </table>
            </div>
            <% } %>
        </div>
<% } %>
    </div>
</main>

<script>
(function () {
    // Picking a date opens that date's sheet straight away
    var date = document.getElementById("date");
    if (date) date.addEventListener("change", function () { if (date.value) document.getElementById("dateForm").submit(); });

    var form = document.getElementById("sheetForm");
    if (!form) return;
    var tally = document.getElementById("tally");
    var update = function () {
        var p = form.querySelectorAll('input[value="Present"]:checked').length;
        var a = form.querySelectorAll('input[value="Absent"]:checked').length;
        var l = form.querySelectorAll('input[value="Leave"]:checked').length;
        tally.innerHTML = '<b class="p">' + p + '</b> present &middot; <b class="a">' + a + '</b> absent &middot; <b class="l">' + l + '</b> on leave';
    };
    form.addEventListener("change", update);
    document.querySelectorAll(".js-all").forEach(function (b) {
        b.addEventListener("click", function () {
            form.querySelectorAll('input[type="radio"][value="' + b.dataset.status + '"]').forEach(function (r) { r.checked = true; });
            update();
        });
    });
    // Warn before leaving with unsaved changes
    var dirty = false;
    form.addEventListener("change", function () { dirty = true; });
    form.addEventListener("submit", function () { dirty = false; });
    window.addEventListener("beforeunload", function (e) { if (dirty) { e.preventDefault(); e.returnValue = ""; } });
    update();
})();
</script>
</body>
</html>
