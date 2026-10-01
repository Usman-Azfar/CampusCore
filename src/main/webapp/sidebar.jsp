<%@ page contentType="text/html;charset=UTF-8" language="java"
    import="com.cms.models.User" %>

<%
    User currentUser = (User) session.getAttribute("user");
    String userRole = (String) session.getAttribute("role");
    // The address the user opened (e.g. /updateProfile). After a servlet forwards to a JSP,
    // getRequestURI() is the JSP path (/update_profile.jsp), so prefer the original URI.
    String uri = (String) request.getAttribute("jakarta.servlet.forward.request_uri");
    if (uri == null) uri = request.getRequestURI();
    int unreadMessages = (currentUser != null) ? new com.cms.dao.MessageDAO().countUnread(currentUser.getUserId()) : 0;
%>

<aside class="sidebar">

    <div class="sidebar-header">
        <a href="dashboard" class="sidebar-brand" aria-label="CampusCore home">
            <img src="images/campuscore-icon-192.png" alt="" class="sidebar-logo" width="60" height="60">
            <span class="brand-name"><span class="brand-campus">Campus</span><span class="brand-core">Core</span></span>
        </a>
        <div class="brand-tagline">A Smart Campus Management System</div>

        <small class="sidebar-role"><%= userRole %></small>
    </div>

    <nav class="sidebar-nav">

        <!-- Dashboard -->
        <a href="dashboard" class="nav-item <%= uri.contains("/dashboard") ? "active" : "" %>">
            Dashboard
        </a>

        <!-- Non Admin -->
        <% if (!"ADMIN".equals(userRole)) { %>
            <a href="faq.jsp" class="nav-item">FAQs</a>
        <% } %>

        <a href="helpdesk" class="nav-item <%= uri.contains("/helpdesk") ? "active" : "" %>">
            Help Desk
        </a>

        <a href="messages" class="nav-item <%= uri.contains("/messages") ? "active" : "" %>">
            Messages
            <% if (unreadMessages > 0) { %>
                <span style="background:var(--secondary-color); color:#fff; border-radius:10px; padding:0 7px; font-size:0.75em; margin-left:6px;"><%= unreadMessages %></span>
            <% } %>
        </a>

        <!-- ADMIN -->
        <% if ("ADMIN".equals(userRole)) { %>
            <a href="manageCourseRequests"
               class="nav-item <%= uri.contains("/manageCourseRequests") ? "active" : "" %>">
                View Add / Drop Requests
            </a>
        <% } %>

        <!-- STUDENT -->
        <% if ("STUDENT".equals(userRole)) { %>

            <div class="sidebar-section">Academic</div>

            <a href="gradebook" class="nav-item <%= uri.contains("/gradebook") ? "active" : "" %>">
                Gradebook
            </a>

            <a href="attendance" class="nav-item <%= uri.contains("/attendance") ? "active" : "" %>">
                Attendance
            </a>

            <a href="announcements" class="nav-item <%= uri.contains("/announcements") ? "active" : "" %>">
                Announcements
            </a>

            <div class="sidebar-section">Services</div>

            <a href="transcript" class="nav-item <%= uri.contains("/transcript") ? "active" : "" %>">
                View Transcript
            </a>

            <a href="courseRequests" class="nav-item <%= uri.contains("/courseRequests") ? "active" : "" %>">
                Add / Drop Subjects
            </a>

            <a href="challans" class="nav-item <%= uri.contains("/challans") ? "active" : "" %>">
                Accounts / Challan
            </a>

        <% } else if ("TEACHER".equals(userRole)) { %>

            <div class="sidebar-section">Teaching</div>

            <a href="myCourses"
               class="nav-item <%= uri.contains("/myCourses") || uri.contains("/my_courses") || uri.contains("/manageAnnouncements") || uri.contains("/course_announcements") ? "active" : "" %>">
                Course List
            </a>

            <a href="manageAttendance"
               class="nav-item <%= uri.contains("/manageAttendance") ? "active" : "" %>">
                Mark Attendance
            </a>

            <a href="uploadGrades"
               class="nav-item <%= uri.contains("/uploadGrades") || uri.contains("/upload_grades") ? "active" : "" %>">
                Upload Grades
            </a>

            <a href="announcements" class="nav-item <%= uri.contains("/announcements") ? "active" : "" %>">
                Announcements
            </a>

        <% } else if ("ADMIN".equals(userRole)) { %>

            <div class="sidebar-section">Admin Services</div>

            <a href="announcements" class="nav-item <%= uri.contains("/announcements") ? "active" : "" %>">
                Announcements
            </a>

            <a href="manageTeachers"
               class="nav-item <%= uri.contains("/manageTeachers") ? "active" : "" %>">
                Manage Teachers
            </a>

            <a href="manageStudents"
               class="nav-item <%= uri.contains("/manageStudents") ? "active" : "" %>">
                Manage Students
            </a>

            <a href="manageCourses"
               class="nav-item <%= (uri.contains("/manageCourses") || uri.contains("/manageAllocations") || uri.contains("/manageEnrollment")) ? "active" : "" %>">
                Manage Courses
            </a>

            <a href="manageSemesters"
               class="nav-item <%= uri.contains("/manageSemesters") || uri.contains("/manage_semesters") ? "active" : "" %>">
                Manage Semesters
            </a>

            <a href="manageClasses"
               class="nav-item <%= uri.contains("/manageClasses") || uri.contains("/manage_classes") ? "active" : "" %>">
                Classes &amp; Departments
            </a>

            <a href="uploadChallan"
               class="nav-item <%= uri.contains("/uploadChallan") ? "active" : "" %>">
                Accounts / Challan
            </a>

        <% } %>

        <!-- ACCOUNT -->
        <div class="sidebar-section">Account</div>

        <a href="updateProfile"
           class="nav-item <%= uri.contains("/updateProfile") ? "active" : "" %>">
            Update Profile
        </a>

        <a href="changePassword"
           class="nav-item <%= uri.contains("/changePassword") ? "active" : "" %>">
            Change Password
        </a>

    </nav>

    <div class="sidebar-footer">
        <a href="logout" class="logout-btn">Logout</a>
    </div>

</aside>
