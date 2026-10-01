<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Map, java.util.HashMap, java.util.LinkedHashMap, java.util.ArrayList, java.util.Collections, java.util.Objects, com.cms.models.Course, com.cms.models.User, com.cms.models.Semester, com.cms.models.CourseAllocation, com.cms.models.AcademicClass, com.cms.util.HtmlUtil" %>
<%!
    private String teacherLabel(User t) {
        String dept = t.getDepartmentName() != null ? t.getDepartmentName() : "No department";
        return t.getDisplayName() + " · " + dept;
    }

    private String classTags(Course c) {
        if (c == null || c.getClasses().isEmpty()) return "<span class=\"tag tag-missing\">No class</span>";
        StringBuilder sb = new StringBuilder();
        for (AcademicClass cl : c.getClasses())
            sb.append("<span class=\"tag\">").append(HtmlUtil.esc(cl.getDisplayName())).append("</span> ");
        return sb.toString();
    }

    private String deptTag(String name) {
        return name == null ? "<span class=\"tag tag-missing\">No department</span>"
                : "<span class=\"tag tag-department\">" + HtmlUtil.esc(name) + "</span>";
    }
%>
<%
    List<Course> courses = (List<Course>) request.getAttribute("courses");
    List<User> teachers = (List<User>) request.getAttribute("teachers");
    List<Semester> semesters = (List<Semester>) request.getAttribute("semesters");
    List<CourseAllocation> allocs = (List<CourseAllocation>) request.getAttribute("allocations");
    if (courses == null) courses = Collections.emptyList();
    if (teachers == null) teachers = Collections.emptyList();
    if (semesters == null) semesters = Collections.emptyList();
    if (allocs == null) allocs = Collections.emptyList();
    Course locked = (Course) request.getAttribute("lockedCourse");

    Map<Integer, Course> courseById = new HashMap<>();
    for (Course c : courses) courseById.put(c.getCourseId(), c);
    if (locked != null && courseById.containsKey(locked.getCourseId())) locked = courseById.get(locked.getCourseId());

    // Teacher groups for the (locked) course: its department first, then everyone else
    Integer focusDept = locked != null ? locked.getDepartmentId() : null;
    Map<String, List<User>> teacherGroups = new LinkedHashMap<>();
    if (focusDept != null) {
        List<User> same = new ArrayList<>(), other = new ArrayList<>();
        for (User t : teachers) (Objects.equals(t.getDepartmentId(), focusDept) ? same : other).add(t);
        teacherGroups.put(locked.getDepartmentName() + " Department (course department)", same);
        teacherGroups.put("Other departments", other);
    } else {
        for (User t : teachers) {
            String g = t.getDepartmentName() != null ? t.getDepartmentName() + " Department" : "No department";
            teacherGroups.computeIfAbsent(g, k -> new ArrayList<>()).add(t);
        }
    }
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Faculty Assignment | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .assign-form { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 14px; }
        .assign-form .form-group { margin-bottom: 0; }
        .assign-form .span-all { grid-column: 1 / -1; }
        .course-context { background: #f8fafc; border: 1px solid #e5e7eb; border-radius: var(--border-radius); padding: 12px 14px; margin: 12px 0 16px; display: flex; flex-wrap: wrap; gap: 8px 16px; align-items: center; }
        .course-context strong { font-size: 1.05rem; }
        .current-note { font-size: 0.88em; padding: 8px 12px; border-radius: 6px; background: #fffbeb; color: #92400e; border: 1px solid #fde68a; }
        .current-note.none { background: #f0fdf4; color: #166534; border-color: #bbf7d0; }
        .cell-sub { display: block; margin-top: 3px; }
        .tags { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 4px; }
        .warn { color: #b45309; font-size: 0.8em; }
        .top-links { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }
        @media (max-width: 900px) { .assign-form { grid-template-columns: 1fr; } }
    </style>
</head>

<body>
<jsp:include page="sidebar.jsp" />

<main class="main-content">
    <jsp:include page="header.jsp" />

    <div class="page-container">
        <div class="top-links">
            <a href="manageCourses" class="btn btn-secondary btn-sm">&larr; Back to Courses</a>
            <a href="manageSemesters" class="btn btn-secondary btn-sm">Manage Semesters</a>
        </div>

        <% String okMsg = (String) request.getAttribute("successMessage");
           if (okMsg != null) { %>
            <div class="alert alert-success"><%= HtmlUtil.esc(okMsg) %></div>
        <% } %>
        <% String errMsg = (String) request.getAttribute("errorMessage");
           if (errMsg != null) { %>
            <div class="alert alert-error"><%= HtmlUtil.esc(errMsg) %></div>
        <% } %>

        <!-- Assign -->
        <div class="card">
            <h3>Assign Course to Faculty</h3>

            <% if (locked != null) { %>
                <div class="course-context">
                    <strong><%= HtmlUtil.esc(locked.getCourseCode()) %> - <%= HtmlUtil.esc(locked.getCourseName()) %></strong>
                    <span><%= deptTag(locked.getDepartmentName()) %></span>
                    <span class="tags" style="margin:0;"><%= classTags(locked) %></span>
                    <a href="manageAllocations" class="rp-link" style="margin-left:auto; color:var(--primary-color);">Choose another course</a>
                </div>
            <% } %>

            <form action="manageAllocations" method="post" class="assign-form" id="assignForm" style="margin-top:12px;">
                <input type="hidden" name="action" value="add">
                <% if (locked != null) { %>
                    <input type="hidden" name="courseId" value="<%= locked.getCourseId() %>">
                    <input type="hidden" name="returnCourseId" value="<%= locked.getCourseId() %>">
                <% } %>

                <div class="form-group">
                    <label class="form-label" for="courseSel">Course</label>
                    <select <%= locked == null ? "name=\"courseId\"" : "" %> id="courseSel" class="form-control" required <%= locked != null ? "disabled" : "" %>>
                        <option value="">-- Select course --</option>
                        <% for (Course c : courses) { %>
                            <option value="<%= c.getCourseId() %>" data-dept="<%= c.getDepartmentId() != null ? c.getDepartmentId() : "" %>"
                                data-dept-name="<%= HtmlUtil.esc(c.getDepartmentName() != null ? c.getDepartmentName() : "") %>"
                                <%= locked != null && locked.getCourseId() == c.getCourseId() ? "selected" : "" %>>
                                <%= HtmlUtil.esc(c.getCourseCode()) %> - <%= HtmlUtil.esc(c.getCourseName()) %><%= c.getDepartmentName() != null ? " · " + HtmlUtil.esc(c.getDepartmentName()) : "" %>
                            </option>
                        <% } %>
                    </select>
                </div>

                <div class="form-group">
                    <label class="form-label" for="teacherSel">Teacher</label>
                    <select name="teacherId" id="teacherSel" class="form-control" required>
                        <option value="">-- Select teacher --</option>
                        <% for (Map.Entry<String, List<User>> g : teacherGroups.entrySet()) { %>
                            <optgroup label="<%= HtmlUtil.esc(g.getKey()) %>">
                                <% if (g.getValue().isEmpty()) { %><option disabled>(none)</option><% } %>
                                <% for (User t : g.getValue()) { %>
                                    <option value="<%= t.getUserId() %>" data-dept="<%= t.getDepartmentId() != null ? t.getDepartmentId() : "" %>"
                                        data-dept-name="<%= HtmlUtil.esc(t.getDepartmentName() != null ? t.getDepartmentName() : "") %>"
                                        data-label="<%= HtmlUtil.esc(teacherLabel(t)) %>"><%= HtmlUtil.esc(teacherLabel(t)) %></option>
                                <% } %>
                            </optgroup>
                        <% } %>
                    </select>
                    <% if (teachers.isEmpty()) { %><small class="muted">No active teachers. <a href="manageTeachers">Add a teacher</a>.</small><% } %>
                </div>

                <div class="form-group">
                    <label class="form-label" for="semesterSel">Semester</label>
                    <select name="semesterId" id="semesterSel" class="form-control" required>
                        <% for (Semester s : semesters) { %>
                            <option value="<%= s.getSemesterId() %>" <%= s.isActive() ? "selected" : "" %>><%= HtmlUtil.esc(s.getLabel()) %></option>
                        <% } %>
                    </select>
                    <% if (semesters.isEmpty()) { %><small class="muted">No semesters. <a href="manageSemesters">Add one</a>.</small><% } %>
                </div>

                <div class="span-all" id="currentNote" hidden></div>

                <div class="span-all">
                    <button type="submit" class="btn btn-primary" id="assignBtn">Assign Teacher</button>
                </div>
            </form>
        </div>

        <!-- Assignments -->
        <div class="card">
            <h3>Faculty Assignments</h3>
            <div class="filter-bar" style="margin-top:12px;">
                <input type="search" id="aSearch" class="form-control" placeholder="Search course, teacher, class or department" aria-label="Search assignments">
                <select id="aCourse" class="form-control" aria-label="Filter by course">
                    <option value="">All courses</option>
                    <% for (Course c : courses) { %>
                        <option value="<%= c.getCourseId() %>" <%= locked != null && locked.getCourseId() == c.getCourseId() ? "selected" : "" %>><%= HtmlUtil.esc(c.getCourseCode()) %></option>
                    <% } %>
                </select>
                <select id="aSemester" class="form-control" aria-label="Filter by semester">
                    <option value="">All semesters</option>
                    <% for (Semester s : semesters) { %>
                        <option value="<%= s.getSemesterId() %>"><%= HtmlUtil.esc(s.getName()) %><%= s.isActive() ? " (Active)" : "" %></option>
                    <% } %>
                </select>
                <span class="muted" id="aCount"></span>
            </div>

            <div class="table-scroll">
            <table class="styled-table" id="aTable">
                <thead>
                    <tr>
                        <th>Semester</th>
                        <th>Course</th>
                        <th>Teacher</th>
                        <th>Students</th>
                        <th>Action</th>
                    </tr>
                </thead>
                <tbody>
                <% if (allocs.isEmpty()) { %>
                    <tr><td colspan="5" style="text-align:center;">No teacher assignments yet.</td></tr>
                <% } %>
                <% for (CourseAllocation ca : allocs) {
                       Course full = courseById.get(ca.getCourseId());
                       User t = ca.getTeacher();
                       boolean deptMismatch = ca.getCourse().getDepartmentId() != null && !Objects.equals(ca.getCourse().getDepartmentId(), t.getDepartmentId());
                       StringBuilder classNames = new StringBuilder();
                       if (full != null) for (AcademicClass cl : full.getClasses()) classNames.append(' ').append(cl.getDisplayName());
                       String confirmMsg = "Remove " + t.getDisplayName() + " from " + ca.getCourse().getCourseCode() + " in " + ca.getSemester().getName() + "?"
                               + (ca.getEnrollmentRows() > 0 ? "\n\nThis also permanently deletes " + ca.getEnrollmentRows() + " enrollment record(s) of this offering, including their grades and attendance." : "");
                %>
                    <tr class="a-row" data-course="<%= ca.getCourseId() %>" data-semester="<%= ca.getSemesterId() %>" data-teacher="<%= HtmlUtil.esc(t.getDisplayName()) %>"
                        data-text="<%= HtmlUtil.esc((ca.getCourse().getCourseCode() + " " + ca.getCourse().getCourseName() + " " + t.getDisplayName() + " " + (t.getDepartmentName() != null ? t.getDepartmentName() : "") + " " + (ca.getCourse().getDepartmentName() != null ? ca.getCourse().getDepartmentName() : "") + classNames).toLowerCase()) %>">
                        <td>
                            <strong><%= HtmlUtil.esc(ca.getSemester().getName()) %></strong>
                            <% if (ca.getSemester().isActive()) { %><span class="status-badge status-active" style="margin-left:4px;">Active</span><% } %>
                            <span class="muted cell-sub"><%= HtmlUtil.esc(ca.getSemester().getDateRange()) %></span>
                        </td>
                        <td>
                            <strong><%= HtmlUtil.esc(ca.getCourse().getCourseCode()) %></strong> - <%= HtmlUtil.esc(ca.getCourse().getCourseName()) %>
                            <div class="tags"><%= deptTag(ca.getCourse().getDepartmentName()) %> <%= classTags(full) %></div>
                        </td>
                        <td>
                            <%= HtmlUtil.esc(t.getProfile() != null && t.getProfile().getFullName() != null ? t.getProfile().getFullName() : t.getUsername()) %>
                            <span class="muted cell-sub"><%= HtmlUtil.esc(t.getUsername()) %></span>
                            <div class="tags"><%= deptTag(t.getDepartmentName()) %></div>
                            <% if (deptMismatch) { %><span class="warn">Teacher is from another department</span><% } %>
                        </td>
                        <td>
                            <strong><%= ca.getEnrolledCount() %></strong> enrolled
                            <a class="cell-sub" href="manageEnrollment?courseId=<%= ca.getCourseId() %>&allocationId=<%= ca.getAllocationId() %>" style="color:var(--primary-color);">Manage students</a>
                        </td>
                        <td>
                            <form action="manageAllocations" method="post" data-confirm="<%= HtmlUtil.esc(confirmMsg) %>" onsubmit="return confirm(this.dataset.confirm);" style="margin:0;">
                                <input type="hidden" name="action" value="delete">
                                <input type="hidden" name="allocationId" value="<%= ca.getAllocationId() %>">
                                <% if (locked != null) { %><input type="hidden" name="returnCourseId" value="<%= locked.getCourseId() %>"><% } %>
                                <button type="submit" class="btn btn-danger btn-sm">Remove</button>
                            </form>
                        </td>
                    </tr>
                <% } %>
                    <tr id="aNoMatch" hidden><td colspan="5" style="text-align:center;">No assignments match your filters.</td></tr>
                </tbody>
            </table>
            </div>
        </div>
    </div>
</main>


<script>
(function () {
    var courseSel = document.getElementById("courseSel");
    var teacherSel = document.getElementById("teacherSel");
    var semesterSel = document.getElementById("semesterSel");
    var note = document.getElementById("currentNote");
    var assignBtn = document.getElementById("assignBtn");
    var current = {};
    document.querySelectorAll("#aTable .a-row").forEach(function (r) { current[r.dataset.course + "|" + r.dataset.semester] = r.dataset.teacher; });

    // All teacher options, captured once so they can be regrouped for any course
    var teacherOpts = Array.prototype.map.call(teacherSel.querySelectorAll("option[value]:not([value=''])"), function (o) {
        return { value: o.value, dept: o.dataset.dept, deptName: o.dataset.deptName, label: o.dataset.label };
    });

    function addGroup(label, list) {
        var g = document.createElement("optgroup");
        g.label = label;
        if (!list.length) {
            var none = document.createElement("option");
            none.disabled = true;
            none.textContent = "(none)";
            g.appendChild(none);
        }
        list.forEach(function (t) {
            var o = document.createElement("option");
            o.value = t.value;
            o.textContent = t.label;
            o.dataset.dept = t.dept;
            o.dataset.deptName = t.deptName;
            o.dataset.label = t.label;
            g.appendChild(o);
        });
        teacherSel.appendChild(g);
    }

    // Course's department first, then "Other departments"; without a department, group by department
    function regroupTeachers() {
        if (courseSel.disabled) return; // locked course: the server already grouped them
        var picked = teacherSel.value;
        var opt = courseSel.options[courseSel.selectedIndex];
        var dept = opt ? opt.dataset.dept : "";
        teacherSel.innerHTML = '<option value="">-- Select teacher --</option>';
        if (dept) {
            addGroup(opt.dataset.deptName + " Department (course department)", teacherOpts.filter(function (t) { return t.dept === dept; }));
            addGroup("Other departments", teacherOpts.filter(function (t) { return t.dept !== dept; }));
        } else {
            var groups = {};
            teacherOpts.forEach(function (t) {
                var k = t.deptName ? t.deptName + " Department" : "No department";
                (groups[k] = groups[k] || []).push(t);
            });
            Object.keys(groups).forEach(function (k) { addGroup(k, groups[k]); });
        }
        teacherSel.value = picked;
    }

    // Tell the admin who currently teaches this course in this semester
    function updateNote() {
        var key = courseSel.value + "|" + semesterSel.value;
        if (!courseSel.value || !semesterSel.value) { note.hidden = true; assignBtn.textContent = "Assign Teacher"; return; }
        var who = current[key];
        note.hidden = false;
        note.className = "span-all current-note" + (who ? "" : " none");
        note.textContent = who
            ? "Currently taught by " + who + " in this semester. Assigning a different teacher will replace them (enrolled students are kept)."
            : "No teacher is assigned to this course in this semester yet.";
        assignBtn.textContent = who ? "Replace Teacher" : "Assign Teacher";
    }

    courseSel.addEventListener("change", function () { regroupTeachers(); updateNote(); });
    semesterSel.addEventListener("change", updateNote);
    updateNote();

    // Assignment table filters
    var search = document.getElementById("aSearch"), fCourse = document.getElementById("aCourse"), fSem = document.getElementById("aSemester");
    var rows = Array.prototype.slice.call(document.querySelectorAll("#aTable .a-row"));
    var countEl = document.getElementById("aCount"), none = document.getElementById("aNoMatch");
    function apply() {
        var terms = search.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
        var visible = 0;
        rows.forEach(function (r) {
            var ok = (!fCourse.value || r.dataset.course === fCourse.value)
                && (!fSem.value || r.dataset.semester === fSem.value)
                && terms.every(function (t) { return r.dataset.text.indexOf(t) !== -1; });
            r.hidden = !ok;
            if (ok) visible++;
        });
        countEl.textContent = "Showing " + visible + " of " + rows.length;
        none.hidden = !(rows.length && visible === 0);
    }
    search.addEventListener("input", apply);
    fCourse.addEventListener("change", apply);
    fSem.addEventListener("change", apply);
    apply();
})();
</script>
</body>
</html>
