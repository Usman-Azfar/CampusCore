<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.Map, com.cms.models.Profile, com.cms.models.User, com.cms.util.HtmlUtil" %>
<%!
    // Form value: the draft (after a failed save) wins, then the saved value
    private String val(Map<String, String> draft, String field, Object saved) {
        if (draft != null && draft.containsKey(field)) return HtmlUtil.esc(draft.get(field));
        return saved == null ? "" : HtmlUtil.esc(saved);
    }
%>
<%
    Profile p = (Profile) request.getAttribute("profile");
    if (p == null) p = new Profile();
    User acct = (User) request.getAttribute("account");
    boolean nameLocked = Boolean.TRUE.equals(request.getAttribute("nameLocked"));
    Map<String, String> draft = "1".equals(request.getParameter("draft")) ? (Map<String, String>) request.getAttribute("draft") : null;
    String gender = draft != null && draft.containsKey("gender") ? draft.get("gender") : p.getGender();
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Update Profile | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .profile-form { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
        .profile-form .form-group { margin-bottom: 0; }
        .profile-form .span-all { grid-column: 1 / -1; }
        .profile-form input[readonly] { background: #f3f4f6; color: #4b5563; cursor: not-allowed; }
        .profile-form small { display: block; margin-top: 4px; }
        .req { color: var(--danger-color); }
        @media (max-width: 640px) { .profile-form { grid-template-columns: 1fr; } }
    </style>
</head>

<body>
    <jsp:include page="sidebar.jsp" />

    <main class="main-content">
        <jsp:include page="header.jsp" />

        <div class="page-container">
            <h2 class="card-title">My Profile</h2>

            <% String okMsg = (String) request.getAttribute("successMessage");
               if (okMsg != null) { %>
                <div class="alert alert-success"><%= HtmlUtil.esc(okMsg) %></div>
            <% } %>
            <% String errMsg = (String) request.getAttribute("errorMessage");
               if (errMsg != null) { %>
                <div class="alert alert-error"><%= HtmlUtil.esc(errMsg) %></div>
            <% } %>

            <% if (acct != null && !"ADMIN".equals(acct.getRole())) { %>
            <div class="card">
                <strong><%= HtmlUtil.esc(acct.getUsername()) %></strong>
                <span class="muted">&middot; <%= "STUDENT".equals(acct.getRole()) ? "Student" : "Teacher" %></span>
                &nbsp;
                <% if (acct.getAffiliation() != null) { %>
                    <span class="tag <%= "TEACHER".equals(acct.getRole()) ? "tag-department" : "" %>">
                        <%= "TEACHER".equals(acct.getRole()) ? "Department: " : "Class: " %><%= HtmlUtil.esc(acct.getAffiliation()) %>
                    </span>
                <% } else { %>
                    <span class="tag tag-missing"><%= "TEACHER".equals(acct.getRole()) ? "No department assigned" : "No class assigned" %></span>
                <% } %>
                <p class="muted" style="margin-top:6px;">Your <%= "TEACHER".equals(acct.getRole()) ? "department" : "class" %>, name and roll number / username are set by the admin.</p>
            </div>
            <% } %>

            <div class="card">
                <form action="updateProfile" method="post" class="profile-form">
                    <div class="form-group">
                        <label class="form-label" for="fullName">Full Name</label>
                        <input type="text" id="fullName" class="form-control" maxlength="100"
                            <%= nameLocked ? "readonly" : "name=\"fullName\" required" %>
                            value="<%= nameLocked ? HtmlUtil.esc(p.getFullName()) : val(draft, "fullName", p.getFullName()) %>">
                        <% if (nameLocked) { %><small class="muted">To correct your name, contact the admin through the <a href="helpdesk">Help Desk</a>.</small><% } %>
                    </div>

                    <div class="form-group">
                        <label class="form-label" for="fatherName">Father Name</label>
                        <input type="text" id="fatherName" class="form-control" maxlength="100"
                            <%= nameLocked ? "readonly" : "name=\"fatherName\"" %>
                            value="<%= nameLocked ? HtmlUtil.esc(p.getFatherName()) : val(draft, "fatherName", p.getFatherName()) %>">
                    </div>

                    <div class="form-group">
                        <label class="form-label" for="email">Email <span class="req">*</span></label>
                        <input type="email" name="email" id="email" class="form-control" required maxlength="100"
                            value="<%= val(draft, "email", p.getEmail()) %>">
                    </div>

                    <div class="form-group">
                        <label class="form-label" for="gender">Gender</label>
                        <select name="gender" id="gender" class="form-control">
                            <option value="" <%= gender == null || gender.isEmpty() ? "selected" : "" %>>-- Not specified --</option>
                            <% for (String g : new String[] { "Male", "Female", "Other" }) { %>
                                <option value="<%= g %>" <%= g.equals(gender) ? "selected" : "" %>><%= g %></option>
                            <% } %>
                        </select>
                    </div>

                    <div class="form-group">
                        <label class="form-label" for="phone">Phone</label>
                        <input type="tel" name="phone" id="phone" class="form-control" maxlength="20" placeholder="e.g. +92 300 1234567"
                            value="<%= val(draft, "phone", p.getPhone()) %>">
                    </div>

                    <div class="form-group">
                        <label class="form-label" for="city">City</label>
                        <input type="text" name="city" id="city" class="form-control" maxlength="50"
                            value="<%= val(draft, "city", p.getCity()) %>">
                    </div>

                    <div class="form-group">
                        <label class="form-label" for="country">Country</label>
                        <input type="text" name="country" id="country" class="form-control" maxlength="50"
                            value="<%= val(draft, "country", p.getCountry()) %>">
                    </div>

                    <div class="form-group">
                        <label class="form-label" for="address">Address</label>
                        <input type="text" name="address" id="address" class="form-control" maxlength="500"
                            value="<%= val(draft, "address", p.getAddress()) %>">
                    </div>

                    <div class="span-all">
                        <button type="submit" class="btn btn-primary">Save Changes</button>
                    </div>
                </form>
            </div>
        </div>
    </main>
</body>

</html>
