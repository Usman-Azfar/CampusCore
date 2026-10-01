<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
    <!DOCTYPE html>
    <html lang="en">

    <head>
        <meta charset="UTF-8">
        <title>FAQs | CampusCore</title>
        <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
        <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
        <link rel="stylesheet" href="css/styles.css">
    </head>

    <body>
        <jsp:include page="sidebar.jsp" />

        <main class="main-content">
            <jsp:include page="header.jsp" />

            <div class="page-container">
                <div class="card">
                    <div class="card-header">
                        <h3 class="card-title">Frequently Asked Questions</h3>
                    </div>
                    <div style="padding: 20px;">
                        <details style="margin-bottom: 15px; border-bottom: 1px solid #eee; padding-bottom: 10px;">
                            <summary style="font-weight: 600; cursor: pointer; color: var(--primary-color);">How do I
                                reset my password?</summary>
                            <p style="margin-top: 10px; color: #555;">Go to the "Account" section in the sidebar and
                                click on "Change Password". You will need your current password to set a new one.</p>
                        </details>

                        <details style="margin-bottom: 15px; border-bottom: 1px solid #eee; padding-bottom: 10px;">
                            <summary style="font-weight: 600; cursor: pointer; color: var(--primary-color);">How can I
                                view my attendance?</summary>
                            <p style="margin-top: 10px; color: #555;">Click on "Attendance" in the sidebar. You will see
                                a list of your enrolled courses. Click "View Attendance" next to any course to see your
                                detailed record.</p>
                        </details>

                        <details style="margin-bottom: 15px; border-bottom: 1px solid #eee; padding-bottom: 10px;">
                            <summary style="font-weight: 600; cursor: pointer; color: var(--primary-color);">When are
                                grades updated?</summary>
                            <p style="margin-top: 10px; color: #555;">Grades are updated by your instructors. If a grade
                                is "Unpublished", it means the teacher has not finalized it yet.</p>
                        </details>

                        <details style="margin-bottom: 15px; border-bottom: 1px solid #eee; padding-bottom: 10px;">
                            <summary style="font-weight: 600; cursor: pointer; color: var(--primary-color);">How do I
                                contact the Admin?</summary>
                            <p style="margin-top: 10px; color: #555;">Use the "Help Desk" feature in the sidebar to
                                submit a support ticket directly to the administration.</p>
                        </details>
                    </div>
                </div>
            </div>
        </main>
    </body>

    </html>