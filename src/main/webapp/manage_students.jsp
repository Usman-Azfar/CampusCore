<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Map, java.util.Collections, com.cms.models.User, com.cms.models.Profile, com.cms.models.AcademicClass, com.cms.dao.EnrollmentDAO, com.cms.util.HtmlUtil" %>
<%
    List<User> students = (List<User>) request.getAttribute("students");
    List<AcademicClass> classes = (List<AcademicClass>) request.getAttribute("classes");
    Map<Integer, EnrollmentDAO.StudentSummary> summaries = (Map<Integer, EnrollmentDAO.StudentSummary>) request.getAttribute("summaries");
    if (students == null) students = Collections.emptyList();
    if (classes == null) classes = Collections.emptyList();
    if (summaries == null) summaries = Collections.emptyMap();

    User edit = (User) request.getAttribute("studentToEdit");
    boolean isEdit = edit != null;
    Profile ep = isEdit && edit.getProfile() != null ? edit.getProfile() : new Profile();
    Map<String, String> draft = "1".equals(request.getParameter("draft")) ? (Map<String, String>) request.getAttribute("draft") : null;

    int activeCount = 0;
    for (User s : students) if (s.isActive()) activeCount++;
    EnrollmentDAO.StudentSummary none = new EnrollmentDAO.StudentSummary();
%>
<%!
    // Form value: the draft (after a failed save) wins, then the saved value
    private String val(Map<String, String> draft, String field, Object saved) {
        if (draft != null && draft.containsKey(field)) return HtmlUtil.esc(draft.get(field));
        return saved == null ? "" : HtmlUtil.esc(saved);
    }

    private String sel(Map<String, String> draft, String field, Object saved, String option) {
        String v = (draft != null && draft.containsKey(field)) ? draft.get(field) : (saved == null ? "" : saved.toString());
        return option.equals(v) ? "selected" : "";
    }
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Manage Students | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .user-form { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
        .user-form .form-group { margin-bottom: 0; }
        .user-form .span-2 { grid-column: span 2; }
        .user-form .span-all { grid-column: 1 / -1; }
        .user-form small { display: block; margin-top: 4px; }
        .req { color: var(--danger-color); }
        #username::placeholder { text-transform: none; }
        .row-actions { display: flex; gap: 5px; flex-wrap: wrap; }
        .row-actions form { margin: 0; }
        .cell-sub { display: block; font-size: 0.82em; color: #6b7280; margin-top: 2px; }
        .tags { display: flex; flex-wrap: wrap; gap: 4px; }
        .inactive-row { opacity: 0.7; }
        .pending-pill { display: inline-block; font-size: 0.75em; background: #fef3c7; color: #92400e; border-radius: 999px; padding: 1px 8px; margin-top: 4px; text-decoration: none; }
        #userTable th, #userTable td { padding: 10px; vertical-align: top; }
        @media (max-width: 900px) { .user-form { grid-template-columns: 1fr 1fr; } }
        @media (max-width: 600px) { .user-form { grid-template-columns: 1fr; } .user-form .span-2 { grid-column: auto; } }
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

    <!-- ADD / EDIT STUDENT -->
    <div class="card" id="studentForm">
        <h3><%= isEdit ? "Edit Student: " + HtmlUtil.esc(edit.getDisplayName()) : "Add New Student" %></h3>

        <form action="manageStudents" method="post" class="user-form" style="margin-top:14px;">
            <% if (isEdit) { %><input type="hidden" name="userId" value="<%= edit.getUserId() %>"><% } %>

            <div class="form-group">
                <label class="form-label" for="fullName">Full Name <span class="req">*</span></label>
                <input type="text" name="fullName" id="fullName" class="form-control" required maxlength="100"
                       placeholder="e.g. Muhammad Usman" value="<%= val(draft, "fullName", ep.getFullName()) %>">
            </div>
            <div class="form-group">
                <label class="form-label" for="username">Roll Number <span class="req">*</span></label>
                <input type="text" name="username" id="username" class="form-control" required maxlength="50"
                       pattern="[A-Za-z0-9._\-]{3,50}" placeholder="e.g. BCSF22M512" style="text-transform:uppercase;"
                       value="<%= val(draft, "username", isEdit ? edit.getUsername() : null) %>">
                <small class="muted">Used to log in. Saved in capitals.</small>
            </div>
            <div class="form-group">
                <label class="form-label" for="email">Email <span class="req">*</span></label>
                <input type="email" name="email" id="email" class="form-control" required maxlength="100"
                       placeholder="e.g. bcsf22m512@campuscore.edu.pk" value="<%= val(draft, "email", ep.getEmail()) %>">
            </div>

            <div class="form-group">
                <label class="form-label" for="password">Password <%= isEdit ? "" : "<span class=\"req\">*</span>" %></label>
                <input type="password" name="password" id="password" class="form-control" autocomplete="new-password"
                       minlength="6" <%= isEdit ? "" : "required" %>
                       placeholder="<%= isEdit ? "Leave blank to keep the current password" : "At least 6 characters" %>">
            </div>
            <div class="form-group">
                <label class="form-label" for="classId">Class</label>
                <select name="classId" id="classId" class="form-control">
                    <option value="">-- Not assigned --</option>
                    <% for (AcademicClass c : classes) { %>
                        <option value="<%= c.getClassId() %>" <%= sel(draft, "classId", isEdit ? edit.getClassId() : null, String.valueOf(c.getClassId())) %>>
                            <%= HtmlUtil.esc(c.getDisplayName()) %>
                        </option>
                    <% } %>
                </select>
                <small class="muted"><% if (classes.isEmpty()) { %>No classes yet. <% } %><a href="manageClasses">Manage classes</a></small>
            </div>
            <div class="form-group">
                <label class="form-label" for="fatherName">Father Name</label>
                <input type="text" name="fatherName" id="fatherName" class="form-control" maxlength="100"
                       value="<%= val(draft, "fatherName", ep.getFatherName()) %>">
            </div>

            <div class="form-group">
                <label class="form-label" for="gender">Gender</label>
                <select name="gender" id="gender" class="form-control">
                    <option value="">-- Not specified --</option>
                    <% for (String g : new String[] { "Male", "Female", "Other" }) { %>
                        <option value="<%= g %>" <%= sel(draft, "gender", ep.getGender(), g) %>><%= g %></option>
                    <% } %>
                </select>
            </div>
            <div class="form-group">
                <label class="form-label" for="phone">Phone</label>
                <input type="tel" name="phone" id="phone" class="form-control" maxlength="20" placeholder="e.g. +92 300 1234567"
                       value="<%= val(draft, "phone", ep.getPhone()) %>">
            </div>
            <div class="form-group">
                <label class="form-label" for="city">City</label>
                <input type="text" name="city" id="city" class="form-control" maxlength="50" value="<%= val(draft, "city", ep.getCity()) %>">
            </div>

            <div class="form-group">
                <label class="form-label" for="country">Country</label>
                <input type="text" name="country" id="country" class="form-control" maxlength="50"
                       value="<%= val(draft, "country", isEdit ? ep.getCountry() : "Pakistan") %>">
            </div>
            <div class="form-group <%= isEdit ? "" : "span-2" %>">
                <label class="form-label" for="address">Address</label>
                <input type="text" name="address" id="address" class="form-control" maxlength="500" value="<%= val(draft, "address", ep.getAddress()) %>">
            </div>
            <% if (isEdit) { %>
                <div class="form-group">
                    <label class="form-label" for="isActive">Status</label>
                    <select name="isActive" id="isActive" class="form-control">
                        <option value="true" <%= sel(draft, "isActive", edit.isActive(), "true") %>>Active (can log in)</option>
                        <option value="false" <%= sel(draft, "isActive", edit.isActive(), "false") %>>Inactive (cannot log in)</option>
                    </select>
                </div>
            <% } %>

            <div class="span-all" style="display:flex; gap:10px;">
                <button type="submit" class="btn btn-primary"><%= isEdit ? "Save Changes" : "Add Student" %></button>
                <% if (isEdit) { %><a href="manageStudents" class="btn btn-secondary">Cancel</a><% } %>
            </div>
        </form>
    </div>

    <!-- STUDENTS LIST -->
    <div class="card">
        <h3>Existing Students <span class="muted">(<%= students.size() %> total, <%= activeCount %> active)</span></h3>

        <div class="filter-bar" style="margin-top:12px;">
            <input type="search" id="userSearch" class="form-control" autocomplete="off"
                placeholder="Search by roll no, name, father name, email or course" aria-label="Search students">
            <select id="userGroup" class="form-control" aria-label="Filter by class">
                <option value="">All classes</option>
                <% for (AcademicClass c : classes) { %>
                    <option value="<%= c.getClassId() %>"><%= HtmlUtil.esc(c.getDisplayName()) %> (<%= c.getStudentCount() %>)</option>
                <% } %>
                <option value="none">Not assigned</option>
            </select>
            <select id="userStatus" class="form-control" aria-label="Filter by status">
                <option value="">All statuses</option>
                <option value="active">Active</option>
                <option value="inactive">Inactive</option>
            </select>
            <span class="muted" id="userCount"></span>
        </div>

        <div class="table-scroll">
        <table class="styled-table" id="userTable">
            <thead>
                <tr>
                    <th>Student</th>
                    <th>Contact</th>
                    <th>Class</th>
                    <th>Courses (this semester)</th>
                    <th>Status</th>
                    <th>Actions</th>
                </tr>
            </thead>
            <tbody>
            <% if (students.isEmpty()) { %>
                <tr><td colspan="6" style="text-align:center;">No students found.</td></tr>
            <% } %>
            <% for (User s : students) {
                   Profile p = s.getProfile() != null ? s.getProfile() : new Profile();
                   EnrollmentDAO.StudentSummary sum = summaries.getOrDefault(s.getUserId(), none);
                   String name = p.getFullName() != null ? p.getFullName() : s.getUsername();
                   String blockDelete = sum.hasAcademicRecords()
                           ? "Has " + sum.enrollmentRecords + " enrollment record(s)" + (sum.challans > 0 ? " and " + sum.challans + " challan(s)" : "") + ": deactivate instead"
                           : null;
            %>
                <tr class="user-row <%= s.isActive() ? "" : "inactive-row" %>"
                    data-group="<%= s.getClassId() != null ? s.getClassId() : "none" %>"
                    data-status="<%= s.isActive() ? "active" : "inactive" %>"
                    data-text="<%= HtmlUtil.esc((s.getUsername() + " " + name + " " + (p.getFatherName() != null ? p.getFatherName() : "") + " " + (p.getEmail() != null ? p.getEmail() : "") + " " + (s.getClassName() != null ? s.getClassName() : "") + " " + String.join(" ", sum.currentCourses)).toLowerCase()) %>">
                    <td>
                        <strong><%= HtmlUtil.esc(name) %></strong>
                        <span class="cell-sub"><%= HtmlUtil.esc(s.getUsername()) %></span>
                        <% if (p.getFatherName() != null) { %><span class="cell-sub">Father: <%= HtmlUtil.esc(p.getFatherName()) %></span><% } %>
                    </td>
                    <td>
                        <%= p.getEmail() != null ? HtmlUtil.esc(p.getEmail()) : "<span class=\"muted\">-</span>" %>
                        <% if (p.getPhone() != null) { %><span class="cell-sub"><%= HtmlUtil.esc(p.getPhone()) %></span><% } %>
                    </td>
                    <td>
                        <% if (s.getClassName() != null) { %><span class="tag"><%= HtmlUtil.esc(s.getClassName()) %></span>
                        <% } else { %><span class="tag tag-missing">Not assigned</span><% } %>
                    </td>
                    <td>
                        <div class="tags">
                        <% for (String code : sum.currentCourses) { %><span class="tag"><%= HtmlUtil.esc(code) %></span><% } %>
                        <% if (sum.currentCourses.isEmpty()) { %><span class="muted">None</span><% } %>
                        </div>
                        <% if (sum.enrollmentRecords > 0) { %><span class="cell-sub"><%= sum.enrollmentRecords %> enrollment record<%= sum.enrollmentRecords == 1 ? "" : "s" %> in total</span><% } %>
                        <% if (sum.pendingRequests > 0) { %><a class="pending-pill" href="manageCourseRequests"><%= sum.pendingRequests %> pending add/drop</a><% } %>
                    </td>
                    <td>
                        <span class="status-badge status-<%= s.isActive() ? "active" : "inactive" %>"><%= s.isActive() ? "Active" : "Inactive" %></span>
                    </td>
                    <td>
                        <div class="row-actions">
                            <a href="manageStudents?editId=<%= s.getUserId() %>#studentForm" class="btn btn-primary btn-sm">Edit</a>
                            <form action="manageStudents" method="post"
                                  data-confirm="<%= s.isActive() ? "Deactivate " + HtmlUtil.esc(name) + "? They will not be able to log in." : "Activate " + HtmlUtil.esc(name) + "? They will be able to log in again." %>"
                                  onsubmit="return confirm(this.dataset.confirm);">
                                <input type="hidden" name="action" value="<%= s.isActive() ? "deactivate" : "activate" %>">
                                <input type="hidden" name="userId" value="<%= s.getUserId() %>">
                                <button type="submit" class="btn btn-sm <%= s.isActive() ? "" : "btn-success" %>" style="<%= s.isActive() ? "background:#fef3c7; color:#92400e;" : "" %>">
                                    <%= s.isActive() ? "Deactivate" : "Activate" %>
                                </button>
                            </form>
                            <form action="manageStudents" method="post"
                                  data-confirm="Permanently delete <%= HtmlUtil.esc(name) %>? Their messages, help desk tickets and requests are deleted too. This cannot be undone."
                                  onsubmit="return confirm(this.dataset.confirm);">
                                <input type="hidden" name="action" value="delete">
                                <input type="hidden" name="userId" value="<%= s.getUserId() %>">
                                <button type="submit" class="btn btn-danger btn-sm" <%= blockDelete == null ? "" : "disabled title=\"" + HtmlUtil.esc(blockDelete) + "\"" %>>Delete</button>
                            </form>
                        </div>
                        <% if (blockDelete != null) { %><span class="cell-sub" style="font-size:0.75em;">Delete: has academic records</span><% } %>
                    </td>
                </tr>
            <% } %>
                <tr id="userNoMatch" hidden>
                    <td colspan="6" style="text-align:center;">No students match your filters.</td>
                </tr>
            </tbody>
        </table>
        </div>
    </div>

</div>
</main>

<script src="js/user-filter.js"></script>
</body>
</html>
