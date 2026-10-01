<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Collections, com.cms.models.Semester, com.cms.util.HtmlUtil" %>
<%
    List<Semester> semesters = (List<Semester>) request.getAttribute("semesters");
    if (semesters == null) semesters = Collections.emptyList();
    Semester edit = (Semester) request.getAttribute("semesterToEdit");
    boolean isEdit = edit != null;
    boolean anyActive = false;
    for (Semester s : semesters) if (s.isActive()) anyActive = true;
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
        .row-actions { display: flex; gap: 6px; flex-wrap: wrap; }
        .row-actions form { margin: 0; }
        .active-row { background: #f0fdf4 !important; }
        .usage { font-size: 0.85em; }
        @media (max-width: 800px) { .sem-form { grid-template-columns: 1fr; } }
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
        <% if (!anyActive && !semesters.isEmpty()) { %>
            <div class="alert alert-error">No semester is active. Students cannot submit add/drop requests and new course offerings have no current semester until you activate one.</div>
        <% } %>

        <!-- Add / Edit -->
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
            <p class="muted" style="margin-top:10px;">New semesters start inactive. Only one semester can be active at a time.</p>
        </div>

        <!-- List -->
        <div class="card">
            <h3>Semesters</h3>
            <div class="table-scroll">
            <table class="styled-table">
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>Dates</th>
                        <th>Status</th>
                        <th>In use</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                <% if (semesters.isEmpty()) { %>
                    <tr><td colspan="5" style="text-align:center;">No semesters yet.</td></tr>
                <% } %>
                <% for (Semester s : semesters) {
                       String removeBlock = s.isActive() ? "Deactivate it or activate another semester first"
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
                            <%= s.getAllocationCount() %> course offering<%= s.getAllocationCount() == 1 ? "" : "s" %>
                            <% if (s.getChallanCount() > 0) { %><br><%= s.getChallanCount() %> challan<%= s.getChallanCount() == 1 ? "" : "s" %><% } %>
                            <% if (s.getAllocationCount() > 0) { %><br><a href="manageAllocations" style="color:var(--primary-color);">View offerings</a><% } %>
                        </td>
                        <td>
                            <div class="row-actions">
                                <a href="manageSemesters?editId=<%= s.getSemesterId() %>#semForm" class="btn btn-secondary btn-sm">Edit</a>
                                <% if (s.isActive()) { %>
                                    <form action="manageSemesters" method="post"
                                        data-confirm="Deactivate <%= HtmlUtil.esc(s.getName()) %>? No semester will be active, so students cannot submit add/drop requests until you activate one."
                                        onsubmit="return confirm(this.dataset.confirm);">
                                        <input type="hidden" name="action" value="deactivate">
                                        <input type="hidden" name="semesterId" value="<%= s.getSemesterId() %>">
                                        <button type="submit" class="btn btn-sm" style="background:#fef3c7; color:#92400e;">Deactivate</button>
                                    </form>
                                <% } else { %>
                                    <form action="manageSemesters" method="post"
                                        data-confirm="Make <%= HtmlUtil.esc(s.getName()) %> the active semester?<%= anyActive ? " The current active semester will be deactivated." : "" %>"
                                        onsubmit="return confirm(this.dataset.confirm);">
                                        <input type="hidden" name="action" value="activate">
                                        <input type="hidden" name="semesterId" value="<%= s.getSemesterId() %>">
                                        <button type="submit" class="btn btn-success btn-sm">Activate</button>
                                    </form>
                                <% } %>
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
    </div>
</main>
</body>
</html>
