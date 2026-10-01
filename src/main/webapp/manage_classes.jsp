<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Collections, com.cms.models.AcademicClass, com.cms.models.Department, com.cms.util.HtmlUtil" %>
<%
    List<AcademicClass> classes = (List<AcademicClass>) request.getAttribute("classes");
    List<Department> departments = (List<Department>) request.getAttribute("departments");
    if (classes == null) classes = Collections.emptyList();
    if (departments == null) departments = Collections.emptyList();
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Classes &amp; Departments | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .cd-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 20px; align-items: start; }
        .cd-grid .styled-table th, .cd-grid .styled-table td { padding: 10px 12px; }
        .form-row { display: grid; grid-template-columns: 110px 1fr 110px; gap: 10px; }
        .form-row .form-group { margin-bottom: 0; }
        .preview { margin: 12px 0; font-size: 0.9em; }
        .form-actions { display: flex; gap: 8px; margin-top: 12px; }
        .row-actions { display: flex; gap: 6px; }
        .row-actions form { margin: 0; }
        .card h3 { margin-bottom: 4px; }
        @media (max-width: 1399px) { .cd-grid { grid-template-columns: 1fr; } }
        @media (max-width: 720px) { .form-row { grid-template-columns: 1fr; } }
    </style>
</head>

<body>
<jsp:include page="sidebar.jsp" />

<main class="main-content">
    <jsp:include page="header.jsp" />

    <div class="page-container">
        <h2 class="card-title">Classes &amp; Departments</h2>

        <% String okMsg = (String) request.getAttribute("successMessage");
           if (okMsg != null) { %>
            <div class="alert alert-success"><%= HtmlUtil.esc(okMsg) %></div>
        <% } %>
        <% String errMsg = (String) request.getAttribute("errorMessage");
           if (errMsg != null) { %>
            <div class="alert alert-error"><%= HtmlUtil.esc(errMsg) %></div>
        <% } %>

        <div class="cd-grid">

            <!-- CLASSES -->
            <div class="card">
                <h3 id="classFormTitle">Add Class</h3>
                <p class="muted">Students are assigned to a class. Format: Degree Program-Year.</p>

                <form action="manageClasses" method="post" id="classForm" style="margin-top:12px;">
                    <input type="hidden" name="action" value="saveClass">
                    <input type="hidden" name="classId" id="classId">
                    <div class="form-row">
                        <div class="form-group">
                            <label class="form-label" for="degree">Degree</label>
                            <input type="text" name="degree" id="degree" class="form-control" list="degreeOptions"
                                required maxlength="20" placeholder="BS">
                            <datalist id="degreeOptions">
                                <option value="BS"><option value="BSc"><option value="MS"><option value="MSc">
                                <option value="BBA"><option value="MBA"><option value="M.Phil"><option value="Ph.D">
                            </datalist>
                        </div>
                        <div class="form-group">
                            <label class="form-label" for="programName">Program</label>
                            <input type="text" name="programName" id="programName" class="form-control"
                                required maxlength="100" placeholder="Computer Science">
                        </div>
                        <div class="form-group">
                            <label class="form-label" for="batchYear">Year</label>
                            <input type="number" name="batchYear" id="batchYear" class="form-control"
                                required min="1950" max="2100" placeholder="2026">
                        </div>
                    </div>
                    <div class="preview">Will appear as: <span class="tag" id="classPreview">BS Computer Science-2026</span></div>
                    <div class="form-actions">
                        <button type="submit" class="btn btn-primary" id="classSubmit">Add Class</button>
                        <button type="button" class="btn btn-secondary" id="classCancel" hidden>Cancel</button>
                    </div>
                </form>

                <div class="table-scroll" style="margin-top:18px;">
                <table class="styled-table">
                    <thead>
                        <tr><th>Class</th><th>Students</th><th>Actions</th></tr>
                    </thead>
                    <tbody>
                    <% if (classes.isEmpty()) { %>
                        <tr><td colspan="3" style="text-align:center;">No classes yet.</td></tr>
                    <% } %>
                    <% for (AcademicClass c : classes) { %>
                        <tr>
                            <td><span class="tag"><%= HtmlUtil.esc(c.getDisplayName()) %></span></td>
                            <td><%= c.getStudentCount() %></td>
                            <td>
                                <div class="row-actions">
                                    <button type="button" class="btn btn-primary btn-sm js-edit-class"
                                        data-id="<%= c.getClassId() %>" data-degree="<%= HtmlUtil.esc(c.getDegree()) %>"
                                        data-program="<%= HtmlUtil.esc(c.getProgramName()) %>" data-year="<%= c.getBatchYear() %>">Edit</button>
                                    <form action="manageClasses" method="post"
                                        data-confirm="Delete class <%= HtmlUtil.esc(c.getDisplayName()) %>?" onsubmit="return confirm(this.dataset.confirm);">
                                        <input type="hidden" name="action" value="deleteClass">
                                        <input type="hidden" name="classId" value="<%= c.getClassId() %>">
                                        <button type="submit" class="btn btn-danger btn-sm" <%= c.getStudentCount() > 0 ? "disabled title=\"Move its students to another class first\"" : "" %>>Delete</button>
                                    </form>
                                </div>
                            </td>
                        </tr>
                    <% } %>
                    </tbody>
                </table>
                </div>
            </div>

            <!-- DEPARTMENTS -->
            <div class="card">
                <h3 id="deptFormTitle">Add Department</h3>
                <p class="muted">Teachers are assigned to a department.</p>

                <form action="manageClasses" method="post" id="deptForm" style="margin-top:12px;">
                    <input type="hidden" name="action" value="saveDepartment">
                    <input type="hidden" name="departmentId" id="departmentId">
                    <div class="form-group">
                        <label class="form-label" for="deptName">Department Name</label>
                        <input type="text" name="name" id="deptName" class="form-control"
                            required maxlength="100" placeholder="Computer Science">
                    </div>
                    <div class="form-actions">
                        <button type="submit" class="btn btn-primary" id="deptSubmit">Add Department</button>
                        <button type="button" class="btn btn-secondary" id="deptCancel" hidden>Cancel</button>
                    </div>
                </form>

                <div class="table-scroll" style="margin-top:18px;">
                <table class="styled-table">
                    <thead>
                        <tr><th>Department</th><th>Teachers</th><th>Actions</th></tr>
                    </thead>
                    <tbody>
                    <% if (departments.isEmpty()) { %>
                        <tr><td colspan="3" style="text-align:center;">No departments yet.</td></tr>
                    <% } %>
                    <% for (Department d : departments) { %>
                        <tr>
                            <td><span class="tag tag-department"><%= HtmlUtil.esc(d.getName()) %></span></td>
                            <td><%= d.getTeacherCount() %></td>
                            <td>
                                <div class="row-actions">
                                    <button type="button" class="btn btn-primary btn-sm js-edit-dept"
                                        data-id="<%= d.getDepartmentId() %>" data-name="<%= HtmlUtil.esc(d.getName()) %>">Edit</button>
                                    <form action="manageClasses" method="post"
                                        data-confirm="Delete department <%= HtmlUtil.esc(d.getName()) %>?" onsubmit="return confirm(this.dataset.confirm);">
                                        <input type="hidden" name="action" value="deleteDepartment">
                                        <input type="hidden" name="departmentId" value="<%= d.getDepartmentId() %>">
                                        <button type="submit" class="btn btn-danger btn-sm" <%= d.getTeacherCount() > 0 ? "disabled title=\"Move its teachers to another department first\"" : "" %>>Delete</button>
                                    </form>
                                </div>
                            </td>
                        </tr>
                    <% } %>
                    </tbody>
                </table>
                </div>
            </div>

        </div>
    </div>
</main>

<script>
(function () {
    var degree = document.getElementById("degree");
    var program = document.getElementById("programName");
    var year = document.getElementById("batchYear");
    var preview = document.getElementById("classPreview");

    function updatePreview() {
        var d = degree.value.trim() || "BS";
        var p = program.value.trim().replace(/\s+/g, " ") || "Computer Science";
        var y = year.value.trim() || "2026";
        preview.textContent = d + " " + p + "-" + y;
    }
    [degree, program, year].forEach(function (el) { el.addEventListener("input", updatePreview); });

    function setClassMode(editing) {
        document.getElementById("classFormTitle").textContent = editing ? "Edit Class" : "Add Class";
        document.getElementById("classSubmit").textContent = editing ? "Save Changes" : "Add Class";
        document.getElementById("classCancel").hidden = !editing;
    }
    document.querySelectorAll(".js-edit-class").forEach(function (b) {
        b.addEventListener("click", function () {
            document.getElementById("classId").value = b.dataset.id;
            degree.value = b.dataset.degree;
            program.value = b.dataset.program;
            year.value = b.dataset.year;
            updatePreview();
            setClassMode(true);
            degree.focus();
        });
    });
    document.getElementById("classCancel").addEventListener("click", function () {
        document.getElementById("classForm").reset();
        document.getElementById("classId").value = "";
        updatePreview();
        setClassMode(false);
    });

    var deptName = document.getElementById("deptName");
    function setDeptMode(editing) {
        document.getElementById("deptFormTitle").textContent = editing ? "Edit Department" : "Add Department";
        document.getElementById("deptSubmit").textContent = editing ? "Save Changes" : "Add Department";
        document.getElementById("deptCancel").hidden = !editing;
    }
    document.querySelectorAll(".js-edit-dept").forEach(function (b) {
        b.addEventListener("click", function () {
            document.getElementById("departmentId").value = b.dataset.id;
            deptName.value = b.dataset.name;
            setDeptMode(true);
            deptName.focus();
        });
    });
    document.getElementById("deptCancel").addEventListener("click", function () {
        document.getElementById("deptForm").reset();
        document.getElementById("departmentId").value = "";
        setDeptMode(false);
    });
})();
</script>
</body>
</html>
