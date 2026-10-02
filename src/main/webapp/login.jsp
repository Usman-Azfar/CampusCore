<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <title>Login | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        /* Force Reset for Login Page */
        body {
            display: block !important;
            margin: 0 !important;
            padding: 0 !important;
            width: 100vw !important;
            height: 100vh !important;
            overflow: hidden !important;
        }

        .login-container {
            display: flex !important;
            justify-content: center !important;
            align-items: center !important;
            width: 100% !important;
            height: 100% !important;
            background: linear-gradient(135deg, var(--primary-color), #1e1b4b) !important;
        }

        .login-brand { width: 100%; max-width: 330px; height: auto; display: block; margin: 0 auto 26px; }

        .login-card {
            height: 100% !important;
            border-radius: 0 !important;
            display: flex !important;
            flex-direction: column !important;
            justify-content: center !important;
            box-shadow: 0 0 40px rgba(0, 0, 0, 0.3) !important;
        }
    </style>
</head>

<body style="display: block !important;">
    <div class="login-container">
        <div class="login-card">
            <!-- CampusCore brand -->
            <img src="${pageContext.request.contextPath}/images/campuscore-logo.png"
                alt="CampusCore - A Smart Campus Management System" class="login-brand">

            <% String error=(String) request.getAttribute("errorMessage"); if (error !=null) { %>
                <div class="alert alert-error" style="font-size: 0.9em; padding: 10px;">
                    <%= com.cms.util.HtmlUtil.esc(error) %>
                </div>
                <% } %>

                    <form action="login" method="post">
                        <div class="form-group" style="text-align: left;">
                            <label class="form-label" for="username">Roll Number / Username</label>
                            <input type="text" id="username" name="username" class="form-control" required
                                placeholder="e.g. BCSF22M512">
                        </div>

                        <div class="form-group" style="text-align: left;">
                            <label class="form-label" for="password">Password</label>
                            <input type="password" id="password" name="password" class="form-control" required
                                placeholder="Enter your password">
                        </div>

                        <button type="submit" class="btn btn-primary" style="width: 100%; margin-top: 10px;">Sign in to CampusCore</button>
                    </form>

                    <p style="margin-top: 20px; font-size: 0.85em; color: #666;">
                        Use your CampusCore username and password to sign in.
                    </p>

                    <% if (com.cms.util.DemoMode.isOn()) { %>
                    <div class="demo-logins" style="margin-top: 18px; text-align: left; background: #fffbeb; border: 1px solid #fde68a; border-radius: 8px; padding: 12px 14px; font-size: 0.85em; color: #78350f;">
                        <strong>Live demo</strong> &mdash; fictional data, reset every night. Pick an account to fill in the form:
                        <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-top: 10px;">
                            <% for (String[] a : com.cms.util.DemoMode.ACCOUNTS) { %>
                                <button type="button" class="btn btn-secondary btn-sm js-demo-login"
                                        data-user="<%= a[0] %>" data-pass="<%= a[1] %>"><%= a[2] %>: <%= a[0] %></button>
                            <% } %>
                        </div>
                    </div>
                    <script>
                        document.querySelectorAll(".js-demo-login").forEach(function (b) {
                            b.addEventListener("click", function () {
                                document.getElementById("username").value = b.dataset.user;
                                document.getElementById("password").value = b.dataset.pass;
                                document.getElementById("password").focus();
                            });
                        });
                    </script>
                    <% } %>
        </div>
    </div>
</body>

</html>