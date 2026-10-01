<%@ page import="java.util.List, com.cms.models.Announcement" %>
    <!DOCTYPE html>
    <html lang="en">

    <head>
        <meta charset="UTF-8">
        <title>Announcements | CampusCore</title>
        <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
        <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
        <link rel="stylesheet" href="css/styles.css">
    </head>

    <body>
        <jsp:include page="sidebar.jsp" />

        <main class="main-content">
            <jsp:include page="header.jsp" />

            <div class="page-container">
                <h2 class="card-title" style="margin-bottom: 20px;">Announcements</h2>

                <% List<Announcement> announcements = (List<Announcement>) request.getAttribute("announcements");
                        if (announcements != null && !announcements.isEmpty()) {
                        for(Announcement ann : announcements) {
                        %>
                        <div class="card">
                            <div style="display:flex; justify-content:space-between; margin-bottom:10px;">
                                <h3 style="color:var(--primary-color);">
                                    <%= ann.getTitle() %>
                                        <% if(ann.getCourseId() !=0 && ann.getCourse() !=null) { %>
                                            <span
                                                style="font-size:0.7em; background:#eee; padding:2px 6px; border-radius:4px; color:#666;">
                                                <%= ann.getCourse().getCourseName() %>
                                            </span>
                                            <% } else { %>
                                                <span
                                                    style="font-size:0.7em; background:var(--secondary-color); padding:2px 6px; border-radius:4px; color:#black;">GENERAL</span>
                                                <% } %>
                                </h3>
                                <small style="color:#888;">
                                    <%= ann.getCreatedAt() %>
                                </small>
                            </div>
                            <p style="white-space: pre-wrap;">
                                <%= ann.getContent() %>
                            </p>
                            <div style="margin-top:10px; font-size:0.85em; color:#666;">
                                Posted by: <%= ann.getCreator() !=null ? ann.getCreator().getUsername() : "Admin" %>
                            </div>
                        </div>
                        <% } } else { %>
                            <div class="card">
                                <p>No new announcements.</p>
                            </div>
                            <% } %>

            </div>
        </main>
    </body>

    </html>