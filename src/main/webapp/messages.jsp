<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Map, java.util.Set, java.util.ArrayList, java.util.LinkedHashSet, java.util.Collections, java.util.Objects, com.cms.models.Message, com.cms.models.User, com.cms.util.HtmlUtil" %>
<%!
    private String roleLabel(String role) {
        if ("ADMIN".equals(role)) return "Admin";
        if ("TEACHER".equals(role)) return "Teacher";
        if ("STUDENT".equals(role)) return "Student";
        return role == null ? "" : role;
    }

    // Sent view: consecutive rows with the same text and timestamp are one broadcast
    private List<List<Message>> groupBroadcasts(List<Message> sent) {
        List<List<Message>> groups = new ArrayList<>();
        for (Message m : sent) {
            List<Message> last = groups.isEmpty() ? null : groups.get(groups.size() - 1);
            if (last != null && Objects.equals(last.get(0).getModelMessage(), m.getModelMessage())
                    && Objects.equals(last.get(0).getTimestamp(), m.getTimestamp())) {
                last.add(m);
            } else {
                List<Message> g = new ArrayList<>();
                g.add(m);
                groups.add(g);
            }
        }
        return groups;
    }

    private String tagFor(User u) {
        if (u == null || u.getAffiliation() == null) return "";
        return "<span class=\"tag" + ("TEACHER".equals(u.getRole()) ? " tag-department" : "") + "\">"
                + HtmlUtil.esc(u.getAffiliation()) + "</span>";
    }
%>
<%
    String view = (String) request.getAttribute("view");
    boolean sentView = "sent".equals(view);

    List<Message> inbox = (List<Message>) request.getAttribute("messages");
    List<Message> sent = (List<Message>) request.getAttribute("sentMessages");
    if (inbox == null) inbox = new ArrayList<>();
    if (sent == null) sent = new ArrayList<>();

    Map<String, List<User>> contactGroups = (Map<String, List<User>>) request.getAttribute("contactGroups");
    if (contactGroups == null) contactGroups = Collections.emptyMap();
    Map<Integer, Map<String, String>> contactCourses = (Map<Integer, Map<String, String>>) request.getAttribute("contactCourses");
    if (contactCourses == null) contactCourses = Collections.emptyMap();
    Map<String, String> filterCourses = (Map<String, String>) request.getAttribute("filterCourses");
    if (filterCourses == null) filterCourses = Collections.emptyMap();

    Set<Integer> selectedIds = (Set<Integer>) request.getAttribute("selectedReceiverIds");
    if (selectedIds == null) selectedIds = Collections.emptySet();
    String draftContent = (String) request.getAttribute("draftContent");

    int unread = 0;
    for (Message m : inbox) if (!m.isRead()) unread++;

    // Each card is a list of messages: one for inbox rows, one or more for a sent broadcast
    List<List<Message>> cardsData = new ArrayList<>();
    if (sentView) {
        cardsData = groupBroadcasts(sent);
    } else {
        for (Message m : inbox) cardsData.add(Collections.singletonList(m));
    }
    final int INLINE_NAMES = 3;
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Messages | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .msg-text { white-space: pre-wrap; word-break: break-word; margin: 8px 0 10px; }
        .msg-unread { border-left: 4px solid var(--secondary-color); }
        .msg-meta { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
        .role-tag { font-size: 0.75em; color: #6b7280; font-weight: normal; }
        .new-tag { font-size: 0.7em; background: var(--secondary-color); color: var(--text-color); padding: 1px 6px; border-radius: 8px; margin-left: 6px; }
        .bcast-tag { font-size: 0.7em; background: #e0e7ff; color: #3730a3; padding: 1px 7px; border-radius: 8px; margin-left: 6px; font-weight: 600; }
        .msg-actions a { color: var(--primary-color); font-size: 0.9em; text-decoration: none; margin-right: 14px; }
        .msg-recipients summary { cursor: pointer; color: var(--primary-color); font-size: 0.85em; margin-top: 4px; }
        .msg-recipients ul { list-style: none; margin: 8px 0 0; padding: 0; display: flex; flex-wrap: wrap; gap: 6px; }
        .msg-recipients li { font-size: 0.82em; background: #f3f4f6; border-radius: 6px; padding: 3px 8px; }
        .tabs { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
        .tabs a { padding: 6px 14px; border-radius: 6px; text-decoration: none; border: 1px solid #ddd; color: inherit; background: var(--white); }
        .tabs a.active { background: var(--primary-color); color: #fff; border-color: var(--primary-color); }
        .char-count { font-size: 0.8em; color: #6b7280; text-align: right; margin-top: 4px; }

        /* Recipient picker */
        .rp { border: 1px solid #ddd; border-radius: var(--border-radius); background: var(--white); }
        .rp.rp-invalid { border-color: var(--danger-color); }
        .rp-chosen { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; padding: 10px 12px; border-bottom: 1px solid #eee; min-height: 46px; }
        .rp-chosen-label { font-size: 0.85em; font-weight: 600; margin-right: 4px; }
        .rp-placeholder { font-size: 0.85em; color: #9ca3af; }
        .rp-pill { display: inline-flex; align-items: center; gap: 6px; background: #eff6ff; border: 1px solid #bfdbfe; color: #1e3a8a; border-radius: 999px; padding: 2px 4px 2px 10px; font-size: 0.82em; max-width: 100%; }
        .rp-pill span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .rp-pill button { border: none; background: none; cursor: pointer; color: #1e3a8a; font-size: 1rem; line-height: 1; padding: 0 5px; border-radius: 50%; }
        .rp-pill button:hover { background: #dbeafe; }
        .rp-panel { padding: 12px; }
        .rp-search { position: relative; }
        .rp-search input { padding-left: 34px; }
        .rp-search svg { position: absolute; left: 11px; top: 50%; transform: translateY(-50%); color: #9ca3af; pointer-events: none; }
        .rp-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
        .rp-chip { border: 1px solid #d1d5db; background: var(--white); color: var(--text-color); border-radius: 999px; padding: 4px 12px; font-size: 0.85em; cursor: pointer; }
        .rp-chip:hover { border-color: var(--primary-color); }
        .rp-chip[aria-pressed="true"] { background: var(--primary-color); border-color: var(--primary-color); color: #fff; }
        .rp-chip .rp-count { opacity: 0.75; margin-left: 4px; }
        /* Filters share the full row equally */
        .rp-selects { display: flex; gap: 8px; margin-top: 10px; width: 100%; }
        .rp-select { flex: 1 1 0; min-width: 0; width: auto; padding: 6px 8px; font-size: 0.85em; }
        .rp-select:disabled { background: #f3f4f6; color: #9ca3af; cursor: not-allowed; }
        .rp-status { font-size: 0.82em; color: #6b7280; margin: 10px 0 6px; display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; }
        .rp-selectall { display: inline-flex; align-items: center; gap: 6px; color: var(--text-color); font-weight: 600; cursor: pointer; }
        .rp-selectall input { width: 16px; height: 16px; cursor: pointer; }
        .rp-list { list-style: none; max-height: 280px; overflow-y: auto; border: 1px solid #eee; border-radius: 6px; }
        .rp-item { display: flex; align-items: center; gap: 10px; padding: 8px 10px; cursor: pointer; border-bottom: 1px solid #f3f4f6; }
        .rp-item:last-child { border-bottom: none; }
        .rp-item:hover, .rp-item.rp-active { background: #f8fafc; }
        .rp-item[aria-selected="true"] { background: #eff6ff; }
        .rp-check { width: 18px; height: 18px; border: 2px solid #cbd5e1; border-radius: 4px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 12px; }
        .rp-item[aria-selected="true"] .rp-check { background: var(--primary-color); border-color: var(--primary-color); }
        .rp-item .rp-info { flex: 1; min-width: 0; }
        .rp-avatar { width: 34px; height: 34px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 0.8em; font-weight: 700; flex-shrink: 0; }
        .rp-avatar.ADMIN { background: #fee2e2; color: #b91c1c; }
        .rp-avatar.TEACHER { background: #dbeafe; color: var(--primary-color); }
        .rp-avatar.STUDENT { background: #fef3c7; color: #92400e; }
        .rp-name { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .rp-sub { font-size: 0.8em; color: #6b7280; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .rp-name mark, .rp-sub mark { background: #fde68a; color: inherit; padding: 0; }
        .rp-badge { font-size: 0.72em; padding: 2px 8px; border-radius: 999px; background: #f3f4f6; color: #4b5563; flex-shrink: 0; }
        .rp-empty { padding: 18px; text-align: center; color: #6b7280; font-size: 0.9em; }
        .rp-link { background: none; border: none; color: var(--primary-color); cursor: pointer; font-size: 0.9em; padding: 0; }
        .rp-error { color: var(--danger-color); font-size: 0.85em; margin-top: 6px; display: none; }

        /* Message list toolbar */
        .list-toolbar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 14px; }
        .list-toolbar input[type="search"] { flex: 1; min-width: 200px; }
        .list-toolbar select { width: auto; }
        .list-toolbar label { font-size: 0.9em; display: flex; align-items: center; gap: 6px; white-space: nowrap; }
        .list-status { font-size: 0.8em; color: #6b7280; margin-bottom: 10px; }

        @media (max-width: 640px) {
            .rp-selects { flex-direction: column; }
            .rp-select { flex: none; width: 100%; }
            .rp-badge { display: none; }
        }
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

            <!-- Compose Section -->
            <div class="card" id="compose">
                <h3>Compose Message</h3>
                <% if (contactGroups.isEmpty()) { %>
                    <p style="margin-top: 10px;">There is no one you can message yet.</p>
                <% } else { %>
                <form action="messages" method="post" id="composeForm" style="margin-top: 15px;" novalidate>
                    <div class="form-group" style="margin-bottom: 12px;">
                        <label class="form-label" for="receiverId" id="toLabel">To:</label>

                        <!-- Native multi-select: used as-is without JavaScript (Ctrl/Cmd+click), and as the picker's data source -->
                        <select name="receiverId" id="receiverId" class="form-control" multiple size="8" required>
                            <% for (Map.Entry<String, List<User>> group : contactGroups.entrySet()) { %>
                                <optgroup label="<%= HtmlUtil.esc(group.getKey()) %>">
                                    <% for (User c : group.getValue()) {
                                           boolean selected = selectedIds.contains(c.getUserId());
                                           Map<String, String> courses = contactCourses.getOrDefault(c.getUserId(), Collections.emptyMap());
                                           String name = (c.getProfile() != null && c.getProfile().getFullName() != null
                                                   && !c.getProfile().getFullName().trim().isEmpty()) ? c.getProfile().getFullName() : c.getUsername();
                                    %>
                                        <option value="<%= c.getUserId() %>" <%= selected ? "selected" : "" %>
                                            data-name="<%= HtmlUtil.esc(name) %>"
                                            data-username="<%= HtmlUtil.esc(c.getUsername()) %>"
                                            data-role="<%= HtmlUtil.esc(c.getRole()) %>"
                                            data-courses="<%= HtmlUtil.esc(String.join(" ", courses.keySet())) %>"
                                            data-class="<%= "STUDENT".equals(c.getRole()) && c.getClassName() != null ? HtmlUtil.esc(c.getClassName()) : "" %>"
                                            data-dept="<%= "TEACHER".equals(c.getRole()) && c.getDepartmentName() != null ? HtmlUtil.esc(c.getDepartmentName()) : "" %>">
                                            <%= HtmlUtil.esc(c.getDisplayName()) %> - <%= roleLabel(c.getRole()) %>
                                        </option>
                                    <% } %>
                                </optgroup>
                            <% } %>
                        </select>
                        <!-- Filled by the picker on submit: all selected IDs in one field -->
                        <input type="hidden" name="receiverIds" id="receiverIds" disabled>

                        <!-- Enhanced picker (shown by the script below) -->
                        <div class="rp" id="rp" hidden>
                            <div class="rp-chosen" id="rpChosen" aria-live="polite"></div>

                            <div class="rp-panel">
                                <div class="rp-search">
                                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                                    <input type="search" id="rpSearch" class="form-control" autocomplete="off"
                                        placeholder="Search by name, roll no, class, department or course"
                                        role="combobox" aria-expanded="true" aria-controls="rpList" aria-autocomplete="list"
                                        aria-labelledby="toLabel">
                                </div>

                                <div class="rp-chips" id="rpChips" role="group" aria-label="Filter by role"></div>

                                <div class="rp-selects">
                                    <select id="rpClass" class="form-control rp-select" aria-label="Filter students by class" hidden>
                                        <option value="">All classes</option>
                                    </select>
                                    <select id="rpDept" class="form-control rp-select" aria-label="Filter teachers by department" hidden>
                                        <option value="">All departments</option>
                                    </select>
                                    <% if (!filterCourses.isEmpty()) { %>
                                        <select id="rpCourse" class="form-control rp-select" aria-label="Filter by course">
                                            <option value="">All courses</option>
                                            <% for (Map.Entry<String, String> course : filterCourses.entrySet()) { %>
                                                <option value="<%= HtmlUtil.esc(course.getKey()) %>">
                                                    <%= HtmlUtil.esc(course.getKey()) %> - <%= HtmlUtil.esc(course.getValue()) %>
                                                </option>
                                            <% } %>
                                        </select>
                                    <% } %>
                                </div>

                                <div class="rp-status">
                                    <label class="rp-selectall">
                                        <input type="checkbox" id="rpSelectAll">
                                        <span id="rpSelectAllText">Select all</span>
                                    </label>
                                    <span id="rpCountText" aria-live="polite"></span>
                                    <button type="button" class="rp-link" id="rpClear" hidden>Clear filters</button>
                                </div>
                                <ul class="rp-list" id="rpList" role="listbox" aria-multiselectable="true" aria-labelledby="toLabel"></ul>
                            </div>
                        </div>
                        <div class="rp-error" id="rpError">Please choose at least one recipient.</div>
                    </div>

                    <div class="form-group">
                        <label class="form-label" for="content">Message</label>
                        <textarea name="content" id="content" rows="4" class="form-control" required maxlength="2000"
                            placeholder="Type your message here..."><%= HtmlUtil.esc(draftContent) %></textarea>
                        <div class="char-count"><span id="charCount">0</span> / 2000</div>
                    </div>
                    <button type="submit" class="btn btn-primary" id="sendBtn" style="margin-top: 6px;">Send Message</button>
                </form>
                <% } %>
            </div>

            <div class="tabs">
                <a href="messages" class="<%= sentView ? "" : "active" %>">
                    Inbox (<%= inbox.size() %><%= unread > 0 ? ", " + unread + " new" : "" %>)
                </a>
                <a href="messages?view=sent" class="<%= sentView ? "active" : "" %>">Sent (<%= sentView ? cardsData.size() : groupBroadcasts(sent).size() %>)</a>
            </div>

            <% if (cardsData.isEmpty()) { %>
                <div class="card">
                    <p><%= sentView ? "You have not sent any messages." : "No messages received." %></p>
                </div>
            <% } else { %>
                <div class="list-toolbar">
                    <input type="search" id="listSearch" class="form-control" autocomplete="off"
                        placeholder="Search <%= sentView ? "sent messages" : "inbox" %> by person or text"
                        aria-label="Search messages">
                    <select id="listRole" class="form-control" aria-label="Filter messages by role">
                        <option value="">Everyone</option>
                        <option value="ADMIN">Admin</option>
                        <option value="TEACHER">Teachers</option>
                        <option value="STUDENT">Students</option>
                    </select>
                    <% if (!sentView) { %>
                        <label><input type="checkbox" id="listUnread"> Unread only</label>
                    <% } %>
                </div>
                <div class="list-status" id="listStatus" aria-live="polite"></div>
                <div class="card" id="listEmpty" style="display:none;"><p>No messages match your filters.</p></div>
            <% } %>

            <% for (List<Message> card : cardsData) {
                   Message first = card.get(0);
                   boolean highlight = !sentView && !first.isRead();
                   boolean broadcast = card.size() > 1;

                   // People on the card (the sender for inbox, the recipients for sent)
                   List<User> people = new ArrayList<>();
                   Set<String> roles = new LinkedHashSet<>();
                   StringBuilder searchText = new StringBuilder();
                   StringBuilder ids = new StringBuilder();
                   for (Message m : card) {
                       User u = sentView ? m.getReceiver() : m.getSender();
                       people.add(u);
                       if (u != null) {
                           roles.add(u.getRole());
                           searchText.append(u.getDisplayName()).append(' ')
                                     .append(u.getAffiliation() != null ? u.getAffiliation() : "").append(' ');
                       }
                       if (ids.length() > 0) ids.append(',');
                       ids.append(sentView ? m.getReceiverId() : m.getSenderId());
                   }
                   searchText.append(first.getModelMessage());
            %>
                <div class="card msg-card <%= highlight ? "msg-unread" : "" %>"
                    data-roles="<%= HtmlUtil.esc(String.join(" ", roles)) %>"
                    data-unread="<%= highlight %>"
                    data-text="<%= HtmlUtil.esc(searchText.toString().toLowerCase()) %>">
                    <div class="msg-meta">
                        <strong>
                            <%= sentView ? "To: " : "From: " %>
                            <% if (!broadcast) { User u = people.get(0); %>
                                <%= HtmlUtil.esc(u != null ? u.getDisplayName() : "Unknown user") %>
                                <span class="role-tag"><%= roleLabel(u != null ? u.getRole() : "") %></span>
                                <%= tagFor(u) %>
                            <% } else {
                                   for (int i = 0; i < Math.min(INLINE_NAMES, people.size()); i++) {
                                       User u = people.get(i); %>
                                    <%= i > 0 ? ", " : "" %><%= HtmlUtil.esc(u != null ? u.getDisplayName() : "Unknown user") %>
                                <% }
                                   if (people.size() > INLINE_NAMES) { %>
                                    and <%= people.size() - INLINE_NAMES %> more
                                <% } %>
                                <span class="bcast-tag"><%= people.size() %> recipients</span>
                            <% } %>
                            <% if (highlight) { %><span class="new-tag">NEW</span><% } %>
                        </strong>
                        <small><%= HtmlUtil.formatDate(first.getTimestamp()) %></small>
                    </div>

                    <% if (broadcast) { %>
                        <details class="msg-recipients">
                            <summary>Show all <%= people.size() %> recipients</summary>
                            <ul>
                                <% for (User u : people) { %>
                                    <li><%= HtmlUtil.esc(u != null ? u.getDisplayName() : "Unknown user") %>
                                        <span class="role-tag"><%= roleLabel(u != null ? u.getRole() : "") %></span>
                                        <%= tagFor(u) %></li>
                                <% } %>
                            </ul>
                        </details>
                    <% } %>

                    <p class="msg-text"><%= HtmlUtil.esc(first.getModelMessage()) %></p>

                    <div class="msg-actions">
                        <a href="messages?to=<%= ids %>#compose"><%= sentView ? (broadcast ? "&#9993; Message these recipients again" : "&#9993; Message again") : "&#8617; Reply" %></a>
                    </div>
                </div>
            <% } %>
        </div>
    </main>

    <script>
    (function () {
        var ROLE_LABELS = { ADMIN: "Admin", TEACHER: "Teacher", STUDENT: "Student" };
        var ROLE_CHIPS = [["", "All"], ["ADMIN", "Admin"], ["TEACHER", "Teachers"], ["STUDENT", "Students"]];
        var MAX_RENDER = 200;      // rows drawn at once (Select all still covers every match)
        var MAX_PILLS = 12;        // selected pills shown before "+N more"
        var CONFIRM_ABOVE = 20;    // ask before sending to more recipients than this

        function escapeHtml(s) {
            return String(s).replace(/[&<>"']/g, function (c) {
                return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
            });
        }
        // Escape text and wrap each occurrence of every search term in <mark>
        function highlight(text, terms) {
            var html = escapeHtml(text);
            terms.forEach(function (t) {
                if (!t) return;
                var re = new RegExp("(" + escapeHtml(t).replace(/[.*+?^$(){}|[\]\\]/g, "\\$&") + ")", "ig");
                html = html.replace(re, "<mark>$1</mark>");
            });
            return html;
        }
        function initials(name) {
            var parts = name.replace(/^(dr|mr|mrs|ms|prof)\.?\s+/i, "").trim().split(/\s+/);
            return ((parts[0] || "?")[0] + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase();
        }

        /* ---------- Recipient picker (multi-select) ---------- */
        var select = document.getElementById("receiverId");
        var rp = document.getElementById("rp");
        if (select && rp) {
            var search = document.getElementById("rpSearch");
            var list = document.getElementById("rpList");
            var chipsBox = document.getElementById("rpChips");
            var classSel = document.getElementById("rpClass");
            var deptSel = document.getElementById("rpDept");
            var course = document.getElementById("rpCourse");
            var countText = document.getElementById("rpCountText");
            var clearBtn = document.getElementById("rpClear");
            var chosenBox = document.getElementById("rpChosen");
            var selectAll = document.getElementById("rpSelectAll");
            var selectAllText = document.getElementById("rpSelectAllText");
            var errorBox = document.getElementById("rpError");
            var sendBtn = document.getElementById("sendBtn");

            var people = Array.prototype.map.call(select.querySelectorAll("option"), function (o) {
                return {
                    id: o.value,
                    name: o.dataset.name,
                    username: o.dataset.username,
                    role: o.dataset.role,
                    courses: o.dataset.courses ? o.dataset.courses.split(" ") : [],
                    cls: o.dataset.class || "",   // students: e.g. "BS Computer Science-2026"
                    dept: o.dataset.dept || "",   // teachers: e.g. "Computer Science"
                    option: o
                };
            });
            var byId = {};
            people.forEach(function (p) { byId[p.id] = p; });

            var state = { role: "", results: [], active: -1, selected: {}, showAllPills: false };
            people.forEach(function (p) { if (p.option.selected) state.selected[p.id] = true; });

            // Swap the native list for the picker
            select.hidden = true;
            select.removeAttribute("required");
            rp.hidden = false;

            /* Class (students) and Department (teachers) filters, built from the recipients present */
            function fillSelect(sel, key, noneLabel, role) {
                var values = {}, missing = false;
                people.forEach(function (p) {
                    if (p.role !== role) return;
                    if (p[key]) values[p[key]] = true; else missing = true;
                });
                var vals = Object.keys(values).sort();
                vals.forEach(function (v) {
                    var o = document.createElement("option");
                    o.value = v;
                    o.textContent = v;
                    sel.appendChild(o);
                });
                if (missing && vals.length) {
                    var o = document.createElement("option");
                    o.value = "__none__";
                    o.textContent = noneLabel;
                    sel.appendChild(o);
                }
                sel.hidden = vals.length === 0;
            }
            fillSelect(classSel, "cls", "No class assigned", "STUDENT");
            fillSelect(deptSel, "dept", "No department assigned", "TEACHER");

            // A filter that cannot apply to the chosen role is disabled and cleared:
            // Teachers -> no class filter; Students -> no department filter; Admin -> none apply
            function syncFilterAvailability() {
                var r = state.role;
                setDisabled(classSel, r === "TEACHER" || r === "ADMIN", r === "TEACHER" ? "Classes apply to students only" : "Not applicable to admin");
                setDisabled(deptSel, r === "STUDENT" || r === "ADMIN", r === "STUDENT" ? "Departments apply to teachers only" : "Not applicable to admin");
                if (course) setDisabled(course, r === "ADMIN", "Not applicable to admin");
            }
            function setDisabled(sel, off, why) {
                sel.disabled = off;
                if (off) sel.value = "";
                sel.title = off ? why : "";
            }

            function affiliationOk(p) {
                if (classSel.value) {
                    if (p.role !== "STUDENT") return false;
                    if (classSel.value === "__none__" ? p.cls : p.cls !== classSel.value) return false;
                }
                if (deptSel.value) {
                    if (p.role !== "TEACHER") return false;
                    if (deptSel.value === "__none__" ? p.dept : p.dept !== deptSel.value) return false;
                }
                return true;
            }

            ROLE_CHIPS.forEach(function (c) {
                if (c[0] && !people.some(function (p) { return p.role === c[0]; })) return;
                var b = document.createElement("button");
                b.type = "button";
                b.className = "rp-chip";
                b.dataset.role = c[0];
                b.setAttribute("aria-pressed", c[0] === "" ? "true" : "false");
                b.innerHTML = c[1] + '<span class="rp-count"></span>';
                b.addEventListener("click", function () {
                    state.role = c[0];
                    syncFilterAvailability();
                    render();
                    search.focus();
                });
                chipsBox.appendChild(b);
            });

            function subLine(p) {
                var s = p.username + " · " + (ROLE_LABELS[p.role] || p.role);
                if (p.cls) s += " · " + p.cls;
                if (p.dept) s += " · " + p.dept + " Dept.";
                if (p.courses.length) s += " · " + p.courses.join(", ");
                return s;
            }

            function matches(p, terms, ignoreRole) {
                if (!ignoreRole && state.role && p.role !== state.role) return false;
                if (course && course.value && p.courses.indexOf(course.value) === -1) return false;
                if (!affiliationOk(p)) return false;
                var hay = (p.name + " " + p.username + " " + (ROLE_LABELS[p.role] || "") + " " + p.cls + " " + p.dept + " " + p.courses.join(" ")).toLowerCase();
                return terms.every(function (t) { return hay.indexOf(t) !== -1; });
            }

            function selectedCount() {
                return Object.keys(state.selected).length;
            }

            function setSelected(p, on) {
                if (on) state.selected[p.id] = true; else delete state.selected[p.id];
                p.option.selected = on; // keep the native select in sync
            }

            function toggle(p) {
                setSelected(p, !state.selected[p.id]);
                rp.classList.remove("rp-invalid");
                errorBox.style.display = "none";
                refreshSelectionUI();
            }

            // Selected recipients as removable pills, in the order people appear
            function renderChosen() {
                var chosen = people.filter(function (p) { return state.selected[p.id]; });
                chosenBox.innerHTML = "";
                var label = document.createElement("span");
                label.className = "rp-chosen-label";
                label.textContent = chosen.length ? "Selected (" + chosen.length + "):" : "";
                chosenBox.appendChild(label);
                if (!chosen.length) {
                    var ph = document.createElement("span");
                    ph.className = "rp-placeholder";
                    ph.textContent = "No recipients selected yet — tick people below, or use Select all.";
                    chosenBox.appendChild(ph);
                    return;
                }
                var shownPills = state.showAllPills ? chosen : chosen.slice(0, MAX_PILLS);
                shownPills.forEach(function (p) {
                    var pill = document.createElement("span");
                    pill.className = "rp-pill";
                    pill.innerHTML = "<span>" + escapeHtml(p.name) + "</span>";
                    var x = document.createElement("button");
                    x.type = "button";
                    x.setAttribute("aria-label", "Remove " + p.name);
                    x.textContent = "×";
                    x.addEventListener("click", function () { toggle(p); });
                    pill.appendChild(x);
                    chosenBox.appendChild(pill);
                });
                if (chosen.length > MAX_PILLS) {
                    var more = document.createElement("button");
                    more.type = "button";
                    more.className = "rp-link";
                    more.textContent = state.showAllPills ? "Show less" : "+" + (chosen.length - MAX_PILLS) + " more";
                    more.addEventListener("click", function () { state.showAllPills = !state.showAllPills; renderChosen(); });
                    chosenBox.appendChild(more);
                }
                var clearSel = document.createElement("button");
                clearSel.type = "button";
                clearSel.className = "rp-link";
                clearSel.style.marginLeft = "auto";
                clearSel.textContent = "Clear selection";
                clearSel.addEventListener("click", function () {
                    people.forEach(function (p) { setSelected(p, false); });
                    state.showAllPills = false;
                    refreshSelectionUI();
                    search.focus();
                });
                chosenBox.appendChild(clearSel);
            }

            // Select-all checkbox reflects the current matches: checked / partly / none
            function syncSelectAll() {
                var n = state.results.length;
                var sel = state.results.filter(function (p) { return state.selected[p.id]; }).length;
                selectAll.disabled = n === 0;
                selectAll.checked = n > 0 && sel === n;
                selectAll.indeterminate = sel > 0 && sel < n;
                var filtered = search.value.trim() || state.role || classSel.value || deptSel.value || (course && course.value);
                selectAllText.textContent = (filtered ? "Select all matching" : "Select all") + " (" + n + ")";
            }

            function refreshSelectionUI() {
                Array.prototype.forEach.call(list.querySelectorAll(".rp-item"), function (li) {
                    var on = !!state.selected[li.dataset.id];
                    li.setAttribute("aria-selected", on ? "true" : "false");
                    li.querySelector(".rp-check").textContent = on ? "✓" : "";
                });
                renderChosen();
                syncSelectAll();
                var n = selectedCount();
                sendBtn.textContent = n > 1 ? "Send to " + n + " recipients" : "Send Message";
            }

            function render() {
                var terms = search.value.trim().toLowerCase().split(/\s+/).filter(Boolean);

                // Chip counts reflect the search and other filters, not the chosen role
                Array.prototype.forEach.call(chipsBox.children, function (b) {
                    var r = b.dataset.role;
                    var n = people.filter(function (p) { return matches(p, terms, true) && (!r || p.role === r); }).length;
                    b.querySelector(".rp-count").textContent = "(" + n + ")";
                    b.setAttribute("aria-pressed", r === state.role ? "true" : "false");
                });

                state.results = people.filter(function (p) { return matches(p, terms, false); });
                state.active = state.results.length ? 0 : -1;

                list.innerHTML = "";
                if (!state.results.length) {
                    list.innerHTML = '<li class="rp-empty">No recipients match your search.</li>';
                }
                state.results.slice(0, MAX_RENDER).forEach(function (p, i) {
                    var li = document.createElement("li");
                    li.className = "rp-item";
                    li.id = "rp-opt-" + p.id;
                    li.dataset.id = p.id;
                    li.setAttribute("role", "option");
                    li.innerHTML =
                        '<span class="rp-check" aria-hidden="true"></span>' +
                        '<div class="rp-avatar ' + escapeHtml(p.role) + '">' + escapeHtml(initials(p.name)) + '</div>' +
                        '<div class="rp-info"><div class="rp-name">' + highlight(p.name, terms) + '</div>' +
                        '<div class="rp-sub">' + highlight(subLine(p), terms) + '</div></div>' +
                        '<span class="rp-badge">' + escapeHtml(ROLE_LABELS[p.role] || p.role) + '</span>';
                    li.addEventListener("mousedown", function (e) { e.preventDefault(); });
                    li.addEventListener("click", function () { setActive(i, false); toggle(p); });
                    li.addEventListener("mousemove", function () { if (state.active !== i) setActive(i, false); });
                    list.appendChild(li);
                });
                setActive(state.active, false);

                countText.textContent = state.results.length > MAX_RENDER
                    ? "Showing first " + MAX_RENDER + " of " + state.results.length + " matches"
                    : state.results.length + " of " + people.length + " recipient" + (people.length === 1 ? "" : "s");
                clearBtn.hidden = !(terms.length || state.role || (course && course.value) || classSel.value || deptSel.value);
                refreshSelectionUI();
            }

            function setActive(i, scroll) {
                var items = list.querySelectorAll(".rp-item");
                Array.prototype.forEach.call(items, function (el, j) { el.classList.toggle("rp-active", j === i); });
                state.active = i;
                if (items[i]) {
                    if (scroll !== false) items[i].scrollIntoView({ block: "nearest" });
                    search.setAttribute("aria-activedescendant", items[i].id);
                } else {
                    search.removeAttribute("aria-activedescendant");
                }
            }

            selectAll.addEventListener("change", function () {
                // Ticked: add every match (not just the drawn rows); unticked: remove every match
                var on = selectAll.checked;
                state.results.forEach(function (p) { setSelected(p, on); });
                rp.classList.remove("rp-invalid");
                errorBox.style.display = "none";
                refreshSelectionUI();
            });

            search.addEventListener("input", render);
            [classSel, deptSel, course].forEach(function (s) {
                if (s) s.addEventListener("change", function () { render(); search.focus(); });
            });
            clearBtn.addEventListener("click", function () {
                search.value = "";
                state.role = "";
                if (course) course.value = "";
                classSel.value = "";
                deptSel.value = "";
                syncFilterAvailability();
                render();
                search.focus();
            });

            search.addEventListener("keydown", function (e) {
                var max = Math.min(state.results.length, MAX_RENDER) - 1;
                if (e.key === "ArrowDown") { e.preventDefault(); if (max >= 0) setActive(Math.min(state.active + 1, max)); }
                else if (e.key === "ArrowUp") { e.preventDefault(); if (max >= 0) setActive(Math.max(state.active - 1, 0)); }
                else if (e.key === "Enter") { e.preventDefault(); if (state.active >= 0) toggle(state.results[state.active]); }
                else if (e.key === "Escape" && search.value) { e.preventDefault(); search.value = ""; render(); }
            });

            syncFilterAvailability();
            render();

            document.getElementById("composeForm").addEventListener("submit", function (e) {
                var content = document.getElementById("content");
                var ids = people.filter(function (p) { return state.selected[p.id]; }).map(function (p) { return p.id; });
                if (!ids.length) {
                    e.preventDefault();
                    rp.classList.add("rp-invalid");
                    errorBox.style.display = "block";
                    search.focus();
                    return;
                }
                if (!content.value.trim()) {
                    e.preventDefault();
                    content.focus();
                    content.reportValidity && content.reportValidity();
                    return;
                }
                if (ids.length > CONFIRM_ABOVE && !confirm("Send this message to " + ids.length + " recipients?")) {
                    e.preventDefault();
                    return;
                }
                // One comma-separated field instead of many (keeps large selections under form limits)
                var hidden = document.getElementById("receiverIds");
                hidden.value = ids.join(",");
                hidden.disabled = false;
                select.disabled = true;
                sendBtn.disabled = true;
                sendBtn.textContent = "Sending...";
            });

            // Coming back via the browser's back button: re-enable what submit disabled
            window.addEventListener("pageshow", function () {
                select.disabled = false;
                document.getElementById("receiverIds").disabled = true;
                sendBtn.disabled = false;
                refreshSelectionUI();
            });
        }

        /* ---------- Character counter ---------- */
        var content = document.getElementById("content");
        var charCount = document.getElementById("charCount");
        if (content && charCount) {
            var updateCount = function () { charCount.textContent = content.value.length; };
            content.addEventListener("input", updateCount);
            updateCount();
        }

        /* ---------- Message list filters ---------- */
        var listSearch = document.getElementById("listSearch");
        if (listSearch) {
            var listRole = document.getElementById("listRole");
            var listUnread = document.getElementById("listUnread");
            var cards = Array.prototype.slice.call(document.querySelectorAll(".msg-card"));
            var status = document.getElementById("listStatus");
            var empty = document.getElementById("listEmpty");

            var filterList = function () {
                var terms = listSearch.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
                var visible = 0;
                cards.forEach(function (c) {
                    var ok = (!listRole.value || (" " + c.dataset.roles + " ").indexOf(" " + listRole.value + " ") !== -1)
                        && (!listUnread || !listUnread.checked || c.dataset.unread === "true")
                        && terms.every(function (t) { return c.dataset.text.indexOf(t) !== -1; });
                    c.style.display = ok ? "" : "none";
                    if (ok) visible++;
                });
                status.textContent = "Showing " + visible + " of " + cards.length + " message" + (cards.length === 1 ? "" : "s");
                empty.style.display = visible ? "none" : "";
            };
            listSearch.addEventListener("input", filterList);
            listRole.addEventListener("change", filterList);
            if (listUnread) listUnread.addEventListener("change", filterList);
            filterList();
        }
    })();
    </script>
</body>

</html>
