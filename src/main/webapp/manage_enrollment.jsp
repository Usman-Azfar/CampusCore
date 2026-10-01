<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Map, java.util.Set, java.util.LinkedHashMap, java.util.Collections, com.cms.models.Course, com.cms.models.CourseAllocation, com.cms.models.User, com.cms.models.Enrollment, com.cms.models.AcademicClass, com.cms.util.HtmlUtil" %>
<%!
    private String name(User u) {
        if (u == null) return "Unknown";
        String n = u.getProfile() != null ? u.getProfile().getFullName() : null;
        return (n == null || n.trim().isEmpty()) ? u.getUsername() : n;
    }

    private String offeringLabel(CourseAllocation a) {
        User t = a.getTeacher();
        return a.getSemester().getName() + (a.getSemester().isActive() ? " (Active)" : "")
                + " · " + name(t) + " (" + t.getUsername() + ")"
                + " · " + a.getEnrolledCount() + " enrolled";
    }
%>
<%
    Course course = (Course) request.getAttribute("course");
    List<CourseAllocation> allocations = (List<CourseAllocation>) request.getAttribute("allocations");
    CourseAllocation selected = (CourseAllocation) request.getAttribute("selectedAllocation");
    List<User> students = (List<User>) request.getAttribute("students");
    Map<Integer, String> enrolledIn = (Map<Integer, String>) request.getAttribute("enrolledIn");
    List<Enrollment> records = (List<Enrollment>) request.getAttribute("records");
    List<AcademicClass> classes = (List<AcademicClass>) request.getAttribute("classes");
    if (allocations == null) allocations = Collections.emptyList();
    if (students == null) students = Collections.emptyList();
    if (enrolledIn == null) enrolledIn = Collections.emptyMap();
    if (records == null) records = Collections.emptyList();
    if (classes == null) classes = Collections.emptyList();

    // Distinct semesters that have records, for the enrolled-list filter
    Map<Integer, String> recordSemesters = new LinkedHashMap<>();
    for (Enrollment e : records)
        recordSemesters.put(e.getCourseAllocation().getSemester().getSemesterId(),
                e.getCourseAllocation().getSemester().getName() + (e.getCourseAllocation().getSemester().isActive() ? " (Active)" : ""));
    Integer defaultSemester = selected != null ? selected.getSemesterId() : null;
    if (defaultSemester != null && !recordSemesters.containsKey(defaultSemester)) defaultSemester = null;
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Manage Enrollment | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .course-head { display: flex; flex-wrap: wrap; gap: 8px 14px; align-items: center; }
        .course-head h2 { margin: 0; }
        .tags { display: flex; flex-wrap: wrap; gap: 4px; }
        .top-links { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }

        /* Student picker */
        .sp { border: 1px solid #ddd; border-radius: var(--border-radius); }
        .sp.sp-invalid { border-color: var(--danger-color); }
        .sp-chosen { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; padding: 10px 12px; border-bottom: 1px solid #eee; min-height: 46px; }
        .sp-chosen-label { font-size: 0.85em; font-weight: 600; }
        .sp-placeholder { font-size: 0.85em; color: #9ca3af; }
        .sp-pill { display: inline-flex; align-items: center; gap: 6px; background: #eff6ff; border: 1px solid #bfdbfe; color: #1e3a8a; border-radius: 999px; padding: 2px 4px 2px 10px; font-size: 0.82em; }
        .sp-pill button { border: none; background: none; cursor: pointer; color: #1e3a8a; font-size: 1rem; line-height: 1; padding: 0 5px; border-radius: 50%; }
        .sp-panel { padding: 12px; }
        .sp-row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; margin-top: 10px; }
        .sp-row > .form-control { flex: 1 1 0; min-width: 160px; }
        .sp-chip { border: 1px solid #d1d5db; background: #fff; border-radius: 999px; padding: 4px 12px; font-size: 0.85em; cursor: pointer; }
        .sp-chip[aria-pressed="true"] { background: var(--primary-color); border-color: var(--primary-color); color: #fff; }
        .sp-toggle { font-size: 0.85em; display: inline-flex; gap: 6px; align-items: center; white-space: nowrap; }
        .sp-status { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 0.82em; color: #6b7280; margin: 10px 0 6px; }
        .sp-selectall { display: inline-flex; align-items: center; gap: 6px; font-weight: 600; color: var(--text-color); cursor: pointer; }
        .sp-selectall input { width: 16px; height: 16px; }
        .sp-list { list-style: none; max-height: 320px; overflow-y: auto; border: 1px solid #eee; border-radius: 6px; }
        .sp-group { background: #f8fafc; font-size: 0.75em; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: #475569; padding: 6px 10px; position: sticky; top: 0; z-index: 1; border-bottom: 1px solid #e5e7eb; }
        .sp-item { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-bottom: 1px solid #f3f4f6; cursor: pointer; }
        .sp-item:hover, .sp-item.sp-active { background: #f8fafc; }
        .sp-item[aria-selected="true"] { background: #eff6ff; }
        .sp-item[aria-disabled="true"] { cursor: not-allowed; opacity: 0.6; }
        .sp-check { width: 18px; height: 18px; border: 2px solid #cbd5e1; border-radius: 4px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 12px; }
        .sp-item[aria-selected="true"] .sp-check { background: var(--primary-color); border-color: var(--primary-color); }
        .sp-info { flex: 1; min-width: 0; }
        .sp-name { font-weight: 600; }
        .sp-sub { font-size: 0.8em; color: #6b7280; }
        .sp-name mark, .sp-sub mark { background: #fde68a; color: inherit; padding: 0; }
        .sp-badge { font-size: 0.72em; padding: 2px 8px; border-radius: 999px; background: #d1fae5; color: #047857; white-space: nowrap; }
        .sp-empty { padding: 18px; text-align: center; color: #6b7280; font-size: 0.9em; }
        .sp-error { color: var(--danger-color); font-size: 0.85em; margin-top: 6px; display: none; }
        .rp-link { background: none; border: none; color: var(--primary-color); cursor: pointer; font-size: 0.9em; padding: 0; }
    </style>
</head>

<body>
<jsp:include page="sidebar.jsp" />

<main class="main-content">
    <jsp:include page="header.jsp" />

    <div class="page-container">
        <div class="top-links">
            <a href="manageCourses" class="btn btn-secondary btn-sm">&larr; Back to Courses</a>
            <a href="manageAllocations?courseId=<%= course.getCourseId() %>" class="btn btn-secondary btn-sm">Assign Teacher</a>
        </div>

        <% String okMsg = (String) request.getAttribute("successMessage");
           if (okMsg != null) { %>
            <div class="alert alert-success"><%= HtmlUtil.esc(okMsg) %></div>
        <% } %>
        <% String errMsg = (String) request.getAttribute("errorMessage");
           if (errMsg != null) { %>
            <div class="alert alert-error"><%= HtmlUtil.esc(errMsg) %></div>
        <% } %>

        <div class="card">
            <div class="course-head">
                <h2><%= HtmlUtil.esc(course.getCourseCode()) %> - <%= HtmlUtil.esc(course.getCourseName()) %></h2>
                <% if (course.getDepartmentName() != null) { %><span class="tag tag-department"><%= HtmlUtil.esc(course.getDepartmentName()) %></span><% } %>
                <div class="tags">
                    <% for (AcademicClass cl : course.getClasses()) { %><span class="tag"><%= HtmlUtil.esc(cl.getDisplayName()) %></span><% } %>
                    <% if (course.getClasses().isEmpty()) { %><span class="tag tag-missing">No class assigned</span><% } %>
                </div>
            </div>

            <h3 style="margin-top:18px;">Enroll Students</h3>
            <% if (allocations.isEmpty()) { %>
                <p style="margin-top:10px;">This course has no teacher assignment yet, so students cannot be enrolled.
                    <a href="manageAllocations?courseId=<%= course.getCourseId() %>">Assign a teacher first</a>.</p>
            <% } else { %>
            <form action="manageEnrollment" method="post" id="enrollForm" novalidate style="margin-top:12px;">
                <input type="hidden" name="courseId" value="<%= course.getCourseId() %>">

                <div class="form-group">
                    <label class="form-label" for="allocationId">Semester offering (semester &middot; teacher)</label>
                    <select name="allocationId" id="allocationId" class="form-control" required
                        onchange="window.location.href='manageEnrollment?courseId=<%= course.getCourseId() %>&allocationId=' + this.value">
                        <% for (CourseAllocation a : allocations) { %>
                            <option value="<%= a.getAllocationId() %>" <%= selected != null && selected.getAllocationId() == a.getAllocationId() ? "selected" : "" %>>
                                <%= HtmlUtil.esc(offeringLabel(a)) %>
                            </option>
                        <% } %>
                    </select>
                </div>

                <div class="form-group">
                    <label class="form-label" for="studentId" id="stuLabel">Students</label>
                    <!-- Native multi-select: works without JavaScript and feeds the picker -->
                    <select name="studentId" id="studentId" class="form-control" multiple size="8">
                        <% for (User s : students) {
                               String in = enrolledIn.get(s.getUserId());
                               boolean ofCourseClass = s.getClassId() != null && course.hasClass(s.getClassId());
                        %>
                            <option value="<%= s.getUserId() %>" <%= in != null ? "disabled" : "" %>
                                data-name="<%= HtmlUtil.esc(name(s)) %>" data-roll="<%= HtmlUtil.esc(s.getUsername()) %>"
                                data-class-id="<%= s.getClassId() != null ? s.getClassId() : "" %>"
                                data-class="<%= HtmlUtil.esc(s.getClassName() != null ? s.getClassName() : "") %>"
                                data-course-class="<%= ofCourseClass %>"
                                data-enrolled="<%= HtmlUtil.esc(in != null ? in : "") %>">
                                <%= HtmlUtil.esc(name(s)) %> (<%= HtmlUtil.esc(s.getUsername()) %>)<%= s.getClassName() != null ? " · " + HtmlUtil.esc(s.getClassName()) : "" %><%= in != null ? " - already enrolled" : "" %>
                            </option>
                        <% } %>
                    </select>
                    <input type="hidden" name="studentIds" id="studentIds" disabled>

                    <div class="sp" id="sp" hidden>
                        <div class="sp-chosen" id="spChosen" aria-live="polite"></div>
                        <div class="sp-panel">
                            <input type="search" id="spSearch" class="form-control" autocomplete="off"
                                placeholder="Search by name, roll number or class" aria-labelledby="stuLabel" aria-controls="spList">
                            <div class="sp-row">
                                <button type="button" class="sp-chip" id="spAll" aria-pressed="true">All students</button>
                                <button type="button" class="sp-chip" id="spCourseClasses" aria-pressed="false" <%= course.getClasses().isEmpty() ? "hidden" : "" %>>This course's classes</button>
                                <select id="spClass" class="form-control" aria-label="Filter by class">
                                    <option value="">All classes</option>
                                    <% for (AcademicClass cl : classes) { %><option value="<%= cl.getClassId() %>"><%= HtmlUtil.esc(cl.getDisplayName()) %></option><% } %>
                                    <option value="none">No class assigned</option>
                                </select>
                                <label class="sp-toggle"><input type="checkbox" id="spHideEnrolled" checked> Hide already enrolled</label>
                            </div>
                            <div class="sp-status">
                                <label class="sp-selectall"><input type="checkbox" id="spSelectAll"> <span id="spSelectAllText">Select all</span></label>
                                <span id="spCount" aria-live="polite"></span>
                                <button type="button" class="rp-link" id="spClear" hidden>Clear filters</button>
                            </div>
                            <ul class="sp-list" id="spList" role="listbox" aria-multiselectable="true" aria-labelledby="stuLabel"></ul>
                        </div>
                    </div>
                    <div class="sp-error" id="spError">Please select at least one student to enroll.</div>
                </div>

                <button type="submit" class="btn btn-primary" id="enrollBtn">Enroll Students</button>
            </form>
            <% } %>
        </div>

        <!-- Enrolled list -->
        <div class="card" id="enrolled">
            <h3>Students Enrolled in this Course</h3>
            <div class="filter-bar" style="margin-top:12px;">
                <input type="search" id="eSearch" class="form-control" placeholder="Search by name or roll number" aria-label="Search enrolled students">
                <select id="eSemester" class="form-control" aria-label="Filter by semester">
                    <option value="">All semesters</option>
                    <% for (Map.Entry<Integer, String> s : recordSemesters.entrySet()) { %>
                        <option value="<%= s.getKey() %>" <%= s.getKey().equals(defaultSemester) ? "selected" : "" %>><%= HtmlUtil.esc(s.getValue()) %></option>
                    <% } %>
                </select>
                <select id="eClass" class="form-control" aria-label="Filter by class">
                    <option value="">All classes</option>
                    <% for (AcademicClass cl : classes) { %><option value="<%= cl.getClassId() %>"><%= HtmlUtil.esc(cl.getDisplayName()) %></option><% } %>
                    <option value="none">No class assigned</option>
                </select>
                <select id="eStatus" class="form-control" aria-label="Filter by status">
                    <option value="ENROLLED" selected>Enrolled</option>
                    <option value="">All statuses</option>
                    <option value="DROPPED">Dropped</option>
                    <option value="WITHDRAWN">Withdrawn</option>
                </select>
                <span class="muted" id="eCount"></span>
            </div>

            <div class="table-scroll">
            <table class="styled-table" id="eTable">
                <thead>
                    <tr>
                        <th>Roll Number</th>
                        <th>Name</th>
                        <th>Class</th>
                        <th>Semester</th>
                        <th>Status</th>
                        <th>Action</th>
                    </tr>
                </thead>
                <tbody>
                <% if (records.isEmpty()) { %>
                    <tr><td colspan="6" style="text-align:center;">No students have been enrolled in this course yet.</td></tr>
                <% } %>
                <% for (Enrollment e : records) {
                       User s = e.getStudent();
                       String st = e.getStatus();
                       String badge = "ENROLLED".equals(st) ? "status-active" : "DROPPED".equals(st) ? "status-rejected" : "status-pending";
                %>
                    <tr class="e-row" data-semester="<%= e.getCourseAllocation().getSemester().getSemesterId() %>"
                        data-class="<%= s.getClassId() != null ? s.getClassId() : "none" %>" data-status="<%= HtmlUtil.esc(st) %>"
                        data-text="<%= HtmlUtil.esc((s.getUsername() + " " + name(s) + " " + (s.getClassName() != null ? s.getClassName() : "")).toLowerCase()) %>">
                        <td><%= HtmlUtil.esc(s.getUsername()) %></td>
                        <td><%= HtmlUtil.esc(name(s)) %></td>
                        <td><% if (s.getClassName() != null) { %><span class="tag"><%= HtmlUtil.esc(s.getClassName()) %></span><% } else { %><span class="tag tag-missing">Not assigned</span><% } %></td>
                        <td>
                            <%= HtmlUtil.esc(e.getCourseAllocation().getSemester().getName()) %>
                            <br><span class="muted"><%= HtmlUtil.esc(name(e.getCourseAllocation().getTeacher())) %></span>
                        </td>
                        <td><span class="status-badge <%= badge %>"><%= HtmlUtil.esc(st) %></span></td>
                        <td>
                            <% if ("ENROLLED".equals(st)) { %>
                                <form action="manageEnrollment" method="post" style="margin:0;"
                                    data-confirm="Drop <%= HtmlUtil.esc(name(s)) %> from <%= HtmlUtil.esc(course.getCourseCode()) %>? Their grades and attendance are kept."
                                    onsubmit="return confirm(this.dataset.confirm);">
                                    <input type="hidden" name="action" value="drop">
                                    <input type="hidden" name="courseId" value="<%= course.getCourseId() %>">
                                    <input type="hidden" name="enrollmentId" value="<%= e.getEnrollmentId() %>">
                                    <% if (selected != null) { %><input type="hidden" name="allocationId" value="<%= selected.getAllocationId() %>"><% } %>
                                    <button type="submit" class="btn btn-danger btn-sm">Drop</button>
                                </form>
                            <% } else { %><span class="muted">-</span><% } %>
                        </td>
                    </tr>
                <% } %>
                    <tr id="eNoMatch" hidden><td colspan="6" style="text-align:center;">No enrollments match your filters.</td></tr>
                </tbody>
            </table>
            </div>
        </div>
    </div>
</main>

<script>
(function () {
    function esc(s) {
        return String(s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; });
    }
    function mark(text, terms) {
        var html = esc(text);
        terms.forEach(function (t) {
            var re = new RegExp("(" + esc(t).replace(/[.*+?^$(){}|[\]\\]/g, "\\$&") + ")", "ig");
            html = html.replace(re, "<mark>$1</mark>");
        });
        return html;
    }

    /* ---------- Student picker ---------- */
    var select = document.getElementById("studentId");
    var sp = document.getElementById("sp");
    if (select && sp) {
        var MAX_RENDER = 300, MAX_PILLS = 12, CONFIRM_ABOVE = 25;
        var search = document.getElementById("spSearch"), list = document.getElementById("spList");
        var chipAll = document.getElementById("spAll"), chipCourse = document.getElementById("spCourseClasses");
        var classSel = document.getElementById("spClass"), hideEnrolled = document.getElementById("spHideEnrolled");
        var selectAll = document.getElementById("spSelectAll"), selectAllText = document.getElementById("spSelectAllText");
        var countEl = document.getElementById("spCount"), clearBtn = document.getElementById("spClear");
        var chosen = document.getElementById("spChosen"), errorEl = document.getElementById("spError");
        var btn = document.getElementById("enrollBtn");

        var people = Array.prototype.map.call(select.options, function (o) {
            return { id: o.value, name: o.dataset.name, roll: o.dataset.roll, classId: o.dataset.classId, cls: o.dataset.class,
                     courseClass: o.dataset.courseClass === "true", enrolled: o.dataset.enrolled, option: o };
        });
        var state = { courseOnly: false, results: [], active: -1, selected: {}, showAll: false };

        select.hidden = true;
        sp.hidden = false;

        function matches(p, terms) {
            if (state.courseOnly && !p.courseClass) return false;
            if (classSel.value && (classSel.value === "none" ? p.classId : p.classId !== classSel.value)) return false;
            if (hideEnrolled.checked && p.enrolled) return false;
            var hay = (p.name + " " + p.roll + " " + p.cls).toLowerCase();
            return terms.every(function (t) { return hay.indexOf(t) !== -1; });
        }
        function selectable(p) { return !p.enrolled; }

        function setSel(p, on) {
            if (!selectable(p)) return;
            if (on) state.selected[p.id] = true; else delete state.selected[p.id];
            p.option.selected = on;
        }
        function toggle(p) {
            setSel(p, !state.selected[p.id]);
            sp.classList.remove("sp-invalid");
            errorEl.style.display = "none";
            refresh();
        }

        function renderChosen() {
            var picked = people.filter(function (p) { return state.selected[p.id]; });
            chosen.innerHTML = "";
            if (!picked.length) {
                chosen.innerHTML = '<span class="sp-placeholder">No students selected yet — tick students below, or use Select all.</span>';
                return;
            }
            var label = document.createElement("span");
            label.className = "sp-chosen-label";
            label.textContent = "Selected (" + picked.length + "):";
            chosen.appendChild(label);
            (state.showAll ? picked : picked.slice(0, MAX_PILLS)).forEach(function (p) {
                var pill = document.createElement("span");
                pill.className = "sp-pill";
                pill.innerHTML = "<span>" + esc(p.name) + "</span>";
                var x = document.createElement("button");
                x.type = "button";
                x.textContent = "×";
                x.setAttribute("aria-label", "Remove " + p.name);
                x.addEventListener("click", function () { toggle(p); });
                pill.appendChild(x);
                chosen.appendChild(pill);
            });
            if (picked.length > MAX_PILLS) {
                var more = document.createElement("button");
                more.type = "button";
                more.className = "rp-link";
                more.textContent = state.showAll ? "Show less" : "+" + (picked.length - MAX_PILLS) + " more";
                more.addEventListener("click", function () { state.showAll = !state.showAll; renderChosen(); });
                chosen.appendChild(more);
            }
            var clr = document.createElement("button");
            clr.type = "button";
            clr.className = "rp-link";
            clr.style.marginLeft = "auto";
            clr.textContent = "Clear selection";
            clr.addEventListener("click", function () { people.forEach(function (p) { setSel(p, false); }); state.showAll = false; refresh(); });
            chosen.appendChild(clr);
        }

        function refresh() {
            Array.prototype.forEach.call(list.querySelectorAll(".sp-item"), function (li) {
                var on = !!state.selected[li.dataset.id];
                li.setAttribute("aria-selected", on ? "true" : "false");
                li.querySelector(".sp-check").textContent = on ? "✓" : "";
            });
            renderChosen();
            var pickable = state.results.filter(selectable);
            var sel = pickable.filter(function (p) { return state.selected[p.id]; }).length;
            selectAll.disabled = pickable.length === 0;
            selectAll.checked = pickable.length > 0 && sel === pickable.length;
            selectAll.indeterminate = sel > 0 && sel < pickable.length;
            var filtered = search.value.trim() || state.courseOnly || classSel.value;
            selectAllText.textContent = (filtered ? "Select all matching" : "Select all") + " (" + pickable.length + ")";
            var n = Object.keys(state.selected).length;
            btn.textContent = n > 1 ? "Enroll " + n + " students" : n === 1 ? "Enroll 1 student" : "Enroll Students";
        }

        function item(p, terms, index) {
            var li = document.createElement("li");
            li.className = "sp-item";
            li.dataset.id = p.id;
            li.setAttribute("role", "option");
            if (!selectable(p)) li.setAttribute("aria-disabled", "true");
            li.innerHTML = '<span class="sp-check" aria-hidden="true"></span>' +
                '<div class="sp-info"><div class="sp-name">' + mark(p.name, terms) + '</div>' +
                '<div class="sp-sub">' + mark(p.roll + (p.cls ? " · " + p.cls : " · No class"), terms) + '</div></div>' +
                (p.enrolled ? '<span class="sp-badge">Enrolled · ' + esc(p.enrolled) + '</span>' : "");
            li.addEventListener("mousedown", function (e) { e.preventDefault(); });
            li.addEventListener("click", function () { if (selectable(p)) { state.active = index; toggle(p); } });
            return li;
        }

        function render() {
            var terms = search.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
            state.results = people.filter(function (p) { return matches(p, terms); });
            // The course's classes first, then everyone else
            var first = state.results.filter(function (p) { return p.courseClass; });
            var rest = state.results.filter(function (p) { return !p.courseClass; });
            state.results = first.concat(rest);

            list.innerHTML = "";
            if (!state.results.length) list.innerHTML = '<li class="sp-empty">No students match your filters.</li>';
            var drawn = 0;
            [[first, "Students of this course's classes"], [rest, first.length ? "Other students" : "Students"]].forEach(function (g) {
                if (!g[0].length || drawn >= MAX_RENDER) return;
                var h = document.createElement("li");
                h.className = "sp-group";
                h.textContent = g[1] + " (" + g[0].length + ")";
                list.appendChild(h);
                g[0].slice(0, MAX_RENDER - drawn).forEach(function (p) { list.appendChild(item(p, terms, drawn)); drawn++; });
            });

            countEl.textContent = state.results.length > MAX_RENDER
                ? "Showing first " + MAX_RENDER + " of " + state.results.length + " (Select all still covers every match)"
                : state.results.length + " of " + people.length + " students";
            chipAll.setAttribute("aria-pressed", state.courseOnly ? "false" : "true");
            chipCourse.setAttribute("aria-pressed", state.courseOnly ? "true" : "false");
            clearBtn.hidden = !(terms.length || state.courseOnly || classSel.value || !hideEnrolled.checked);
            refresh();
        }

        chipAll.addEventListener("click", function () { state.courseOnly = false; render(); });
        chipCourse.addEventListener("click", function () { state.courseOnly = true; classSel.value = ""; render(); });
        classSel.addEventListener("change", function () { if (classSel.value) state.courseOnly = false; render(); });
        hideEnrolled.addEventListener("change", render);
        search.addEventListener("input", render);
        clearBtn.addEventListener("click", function () {
            search.value = ""; state.courseOnly = false; classSel.value = ""; hideEnrolled.checked = true; render();
        });
        selectAll.addEventListener("change", function () {
            var on = selectAll.checked;
            state.results.forEach(function (p) { setSel(p, on); });
            sp.classList.remove("sp-invalid");
            errorEl.style.display = "none";
            refresh();
        });
        search.addEventListener("keydown", function (e) {
            var items = list.querySelectorAll(".sp-item");
            if (e.key === "ArrowDown" || e.key === "ArrowUp") {
                e.preventDefault();
                state.active = Math.max(0, Math.min(items.length - 1, state.active + (e.key === "ArrowDown" ? 1 : -1)));
                items.forEach(function (el, i) { el.classList.toggle("sp-active", i === state.active); });
                if (items[state.active]) items[state.active].scrollIntoView({ block: "nearest" });
            } else if (e.key === "Enter") {
                e.preventDefault();
                if (items[state.active]) items[state.active].click();
            }
        });

        // Start with the course's classes when it has any
        state.courseOnly = !chipCourse.hidden && people.some(function (p) { return p.courseClass && !p.enrolled; });
        render();

        document.getElementById("enrollForm").addEventListener("submit", function (e) {
            var ids = people.filter(function (p) { return state.selected[p.id]; }).map(function (p) { return p.id; });
            if (!ids.length) {
                e.preventDefault();
                sp.classList.add("sp-invalid");
                errorEl.style.display = "block";
                search.focus();
                return;
            }
            if (ids.length > CONFIRM_ABOVE && !confirm("Enroll " + ids.length + " students in this offering?")) {
                e.preventDefault();
                return;
            }
            var hidden = document.getElementById("studentIds");
            hidden.value = ids.join(",");
            hidden.disabled = false;
            select.disabled = true; // send one comma-separated field instead of many
            btn.disabled = true;
            btn.textContent = "Enrolling...";
        });
        window.addEventListener("pageshow", function () {
            select.disabled = false;
            document.getElementById("studentIds").disabled = true;
            btn.disabled = false;
            refresh();
        });
    }

    /* ---------- Enrolled list filters ---------- */
    var eSearch = document.getElementById("eSearch"), eSem = document.getElementById("eSemester");
    var eClass = document.getElementById("eClass"), eStatus = document.getElementById("eStatus");
    var rows = Array.prototype.slice.call(document.querySelectorAll("#eTable .e-row"));
    var eCount = document.getElementById("eCount"), eNone = document.getElementById("eNoMatch");
    function applyList() {
        var terms = eSearch.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
        var visible = 0;
        rows.forEach(function (r) {
            var ok = (!eSem.value || r.dataset.semester === eSem.value)
                && (!eClass.value || r.dataset.class === eClass.value)
                && (!eStatus.value || r.dataset.status === eStatus.value)
                && terms.every(function (t) { return r.dataset.text.indexOf(t) !== -1; });
            r.hidden = !ok;
            if (ok) visible++;
        });
        eCount.textContent = "Showing " + visible + " of " + rows.length + " record" + (rows.length === 1 ? "" : "s");
        eNone.hidden = !(rows.length && visible === 0);
    }
    [eSearch, eSem, eClass, eStatus].forEach(function (el) { el.addEventListener(el === eSearch ? "input" : "change", applyList); });
    applyList();
})();
</script>
</body>
</html>
