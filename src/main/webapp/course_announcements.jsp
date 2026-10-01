<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Collections, com.cms.models.Announcement, com.cms.models.CourseAllocation, com.cms.dao.AnnouncementDAO, com.cms.util.HtmlUtil" %>
<%
    CourseAllocation offering = (CourseAllocation) request.getAttribute("offering");
    List<Announcement> announcements = (List<Announcement>) request.getAttribute("announcements");
    if (announcements == null) announcements = Collections.emptyList();
    Announcement editing = (Announcement) request.getAttribute("editing");
    String draftTitle = (String) request.getAttribute("draftTitle");
    String draftContent = (String) request.getAttribute("draftContent");
    String formTitle = draftTitle != null ? draftTitle : editing != null ? editing.getTitle() : "";
    String formContent = draftContent != null ? draftContent : editing != null ? editing.getContent() : "";
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Course Announcements | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .page-head { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; align-items: flex-start; }
        .facts { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
        .ann { border-top: 1px solid #e5e7eb; padding: 14px 0; }
        .ann:first-of-type { border-top: none; }
        .ann-head { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; align-items: baseline; }
        .ann h4 { margin: 0; font-size: 1.05rem; color: #0f172a; }
        .ann-text { white-space: pre-wrap; word-break: break-word; margin-top: 8px; color: #334155; line-height: 1.55; }
        .ann-actions { display: flex; gap: 8px; margin-top: 10px; }
        .ann.editing { background: #fffbeb; margin: 0 -12px; padding: 14px 12px; border-radius: 8px; }
        .counter { text-align: right; }
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
            <div class="page-head">
                <div>
                    <h2>Course Announcements</h2>
                    <div class="facts">
                        <span class="tag"><%= HtmlUtil.esc(offering.getCourse().getCourseCode()) %></span>
                        <span class="tag"><%= HtmlUtil.esc(offering.getCourse().getCourseName()) %></span>
                        <span class="tag tag-department"><%= HtmlUtil.esc(offering.getSemester().getName()) %></span>
                        <span class="tag"><%= offering.getEnrolledCount() %> student<%= offering.getEnrolledCount() == 1 ? "" : "s" %></span>
                    </div>
                </div>
                <a href="myCourses" class="btn btn-secondary">Course List</a>
            </div>
            <p class="muted" style="margin-top:10px;">Students currently enrolled in this course see these on their Announcements page.</p>
        </div>

        <div class="card" id="form">
            <h3><%= editing != null ? "Edit announcement" : "Post a new announcement" %></h3>
            <form action="manageAnnouncements" method="post" style="margin-top:12px;">
                <input type="hidden" name="action" value="<%= editing != null ? "update" : "create" %>">
                <input type="hidden" name="allocationId" value="<%= offering.getAllocationId() %>">
                <% if (editing != null) { %><input type="hidden" name="announcementId" value="<%= editing.getAnnouncementId() %>"><% } %>
                <div class="form-group">
                    <label class="form-label" for="title">Title</label>
                    <input type="text" id="title" name="title" class="form-control" required maxlength="<%= AnnouncementDAO.MAX_TITLE %>"
                           placeholder="e.g. Quiz 2 on Monday" value="<%= HtmlUtil.esc(formTitle) %>">
                </div>
                <div class="form-group">
                    <label class="form-label" for="content">Announcement</label>
                    <textarea id="content" name="content" class="form-control" rows="5" required maxlength="<%= AnnouncementDAO.MAX_CONTENT %>"
                              placeholder="Details for your students..."><%= HtmlUtil.esc(formContent) %></textarea>
                    <div class="muted counter"><span id="count">0</span>/<%= AnnouncementDAO.MAX_CONTENT %></div>
                </div>
                <div style="display:flex; gap:10px; justify-content:flex-end;">
                    <% if (editing != null) { %><a class="btn btn-secondary" href="manageAnnouncements?allocationId=<%= offering.getAllocationId() %>">Cancel</a><% } %>
                    <button type="submit" class="btn btn-primary"><%= editing != null ? "Save changes" : "Post announcement" %></button>
                </div>
            </form>
        </div>

        <div class="card">
            <h3>Posted announcements (<%= announcements.size() %>)</h3>
            <% if (announcements.isEmpty()) { %>
                <p class="muted" style="margin-top:8px;">No announcements for this course yet.</p>
            <% } %>
            <% for (Announcement a : announcements) { %>
                <div class="ann <%= editing != null && editing.getAnnouncementId() == a.getAnnouncementId() ? "editing" : "" %>">
                    <div class="ann-head">
                        <h4><%= HtmlUtil.esc(a.getTitle()) %></h4>
                        <span class="muted"><%= HtmlUtil.formatDate(a.getCreatedAt()) %><%= a.getUpdatedAt() != null ? " &middot; edited " + HtmlUtil.formatDate(a.getUpdatedAt()) : "" %></span>
                    </div>
                    <div class="muted">Posted by <%= HtmlUtil.esc(a.getCreator().getUsername()) %></div>
                    <div class="ann-text"><%= HtmlUtil.esc(a.getContent()) %></div>
                    <div class="ann-actions">
                        <a class="btn btn-secondary btn-sm" href="manageAnnouncements?allocationId=<%= offering.getAllocationId() %>&edit=<%= a.getAnnouncementId() %>#form">Edit</a>
                        <form method="post" action="manageAnnouncements" data-confirm="Delete the announcement &quot;<%= HtmlUtil.esc(a.getTitle()) %>&quot;? Students will no longer see it."
                              onsubmit="return confirm(this.dataset.confirm);">
                            <input type="hidden" name="action" value="delete">
                            <input type="hidden" name="allocationId" value="<%= offering.getAllocationId() %>">
                            <input type="hidden" name="announcementId" value="<%= a.getAnnouncementId() %>">
                            <button type="submit" class="btn btn-danger btn-sm">Delete</button>
                        </form>
                    </div>
                </div>
            <% } %>
        </div>
    </div>
</main>
<script>
(function () {
    var content = document.getElementById("content"), count = document.getElementById("count");
    var update = function () { count.textContent = content.value.length; };
    content.addEventListener("input", update);
    update();
})();
</script>
</body>
</html>
