<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Map, java.util.Collections, com.cms.models.Semester, com.cms.models.ClassSemester, com.cms.models.AcademicClass, com.cms.models.Department, com.cms.dao.ClassSemesterDAO, com.cms.util.HtmlUtil" %>
<%
    List<Semester> semesters = (List<Semester>) request.getAttribute("semesters");
    List<ClassSemester> classSemesters = (List<ClassSemester>) request.getAttribute("classSemesters");
    List<AcademicClass> classes = (List<AcademicClass>) request.getAttribute("classes");
    List<Department> departments = (List<Department>) request.getAttribute("departments");
    Map<Integer, ClassSemester> currentByClass = (Map<Integer, ClassSemester>) request.getAttribute("currentByClass");
    if (semesters == null) semesters = Collections.emptyList();
    if (classSemesters == null) classSemesters = Collections.emptyList();
    if (classes == null) classes = Collections.emptyList();
    if (departments == null) departments = Collections.emptyList();
    if (currentByClass == null) currentByClass = Collections.emptyMap();
    Semester edit = (Semester) request.getAttribute("semesterToEdit");
    boolean isEdit = edit != null;
    int max = ClassSemesterDAO.MAX_SEMESTER;

    // Classes with students but no current semester: their students cannot use add/drop
    StringBuilder idle = new StringBuilder();
    for (AcademicClass c : classes)
        if (c.getStudentCount() > 0 && !currentByClass.containsKey(c.getClassId()))
            idle.append(idle.length() == 0 ? "" : ", ").append(c.getDisplayName());
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Manage Semesters | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .sem-form { display: grid; grid-template-columns: 2fr 1fr 1fr auto; gap: 14px; align-items: end; }
        .sem-form .form-group { margin-bottom: 0; }
        .row-actions { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }
        .row-actions form { margin: 0; display: flex; gap: 4px; align-items: center; }
        .active-row { background: #f0fdf4 !important; }
        .usage { font-size: 0.85em; }
        .assign-grid { display: grid; grid-template-columns: 1.4fr 1.2fr; gap: 14px; align-items: end; }
        .assign-grid .form-group { margin-bottom: 0; }
        .assign-opts { display: flex; gap: 18px; flex-wrap: wrap; align-items: center; margin: 12px 0; }
        .assign-opts label { display: inline-flex; gap: 6px; align-items: center; cursor: pointer; }
        .picker { border: 1px solid #ddd; border-radius: var(--border-radius); background: #fff; padding: 12px; }
        .picker-row { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
        .picker-status { display: flex; justify-content: space-between; gap: 8px; flex-wrap: wrap; font-size: 0.82em; color: #6b7280; margin: 10px 0 6px; }
        .picker-status label { display: inline-flex; gap: 6px; align-items: center; font-weight: 600; color: var(--text-color); cursor: pointer; }
        .class-list { list-style: none; max-height: 260px; overflow-y: auto; border: 1px solid #eee; border-radius: 6px; }
        .class-item label { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-bottom: 1px solid #f3f4f6; cursor: pointer; }
        .class-item label:hover { background: #f8fafc; }
        .class-item input { width: 16px; height: 16px; flex-shrink: 0; }
        .class-item input:checked + .ci-info .ci-name { color: var(--primary-color); }
        .ci-info { flex: 1; min-width: 0; }
        .ci-name { font-weight: 600; }
        .ci-sub { font-size: 0.8em; color: #6b7280; }
        .cs-filters { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; margin: 6px 0 10px; }
        .cs-filters input[type="search"] { grid-column: 1 / -1; }
        .cs-count { font-size: 0.85em; color: #6b7280; margin-bottom: 6px; }
        .num-select { width: auto; padding: 4px 6px; font-size: 0.85rem; }
        .sem-num { font-weight: 700; color: var(--primary-color); white-space: nowrap; }
        @media (max-width: 1100px) { .cs-filters { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
        @media (max-width: 800px) { .sem-form, .assign-grid, .picker-row { grid-template-columns: 1fr; } .cs-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
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
        <% if (idle.length() > 0) { %>
            <div class="alert alert-error">Not in a semester right now: <strong><%= HtmlUtil.esc(idle.toString()) %></strong>.
                Their students cannot submit add/drop requests. Place them in a semester under <a href="#classSemesters">Class Semesters</a>.</div>
        <% } %>

        <!-- TERMS: Add / Edit -->
        <div class="card" id="semForm">
            <h3><%= isEdit ? "Edit Semester: " + HtmlUtil.esc(edit.getName()) : "Add New Semester" %></h3>
            <form action="manageSemesters" method="post" class="sem-form" style="margin-top:14px;">
                <input type="hidden" name="action" value="<%= isEdit ? "update" : "add" %>">
                <% if (isEdit) { %><input type="hidden" name="semesterId" value="<%= edit.getSemesterId() %>"><% } %>
                <div class="form-group">
                    <label class="form-label" for="name">Semester Name</label>
                    <input type="text" name="name" id="name" class="form-control" required maxlength="50"
                        placeholder="e.g. Spring 2025" value="<%= isEdit ? HtmlUtil.esc(edit.getName()) : "" %>">
                </div>
                <div class="form-group">
                    <label class="form-label" for="startDate">Start Date</label>
                    <input type="date" name="startDate" id="startDate" class="form-control" required
                        value="<%= isEdit && edit.getStartDate() != null ? edit.getStartDate() : "" %>">
                </div>
                <div class="form-group">
                    <label class="form-label" for="endDate">End Date</label>
                    <input type="date" name="endDate" id="endDate" class="form-control" required
                        value="<%= isEdit && edit.getEndDate() != null ? edit.getEndDate() : "" %>">
                </div>
                <div class="form-group" style="display:flex; gap:8px;">
                    <button type="submit" class="btn btn-primary"><%= isEdit ? "Save Changes" : "Add Semester" %></button>
                    <% if (isEdit) { %><a href="manageSemesters" class="btn btn-secondary">Cancel</a><% } %>
                </div>
            </form>
            <p class="muted" style="margin-top:10px;">A semester (term) such as Fall 2024 is shared by all classes; each class has its own semester number in it.
                A term is <strong>active</strong> while at least one class is currently in it, so several terms can be active at once.</p>
        </div>

        <!-- TERMS: List -->
        <div class="card">
            <h3>Semesters</h3>
            <div class="table-scroll">
            <table class="styled-table">
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>Dates</th>
                        <th>Status</th>
                        <th>Classes</th>
                        <th>In use</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                <% if (semesters.isEmpty()) { %>
                    <tr><td colspan="6" style="text-align:center;">No semesters yet.</td></tr>
                <% } %>
                <% for (Semester s : semesters) {
                       String removeBlock = s.getClassCount() > 0 ? "Classes are placed in it - remove them from it below first"
                               : s.getAllocationCount() > 0 ? "Has course offerings - remove those teacher assignments first"
                               : s.getChallanCount() > 0 ? "Has fee challans" : null;
                %>
                    <tr class="<%= s.isActive() ? "active-row" : "" %>">
                        <td><strong><%= HtmlUtil.esc(s.getName()) %></strong></td>
                        <td><%= HtmlUtil.esc(s.getDateRange()) %></td>
                        <td>
                            <span class="status-badge <%= s.isActive() ? "status-active" : "status-inactive" %>"><%= s.isActive() ? "Active" : "Inactive" %></span>
                        </td>
                        <td class="usage">
                            <% if (s.getClassCount() == 0) { %><span class="muted">None</span><% } else { %>
                                <%= s.getCurrentClassCount() %> current<% if (s.getClassCount() > s.getCurrentClassCount()) { %>, <%= s.getClassCount() - s.getCurrentClassCount() %> past<% } %>
                                <br><a href="#classList" class="js-show-term" data-term="<%= s.getSemesterId() %>" style="color:var(--primary-color);">View classes</a>
                            <% } %>
                        </td>
                        <td class="usage">
                            <%= s.getAllocationCount() %> course offering<%= s.getAllocationCount() == 1 ? "" : "s" %>
                            <% if (s.getChallanCount() > 0) { %><br><%= s.getChallanCount() %> challan<%= s.getChallanCount() == 1 ? "" : "s" %><% } %>
                            <% if (s.getAllocationCount() > 0) { %><br><a href="manageAllocations" style="color:var(--primary-color);">View offerings</a><% } %>
                        </td>
                        <td>
                            <div class="row-actions">
                                <a href="manageSemesters?editId=<%= s.getSemesterId() %>#semForm" class="btn btn-secondary btn-sm">Edit</a>
                                <form action="manageSemesters" method="post"
                                    data-confirm="Remove semester <%= HtmlUtil.esc(s.getName()) %>? This cannot be undone."
                                    onsubmit="return confirm(this.dataset.confirm);">
                                    <input type="hidden" name="action" value="delete">
                                    <input type="hidden" name="semesterId" value="<%= s.getSemesterId() %>">
                                    <button type="submit" class="btn btn-danger btn-sm" <%= removeBlock != null ? "disabled title=\"" + HtmlUtil.esc(removeBlock) + "\"" : "" %>>Remove</button>
                                </form>
                            </div>
                            <% if (removeBlock != null) { %><span class="muted" style="display:block; margin-top:4px; font-size:0.78em;">Remove: <%= HtmlUtil.esc(removeBlock.toLowerCase()) %></span><% } %>
                        </td>
                    </tr>
                <% } %>
                </tbody>
            </table>
            </div>
        </div>

        <!-- CLASS SEMESTERS: place classes in a term -->
        <div class="card" id="classSemesters">
            <h3>Class Semesters</h3>
            <p class="muted">Place classes in a semester and set which semester number it is for them, e.g. BS Computer Science-2022 in its 5th semester in Fall 2024.
                A class's <strong>current</strong> semester decides its students' add/drop courses and dashboard.</p>

            <% if (semesters.isEmpty() || classes.isEmpty()) { %>
                <p style="margin-top:12px;">Add <%= semesters.isEmpty() ? "a semester above" : "" %><%= semesters.isEmpty() && classes.isEmpty() ? " and " : "" %><%= classes.isEmpty() ? "a class in <a href=\"manageClasses\">Classes &amp; Departments</a>" : "" %> first.</p>
            <% } else { %>
            <form action="manageSemesters" method="post" id="assignForm" style="margin-top:14px;">
                <input type="hidden" name="action" value="assignClasses">
                <div class="assign-grid">
                    <div class="form-group">
                        <label class="form-label" for="assignTerm">Semester (term)</label>
                        <select name="semesterId" id="assignTerm" class="form-control" required>
                            <% for (Semester s : semesters) { %>
                                <option value="<%= s.getSemesterId() %>"><%= HtmlUtil.esc(s.getLabel()) %></option>
                            <% } %>
                        </select>
                    </div>
                    <div class="form-group">
                        <label class="form-label" for="assignNumber">Semester number</label>
                        <select name="semesterNumber" id="assignNumber" class="form-control">
                            <option value="auto">Next semester for each class (automatic)</option>
                            <% for (int n = 1; n <= max; n++) { %>
                                <option value="<%= n %>"><%= ClassSemester.ordinal(n) %> semester</option>
                            <% } %>
                        </select>
                    </div>
                </div>
                <div class="assign-opts">
                    <label><input type="checkbox" name="makeCurrent" value="1" checked> Make it each class's current semester</label>
                    <span class="muted">Automatic = one more than the class's semester in its latest earlier term (1st for a new class).</span>
                </div>

                <label class="form-label">Classes</label>
                <div class="picker">
                    <div class="picker-row">
                        <input type="search" id="pickSearch" class="form-control" placeholder="Search classes" aria-label="Search classes">
                        <select id="pickDept" class="form-control" aria-label="Filter classes by department">
                            <option value="">All departments</option>
                            <% for (Department d : departments) { %>
                                <option value="<%= d.getDepartmentId() %>"><%= HtmlUtil.esc(d.getName()) %></option>
                            <% } %>
                            <option value="none">No department</option>
                        </select>
                    </div>
                    <div class="picker-status">
                        <label><input type="checkbox" id="pickAll"> <span id="pickAllText">Select all</span></label>
                        <span id="pickCount">0 selected</span>
                    </div>
                    <ul class="class-list" id="pickList">
                        <% for (AcademicClass c : classes) {
                               ClassSemester cur = currentByClass.get(c.getClassId()); %>
                            <li class="class-item" data-text="<%= HtmlUtil.esc((c.getDisplayName() + " " + (c.getDepartmentName() == null ? "" : c.getDepartmentName())).toLowerCase()) %>"
                                data-dept="<%= c.getDepartmentId() != null ? c.getDepartmentId() : "none" %>">
                                <label>
                                    <input type="checkbox" name="classIds" value="<%= c.getClassId() %>">
                                    <span class="ci-info">
                                        <span class="ci-name"><%= HtmlUtil.esc(c.getDisplayName()) %></span>
                                        <span class="ci-sub">
                                            <%= c.getDepartmentName() != null ? HtmlUtil.esc(c.getDepartmentName()) + " &middot; " : "" %><%= c.getStudentCount() %> student<%= c.getStudentCount() == 1 ? "" : "s" %>
                                            &middot; <%= cur != null ? "now " + cur.getNumberLabel() + " in " + HtmlUtil.esc(cur.getTermName()) : "no current semester" %>
                                        </span>
                                    </span>
                                </label>
                            </li>
                        <% } %>
                    </ul>
                </div>
                <button type="submit" class="btn btn-primary" style="margin-top:12px;">Save Class Semesters</button>
            </form>
            <% } %>
        </div>

        <!-- CLASS SEMESTERS: list with filters -->
        <div class="card" id="classList">
            <h3>Classes by Semester</h3>
            <div class="cs-filters">
                <input type="search" id="fSearch" class="form-control" placeholder="Search class, department or term" aria-label="Search">
                <select id="fClass" class="form-control" aria-label="Class">
                    <option value="">All classes</option>
                    <% for (AcademicClass c : classes) { %>
                        <option value="<%= c.getClassId() %>"><%= HtmlUtil.esc(c.getDisplayName()) %></option>
                    <% } %>
                </select>
                <select id="fDept" class="form-control" aria-label="Department">
                    <option value="">All departments</option>
                    <% for (Department d : departments) { %>
                        <option value="<%= d.getDepartmentId() %>"><%= HtmlUtil.esc(d.getName()) %></option>
                    <% } %>
                    <option value="none">No department</option>
                </select>
                <select id="fNumber" class="form-control" aria-label="Semester number">
                    <option value="">All semester numbers</option>
                    <% for (int n = 1; n <= max; n++) { %>
                        <option value="<%= n %>"><%= ClassSemester.ordinal(n) %> semester</option>
                    <% } %>
                </select>
                <select id="fTerm" class="form-control" aria-label="Term">
                    <option value="">All terms</option>
                    <% for (Semester s : semesters) { %>
                        <option value="<%= s.getSemesterId() %>"><%= HtmlUtil.esc(s.getName()) %></option>
                    <% } %>
                </select>
                <select id="fStatus" class="form-control" aria-label="Status">
                    <option value="current">Current only</option>
                    <option value="past">Past only</option>
                    <option value="">Current and past</option>
                </select>
            </div>
            <div class="cs-count" id="csCount"></div>
            <div class="table-scroll">
            <table class="styled-table" style="margin-top:0;">
                <thead>
                    <tr>
                        <th>Class</th>
                        <th>Department</th>
                        <th>Term</th>
                        <th>Semester</th>
                        <th>Status</th>
                        <th>Students</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody id="csBody">
                <% for (ClassSemester cs : classSemesters) { %>
                    <tr class="cs-row <%= cs.isCurrent() ? "active-row" : "" %>"
                        data-class="<%= cs.getClassId() %>" data-dept="<%= cs.getDepartmentId() != null ? cs.getDepartmentId() : "none" %>"
                        data-number="<%= cs.getSemesterNumber() %>" data-term="<%= cs.getSemesterId() %>" data-current="<%= cs.isCurrent() %>"
                        data-text="<%= HtmlUtil.esc((cs.getClassName() + " " + (cs.getDepartmentName() == null ? "" : cs.getDepartmentName()) + " " + cs.getTermName()).toLowerCase()) %>">
                        <td><span class="tag"><%= HtmlUtil.esc(cs.getClassName()) %></span></td>
                        <td><% if (cs.getDepartmentName() != null) { %><span class="tag tag-department"><%= HtmlUtil.esc(cs.getDepartmentName()) %></span><% } else { %><span class="muted">-</span><% } %></td>
                        <td><strong><%= HtmlUtil.esc(cs.getTermName()) %></strong><br><span class="muted"><%= HtmlUtil.esc(cs.getTermDateRange()) %></span></td>
                        <td><span class="sem-num"><%= ClassSemester.ordinal(cs.getSemesterNumber()) %></span></td>
                        <td><span class="status-badge <%= cs.isCurrent() ? "status-active" : "" %>"><%= cs.isCurrent() ? "Current" : "Past" %></span></td>
                        <td><%= cs.getStudentCount() %></td>
                        <td>
                            <div class="row-actions">
                                <form action="manageSemesters" method="post">
                                    <input type="hidden" name="action" value="changeNumber">
                                    <input type="hidden" name="classSemesterId" value="<%= cs.getClassSemesterId() %>">
                                    <select name="semesterNumber" class="form-control num-select" aria-label="Semester number for <%= HtmlUtil.esc(cs.getClassName()) %>">
                                        <% for (int n = 1; n <= max; n++) { %>
                                            <option value="<%= n %>" <%= n == cs.getSemesterNumber() ? "selected" : "" %>><%= ClassSemester.ordinal(n) %></option>
                                        <% } %>
                                    </select>
                                    <button type="submit" class="btn btn-secondary btn-sm">Save</button>
                                </form>
                                <% if (cs.isCurrent()) { %>
                                    <form action="manageSemesters" method="post"
                                        data-confirm="End <%= HtmlUtil.esc(cs.getTermName()) %> for <%= HtmlUtil.esc(cs.getClassName()) %>? The class will have no current semester and its students cannot submit add/drop requests until you set one."
                                        onsubmit="return confirm(this.dataset.confirm);">
                                        <input type="hidden" name="action" value="endCurrent">
                                        <input type="hidden" name="classSemesterId" value="<%= cs.getClassSemesterId() %>">
                                        <button type="submit" class="btn btn-sm" style="background:#fef3c7; color:#92400e;">End</button>
                                    </form>
                                <% } else { %>
                                    <form action="manageSemesters" method="post"
                                        data-confirm="Make <%= HtmlUtil.esc(cs.getTermName()) %> (<%= cs.getNumberLabel() %>) the current semester of <%= HtmlUtil.esc(cs.getClassName()) %>?"
                                        onsubmit="return confirm(this.dataset.confirm);">
                                        <input type="hidden" name="action" value="makeCurrent">
                                        <input type="hidden" name="classSemesterId" value="<%= cs.getClassSemesterId() %>">
                                        <button type="submit" class="btn btn-success btn-sm">Make current</button>
                                    </form>
                                <% } %>
                                <form action="manageSemesters" method="post"
                                    data-confirm="Remove <%= HtmlUtil.esc(cs.getClassName()) %> from <%= HtmlUtil.esc(cs.getTermName()) %>? Existing enrollments keep their semester number."
                                    onsubmit="return confirm(this.dataset.confirm);">
                                    <input type="hidden" name="action" value="removeClassSemester">
                                    <input type="hidden" name="classSemesterId" value="<%= cs.getClassSemesterId() %>">
                                    <button type="submit" class="btn btn-danger btn-sm">Remove</button>
                                </form>
                            </div>
                        </td>
                    </tr>
                <% } %>
                    <tr id="csEmpty" hidden><td colspan="7" style="text-align:center;">No classes match these filters.</td></tr>
                </tbody>
            </table>
            </div>
        </div>
    </div>
</main>

<script>
(function () {
    // ---- Class picker in the "place classes" form ----
    var search = document.getElementById("pickSearch");
    if (search) {
        var dept = document.getElementById("pickDept");
        var all = document.getElementById("pickAll"), allText = document.getElementById("pickAllText");
        var count = document.getElementById("pickCount");
        var items = Array.prototype.slice.call(document.querySelectorAll("#pickList .class-item"));
        var visible = function () { return items.filter(function (li) { return !li.hidden; }); };
        var box = function (li) { return li.querySelector("input"); };

        var refresh = function () {
            var v = visible();
            var checked = v.filter(function (li) { return box(li).checked; }).length;
            all.checked = v.length > 0 && checked === v.length;
            all.indeterminate = checked > 0 && checked < v.length;
            allText.textContent = "Select all (" + v.length + ")";
            var total = items.filter(function (li) { return box(li).checked; }).length;
            count.textContent = total + " selected";
        };
        var filter = function () {
            var q = search.value.trim().toLowerCase(), d = dept.value;
            items.forEach(function (li) {
                li.hidden = (q && li.dataset.text.indexOf(q) === -1) || (d && li.dataset.dept !== d);
            });
            refresh();
        };
        search.addEventListener("input", filter);
        dept.addEventListener("change", filter);
        all.addEventListener("change", function () {
            visible().forEach(function (li) { box(li).checked = all.checked; });
            refresh();
        });
        items.forEach(function (li) { box(li).addEventListener("change", refresh); });
        document.getElementById("assignForm").addEventListener("submit", function (e) {
            if (!items.some(function (li) { return box(li).checked; })) {
                e.preventDefault();
                alert("Please select at least one class.");
            }
        });
        filter();
    }

    // ---- Filters for the class-semester list ----
    var f = {
        search: document.getElementById("fSearch"), cls: document.getElementById("fClass"),
        dept: document.getElementById("fDept"), number: document.getElementById("fNumber"),
        term: document.getElementById("fTerm"), status: document.getElementById("fStatus")
    };
    var rows = Array.prototype.slice.call(document.querySelectorAll("#csBody .cs-row"));
    var countEl = document.getElementById("csCount"), empty = document.getElementById("csEmpty");
    var apply = function () {
        var q = f.search.value.trim().toLowerCase(), shown = 0;
        rows.forEach(function (r) {
            var ok = (!q || r.dataset.text.indexOf(q) !== -1)
                && (!f.cls.value || r.dataset.class === f.cls.value)
                && (!f.dept.value || r.dataset.dept === f.dept.value)
                && (!f.number.value || r.dataset.number === f.number.value)
                && (!f.term.value || r.dataset.term === f.term.value)
                && (!f.status.value || (f.status.value === "current") === (r.dataset.current === "true"));
            r.hidden = !ok;
            if (ok) shown++;
        });
        empty.hidden = shown > 0;
        countEl.textContent = "Showing " + shown + " of " + rows.length;
    };
    Object.keys(f).forEach(function (k) { f[k].addEventListener(k === "search" ? "input" : "change", apply); });

    // "View classes" in the semesters table: show every class placed in that term
    document.querySelectorAll(".js-show-term").forEach(function (a) {
        a.addEventListener("click", function () {
            f.search.value = ""; f.cls.value = ""; f.dept.value = ""; f.number.value = "";
            f.term.value = a.dataset.term; f.status.value = "";
            apply();
        });
    });
    apply();
})();
</script>
</body>
</html>
