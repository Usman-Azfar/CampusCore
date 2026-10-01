<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Set, java.util.Collections, com.cms.models.Course, com.cms.models.CourseRequest, com.cms.models.Enrollment, com.cms.models.User, com.cms.util.HtmlUtil" %>
<%
    List<Course> addable = (List<Course>) request.getAttribute("addableCourses");
    List<Enrollment> current = (List<Enrollment>) request.getAttribute("currentEnrollments");
    Set<Integer> pending = (Set<Integer>) request.getAttribute("pendingCourseIds");
    List<CourseRequest> history = (List<CourseRequest>) request.getAttribute("requestHistory");
    User student = (User) request.getAttribute("student");
    if (addable == null) addable = Collections.emptyList();
    if (current == null) current = Collections.emptyList();
    if (pending == null) pending = Collections.emptySet();
    if (history == null) history = Collections.emptyList();

    int addableFree = 0;
    for (Course c : addable) if (!pending.contains(c.getCourseId())) addableFree++;
    int currentFree = 0;
    for (Enrollment e : current) if (!pending.contains(e.getCourseAllocation().getCourse().getCourseId())) currentFree++;
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Course Requests | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .request-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; margin-top: 16px; }
        .request-box { background: #f8f9fa; border: 1px solid #eef0f3; border-radius: var(--border-radius); padding: 16px; display: flex; flex-direction: column; }
        .request-box h3 { font-size: 1.05rem; margin-bottom: 4px; }
        .request-box p.muted { margin-bottom: 12px; }
        .request-box form { margin-top: auto; }
        .request-box .form-group { margin-bottom: 12px; }
        .student-meta { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; margin-top: 6px; }
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

        <div class="card">
            <h2>Add / Drop / Withdraw Course</h2>
            <% if (student != null) { %>
                <div class="student-meta">
                    <strong><%= HtmlUtil.esc(student.getDisplayName()) %></strong>
                    <% if (student.getClassName() != null) { %>
                        <span class="tag"><%= HtmlUtil.esc(student.getClassName()) %></span>
                    <% } else { %>
                        <span class="tag tag-missing">No class assigned</span>
                    <% } %>
                </div>
            <% } %>
            <p class="muted" style="margin-top:8px;">
                Requests are reviewed by the admin. You can have one pending request per course at a time.
            </p>

            <div class="request-grid">

                <!-- ADD -->
                <div class="request-box">
                    <h3 class="type-add">Add Course</h3>
                    <p class="muted">Courses offered this semester that you are not enrolled in.</p>
                    <% if (addable.isEmpty()) { %>
                        <p>No other courses are offered this semester.</p>
                    <% } else { %>
                    <form action="courseRequests" method="post">
                        <input type="hidden" name="type" value="ADD">
                        <div class="form-group">
                            <label class="form-label" for="addCourse">Course</label>
                            <select name="courseId" id="addCourse" class="form-control" required>
                                <option value="">-- Select course --</option>
                                <% for (Course c : addable) { boolean p = pending.contains(c.getCourseId()); %>
                                    <option value="<%= c.getCourseId() %>" <%= p ? "disabled" : "" %>>
                                        <%= HtmlUtil.esc(c.getCourseCode()) %> - <%= HtmlUtil.esc(c.getCourseName()) %>
                                        (<%= c.getCreditHours() %> Cr)<%= p ? " - request pending" : "" %>
                                    </option>
                                <% } %>
                            </select>
                        </div>
                        <button type="submit" class="btn btn-primary" style="width:100%;" <%= addableFree == 0 ? "disabled" : "" %>>
                            Request Add
                        </button>
                    </form>
                    <% } %>
                </div>

                <!-- DROP -->
                <div class="request-box">
                    <h3 class="type-drop">Drop Course</h3>
                    <p class="muted">Remove a course you are taking this semester.</p>
                    <% if (current.isEmpty()) { %>
                        <p>You are not enrolled in any course this semester.</p>
                    <% } else { %>
                    <form action="courseRequests" method="post">
                        <input type="hidden" name="type" value="DROP">
                        <div class="form-group">
                            <label class="form-label" for="dropCourse">Course</label>
                            <select name="courseId" id="dropCourse" class="form-control" required>
                                <option value="">-- Select course --</option>
                                <% for (Enrollment e : current) { Course c = e.getCourseAllocation().getCourse(); boolean p = pending.contains(c.getCourseId()); %>
                                    <option value="<%= c.getCourseId() %>" <%= p ? "disabled" : "" %>>
                                        <%= HtmlUtil.esc(c.getCourseCode()) %> - <%= HtmlUtil.esc(c.getCourseName()) %><%= p ? " - request pending" : "" %>
                                    </option>
                                <% } %>
                            </select>
                        </div>
                        <button type="submit" class="btn btn-danger" style="width:100%;" <%= currentFree == 0 ? "disabled" : "" %>
                            onclick="return confirm('Request to DROP this course?');">
                            Request Drop
                        </button>
                    </form>
                    <% } %>
                </div>

                <!-- WITHDRAW -->
                <div class="request-box">
                    <h3 class="type-withdraw">Withdraw Course</h3>
                    <p class="muted">Leave a course this semester; it is kept in your record as Withdrawn.</p>
                    <% if (current.isEmpty()) { %>
                        <p>You are not enrolled in any course this semester.</p>
                    <% } else { %>
                    <form action="courseRequests" method="post">
                        <input type="hidden" name="type" value="WITHDRAW">
                        <div class="form-group">
                            <label class="form-label" for="withdrawCourse">Course</label>
                            <select name="courseId" id="withdrawCourse" class="form-control" required>
                                <option value="">-- Select course --</option>
                                <% for (Enrollment e : current) { Course c = e.getCourseAllocation().getCourse(); boolean p = pending.contains(c.getCourseId()); %>
                                    <option value="<%= c.getCourseId() %>" <%= p ? "disabled" : "" %>>
                                        <%= HtmlUtil.esc(c.getCourseCode()) %> - <%= HtmlUtil.esc(c.getCourseName()) %><%= p ? " - request pending" : "" %>
                                    </option>
                                <% } %>
                            </select>
                        </div>
                        <button type="submit" class="btn btn-secondary" style="width:100%;" <%= currentFree == 0 ? "disabled" : "" %>
                            onclick="return confirm('Request to WITHDRAW from this course?');">
                            Request Withdraw
                        </button>
                    </form>
                    <% } %>
                </div>

            </div>
        </div>

        <!-- REQUEST HISTORY -->
        <div class="card">
            <h3>My Requests</h3>
            <div class="table-scroll">
            <table class="styled-table">
                <thead>
                <tr>
                    <th>ID</th>
                    <th>Type</th>
                    <th>Course</th>
                    <th>Requested</th>
                    <th>Status</th>
                    <th>Processed</th>
                </tr>
                </thead>
                <tbody>
                <% if (!history.isEmpty()) {
                       for (CourseRequest req : history) { %>
                <tr>
                    <td>#<%= req.getRequestId() %></td>
                    <td><span class="type-<%= HtmlUtil.esc(req.getType().toLowerCase()) %>"><%= HtmlUtil.esc(req.getType()) %></span></td>
                    <td><%= HtmlUtil.esc(req.getCourse().getCourseCode()) %> - <%= HtmlUtil.esc(req.getCourse().getCourseName()) %></td>
                    <td><%= HtmlUtil.formatDate(req.getRequestDate()) %></td>
                    <td><span class="status-badge status-<%= HtmlUtil.esc(req.getStatus().toLowerCase()) %>"><%= HtmlUtil.esc(req.getStatus()) %></span></td>
                    <td><%= req.getProcessedAt() != null ? HtmlUtil.formatDate(req.getProcessedAt()) : "-" %></td>
                </tr>
                <%     }
                   } else { %>
                <tr>
                    <td colspan="6" style="text-align:center;">No requests yet.</td>
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
