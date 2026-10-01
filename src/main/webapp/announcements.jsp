<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Collections, com.cms.models.Announcement, com.cms.models.Enrollment, com.cms.models.User, com.cms.dao.AnnouncementDAO, com.cms.util.HtmlUtil" %>
<%
    User user = (User) session.getAttribute("user");
    String role = user.getRole();
    boolean isAdmin = "ADMIN".equals(role), isStudent = "STUDENT".equals(role);
    List<Announcement> announcements = (List<Announcement>) request.getAttribute("announcements");
    if (announcements == null) announcements = Collections.emptyList();
    List<Enrollment> enrollments = (List<Enrollment>) request.getAttribute("enrollments");
    if (enrollments == null) enrollments = Collections.emptyList();
    Integer selectedAllocationId = (Integer) request.getAttribute("selectedAllocationId");

    Announcement editing = (Announcement) request.getAttribute("editing");
    String draftTitle = (String) request.getAttribute("draftTitle");
    String draftContent = (String) request.getAttribute("draftContent");
    String draftAudience = (String) request.getAttribute("draftAudience");
    String formTitle = draftTitle != null ? draftTitle : editing != null ? editing.getTitle() : "";
    String formContent = draftContent != null ? draftContent : editing != null ? editing.getContent() : "";
    String formAudience = draftAudience != null ? draftAudience : editing != null ? editing.getAudience() : Announcement.AUDIENCE_ALL;
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Announcements | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .head { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; align-items: flex-end; }
        .ann-head { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; align-items: baseline; }
        .ann-head h3 { color: var(--primary-color); margin: 0; font-size: 1.1rem; }
        .ann-tags { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 6px; }
        .tag-general { background: #eef2ff; color: #3730a3; }
        .ann-text { white-space: pre-wrap; word-break: break-word; margin-top: 10px; line-height: 1.55; color: #334155; }
        .ann-actions { display: flex; gap: 8px; margin-top: 10px; }
        .audience { display: flex; gap: 14px; flex-wrap: wrap; }
        .audience label { display: inline-flex; gap: 6px; align-items: center; cursor: pointer; }
        .counter { text-align: right; }
        .card.editing { outline: 2px solid #fbbf24; }
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
            <div class="head">
                <div>
                    <h2>Announcements</h2>
                    <p class="muted">
                        <% if (isStudent) { %>General announcements and those from the teachers of your courses, newest first.
                        <% } else if (isAdmin) { %>General announcements. Choose who sees each one: everyone, students only or teachers only. Teachers post course announcements from their Course List.
                        <% } else { %>Announcements from the administration. Post announcements for your own courses from the <a href="myCourses">Course List</a>.<% } %>
                    </p>
                </div>
                <% if (isStudent && !enrollments.isEmpty()) { %>
                <form method="get" action="announcements" id="courseFilter">
                    <label class="form-label" for="allocationId">Show</label>
                    <select name="allocationId" id="allocationId" class="form-control" onchange="this.form.submit()">
                        <option value="">All announcements</option>
                        <% java.util.Set<Integer> listed = new java.util.HashSet<>();
                           for (Enrollment e : enrollments) {
                               if (!listed.add(e.getAllocationId())) continue; %>
                            <option value="<%= e.getAllocationId() %>" <%= Integer.valueOf(e.getAllocationId()).equals(selectedAllocationId) ? "selected" : "" %>>
                                <%= HtmlUtil.esc(e.getCourseAllocation().getCourse().getCourseCode()) %> - <%= HtmlUtil.esc(e.getCourseAllocation().getCourse().getCourseName()) %>
                            </option>
                        <% } %>
                    </select>
                    <noscript><button type="submit" class="btn btn-secondary btn-sm" style="margin-top:6px;">Show</button></noscript>
                </form>
                <% } %>
            </div>
        </div>

        <% if (isAdmin) { %>
        <div class="card" id="form">
            <h3><%= editing != null ? "Edit announcement" : "Post a general announcement" %></h3>
            <form action="announcements" method="post" style="margin-top:12px;">
                <input type="hidden" name="action" value="<%= editing != null ? "update" : "create" %>">
                <% if (editing != null) { %><input type="hidden" name="announcementId" value="<%= editing.getAnnouncementId() %>"><% } %>
                <div class="form-group">
                    <label class="form-label" for="title">Title</label>
                    <input type="text" id="title" name="title" class="form-control" required maxlength="<%= AnnouncementDAO.MAX_TITLE %>"
                           placeholder="e.g. Mid-term exams schedule" value="<%= HtmlUtil.esc(formTitle) %>">
                </div>
                <div class="form-group">
                    <span class="form-label">Visible to</span>
                    <div class="audience" role="radiogroup" aria-label="Visible to">
                        <% for (String[] o : new String[][] { {Announcement.AUDIENCE_ALL, "Everyone"}, {Announcement.AUDIENCE_STUDENTS, "Students only"}, {Announcement.AUDIENCE_TEACHERS, "Teachers only"} }) { %>
                            <label><input type="radio" name="audience" value="<%= o[0] %>" <%= o[0].equals(formAudience) ? "checked" : "" %> required> <%= o[1] %></label>
                        <% } %>
                    </div>
                </div>
                <div class="form-group">
                    <label class="form-label" for="content">Announcement</label>
                    <textarea id="content" name="content" class="form-control" rows="5" required maxlength="<%= AnnouncementDAO.MAX_CONTENT %>"
                              placeholder="Details..."><%= HtmlUtil.esc(formContent) %></textarea>
                    <div class="muted counter"><span id="count">0</span>/<%= AnnouncementDAO.MAX_CONTENT %></div>
                </div>
                <div style="display:flex; gap:10px; justify-content:flex-end;">
                    <% if (editing != null) { %><a class="btn btn-secondary" href="announcements">Cancel</a><% } %>
                    <button type="submit" class="btn btn-primary"><%= editing != null ? "Save changes" : "Post announcement" %></button>
                </div>
            </form>
        </div>
        <% } %>

        <% if (announcements.isEmpty()) { %>
            <div class="card"><p><%= selectedAllocationId != null ? "No announcements for this course yet." : "No announcements yet." %></p></div>
        <% } %>
        <% for (Announcement a : announcements) { %>
            <div class="card <%= editing != null && editing.getAnnouncementId() == a.getAnnouncementId() ? "editing" : "" %>">
                <div class="ann-head">
                    <h3><%= HtmlUtil.esc(a.getTitle()) %></h3>
                    <span class="muted"><%= HtmlUtil.formatDate(a.getCreatedAt()) %><%= a.getUpdatedAt() != null ? " &middot; edited " + HtmlUtil.formatDate(a.getUpdatedAt()) : "" %></span>
                </div>
                <div class="ann-tags">
                    <% if (a.isGeneral()) { %>
                        <span class="tag tag-general">General</span>
                        <% if (isAdmin) { %><span class="tag">Visible to: <%= a.getAudienceLabel() %></span><% } %>
                    <% } else { %>
                        <span class="tag tag-department"><%= HtmlUtil.esc(a.getCourse().getCourseCode()) %> &middot; <%= HtmlUtil.esc(a.getCourse().getCourseName()) %></span>
                        <% if (a.getTermName() != null) { %><span class="tag"><%= HtmlUtil.esc(a.getTermName()) %></span><% } %>
                    <% } %>
                </div>
                <div class="ann-text"><%= HtmlUtil.esc(a.getContent()) %></div>
                <div class="muted" style="margin-top:10px;">Posted by <%= a.getCreator() != null && a.getCreator().getUsername() != null ? HtmlUtil.esc(a.getCreator().getUsername()) : "Administration" %></div>
                <% if (isAdmin) { %>
                <div class="ann-actions">
                    <a class="btn btn-secondary btn-sm" href="announcements?edit=<%= a.getAnnouncementId() %>#form">Edit</a>
                    <form method="post" action="announcements" data-confirm="Delete the announcement &quot;<%= HtmlUtil.esc(a.getTitle()) %>&quot;?"
                          onsubmit="return confirm(this.dataset.confirm);">
                        <input type="hidden" name="action" value="delete">
                        <input type="hidden" name="announcementId" value="<%= a.getAnnouncementId() %>">
                        <button type="submit" class="btn btn-danger btn-sm">Delete</button>
                    </form>
                </div>
                <% } %>
            </div>
        <% } %>
    </div>
</main>
<script>
(function () {
    var content = document.getElementById("content"), count = document.getElementById("count");
    if (!content || !count) return;
    var update = function () { count.textContent = content.value.length; };
    content.addEventListener("input", update);
    update();
})();
</script>
</body>
</html>
