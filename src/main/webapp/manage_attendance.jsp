<%@ page import="java.util.List, com.cms.models.CourseAllocation, com.cms.models.Enrollment" %>
    <!DOCTYPE html>
    <html lang="en">

    <head>
        <meta charset="UTF-8">
        <title>Manage Attendance | CampusCore</title>
        <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
        <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
        <link rel="stylesheet" href="css/styles.css">

        <style>
            .selection-card {
                background: white;
                padding: 20px;
                border-radius: 8px;
                margin-bottom: 20px;
            }

            .attendance-form {
                width: 100%;
            }

            .status-radio {
                margin-right: 15px;
            }

            .course-grid {
                display: grid;
                grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
                gap: 20px;
                margin-top: 20px;
            }

            .course-card {
                background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
                color: white;
                padding: 20px;
                border-radius: 8px;
                text-decoration: none;
                transition: transform 0.2s;
            }

            .course-card:hover {
                transform: translateY(-5px);
            }
        </style>
    </head>

    <body>

        <jsp:include page="sidebar.jsp" />

        <main class="main-content">
            <jsp:include page="header.jsp" />

            <div class="page-container">

                <% List<CourseAllocation> allocations =
                    (List<CourseAllocation>) request.getAttribute("allocations");

                        CourseAllocation selectedAllocation =
                        (CourseAllocation) request.getAttribute("selectedAllocation");

                        List<Enrollment> enrollments =
                            (List<Enrollment>) request.getAttribute("enrollments");

                                String success = request.getParameter("success");
                                %>

                                <% if (success !=null) { %>
                                    <div class="alert alert-success"
                                        style="background:#d4edda; color:#155724; padding:15px; border-radius:4px; margin-bottom:20px;">
                                        Attendance marked successfully for
                                        <%= request.getParameter("count") %> students!
                                    </div>
                                    <% } %>

                                        <!-- STEP 1: COURSE SELECTION -->
                                        <% if (selectedAllocation==null && allocations !=null) { %>
                                            <div class="card">
                                                <h2>Select Course to Mark Attendance</h2>

                                                <div class="course-grid">
                                                    <% for (CourseAllocation ca : allocations) { %>
                                                        <a href="manageAttendance?allocationId=<%= ca.getAllocationId() %>"
                                                            class="course-card">
                                                            <h3>
                                                                <%= ca.getCourse().getCourseName() %>
                                                            </h3>
                                                            <p>
                                                                <%= ca.getCourse().getCourseCode() %>
                                                            </p>
                                                        </a>
                                                        <% } %>
                                                </div>

                                                <% if (allocations.isEmpty()) { %>
                                                    <p>No courses allocated to you.</p>
                                                    <% } %>
                                            </div>
                                            <% } %>

                                                <!-- STEP 2: MARK ATTENDANCE -->
                                                <% if (selectedAllocation !=null) { %>
                                                    <div class="card">

                                                        <div
                                                            style="display:flex; justify-content:space-between; margin-bottom:20px;">
                                                            <h2>
                                                                Mark Attendance:
                                                                <%= selectedAllocation.getCourse().getCourseName() %>
                                                            </h2>
                                                            <a href="manageAttendance" class="btn btn-secondary">Change
                                                                Course</a>
                                                        </div>

                                                        <form
                                                            action="${pageContext.request.contextPath}/manageAttendance"
                                                            method="post" class="attendance-form">
                                                            <input type="hidden" name="allocationId"
                                                                value="<%= selectedAllocation.getAllocationId() %>">

                                                            <div style="display:flex; gap:20px; margin-bottom:20px;">
                                                                <div class="form-group">
                                                                    <label>Date:</label>
                                                                    <input type="date" name="date" required
                                                                        class="form-control"
                                                                        value="<%= new java.sql.Date(System.currentTimeMillis()) %>">
                                                                </div>

                                                                <div class="form-group">
                                                                    <label>Lecture Number:</label>
                                                                    <input type="number" name="lectureNumber" required
                                                                        class="form-control" min="1" value="1">
                                                                </div>
                                                            </div>

                                                            <table class="styled-table">
                                                                <thead>
                                                                    <tr>
                                                                        <th>Roll No</th>
                                                                        <th>Student Name</th>
                                                                        <th>Class</th>
                                                                        <th>Status</th>
                                                                    </tr>
                                                                </thead>

                                                                <tbody>
                                                                    <% if (enrollments !=null) { for (Enrollment e :
                                                                        enrollments) { String
                                                                        username=e.getStudent().getUsername(); String
                                                                        rollNo=username.contains(" - ")
                                        ? username.split(" - ")[0]
                                        : username;

                                String studentName = username.contains(" - ")
                                        ? username.split(" - ")[1]
                                        : " Student"; %>
                                                                        <tr>
                                                                            <td>
                                                                                <%= rollNo %>
                                                                            </td>
                                                                            <td>
                                                                                <%= studentName %>
                                                                            </td>
                                                                            <td>
                                                                                <% if (e.getStudent().getClassName() != null) { %>
                                                                                    <span class="tag"><%= com.cms.util.HtmlUtil.esc(e.getStudent().getClassName()) %></span>
                                                                                <% } else { %>
                                                                                    <span class="tag tag-missing">Not assigned</span>
                                                                                <% } %>
                                                                            </td>
                                                                            <td>
                                                                                <label class="status-radio">
                                                                                    <input type="radio"
                                                                                        name="status_<%= e.getEnrollmentId() %>"
                                                                                        value="Present" checked>
                                                                                    <span
                                                                                        style="color:green;font-weight:bold;">Present</span>
                                                                                </label>

                                                                                <label class="status-radio">
                                                                                    <input type="radio"
                                                                                        name="status_<%= e.getEnrollmentId() %>"
                                                                                        value="Absent">
                                                                                    <span
                                                                                        style="color:red;font-weight:bold;">Absent</span>
                                                                                </label>

                                                                                <label class="status-radio">
                                                                                    <input type="radio"
                                                                                        name="status_<%= e.getEnrollmentId() %>"
                                                                                        value="Leave">
                                                                                    <span
                                                                                        style="color:orange;font-weight:bold;">Leave</span>
                                                                                </label>
                                                                            </td>
                                                                        </tr>
                                                                        <% } } %>
                                                                </tbody>
                                                            </table>

                                                            <div style="margin-top:20px; text-align:right;">
                                                                <button type="submit" class="btn btn-primary">
                                                                    Submit Attendance
                                                                </button>
                                                            </div>
                                                        </form>
                                                    </div>
                                                    <% } %>

            </div>
        </main>

    </body>

    </html>