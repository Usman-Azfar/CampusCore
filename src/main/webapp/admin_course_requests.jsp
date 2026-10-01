<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Collections, com.cms.models.CourseRequest, com.cms.models.User, com.cms.models.AcademicClass, com.cms.util.HtmlUtil" %>
<%!
    // Student cell: name, roll number and a data-* friendly search string
    private String studentName(User s) {
        if (s == null) return "Unknown";
        String n = s.getProfile() != null ? s.getProfile().getFullName() : null;
        return (n == null || n.trim().isEmpty()) ? s.getUsername() : n;
    }

    private String classTag(User s) {
        if (s != null && s.getClassName() != null)
            return "<span class=\"tag\">" + HtmlUtil.esc(s.getClassName()) + "</span>";
        return "<span class=\"tag tag-missing\">Not assigned</span>";
    }

    private String rowData(CourseRequest r) {
        User s = r.getStudent();
        String text = (studentName(s) + " " + (s != null ? s.getUsername() : "") + " "
                + r.getCourse().getCourseCode() + " " + r.getCourse().getCourseName() + " "
                + (s != null && s.getClassName() != null ? s.getClassName() : "")).toLowerCase();
        return "data-class=\"" + (s != null && s.getClassId() != null ? s.getClassId() : "none") + "\" "
                + "data-type=\"" + HtmlUtil.esc(r.getType()) + "\" "
                + "data-text=\"" + HtmlUtil.esc(text) + "\"";
    }
%>
<%
    List<CourseRequest> pendingList = (List<CourseRequest>) request.getAttribute("pendingRequests");
    List<CourseRequest> processedList = (List<CourseRequest>) request.getAttribute("processedRequests");
    List<AcademicClass> classes = (List<AcademicClass>) request.getAttribute("classes");
    if (pendingList == null) pendingList = Collections.emptyList();
    if (processedList == null) processedList = Collections.emptyList();
    if (classes == null) classes = Collections.emptyList();
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Manage Course Requests | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .student-cell strong { display: block; }
        .actions { display: flex; gap: 8px; flex-wrap: wrap; }
        .count-pill { background: var(--secondary-color); color: var(--text-color); border-radius: 999px; padding: 1px 9px; font-size: 0.8rem; margin-left: 6px; vertical-align: middle; }
        .empty-row td { text-align: center; color: #6b7280; }
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

            <div class="filter-bar" role="search">
                <input type="search" id="reqSearch" class="form-control" autocomplete="off"
                    placeholder="Search by student, roll no, course or class" aria-label="Search requests">
                <select id="reqClass" class="form-control" aria-label="Filter by class">
                    <option value="">All classes</option>
                    <% for (AcademicClass c : classes) { %>
                        <option value="<%= c.getClassId() %>"><%= HtmlUtil.esc(c.getDisplayName()) %></option>
                    <% } %>
                    <option value="none">No class assigned</option>
                </select>
                <select id="reqType" class="form-control" aria-label="Filter by request type">
                    <option value="">All types</option>
                    <option value="ADD">Add</option>
                    <option value="DROP">Drop</option>
                    <option value="WITHDRAW">Withdraw</option>
                </select>
            </div>

            <div class="card">
                <h2>Pending Add/Drop Requests <span class="count-pill" id="pendingCount"><%= pendingList.size() %></span></h2>
                <p class="muted" style="margin:4px 0 12px;">Oldest first. Approving applies the change to the student's enrollment in the active semester.</p>

                <div class="table-scroll">
                <table class="styled-table" id="pendingTable">
                    <thead>
                        <tr>
                            <th>#</th>
                            <th>Student</th>
                            <th>Class</th>
                            <th>Type</th>
                            <th>Course</th>
                            <th>Requested</th>
                            <th>Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% for (CourseRequest req : pendingList) { User s = req.getStudent(); %>
                        <tr class="req-row" <%= rowData(req) %>>
                            <td>#<%= req.getRequestId() %></td>
                            <td class="student-cell">
                                <strong><%= HtmlUtil.esc(studentName(s)) %></strong>
                                <span class="muted"><%= HtmlUtil.esc(s != null ? s.getUsername() : "") %></span>
                            </td>
                            <td><%= classTag(s) %></td>
                            <td><span class="type-<%= HtmlUtil.esc(req.getType().toLowerCase()) %>"><%= HtmlUtil.esc(req.getType()) %></span></td>
                            <td><%= HtmlUtil.esc(req.getCourse().getCourseCode()) %> - <%= HtmlUtil.esc(req.getCourse().getCourseName()) %></td>
                            <td><%= HtmlUtil.formatDate(req.getRequestDate()) %></td>
                            <td>
                                <form action="manageCourseRequests" method="post" class="actions">
                                    <input type="hidden" name="requestId" value="<%= req.getRequestId() %>">
                                    <button type="submit" name="action" value="APPROVE" class="btn btn-success btn-sm">Approve</button>
                                    <button type="submit" name="action" value="REJECT" class="btn btn-danger btn-sm"
                                        onclick="return confirm('Reject request #<%= req.getRequestId() %>?');">Reject</button>
                                </form>
                            </td>
                        </tr>
                        <% } %>
                        <tr class="empty-row" <%= pendingList.isEmpty() ? "" : "hidden" %>>
                            <td colspan="7"><%= pendingList.isEmpty() ? "No pending requests." : "No pending requests match your filters." %></td>
                        </tr>
                    </tbody>
                </table>
                </div>
            </div>

            <!-- HISTORY -->
            <div class="card">
                <h3>History <span class="count-pill" id="historyCount" style="background:#e5e7eb;"><%= processedList.size() %></span></h3>
                <p class="muted" style="margin:4px 0 12px;">Most recently processed first.</p>

                <div class="table-scroll">
                <table class="styled-table" id="historyTable">
                    <thead>
                        <tr>
                            <th>#</th>
                            <th>Student</th>
                            <th>Class</th>
                            <th>Type</th>
                            <th>Course</th>
                            <th>Requested</th>
                            <th>Status</th>
                            <th>Processed</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% for (CourseRequest req : processedList) { User s = req.getStudent(); %>
                        <tr class="req-row" <%= rowData(req) %>>
                            <td>#<%= req.getRequestId() %></td>
                            <td class="student-cell">
                                <strong><%= HtmlUtil.esc(studentName(s)) %></strong>
                                <span class="muted"><%= HtmlUtil.esc(s != null ? s.getUsername() : "") %></span>
                            </td>
                            <td><%= classTag(s) %></td>
                            <td><span class="type-<%= HtmlUtil.esc(req.getType().toLowerCase()) %>"><%= HtmlUtil.esc(req.getType()) %></span></td>
                            <td><%= HtmlUtil.esc(req.getCourse().getCourseCode()) %> - <%= HtmlUtil.esc(req.getCourse().getCourseName()) %></td>
                            <td><%= HtmlUtil.formatDate(req.getRequestDate()) %></td>
                            <td><span class="status-badge status-<%= HtmlUtil.esc(req.getStatus().toLowerCase()) %>"><%= HtmlUtil.esc(req.getStatus()) %></span></td>
                            <td>
                                <% if (req.getProcessedAt() != null) { %>
                                    <%= HtmlUtil.formatDate(req.getProcessedAt()) %><br>
                                    <span class="muted">by <%= HtmlUtil.esc(req.getProcessedByName() != null ? req.getProcessedByName() : "admin") %></span>
                                <% } else { %>
                                    <span class="muted">-</span>
                                <% } %>
                            </td>
                        </tr>
                        <% } %>
                        <tr class="empty-row" <%= processedList.isEmpty() ? "" : "hidden" %>>
                            <td colspan="8"><%= processedList.isEmpty() ? "No processed requests yet." : "No processed requests match your filters." %></td>
                        </tr>
                    </tbody>
                </table>
                </div>
            </div>

        </div>
    </main>

    <script>
    (function () {
        var search = document.getElementById("reqSearch");
        var cls = document.getElementById("reqClass");
        var type = document.getElementById("reqType");

        function filterTable(tableId, countId) {
            var rows = document.querySelectorAll("#" + tableId + " .req-row");
            var terms = search.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
            var visible = 0;
            rows.forEach(function (r) {
                var ok = (!cls.value || r.dataset.class === cls.value)
                    && (!type.value || r.dataset.type === type.value)
                    && terms.every(function (t) { return r.dataset.text.indexOf(t) !== -1; });
                r.hidden = !ok;
                if (ok) visible++;
            });
            var filtered = terms.length || cls.value || type.value;
            document.getElementById(countId).textContent = filtered ? visible + " / " + rows.length : rows.length;
            var empty = document.querySelector("#" + tableId + " .empty-row");
            if (rows.length) empty.hidden = visible > 0;
        }

        function apply() {
            filterTable("pendingTable", "pendingCount");
            filterTable("historyTable", "historyCount");
        }
        search.addEventListener("input", apply);
        cls.addEventListener("change", apply);
        type.addEventListener("change", apply);
    })();
    </script>
</body>
</html>
