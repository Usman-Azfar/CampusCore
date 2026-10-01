<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
    <%@ page import="java.util.List" %>
        <%@ page import="com.cms.models.Announcement" %>
            <%@ page import="com.cms.models.Course" %>

                <!DOCTYPE html>
                <html lang="en">

                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Course Announcements | CampusCore</title>
                    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
                    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
                    <link rel="stylesheet" href="css/styles.css">
                    <style>
                        .announcement-container {
                            background: white;
                            padding: 2rem;
                            border-radius: 12px;
                            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
                            margin: 2rem;
                        }

                        .announcement-form {
                            background: #f8fafc;
                            padding: 1.5rem;
                            border-radius: 8px;
                            border: 1px solid #e2e8f0;
                            margin-bottom: 2rem;
                        }

                        .form-group {
                            margin-bottom: 1rem;
                        }

                        .form-group label {
                            display: block;
                            margin-bottom: 0.5rem;
                            font-weight: 600;
                            color: #1e293b;
                        }

                        .form-control {
                            width: 100%;
                            padding: 0.75rem;
                            border: 1px solid #cbd5e1;
                            border-radius: 6px;
                            font-size: 1rem;
                        }

                        .btn-post {
                            background: linear-gradient(135deg, var(--primary-color, #1e40af), var(--secondary-color, #3b82f6));
                            color: white;
                            padding: 0.75rem 1.5rem;
                            border: none;
                            border-radius: 6px;
                            cursor: pointer;
                            font-weight: 600;
                            transition: opacity 0.2s;
                        }

                        .btn-post:hover {
                            opacity: 0.9;
                        }

                        .announcement-list {
                            margin-top: 2rem;
                        }

                        .announcement-card {
                            padding: 1.5rem;
                            border-bottom: 1px solid #e2e8f0;
                        }

                        .announcement-card:last-child {
                            border-bottom: none;
                        }

                        .ann-title {
                            font-size: 1.25rem;
                            font-weight: 700;
                            color: #0f172a;
                            margin-bottom: 0.5rem;
                        }

                        .ann-meta {
                            font-size: 0.875rem;
                            color: #64748b;
                            margin-bottom: 1rem;
                        }

                        .ann-content {
                            color: #334155;
                            line-height: 1.6;
                        }

                        .success-msg {
                            background-color: #dcfce7;
                            color: #166534;
                            padding: 1rem;
                            border-radius: 6px;
                            margin-bottom: 1rem;
                            border: 1px solid #bbf7d0;
                        }
                    </style>
                </head>

                <body>
                    <jsp:include page="sidebar.jsp" />

                    <main class="main-content">
                        <jsp:include page="header.jsp" />

                        <div class="announcement-container">
                            <% Course course=(Course) request.getAttribute("course"); List<Announcement> announcements =
                                (List<Announcement>) request.getAttribute("announcements");
                                    %>

                                    <div
                                        style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 2rem;">
                                        <div>
                                            <h2 style="margin: 0;">Course Announcements</h2>
                                            <p style="color: #64748b; margin: 0.5rem 0 0 0;">
                                                <%= course !=null ? course.getCourseName() : "Unknown Course" %>
                                                    (<%= course !=null ? course.getCourseCode() : "N/A" %>)
                                            </p>
                                        </div>
                                        <a href="dashboard" class="nav-item" style="text-decoration: none;">&larr; Back
                                            to Dashboard</a>
                                    </div>

                                    <% if (request.getParameter("success") !=null) { %>
                                        <div class="success-msg">Announcement posted successfully!</div>
                                        <% } %>

                                            <div class="announcement-form">
                                                <h3 style="margin-top: 0;">Post New Announcement</h3>
                                                <form action="manageAnnouncements" method="POST">
                                                    <input type="hidden" name="courseId"
                                                        value="<%= course != null ? course.getCourseId() : "" %>">
                                                    <div class="form-group">
                                                        <label for="title">Title</label>
                                                        <input type="text" id="title" name="title" class="form-control"
                                                            required placeholder="Announcement Title">
                                                    </div>
                                                    <div class="form-group">
                                                        <label for="content">Content</label>
                                                        <textarea id="content" name="content" class="form-control"
                                                            rows="4" required
                                                            placeholder="Announcement details..."></textarea>
                                                    </div>
                                                    <button type="submit" class="btn-post">Post Announcement</button>
                                                </form>
                                            </div>

                                            <div class="announcement-list">
                                                <h3>Existing Announcements</h3>
                                                <% if (announcements !=null && !announcements.isEmpty()) { for
                                                    (Announcement ann : announcements) { %>
                                                    <div class="announcement-card">
                                                        <div class="ann-title">
                                                            <%= ann.getTitle() %>
                                                        </div>
                                                        <div class="ann-meta">
                                                            Posted on: <%= ann.getCreatedAt() %>
                                                        </div>
                                                        <div class="ann-content">
                                                            <%= ann.getContent() %>
                                                        </div>
                                                    </div>
                                                    <% } } else { %>
                                                        <p style="text-align: center; color: #64748b; padding: 2rem;">No
                                                            announcements for this course yet.</p>
                                                        <% } %>
                                            </div>
                        </div>
                    </main>
                </body>

                </html>