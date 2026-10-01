<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.ArrayList, com.cms.models.SupportTicket, com.cms.models.User, com.cms.util.HtmlUtil" %>
<%
    boolean isAdmin = "ADMIN".equals(session.getAttribute("role"));
    List<SupportTicket> tickets = (List<SupportTicket>) request.getAttribute("tickets");
    if (tickets == null) tickets = new ArrayList<>();

    List<SupportTicket> openTickets = new ArrayList<>();
    for (SupportTicket t : tickets) {
        if ("OPEN".equals(t.getStatus())) openTickets.add(t);
    }
    Integer selectedTicketId = (Integer) request.getAttribute("selectedTicketId");

    // Admin view groups tickets: Teachers first, then Students (then anyone else)
    String[][] groups = {
        {"TEACHER", "Teachers", "Department"},
        {"STUDENT", "Students", "Class"},
        {"", "Other Users", "Affiliation"}
    };
%>
<%!
    private String groupOf(SupportTicket t) {
        String r = t.getUser() != null ? t.getUser().getRole() : null;
        return ("TEACHER".equals(r) || "STUDENT".equals(r)) ? r : "";
    }

    private String affiliationTag(User u) {
        if (u == null) return "-";
        String a = u.getAffiliation();
        if (a == null) return "<span class=\"tag tag-missing\">Not assigned</span>";
        return "<span class=\"tag" + ("TEACHER".equals(u.getRole()) ? " tag-department" : "") + "\">" + HtmlUtil.esc(a) + "</span>";
    }
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <title>Help Desk | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .ticket-text { white-space: pre-wrap; word-break: break-word; }
        .status-badge { padding: 2px 8px; border-radius: 12px; font-size: 0.85em; white-space: nowrap; }
        .status-open { background: #fef3c7; color: #d97706; }
        .status-closed { background: #d1fae5; color: var(--success-color); }
        .role-tag { display: inline-block; font-size: 0.75em; color: #6b7280; }
        .ticket-group-title { font-size: 0.95rem; margin: 6px 0 10px; padding-bottom: 6px; border-bottom: 2px solid var(--primary-color); color: var(--primary-color); }
        .link-btn { background: none; border: none; color: var(--primary-color); cursor: pointer; padding: 0; font-size: 0.9em; text-decoration: none; }
    </style>
</head>

<body>
    <jsp:include page="sidebar.jsp" />

    <main class="main-content">
        <jsp:include page="header.jsp" />

        <div class="page-container">
            <h2 class="card-title">Support Ticket Center</h2>

            <% String msg = (String) request.getAttribute("successMessage");
               if (msg != null) { %>
                <div class="alert alert-success"><%= HtmlUtil.esc(msg) %></div>
            <% } %>

            <% String errorMsg = (String) request.getAttribute("errorMessage");
               if (errorMsg != null) { %>
                <div class="alert alert-error"><%= HtmlUtil.esc(errorMsg) %></div>
            <% } %>

            <div class="card" id="reply-form">
                <% if (isAdmin) { %>
                    <h3 style="margin-bottom:15px; font-size:1rem;">Reply to a Ticket</h3>

                    <% if (openTickets.isEmpty()) { %>
                        <p>There are no open tickets right now.</p>
                    <% } else { %>
                        <form action="helpdesk" method="post">
                            <div class="form-group">
                                <label class="form-label" for="ticketId">Open Ticket</label>
                                <select name="ticketId" id="ticketId" class="form-control" required>
                                    <option value="">-- Select an open ticket --</option>
                                    <% for (String[] g : groups) {
                                           boolean any = false;
                                           for (SupportTicket t : openTickets) if (groupOf(t).equals(g[0])) { any = true; break; }
                                           if (!any) continue; %>
                                        <optgroup label="<%= g[1] %>">
                                        <% for (SupportTicket t : openTickets) {
                                               if (!groupOf(t).equals(g[0])) continue;
                                               User u = t.getUser();
                                               String preview = t.getQueryText() == null ? "" : t.getQueryText();
                                               if (preview.length() > 60) preview = preview.substring(0, 60) + "...";
                                               String aff = u != null && u.getAffiliation() != null ? " [" + u.getAffiliation() + "]" : "";
                                               boolean selected = selectedTicketId != null && selectedTicketId == t.getTicketId();
                                        %>
                                            <option value="<%= t.getTicketId() %>" <%= selected ? "selected" : "" %>>
                                                #<%= t.getTicketId() %> - <%= HtmlUtil.esc(u != null ? u.getDisplayName() : "User " + t.getUserId()) %><%= HtmlUtil.esc(aff) %> - <%= HtmlUtil.esc(preview) %>
                                            </option>
                                        <% } %>
                                        </optgroup>
                                    <% } %>
                                </select>
                            </div>

                            <div class="form-group">
                                <label class="form-label" for="replyText">Reply Message</label>
                                <textarea name="replyText" id="replyText" class="form-control" rows="4" required
                                    maxlength="2000" placeholder="Enter your reply..."></textarea>
                                <small>The reply closes the ticket and is shown to the user in their Help Desk ticket list.</small>
                            </div>

                            <button type="submit" class="btn btn-primary">Send Reply</button>
                        </form>
                    <% } %>
                <% } else { %>
                    <h3 style="margin-bottom:15px; font-size:1rem;">Submit New Query</h3>
                    <form action="helpdesk" method="post">
                        <div class="form-group">
                            <label class="form-label" for="queryText">Query Description</label>
                            <textarea name="queryText" id="queryText" class="form-control" rows="4" required
                                maxlength="2000" placeholder="Describe your issue..."></textarea>
                            <small>The admin's reply will appear below in My Tickets.</small>
                        </div>
                        <button type="submit" class="btn btn-primary">Submit To Admin</button>
                    </form>
                <% } %>
            </div>

            <% if (isAdmin) { %>
            <div class="card">
                <h3 style="margin-bottom:15px; font-size:1rem;">All Tickets (<%= openTickets.size() %> open)</h3>

                <% if (tickets.isEmpty()) { %>
                    <p style="text-align:center;">No tickets found.</p>
                <% } %>

                <% for (String[] g : groups) {
                       List<SupportTicket> section = new ArrayList<>();
                       int open = 0;
                       for (SupportTicket t : tickets) {
                           if (groupOf(t).equals(g[0])) {
                               section.add(t);
                               if ("OPEN".equals(t.getStatus())) open++;
                           }
                       }
                       // Teachers and Students headings always show; "Other Users" only when present
                       if (section.isEmpty() && g[0].isEmpty()) continue;
                       if (tickets.isEmpty()) continue;
                %>
                <h4 class="ticket-group-title"><%= g[1] %>
                    <span class="muted">(<%= section.size() %> ticket<%= section.size() == 1 ? "" : "s" %>, <%= open %> open)</span>
                </h4>
                <div class="table-scroll">
                <table class="styled-table" style="margin-bottom:22px;">
                    <thead>
                        <tr>
                            <th>Ticket ID</th>
                            <th>Submitted By</th>
                            <th><%= g[2] %></th>
                            <th>Query</th>
                            <th>Status</th>
                            <th>Admin Reply</th>
                            <th>Date</th>
                            <th>Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% if (section.isEmpty()) { %>
                        <tr><td colspan="8" style="text-align:center;">No tickets from <%= g[1].toLowerCase() %>.</td></tr>
                        <% } %>
                        <% for (SupportTicket t : section) {
                               boolean closed = "CLOSED".equals(t.getStatus());
                               User u = t.getUser();
                        %>
                        <tr>
                            <td>#<%= t.getTicketId() %></td>
                            <td><%= HtmlUtil.esc(u != null ? u.getDisplayName() : "User " + t.getUserId()) %></td>
                            <td><%= affiliationTag(u) %></td>
                            <td class="ticket-text"><%= HtmlUtil.esc(t.getQueryText()) %></td>
                            <td>
                                <span class="status-badge <%= closed ? "status-closed" : "status-open" %>">
                                    <%= HtmlUtil.esc(t.getStatus()) %>
                                </span>
                            </td>
                            <td class="ticket-text"><%= t.getAdminReply() != null ? HtmlUtil.esc(t.getAdminReply()) : "-" %></td>
                            <td><%= HtmlUtil.formatDate(t.getCreatedAt()) %></td>
                            <td>
                                <% if (!closed) { %>
                                    <a class="link-btn" href="helpdesk?reply=<%= t.getTicketId() %>#reply-form">&#8617; Reply</a>
                                <% } else { %>
                                    -
                                <% } %>
                            </td>
                        </tr>
                        <% } %>
                    </tbody>
                </table>
                </div>
                <% } %>
            </div>
            <% } else { %>
            <div class="card">
                <h3 style="margin-bottom:15px; font-size:1rem;">My Tickets</h3>

                <table class="styled-table">
                    <thead>
                        <tr>
                            <th>Ticket ID</th>
                            <% if (isAdmin) { %>
                                <th>Submitted By</th>
                            <% } %>
                            <th>Query</th>
                            <th>Status</th>
                            <th>Admin Reply</th>
                            <th>Date</th>
                            <% if (isAdmin) { %>
                                <th>Action</th>
                            <% } %>
                        </tr>
                    </thead>
                    <tbody>
                        <% if (!tickets.isEmpty()) {
                               for (SupportTicket t : tickets) {
                                   boolean closed = "CLOSED".equals(t.getStatus());
                        %>
                        <tr>
                            <td>#<%= t.getTicketId() %></td>
                            <% if (isAdmin) { User u = t.getUser(); %>
                                <td>
                                    <%= HtmlUtil.esc(u != null ? u.getDisplayName() : "User " + t.getUserId()) %><br>
                                    <span class="role-tag"><%= HtmlUtil.esc(u != null ? u.getRole() : "") %></span>
                                </td>
                            <% } %>
                            <td class="ticket-text"><%= HtmlUtil.esc(t.getQueryText()) %></td>
                            <td>
                                <span class="status-badge <%= closed ? "status-closed" : "status-open" %>">
                                    <%= HtmlUtil.esc(t.getStatus()) %>
                                </span>
                            </td>
                            <td class="ticket-text"><%= t.getAdminReply() != null ? HtmlUtil.esc(t.getAdminReply()) : "-" %></td>
                            <td><%= HtmlUtil.formatDate(t.getCreatedAt()) %></td>
                            <% if (isAdmin) { %>
                                <td>
                                    <% if (!closed) { %>
                                        <a class="link-btn" href="helpdesk?reply=<%= t.getTicketId() %>#reply-form">&#8617; Reply</a>
                                    <% } else { %>
                                        -
                                    <% } %>
                                </td>
                            <% } %>
                        </tr>
                        <%     }
                           } else { %>
                        <tr>
                            <td colspan="<%= isAdmin ? 7 : 5 %>" style="text-align:center;">
                                No tickets found.
                            </td>
                        </tr>
                        <% } %>
                    </tbody>
                </table>
            </div>
            <% } %>
        </div>
    </main>
</body>

</html>
