<%@ page contentType="text/html;charset=UTF-8" import="com.cms.models.User, com.cms.util.HtmlUtil" %>
<%
    User me = (User) session.getAttribute("user");
    String okMsg = (String) request.getAttribute("successMessage");
    String errMsg = (String) request.getAttribute("errorMessage");
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Change Password | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .pw-card { max-width: 520px; }
        .pw-card .form-group { margin-bottom: 14px; }
        .rules { list-style: none; margin: 4px 0 14px; padding: 0; font-size: 0.85em; }
        .rules li { color: #6b7280; margin: 3px 0; }
        .rules li::before { content: "\2022"; display: inline-block; width: 16px; }
        .rules li.ok { color: #047857; }
        .rules li.ok::before { content: "\2713"; }
        .rules li.bad { color: #b91c1c; }
        .rules li.bad::before { content: "\2717"; }
        .show-pw { font-size: 0.85em; display: inline-flex; gap: 6px; align-items: center; margin-bottom: 14px; cursor: pointer; }
    </style>
</head>

<body>
    <jsp:include page="sidebar.jsp" />

    <main class="main-content">
        <jsp:include page="header.jsp" />

        <div class="page-container">
            <h2 class="card-title">Change Password</h2>

            <% if (okMsg != null) { %><div class="alert alert-success"><%= HtmlUtil.esc(okMsg) %></div><% } %>
            <% if (errMsg != null) { %><div class="alert alert-error"><%= HtmlUtil.esc(errMsg) %></div><% } %>

            <div class="card pw-card">
                <% if (me != null) { %>
                    <p class="muted" style="margin-bottom:14px;">Account: <strong><%= HtmlUtil.esc(me.getUsername()) %></strong></p>
                <% } %>
                <form action="changePassword" method="post" id="pwForm">
                    <!-- Lets password managers match the password to this account -->
                    <input type="text" name="username" autocomplete="username" value="<%= me != null ? HtmlUtil.esc(me.getUsername()) : "" %>" hidden>

                    <div class="form-group">
                        <label class="form-label" for="oldPassword">Current Password</label>
                        <input type="password" name="oldPassword" id="oldPassword" class="form-control" required autocomplete="current-password">
                    </div>

                    <div class="form-group">
                        <label class="form-label" for="newPassword">New Password</label>
                        <input type="password" name="newPassword" id="newPassword" class="form-control" required minlength="6" autocomplete="new-password">
                    </div>

                    <div class="form-group">
                        <label class="form-label" for="confirmPassword">Confirm New Password</label>
                        <input type="password" name="confirmPassword" id="confirmPassword" class="form-control" required minlength="6" autocomplete="new-password">
                    </div>

                    <ul class="rules" id="rules" aria-live="polite">
                        <li data-rule="length">At least 6 characters</li>
                        <li data-rule="different">Different from your current password</li>
                        <li data-rule="match">Both new passwords match</li>
                    </ul>

                    <label class="show-pw"><input type="checkbox" id="showPw"> Show passwords</label>

                    <div>
                        <button type="submit" class="btn btn-primary" id="pwBtn">Update Password</button>
                    </div>
                </form>
            </div>
        </div>
    </main>

    <script>
    (function () {
        var oldPw = document.getElementById("oldPassword"), newPw = document.getElementById("newPassword"), confirmPw = document.getElementById("confirmPassword");
        var rules = {};
        Array.prototype.forEach.call(document.querySelectorAll("#rules li"), function (li) { rules[li.dataset.rule] = li; });

        function mark(li, ok, touched) {
            li.classList.toggle("ok", touched && ok);
            li.classList.toggle("bad", touched && !ok);
        }
        function check() {
            var n = newPw.value, c = confirmPw.value;
            mark(rules.length, n.length >= 6, n.length > 0);
            mark(rules.different, n !== oldPw.value, n.length > 0 && oldPw.value.length > 0);
            mark(rules.match, n === c, c.length > 0);
            confirmPw.setCustomValidity(c && n !== c ? "The new passwords do not match." : "");
            newPw.setCustomValidity(n && oldPw.value && n === oldPw.value ? "The new password must be different from the current one." : "");
        }
        [oldPw, newPw, confirmPw].forEach(function (el) { el.addEventListener("input", check); });

        document.getElementById("showPw").addEventListener("change", function (e) {
            [oldPw, newPw, confirmPw].forEach(function (el) { el.type = e.target.checked ? "text" : "password"; });
        });
        document.getElementById("pwForm").addEventListener("submit", function () {
            document.getElementById("pwBtn").disabled = true;
            document.getElementById("pwBtn").textContent = "Updating...";
        });
    })();
    </script>
</body>

</html>
