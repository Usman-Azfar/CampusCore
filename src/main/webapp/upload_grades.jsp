<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Map, java.util.Collections, com.cms.models.CourseAllocation, com.cms.models.Grade, com.cms.models.User, com.cms.models.Semester, com.cms.util.GradeRules, com.cms.util.HtmlUtil" %>
<%!
    static String range(Semester s) {
        return s == null ? "" : s.getDateRange();
    }
    static String fmt(java.sql.Timestamp t) {
        return t == null ? "" : new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a").format(t);
    }
    // A typed mark that GradeRules would reject (blank is fine)
    static boolean invalid(String value, double max) {
        try {
            GradeRules.parseMark(value, max, "Mark");
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
%>
<%
    List<CourseAllocation> openOfferings = (List<CourseAllocation>) request.getAttribute("openOfferings");
    List<CourseAllocation> lockedOfferings = (List<CourseAllocation>) request.getAttribute("lockedOfferings");
    String sheetLocked = (String) request.getAttribute("sheetLocked");
    boolean locked = sheetLocked != null;
    CourseAllocation offering = (CourseAllocation) request.getAttribute("offering");
    List<Grade> sheet = (List<Grade>) request.getAttribute("sheet");
    Map<Integer, String[]> typed = (Map<Integer, String[]>) request.getAttribute("typed");
    if (sheet == null) sheet = Collections.emptyList();
    if (typed == null) typed = Collections.emptyMap();
    String maxS = GradeRules.format(GradeRules.SESSIONAL_MAX), maxM = GradeRules.format(GradeRules.MID_MAX),
           maxF = GradeRules.format(GradeRules.FINAL_MAX), maxT = GradeRules.format(GradeRules.TOTAL_MAX);
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Upload Grades | CampusCore</title>
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
        .sheet-head { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; align-items: center; margin-bottom: 10px; }
        .bulk { display: flex; gap: 6px; flex-wrap: wrap; }
        .mark-input { width: 62px; padding: 6px 4px; border: 1px solid #cbd5e1; border-radius: 6px; text-align: center; font-size: 0.95rem; }
        .mark-input:focus { outline: 2px solid var(--primary-color); outline-offset: 0; border-color: transparent; }
        .mark-input.invalid, .mark-input:invalid { border-color: #b91c1c; background: #fef2f2; }
        .total { font-weight: 700; white-space: nowrap; }
        .letter { display: inline-block; min-width: 34px; text-align: center; padding: 2px 8px; border-radius: 4px; font-weight: 700; background: var(--primary-color); color: #fff; }
        .letter.fail { background: #b91c1c; }
        .pending { color: #b45309; font-size: 0.85em; font-style: italic; white-space: nowrap; }
        .publish { display: inline-flex; align-items: center; justify-content: center; cursor: pointer; padding: 4px; }
        .updated { font-size: 0.8em; line-height: 1.35; white-space: nowrap; }
        #sheetForm .styled-table th, #sheetForm .styled-table td { padding-left: 10px; padding-right: 10px; }
        .publish input { width: 16px; height: 16px; }
        .sheet-actions { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; margin-top: 16px; align-items: center; }
        .scale { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 6px; margin-top: 10px; }
        .scale span { background: #f8fafc; border: 1px solid #e5e7eb; border-radius: 6px; padding: 6px 8px; font-size: 0.85em; }
        .changed-row { background: #fffbeb !important; }
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
            <h2>Upload Grades</h2>
            <p class="muted">Choose a course. Marks are Sessional (<%= maxS %>), Mid (<%= maxM %>) and Final (<%= maxF %>), out of <%= maxT %>.
                Students see a result only after you publish it. Grades can be changed until <%= GradeRules.EDIT_DAYS_AFTER_TERM %> days after the term ends.</p>
            <% if (openOfferings == null || openOfferings.isEmpty()) { %>
                <p style="margin-top:14px;">You have no courses open for grading right now.</p>
            <% } else { %>
            <div class="course-grid">
                <% for (CourseAllocation ca : openOfferings) {
                       java.time.LocalDate lock = GradeRules.lockDate(ca.getSemester()); %>
                    <a href="uploadGrades?allocationId=<%= ca.getAllocationId() %>" class="course-card">
                        <h3><%= HtmlUtil.esc(ca.getCourse().getCourseName()) %></h3>
                        <div><%= HtmlUtil.esc(ca.getCourse().getCourseCode()) %> &middot; <%= HtmlUtil.esc(ca.getSemester().getName()) %></div>
                        <div class="meta">
                            <%= ca.getEnrolledCount() %> student<%= ca.getEnrolledCount() == 1 ? "" : "s" %>
                            <br><%= ca.getCompleteCount() %>/<%= ca.getEnrolledCount() %> fully marked
                            &middot; <%= ca.getPublishedCount() %>/<%= ca.getEnrolledCount() %> published
                            <% if (lock != null) { %><br>Editable until <%= GradeRules.formatDate(lock) %><% } %>
                        </div>
                    </a>
                <% } %>
            </div>
            <% } %>
        </div>
        <% if (lockedOfferings != null && !lockedOfferings.isEmpty()) { %>
        <div class="card">
            <h3>Locked courses (view only)</h3>
            <p class="muted">More than <%= GradeRules.EDIT_DAYS_AFTER_TERM %> days have passed since their term ended, so grades can no longer be changed.</p>
            <div class="table-scroll">
            <table class="styled-table">
                <thead><tr><th>Course</th><th>Term</th><th>Students</th><th>Fully marked</th><th>Published</th><th>Locked on</th><th></th></tr></thead>
                <tbody>
                <% for (CourseAllocation ca : lockedOfferings) { %>
                    <tr>
                        <td><strong><%= HtmlUtil.esc(ca.getCourse().getCourseCode()) %></strong> <%= HtmlUtil.esc(ca.getCourse().getCourseName()) %></td>
                        <td><%= HtmlUtil.esc(ca.getSemester().getName()) %><br><span class="muted"><%= HtmlUtil.esc(range(ca.getSemester())) %></span></td>
                        <td><%= ca.getEnrolledCount() %></td>
                        <td><%= ca.getCompleteCount() %></td>
                        <td><%= ca.getPublishedCount() %></td>
                        <td style="white-space:nowrap;"><%= GradeRules.formatDate(GradeRules.lockDate(ca.getSemester()).plusDays(1)) %></td>
                        <td><a class="btn btn-secondary btn-sm" href="uploadGrades?allocationId=<%= ca.getAllocationId() %>">View</a></td>
                    </tr>
                <% } %>
                </tbody>
            </table>
            </div>
        </div>
        <% } %>

<% } else {
       Semester term = offering.getSemester();
       int complete = 0, published = 0;
       for (Grade g : sheet) { if (g.isComplete()) complete++; if (g.isPublished()) published++; }
%>
        <!-- STEP 2: one course -->
        <div class="card">
            <div class="page-head">
                <div>
                    <h2><%= HtmlUtil.esc(offering.getCourse().getCourseName()) %></h2>
                    <div class="facts">
                        <span class="tag"><%= HtmlUtil.esc(offering.getCourse().getCourseCode()) %></span>
                        <span class="tag tag-department"><%= HtmlUtil.esc(term.getName()) %><%= range(term).isEmpty() ? "" : " &middot; " + HtmlUtil.esc(range(term)) %></span>
                        <span class="tag"><%= sheet.size() %> student<%= sheet.size() == 1 ? "" : "s" %></span>
                        <span class="tag"><%= complete %>/<%= sheet.size() %> fully marked</span>
                        <span class="tag"><%= published %>/<%= sheet.size() %> published</span>
                    </div>
                </div>
                <a href="uploadGrades" class="btn btn-secondary">All courses</a>
            </div>
            <p class="muted" style="margin-top:12px;">
                Leave a mark blank until that assessment is held (blank is not zero). The grade is worked out once all three marks are entered.
                Published results appear in the student's Gradebook; complete published results also count towards their transcript and CGPA.
            </p>
            <% if (locked) { %>
                <div class="alert alert-error" style="margin-top:12px;"><%= HtmlUtil.esc(sheetLocked) %> You can still view the grades below.</div>
            <% } else if (GradeRules.lockDate(term) != null) { %>
                <p class="muted" style="margin-top:6px;">You can change grades until <strong><%= GradeRules.formatDate(GradeRules.lockDate(term)) %></strong>
                    (<%= GradeRules.EDIT_DAYS_AFTER_TERM %> days after the term ends on <%= GradeRules.formatDate(term.getEndDate().toLocalDate()) %>).</p>
            <% } %>
            <details style="margin-top:8px;">
                <summary class="muted" style="cursor:pointer;">Grading scale</summary>
                <div class="scale">
                    <% for (String[] row : GradeRules.scale()) { %>
                        <span><strong><%= row[0] %></strong> &middot; <%= row[1] %> &middot; <%= row[2] %> pts</span>
                    <% } %>
                </div>
            </details>
        </div>

        <div class="card">
            <% if (sheet.isEmpty()) { %>
                <p>No students are enrolled in this course yet.</p>
            <% } else { %>
            <form method="post" action="uploadGrades" id="sheetForm" novalidate>
                <input type="hidden" name="allocationId" value="<%= offering.getAllocationId() %>">

                <div class="sheet-head">
                    <h3>Grade sheet<%= locked ? " <span class=\"muted\" style=\"font-weight:400;\">(view only)</span>" : "" %></h3>
                    <% if (!locked) { %>
                    <div class="bulk" role="group" aria-label="Publish everyone">
                        <button type="button" class="btn btn-sm btn-success js-publish" data-publish="1">Publish all</button>
                        <button type="button" class="btn btn-sm btn-secondary js-publish" data-publish="">Unpublish all</button>
                    </div>
                    <% } %>
                </div>

                <div class="table-scroll">
                <table class="styled-table" style="margin-top:0;">
                    <thead>
                        <tr>
                            <th>Roll No</th><th>Student</th><th>Class</th>
                            <th>Sessional (<%= maxS %>)</th><th>Mid (<%= maxM %>)</th><th>Final (<%= maxF %>)</th>
                            <th>Total (<%= maxT %>)</th><th>Grade</th><th>Publish</th><th>Last updated</th>
                        </tr>
                    </thead>
                    <tbody>
                    <% for (Grade g : sheet) {
                           int id = g.getEnrollmentId();
                           User st = g.getEnrollment().getStudent();
                           String[] t = typed.get(id);
                           String vs = t != null ? t[0] : GradeRules.inputValue(g.getSessionalMarks());
                           String vm = t != null ? t[1] : GradeRules.inputValue(g.getMidMarks());
                           String vf = t != null ? t[2] : GradeRules.inputValue(g.getFinalMarks());
                           boolean pub = t != null ? !t[3].isEmpty() : g.isPublished();
                           String name = st.getProfile() != null ? st.getProfile().getFullName() : null;
                    %>
                        <tr data-row="<%= id %>" class="<%= t != null ? "changed-row" : "" %>">
                            <td><strong><%= HtmlUtil.esc(st.getUsername()) %></strong>
                                <input type="hidden" name="enrollmentId" value="<%= id %>"></td>
                            <td><%= HtmlUtil.esc(name != null && !name.isBlank() ? name : "-") %></td>
                            <td><% if (st.getClassName() != null) { %><span class="tag"><%= HtmlUtil.esc(st.getClassName()) %></span><% } else { %><span class="tag tag-missing">Not assigned</span><% } %></td>
                            <td><input type="number" inputmode="decimal" step="0.5" min="0" max="<%= maxS %>" name="sessional_<%= id %>"
                                       class="mark-input<%= t != null && invalid(vs, GradeRules.SESSIONAL_MAX) ? " invalid" : "" %>"
                                       value="<%= HtmlUtil.esc(vs) %>" <%= locked ? "disabled" : "" %> aria-label="Sessional marks for <%= HtmlUtil.esc(st.getUsername()) %>"></td>
                            <td><input type="number" inputmode="decimal" step="0.5" min="0" max="<%= maxM %>" name="mid_<%= id %>"
                                       class="mark-input<%= t != null && invalid(vm, GradeRules.MID_MAX) ? " invalid" : "" %>"
                                       value="<%= HtmlUtil.esc(vm) %>" <%= locked ? "disabled" : "" %> aria-label="Mid marks for <%= HtmlUtil.esc(st.getUsername()) %>"></td>
                            <td><input type="number" inputmode="decimal" step="0.5" min="0" max="<%= maxF %>" name="final_<%= id %>"
                                       class="mark-input<%= t != null && invalid(vf, GradeRules.FINAL_MAX) ? " invalid" : "" %>"
                                       value="<%= HtmlUtil.esc(vf) %>" <%= locked ? "disabled" : "" %> aria-label="Final marks for <%= HtmlUtil.esc(st.getUsername()) %>"></td>
                            <td class="total js-total">-</td>
                            <td class="js-grade"></td>
                            <td style="text-align:center;"><label class="publish" title="Published: the student can see this result"><input type="checkbox" name="publish_<%= id %>" value="1" <%= pub ? "checked" : "" %> <%= locked ? "disabled" : "" %> aria-label="Publish result for <%= HtmlUtil.esc(st.getUsername()) %>"></label></td>
                            <td class="muted updated"><% if (g.getUpdatedAt() != null) { %><%= fmt(g.getUpdatedAt()).replace(", ", "<br>") %><% if (g.getUpdatedByName() != null) { %><br>by <%= HtmlUtil.esc(g.getUpdatedByName()) %><% } %><% } else { %>-<% } %></td>
                        </tr>
                    <% } %>
                    </tbody>
                </table>
                </div>

                <div class="sheet-actions">
                    <span class="muted" id="summary" aria-live="polite"></span>
                    <% if (!locked) { %><button type="submit" class="btn btn-primary">Save grades</button><% } %>
                </div>
            </form>
            <% } %>
        </div>
<% } %>
    </div>
</main>

<script>
(function () {
    var form = document.getElementById("sheetForm");
    if (!form) return;
    var locked = <%= locked %>;
    // Same scale as the server (GradeRules): [lowest total, letter], best first
    var SCALE = <%= GradeRules.scaleJs() %>;
    var letter = function (total) {
        for (var i = 0; i < SCALE.length; i++) if (total >= SCALE[i][0]) return SCALE[i][1];
        return "F";
    };
    var markOf = function (input) {
        var v = input.value.trim();
        if (v === "") return null;
        var n = Number(v);
        return input.checkValidity() && isFinite(n) ? n : NaN;
    };
    var summary = document.getElementById("summary");
    var update = function () {
        var complete = 0, published = 0, rows = form.querySelectorAll("tr[data-row]");
        rows.forEach(function (tr) {
            var marks = Array.prototype.map.call(tr.querySelectorAll(".mark-input"), markOf);
            var bad = marks.some(function (m) { return m !== null && isNaN(m); });
            var entered = marks.filter(function (m) { return m !== null && !isNaN(m); });
            var total = entered.reduce(function (a, b) { return a + b; }, 0);
            var totalCell = tr.querySelector(".js-total"), gradeCell = tr.querySelector(".js-grade");
            if (bad) {
                totalCell.textContent = "-";
                gradeCell.innerHTML = '<span class="pending">Check marks</span>';
            } else if (entered.length === 3) {
                complete++;
                var l = letter(total);
                totalCell.textContent = total;
                gradeCell.innerHTML = '<span class="letter' + (l === "F" ? " fail" : "") + '">' + l + '</span>';
            } else {
                totalCell.textContent = entered.length ? total + " so far" : "-";
                gradeCell.innerHTML = '<span class="pending">' + (entered.length ? "In progress" : "Not marked") + '</span>';
            }
            if (tr.querySelector('input[type="checkbox"]').checked) published++;
        });
        summary.textContent = complete + " of " + rows.length + " fully marked, " + published + " published.";
    };
    form.addEventListener("input", update);
    form.addEventListener("change", update);
    form.querySelectorAll(".mark-input").forEach(function (i) {
        i.addEventListener("input", function () { i.classList.remove("invalid"); });
    });
    document.querySelectorAll(".js-publish").forEach(function (b) {
        b.addEventListener("click", function () {
            form.querySelectorAll('input[type="checkbox"]').forEach(function (c) { c.checked = !!b.dataset.publish; });
            dirty = true;
            update();
        });
    });
    // Stop on invalid marks before sending (the server checks again)
    form.addEventListener("submit", function (e) {
        var firstBad = Array.prototype.find.call(form.querySelectorAll(".mark-input"), function (i) { return !i.checkValidity(); });
        if (firstBad) {
            e.preventDefault();
            firstBad.classList.add("invalid");
            firstBad.focus();
            firstBad.reportValidity();
            return;
        }
        dirty = false;
    });
    // Warn before leaving with unsaved changes
    var dirty = <%= typed.isEmpty() ? "false" : "true" %>;
    form.addEventListener("input", function () { dirty = true; });
    form.addEventListener("change", function () { dirty = true; });
    window.addEventListener("beforeunload", function (e) { if (dirty && !locked) { e.preventDefault(); e.returnValue = ""; } });
    update();
})();
</script>
</body>
</html>
