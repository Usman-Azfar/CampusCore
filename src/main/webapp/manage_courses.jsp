<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Collections, com.cms.models.Course, com.cms.models.AcademicClass, com.cms.models.Department, com.cms.util.HtmlUtil" %>
<%
    List<Course> courses = (List<Course>) request.getAttribute("courses");
    List<Department> departments = (List<Department>) request.getAttribute("departments");
    List<AcademicClass> classes = (List<AcademicClass>) request.getAttribute("classes");
    if (courses == null) courses = Collections.emptyList();
    if (departments == null) departments = Collections.emptyList();
    if (classes == null) classes = Collections.emptyList();
    Course edit = (Course) request.getAttribute("courseToEdit");
    boolean isEdit = edit != null;
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Manage Courses | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .course-form { display: grid; grid-template-columns: 1fr 2fr 120px 1fr; gap: 14px; }
        .course-form .span-all { grid-column: 1 / -1; }
        .course-form .form-group { margin-bottom: 0; }
        /* Classes multi-select dropdown */
        .ms { position: relative; }
        .ms-summary { list-style: none; cursor: pointer; display: flex; justify-content: space-between; align-items: center; gap: 10px; user-select: none; }
        .ms-summary::-webkit-details-marker { display: none; }
        .ms[open] .ms-summary { border-color: var(--primary-color); }
        .ms-caret { color: #6b7280; font-size: 0.8em; }
        .ms[open] .ms-caret { transform: rotate(180deg); }
        .ms-panel { position: absolute; z-index: 20; left: 0; right: 0; top: calc(100% + 4px); background: #fff; border: 1px solid #d1d5db; border-radius: var(--border-radius); box-shadow: 0 10px 25px rgba(0,0,0,0.12); padding: 10px; }
        .ms-tools { display: flex; gap: 14px; margin: 8px 2px; font-size: 0.85em; }
        .ms-list { max-height: 230px; overflow-y: auto; border-top: 1px solid #f1f5f9; }
        .ms-opt { display: flex; align-items: center; gap: 10px; padding: 8px 6px; border-bottom: 1px solid #f8fafc; cursor: pointer; font-size: 0.92em; }
        .ms-opt:hover { background: #f8fafc; }
        .ms-opt:has(input:checked) { background: #eef2ff; }
        .ms-opt input { width: 16px; height: 16px; }
        .ms-meta { margin-left: auto; font-size: 0.8em; }
        .ms-empty { padding: 12px; text-align: center; color: #6b7280; font-size: 0.9em; }
        .ms-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
        .rp-pill { display: inline-flex; align-items: center; gap: 6px; background: #eef2ff; border: 1px solid #c7d2fe; color: #3730a3; border-radius: 999px; padding: 2px 4px 2px 10px; font-size: 0.82em; }
        .rp-pill button { border: none; background: none; cursor: pointer; color: #3730a3; font-size: 1rem; line-height: 1; padding: 0 5px; border-radius: 50%; }
        .rp-link { background: none; border: none; color: var(--primary-color); cursor: pointer; font-size: 0.9em; padding: 0; }
        .top-links { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }
        .row-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; min-width: 176px; }
        .row-actions .btn { padding: 5px 6px; font-size: 0.76rem; }
        #courseTable .tag { white-space: normal; }
        .row-actions .btn, .row-actions form, .row-actions form .btn { width: 100%; text-align: center; white-space: nowrap; }
        .course-cell { min-width: 170px; }
        .cell-sub { display: block; margin-top: 3px; }
        #courseTable th, #courseTable td { padding: 10px 10px; vertical-align: top; }
        .row-actions form { margin: 0; }
        .tags { display: flex; flex-wrap: wrap; gap: 4px; }
        .num { text-align: center; }
        @media (max-width: 900px) { .course-form { grid-template-columns: 1fr 1fr; } }
        @media (max-width: 560px) { .course-form { grid-template-columns: 1fr; } }
    </style>
</head>

<body>
<jsp:include page="sidebar.jsp" />

<main class="main-content">
    <jsp:include page="header.jsp" />

    <div class="page-container">

        <div class="top-links">
            <a href="manageAllocations" class="btn btn-primary btn-sm">Faculty Assignments (all courses)</a>
            <a href="manageSemesters" class="btn btn-secondary btn-sm">Manage Semesters</a>
            <a href="manageClasses" class="btn btn-secondary btn-sm">Classes &amp; Departments</a>
        </div>

        <% String okMsg = (String) request.getAttribute("successMessage");
           if (okMsg != null) { %>
            <div class="alert alert-success"><%= HtmlUtil.esc(okMsg) %></div>
        <% } %>
        <% String errMsg = (String) request.getAttribute("errorMessage");
           if (errMsg != null) { %>
            <div class="alert alert-error"><%= HtmlUtil.esc(errMsg) %></div>
        <% } %>

        <!-- Add / Edit Course -->
        <div class="card" id="courseForm">
            <h3><%= isEdit ? "Edit Course: " + HtmlUtil.esc(edit.getCourseCode()) : "Add New Course" %></h3>
            <form action="manageCourses" method="post" class="course-form" style="margin-top:14px;">
                <% if (isEdit) { %><input type="hidden" name="courseId" value="<%= edit.getCourseId() %>"><% } %>
                <div class="form-group">
                    <label class="form-label" for="courseCode">Course Code</label>
                    <input type="text" name="courseCode" id="courseCode" class="form-control" required maxlength="20"
                        placeholder="e.g. CS-101" value="<%= isEdit ? HtmlUtil.esc(edit.getCourseCode()) : "" %>">
                </div>
                <div class="form-group">
                    <label class="form-label" for="courseName">Course Name</label>
                    <input type="text" name="courseName" id="courseName" class="form-control" required maxlength="100"
                        placeholder="e.g. Introduction to Computing" value="<%= isEdit ? HtmlUtil.esc(edit.getCourseName()) : "" %>">
                </div>
                <div class="form-group">
                    <label class="form-label" for="creditHours">Credit Hours</label>
                    <input type="number" name="creditHours" id="creditHours" class="form-control" required min="1" max="6"
                        value="<%= isEdit ? edit.getCreditHours() : 3 %>">
                </div>
                <div class="form-group">
                    <label class="form-label" for="departmentId">Department</label>
                    <select name="departmentId" id="departmentId" class="form-control">
                        <option value="">-- No department --</option>
                        <% for (Department d : departments) { %>
                            <option value="<%= d.getDepartmentId() %>" <%= isEdit && edit.getDepartmentId() != null && edit.getDepartmentId() == d.getDepartmentId() ? "selected" : "" %>>
                                <%= HtmlUtil.esc(d.getName()) %>
                            </option>
                        <% } %>
                    </select>
                </div>

                <div class="form-group span-all">
                    <label class="form-label" id="classLabel">Classes (the course is offered to)</label>
                    <% if (classes.isEmpty()) { %>
                        <div class="form-control muted">No classes yet. <a href="manageClasses">Add classes</a> first.</div>
                    <% } else { %>
                        <!-- Multi-select dropdown: <details> opens/closes natively; the checkboxes submit as classIds -->
                        <details class="ms" id="classDropdown">
                            <summary class="form-control ms-summary" aria-labelledby="classLabel">
                                <span id="classSummary">Select classes (optional)</span>
                                <span class="ms-caret" aria-hidden="true">&#9662;</span>
                            </summary>
                            <div class="ms-panel">
                                <input type="search" id="classFilter" class="form-control" placeholder="Search classes..." aria-label="Search classes">
                                <div class="ms-tools">
                                    <button type="button" class="rp-link" id="classAll">Select all shown</button>
                                    <button type="button" class="rp-link" id="classNone">Clear</button>
                                </div>
                                <div class="ms-list" id="classList">
                                    <% for (AcademicClass c : classes) { boolean on = isEdit && edit.hasClass(c.getClassId()); %>
                                        <label class="ms-opt" data-name="<%= HtmlUtil.esc(c.getDisplayName().toLowerCase()) %>">
                                            <input type="checkbox" name="classIds" value="<%= c.getClassId() %>" <%= on ? "checked" : "" %>>
                                            <span><%= HtmlUtil.esc(c.getDisplayName()) %></span>
                                            <span class="muted ms-meta"><%= c.getStudentCount() %> student<%= c.getStudentCount() == 1 ? "" : "s" %></span>
                                        </label>
                                    <% } %>
                                    <div class="ms-empty" id="classEmpty" hidden>No classes match.</div>
                                </div>
                            </div>
                        </details>
                        <div class="ms-chips" id="classChips"></div>
                    <% } %>
                </div>

                <div class="form-group span-all">
                    <label class="form-label" for="description">Description</label>
                    <input type="text" name="description" id="description" class="form-control" maxlength="500"
                        placeholder="Brief description" value="<%= isEdit && edit.getDescription() != null ? HtmlUtil.esc(edit.getDescription()) : "" %>">
                </div>

                <div class="span-all" style="display:flex; gap:10px;">
                    <button type="submit" class="btn btn-primary"><%= isEdit ? "Save Changes" : "Add Course" %></button>
                    <% if (isEdit) { %><a href="manageCourses" class="btn btn-secondary">Cancel</a><% } %>
                </div>
            </form>
        </div>

        <!-- Existing Courses -->
        <div class="card">
            <h3>Existing Courses</h3>
            <div class="filter-bar" style="margin-top:12px;">
                <input type="search" id="courseSearch" class="form-control" placeholder="Search by code, name, teacher or class" aria-label="Search courses">
                <select id="courseDept" class="form-control" aria-label="Filter by department">
                    <option value="">All departments</option>
                    <% for (Department d : departments) { %><option value="<%= d.getDepartmentId() %>"><%= HtmlUtil.esc(d.getName()) %></option><% } %>
                    <option value="none">No department</option>
                </select>
                <select id="courseClass" class="form-control" aria-label="Filter by class">
                    <option value="">All classes</option>
                    <% for (AcademicClass c : classes) { %><option value="<%= c.getClassId() %>"><%= HtmlUtil.esc(c.getDisplayName()) %></option><% } %>
                    <option value="none">No class</option>
                </select>
                <span class="muted" id="courseCount"></span>
            </div>

            <div class="table-scroll">
            <table class="styled-table" id="courseTable">
                <thead>
                    <tr>
                        <th>Course</th>
                        <th>Department</th>
                        <th>Classes</th>
                        <th>Teacher (this semester)</th>
                        <th>Enrolled</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                <% if (courses.isEmpty()) { %>
                    <tr><td colspan="6" style="text-align:center;">No courses yet.</td></tr>
                <% } %>
                <% for (Course c : courses) {
                       StringBuilder classIdList = new StringBuilder();
                       StringBuilder classNames = new StringBuilder();
                       for (AcademicClass cl : c.getClasses()) {
                           classIdList.append(' ').append(cl.getClassId());
                           classNames.append(' ').append(cl.getDisplayName());
                       }
                %>
                    <tr class="course-row"
                        data-dept="<%= c.getDepartmentId() != null ? c.getDepartmentId() : "none" %>"
                        data-classes="<%= c.getClasses().isEmpty() ? "none" : classIdList.toString().trim() %>"
                        data-text="<%= HtmlUtil.esc((c.getCourseCode() + " " + c.getCourseName() + " " + (c.getDepartmentName() != null ? c.getDepartmentName() : "") + " " + (c.getActiveTeachers() != null ? c.getActiveTeachers() : "") + classNames).toLowerCase()) %>">
                        <td class="course-cell">
                            <strong><%= HtmlUtil.esc(c.getCourseCode()) %></strong> &middot; <%= HtmlUtil.esc(c.getCourseName()) %>
                            <span class="muted cell-sub"><%= c.getCreditHours() %> credit hour<%= c.getCreditHours() == 1 ? "" : "s" %><%= c.getDescription() != null ? " &middot; " + HtmlUtil.esc(c.getDescription()) : "" %></span>
                        </td>
                        <td>
                            <% if (c.getDepartmentName() != null) { %>
                                <span class="tag tag-department"><%= HtmlUtil.esc(c.getDepartmentName()) %></span>
                            <% } else { %><span class="tag tag-missing">None</span><% } %>
                        </td>
                        <td>
                            <div class="tags">
                            <% if (c.getClasses().isEmpty()) { %><span class="tag tag-missing">None</span><% } %>
                            <% for (AcademicClass cl : c.getClasses()) { %><span class="tag"><%= HtmlUtil.esc(cl.getDisplayName()) %></span><% } %>
                            </div>
                        </td>
                        <td>
                            <% if (c.getActiveTeachers() != null) { %>
                                <%= HtmlUtil.esc(c.getActiveTeachers()) %>
                            <% } else { %><span class="muted">Not assigned</span><% } %>
                            <% if (c.getAllocationCount() > 0) { %><br><span class="muted"><%= c.getAllocationCount() %> offering<%= c.getAllocationCount() == 1 ? "" : "s" %> in total</span><% } %>
                        </td>
                        <td class="num"><%= c.getEnrolledCount() %></td>
                        <td>
                            <div class="row-actions">
                                <a href="manageAllocations?courseId=<%= c.getCourseId() %>" class="btn btn-primary btn-sm">Assign Teacher</a>
                                <a href="manageEnrollment?courseId=<%= c.getCourseId() %>" class="btn btn-success btn-sm">Manage Students</a>
                                <a href="manageCourses?editId=<%= c.getCourseId() %>#courseForm" class="btn btn-secondary btn-sm">Edit</a>
                                <form action="manageCourses" method="post" data-confirm="Delete course <%= HtmlUtil.esc(c.getCourseCode()) %>?"
                                    onsubmit="return confirm(this.dataset.confirm);">
                                    <input type="hidden" name="action" value="delete">
                                    <input type="hidden" name="courseId" value="<%= c.getCourseId() %>">
                                    <button type="submit" class="btn btn-danger btn-sm" <%= c.getAllocationCount() > 0 ? "disabled title=\"Remove its teacher assignments first\"" : "" %>>Delete</button>
                                </form>
                            </div>
                        </td>
                    </tr>
                <% } %>
                    <tr id="courseNoMatch" hidden><td colspan="6" style="text-align:center;">No courses match your filters.</td></tr>
                </tbody>
            </table>
            </div>
        </div>
    </div>
</main>

<script>
(function () {
    // Classes multi-select dropdown: summary text, search, select all/clear, removable chips
    var dd = document.getElementById("classDropdown");
    if (dd) {
        var list = document.getElementById("classList");
        var filter = document.getElementById("classFilter");
        var summary = document.getElementById("classSummary");
        var chips = document.getElementById("classChips");
        var empty = document.getElementById("classEmpty");
        var opts = Array.prototype.slice.call(list.querySelectorAll(".ms-opt"));
        var label = function (o) { return o.querySelector("span").textContent; };

        var update = function () {
            var picked = opts.filter(function (o) { return o.querySelector("input").checked; });
            summary.textContent = !picked.length ? "Select classes (optional)"
                : picked.length <= 2 ? picked.map(label).join(", ")
                : picked.length + " classes selected";
            summary.classList.toggle("muted", !picked.length);
            chips.innerHTML = "";
            picked.forEach(function (o) {
                var chip = document.createElement("span");
                chip.className = "rp-pill";
                chip.innerHTML = "<span></span>";
                chip.firstChild.textContent = label(o);
                var x = document.createElement("button");
                x.type = "button";
                x.textContent = "×";
                x.setAttribute("aria-label", "Remove " + label(o));
                x.addEventListener("click", function () { o.querySelector("input").checked = false; update(); });
                chip.appendChild(x);
                chips.appendChild(chip);
            });
        };
        var applyFilter = function () {
            var t = filter.value.trim().toLowerCase();
            var shown = 0;
            opts.forEach(function (o) {
                o.hidden = !!t && o.dataset.name.indexOf(t) === -1;
                if (!o.hidden) shown++;
            });
            empty.hidden = shown > 0;
        };

        list.addEventListener("change", update);
        filter.addEventListener("input", applyFilter);
        document.getElementById("classAll").addEventListener("click", function () {
            opts.forEach(function (o) { if (!o.hidden) o.querySelector("input").checked = true; });
            update();
        });
        document.getElementById("classNone").addEventListener("click", function () {
            opts.forEach(function (o) { o.querySelector("input").checked = false; });
            update();
        });
        dd.addEventListener("toggle", function () { if (dd.open) filter.focus(); });
        // Close on outside click or Escape, like a normal dropdown
        document.addEventListener("click", function (e) { if (dd.open && !dd.contains(e.target)) dd.open = false; });
        dd.addEventListener("keydown", function (e) {
            if (e.key === "Escape") { dd.open = false; dd.querySelector("summary").focus(); }
            if (e.key === "Enter" && e.target === filter) e.preventDefault(); // do not submit the form
        });
        update();
    }

    // Course table filters
    var search = document.getElementById("courseSearch");
    var dept = document.getElementById("courseDept");
    var cls = document.getElementById("courseClass");
    var rows = Array.prototype.slice.call(document.querySelectorAll("#courseTable .course-row"));
    var countEl = document.getElementById("courseCount");
    var none = document.getElementById("courseNoMatch");
    function apply() {
        var terms = search.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
        var visible = 0;
        rows.forEach(function (r) {
            var ok = (!dept.value || r.dataset.dept === dept.value)
                && (!cls.value || (" " + r.dataset.classes + " ").indexOf(" " + cls.value + " ") !== -1)
                && terms.every(function (t) { return r.dataset.text.indexOf(t) !== -1; });
            r.hidden = !ok;
            if (ok) visible++;
        });
        countEl.textContent = "Showing " + visible + " of " + rows.length;
        none.hidden = !(rows.length && visible === 0);
    }
    [search, dept, cls].forEach(function (el) { el.addEventListener(el === search ? "input" : "change", apply); });
    apply();
})();
</script>
</body>
</html>
