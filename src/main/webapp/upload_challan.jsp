<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Map, java.util.Set, java.util.TreeSet, java.util.HashSet, java.util.Collections, java.math.BigDecimal, com.cms.models.User, com.cms.models.Challan, com.cms.models.Semester, com.cms.models.AcademicClass, com.cms.util.HtmlUtil" %>
<%!
    private String name(User u) {
        String n = u.getProfile() != null ? u.getProfile().getFullName() : null;
        return (n == null || n.trim().isEmpty()) ? u.getUsername() : n;
    }

    private String money(BigDecimal v) {
        return new java.text.DecimalFormat("#,##0").format(v == null ? BigDecimal.ZERO : v);
    }
%>
<%
    List<User> students = (List<User>) request.getAttribute("students");
    List<Semester> semesters = (List<Semester>) request.getAttribute("semesters");
    List<AcademicClass> classes = (List<AcademicClass>) request.getAttribute("classes");
    List<Challan> challans = (List<Challan>) request.getAttribute("challans");
    if (students == null) students = Collections.emptyList();
    if (semesters == null) semesters = Collections.emptyList();
    if (classes == null) classes = Collections.emptyList();
    if (challans == null) challans = Collections.emptyList();
    Map<String, String> draft = "1".equals(request.getParameter("draft")) ? (Map<String, String>) request.getAttribute("draft") : null;

    Set<String> draftIds = new HashSet<>();
    if (draft != null && draft.get("studentIds") != null)
        for (String s : draft.get("studentIds").split(",")) if (!s.isEmpty()) draftIds.add(s);

    int unpaid = 0, overdue = 0;
    BigDecimal outstanding = BigDecimal.ZERO, collected = BigDecimal.ZERO;
    Set<String> titles = new TreeSet<>();
    for (Challan c : challans) {
        titles.add(c.getTitle());
        BigDecimal a = c.getAmount() == null ? BigDecimal.ZERO : c.getAmount();
        if (c.isPaid()) collected = collected.add(a);
        else { unpaid++; outstanding = outstanding.add(a); if (c.isOverdue()) overdue++; }
    }
    // Title suggestions: common fee types, then titles already used; no duplicates (ignoring case)
    java.util.LinkedHashMap<String, String> suggestMap = new java.util.LinkedHashMap<>();
    for (String t : new String[] { "Semester Fee", "Admission Fee", "Examination Fee", "Hostel Fee", "Transport Fee", "Library Fine", "Late Registration Fine" })
        suggestMap.putIfAbsent(t.toLowerCase(), t);
    for (String t : titles) suggestMap.putIfAbsent(t.trim().toLowerCase(), t.trim());
    java.util.Collection<String> titleSuggestions = suggestMap.values();

    int proofsToReview = 0;
    for (Challan c : challans) if (c.isProofPending()) proofsToReview++;

    String today = java.time.LocalDate.now().toString();
    java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("dd MMM yyyy");
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Accounts / Challan | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .stats { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; margin-bottom: 20px; }
        .stat.info .num { color: #1d4ed8; }
        .status-proof { background: #dbeafe; color: #1e40af; }
        .title-field { position: relative; }
        .suggest { position: absolute; z-index: 30; left: 0; right: 0; top: calc(100% + 2px); list-style: none; margin: 0; padding: 4px 0; background: #fff; border: 1px solid #d1d5db; border-radius: var(--border-radius); box-shadow: 0 10px 25px rgba(0,0,0,0.12); max-height: 240px; overflow-y: auto; }
        .suggest li { padding: 8px 12px; cursor: pointer; font-size: 0.92em; }
        .suggest li:hover, .suggest li.active { background: #eff6ff; }
        .suggest li mark { background: #fde68a; color: inherit; padding: 0; }
        .stat { background: #fff; border-radius: var(--border-radius); box-shadow: var(--shadow); padding: 14px 16px; }
        .stat .num { font-size: 1.35rem; font-weight: 700; }
        .stat .lbl { font-size: 0.8rem; color: #6b7280; }
        .stat.warn .num { color: #b45309; }
        .stat.bad .num { color: #b91c1c; }
        .stat.good .num { color: #047857; }
        .issue-form { display: grid; grid-template-columns: 2fr 1fr 1fr 1fr; gap: 14px; }
        .issue-form .form-group { margin-bottom: 0; }
        .issue-form .span-2 { grid-column: span 2; }
        .issue-form .span-all { grid-column: 1 / -1; }
        .issue-form small { display: block; margin-top: 4px; }
        .req { color: var(--danger-color); }
        /* student picker */
        .sp { border: 1px solid #ddd; border-radius: var(--border-radius); }
        .sp.sp-invalid { border-color: var(--danger-color); }
        .sp-chosen { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; padding: 10px 12px; border-bottom: 1px solid #eee; min-height: 46px; }
        .sp-placeholder { font-size: 0.85em; color: #9ca3af; }
        .sp-pill { display: inline-flex; align-items: center; gap: 6px; background: #eff6ff; border: 1px solid #bfdbfe; color: #1e3a8a; border-radius: 999px; padding: 2px 4px 2px 10px; font-size: 0.82em; }
        .sp-pill button { border: none; background: none; cursor: pointer; color: #1e3a8a; font-size: 1rem; line-height: 1; padding: 0 5px; }
        .sp-panel { padding: 12px; }
        .sp-row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; margin-top: 10px; }
        .sp-row > .form-control { flex: 1 1 0; min-width: 180px; }
        .sp-status { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 0.82em; color: #6b7280; margin: 10px 0 6px; }
        .sp-selectall { display: inline-flex; align-items: center; gap: 6px; font-weight: 600; color: var(--text-color); cursor: pointer; }
        .sp-selectall input { width: 16px; height: 16px; }
        .sp-list { list-style: none; max-height: 280px; overflow-y: auto; border: 1px solid #eee; border-radius: 6px; }
        .sp-item { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-bottom: 1px solid #f3f4f6; cursor: pointer; }
        .sp-item:hover { background: #f8fafc; }
        .sp-item[aria-selected="true"] { background: #eff6ff; }
        .sp-check { width: 18px; height: 18px; border: 2px solid #cbd5e1; border-radius: 4px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 12px; }
        .sp-item[aria-selected="true"] .sp-check { background: var(--primary-color); border-color: var(--primary-color); }
        .sp-info { flex: 1; min-width: 0; }
        .sp-name { font-weight: 600; }
        .sp-sub { font-size: 0.8em; color: #6b7280; }
        .sp-empty { padding: 18px; text-align: center; color: #6b7280; font-size: 0.9em; }
        .sp-error { color: var(--danger-color); font-size: 0.85em; margin-top: 6px; display: none; }
        .rp-link { background: none; border: none; color: var(--primary-color); cursor: pointer; font-size: 0.9em; padding: 0; }
        /* list */
        .bulk-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin: 6px 0 4px; font-size: 0.9em; }
        .bulk-bar form { margin: 0; }
        #cTable th, #cTable td { padding: 9px 10px; vertical-align: top; }
        #cTable td.num, #cTable th.num { text-align: right; white-space: nowrap; }
        .cell-sub { display: block; font-size: 0.8em; color: #6b7280; margin-top: 2px; }
        .row-actions { display: flex; gap: 4px; flex-wrap: wrap; }
        #cTable td strong { white-space: nowrap; }
        .row-actions form { margin: 0; }
        .status-overdue { background: #fee2e2; color: #b91c1c; }
        @media (max-width: 1100px) { .stats { grid-template-columns: repeat(3, 1fr); } }
        @media (max-width: 1000px) { .stats { grid-template-columns: repeat(2, 1fr); } .issue-form { grid-template-columns: 1fr 1fr; } }
        @media (max-width: 600px) { .issue-form { grid-template-columns: 1fr; } .issue-form .span-2 { grid-column: auto; } }
    </style>
</head>

<body>
<jsp:include page="sidebar.jsp" />

<main class="main-content">
    <jsp:include page="header.jsp" />

    <div class="page-container">
        <h2 class="card-title">Accounts / Fee Challans</h2>

        <% String okMsg = (String) request.getAttribute("successMessage");
           if (okMsg != null) { %>
            <div class="alert alert-success"><%= HtmlUtil.esc(okMsg) %></div>
        <% } %>
        <% String errMsg = (String) request.getAttribute("errorMessage");
           if (errMsg != null) { %>
            <div class="alert alert-error"><%= HtmlUtil.esc(errMsg) %></div>
        <% } %>

        <div class="stats">
            <div class="stat"><div class="num"><%= challans.size() %></div><div class="lbl">Challans issued</div></div>
            <div class="stat warn"><div class="num"><%= unpaid %></div><div class="lbl">Unpaid</div></div>
            <div class="stat bad"><div class="num"><%= overdue %></div><div class="lbl">Overdue</div></div>
            <div class="stat warn"><div class="num">PKR <%= money(outstanding) %></div><div class="lbl">Outstanding</div></div>
            <div class="stat good"><div class="num">PKR <%= money(collected) %></div><div class="lbl">Collected</div></div>
            <a class="stat info" href="#challanList" onclick="var s=document.getElementById('cStatus'); s.value='PROOF'; s.dispatchEvent(new Event('change'));" style="text-decoration:none; color:inherit;"><div class="num"><%= proofsToReview %></div><div class="lbl">Proofs to review</div></a>
        </div>

        <!-- ISSUE -->
        <div class="card" id="issueForm">
            <h3>Issue Challans</h3>
            <p class="muted" style="margin:4px 0 12px;">Choose one student, several, or everyone in a class. Each student gets their own printable challan with a unique number.</p>

            <form action="uploadChallan" method="post" enctype="multipart/form-data" class="issue-form" id="challanForm" novalidate>
                <input type="hidden" name="action" value="issue">
                <div class="form-group title-field">
                    <label class="form-label" for="title">Challan Title <span class="req">*</span></label>
                    <input type="text" name="title" id="title" class="form-control" required maxlength="100" autocomplete="off"
                        role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="titleSuggest"
                        placeholder="e.g. Semester Fee - Fall 2024" value="<%= draft != null && draft.get("title") != null ? HtmlUtil.esc(draft.get("title")) : "" %>">
                    <!-- Suggestions drawn under the field (the browser's datalist popup can open in the wrong place) -->
                    <ul class="suggest" id="titleSuggest" role="listbox" hidden>
                        <% for (String t : titleSuggestions) { %><li role="option" data-value="<%= HtmlUtil.esc(t) %>"><%= HtmlUtil.esc(t) %></li><% } %>
                    </ul>
                </div>
                <div class="form-group">
                    <label class="form-label" for="amount">Amount (PKR) <span class="req">*</span></label>
                    <input type="number" name="amount" id="amount" class="form-control" required min="1" max="10000000" step="0.01"
                        placeholder="e.g. 45000" value="<%= draft != null && draft.get("amount") != null ? HtmlUtil.esc(draft.get("amount")) : "" %>">
                </div>
                <div class="form-group">
                    <label class="form-label" for="semesterId">Semester <span class="req">*</span></label>
                    <select name="semesterId" id="semesterId" class="form-control" required>
                        <% for (Semester s : semesters) {
                               boolean pick = draft != null ? String.valueOf(s.getSemesterId()).equals(draft.get("semesterId")) : s.isActive(); %>
                            <option value="<%= s.getSemesterId() %>" <%= pick ? "selected" : "" %>><%= HtmlUtil.esc(s.getName()) %><%= s.isActive() ? " (Active)" : "" %></option>
                        <% } %>
                    </select>
                </div>
                <div class="form-group">
                    <label class="form-label" for="dueDate">Due Date <span class="req">*</span></label>
                    <input type="date" name="dueDate" id="dueDate" class="form-control" required min="<%= today %>"
                        value="<%= draft != null && draft.get("dueDate") != null ? HtmlUtil.esc(draft.get("dueDate")) : "" %>">
                </div>

                <div class="form-group span-2">
                    <label class="form-label" for="remarks">Remarks (printed on the challan)</label>
                    <input type="text" name="remarks" id="remarks" class="form-control" maxlength="255"
                        placeholder="Optional, e.g. Includes lab and library charges" value="<%= draft != null && draft.get("remarks") != null ? HtmlUtil.esc(draft.get("remarks")) : "" %>">
                </div>
                <div class="form-group span-2">
                    <label class="form-label" for="attachment">Attachment (optional)</label>
                    <input type="file" name="attachment" id="attachment" class="form-control" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" style="padding:6px;">
                    <small class="muted">PDF, PNG or JPG, up to 10 MB. Shared by every challan in this batch (e.g. a fee schedule).</small>
                </div>

                <div class="form-group span-all">
                    <label class="form-label" for="studentId" id="stuLabel">Students <span class="req">*</span></label>
                    <select name="studentId" id="studentId" class="form-control" multiple size="8">
                        <% for (User s : students) { %>
                            <option value="<%= s.getUserId() %>" <%= draftIds.contains(String.valueOf(s.getUserId())) ? "selected" : "" %>
                                data-name="<%= HtmlUtil.esc(name(s)) %>" data-roll="<%= HtmlUtil.esc(s.getUsername()) %>"
                                data-class-id="<%= s.getClassId() != null ? s.getClassId() : "" %>"
                                data-class="<%= HtmlUtil.esc(s.getClassName() != null ? s.getClassName() : "") %>">
                                <%= HtmlUtil.esc(name(s)) %> (<%= HtmlUtil.esc(s.getUsername()) %>)<%= s.getClassName() != null ? " - " + HtmlUtil.esc(s.getClassName()) : "" %>
                            </option>
                        <% } %>
                    </select>
                    <input type="hidden" name="studentIds" id="studentIds" disabled>

                    <div class="sp" id="sp" hidden>
                        <div class="sp-chosen" id="spChosen" aria-live="polite"></div>
                        <div class="sp-panel">
                            <div class="sp-row" style="margin-top:0;">
                                <input type="search" id="spSearch" class="form-control" autocomplete="off" placeholder="Search by name, roll number or class" aria-labelledby="stuLabel">
                                <select id="spClass" class="form-control" aria-label="Filter by class">
                                    <option value="">All classes</option>
                                    <% for (AcademicClass cl : classes) { %><option value="<%= cl.getClassId() %>"><%= HtmlUtil.esc(cl.getDisplayName()) %></option><% } %>
                                    <option value="none">No class assigned</option>
                                </select>
                            </div>
                            <div class="sp-status">
                                <label class="sp-selectall"><input type="checkbox" id="spSelectAll"> <span id="spSelectAllText">Select all</span></label>
                                <span id="spCount" aria-live="polite"></span>
                            </div>
                            <ul class="sp-list" id="spList" role="listbox" aria-multiselectable="true" aria-labelledby="stuLabel"></ul>
                        </div>
                    </div>
                    <div class="sp-error" id="spError">Please select at least one student.</div>
                </div>

                <div class="span-all">
                    <button type="submit" class="btn btn-primary" id="issueBtn">Issue Challans</button>
                </div>
            </form>
        </div>

        <!-- LIST -->
        <div class="card" id="challanList">
            <h3>Issued Challans</h3>
            <div class="filter-bar" style="margin-top:12px;">
                <input type="search" id="cSearch" class="form-control" placeholder="Search challan no., student, roll no. or title" aria-label="Search challans">
                <select id="cSemester" class="form-control" aria-label="Filter by semester">
                    <option value="">All semesters</option>
                    <% for (Semester s : semesters) { %><option value="<%= s.getSemesterId() %>"><%= HtmlUtil.esc(s.getName()) %></option><% } %>
                </select>
                <select id="cClass" class="form-control" aria-label="Filter by class">
                    <option value="">All classes</option>
                    <% for (AcademicClass cl : classes) { %><option value="<%= cl.getClassId() %>"><%= HtmlUtil.esc(cl.getDisplayName()) %></option><% } %>
                    <option value="none">No class</option>
                </select>
                <select id="cStatus" class="form-control" aria-label="Filter by status">
                    <option value="">All statuses</option>
                    <option value="UNPAID">Unpaid</option>
                    <option value="OVERDUE">Overdue</option>
                    <option value="PAID">Paid</option>
                    <option value="PROOF">Proof submitted (to review)</option>
                </select>
            </div>

            <div class="bulk-bar">
                <span id="cCount" class="muted"></span>
                <span style="margin-left:auto;" class="muted" id="bulkLabel">0 selected</span>
                <% for (String[] b : new String[][] { { "markPaid", "Mark paid", "btn-success" }, { "markUnpaid", "Mark unpaid", "btn-secondary" }, { "delete", "Delete", "btn-danger" } }) { %>
                    <form action="uploadChallan" method="post" class="bulk-form" data-action="<%= b[0] %>">
                        <input type="hidden" name="action" value="<%= b[0] %>">
                        <input type="hidden" name="challanIds" value="">
                        <button type="submit" class="btn btn-sm <%= b[2] %>" disabled><%= b[1] %></button>
                    </form>
                <% } %>
            </div>

            <div class="table-scroll">
            <table class="styled-table" id="cTable">
                <thead>
                    <tr>
                        <th><input type="checkbox" id="cAll" aria-label="Select all shown challans"></th>
                        <th>Challan</th>
                        <th>Student</th>
                        <th>Title</th>
                        <th class="num">Amount</th>
                        <th>Due</th>
                        <th>Status</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                <% if (challans.isEmpty()) { %>
                    <tr><td colspan="8" style="text-align:center;">No challans issued yet.</td></tr>
                <% } %>
                <% for (Challan c : challans) {
                       User s = c.getStudent();
                       String state = c.isPaid() ? "PAID" : c.isOverdue() ? "OVERDUE" : "UNPAID";
                %>
                    <tr class="c-row" data-id="<%= c.getChallanId() %>" data-semester="<%= c.getSemesterId() %>"
                        data-class="<%= s.getClassId() != null ? s.getClassId() : "none" %>" data-state="<%= state %>" data-proof="<%= c.isProofPending() ? "1" : "0" %>"
                        data-text="<%= HtmlUtil.esc((c.getNumber() + " " + name(s) + " " + s.getUsername() + " " + c.getTitle() + " " + (s.getClassName() != null ? s.getClassName() : "")).toLowerCase()) %>">
                        <td><input type="checkbox" class="c-check" value="<%= c.getChallanId() %>" aria-label="Select <%= c.getNumber() %>"></td>
                        <td>
                            <strong><%= c.getNumber() %></strong>
                            <span class="cell-sub">Issued <%= c.getUploadDate() != null ? df.format(c.getUploadDate()) : "-" %></span>
                        </td>
                        <td>
                            <%= HtmlUtil.esc(name(s)) %>
                            <span class="cell-sub"><%= HtmlUtil.esc(s.getUsername()) %><%= s.getClassName() != null ? " &middot; " + HtmlUtil.esc(s.getClassName()) : "" %></span>
                        </td>
                        <td>
                            <%= HtmlUtil.esc(c.getTitle()) %>
                            <span class="cell-sub"><%= HtmlUtil.esc(c.getSemester().getName()) %><%= c.getRemarks() != null ? " &middot; " + HtmlUtil.esc(c.getRemarks()) : "" %></span>
                        </td>
                        <td class="num"><%= HtmlUtil.esc(c.getAmountLabel()) %></td>
                        <td><%= c.getDueDate() != null ? df.format(c.getDueDate()) : "-" %></td>
                        <td>
                            <% if (c.isPaid()) { %>
                                <span class="status-badge status-paid">Paid</span>
                                <% if (c.getPaidAt() != null) { %><span class="cell-sub"><%= df.format(c.getPaidAt()) %></span><% } %>
                            <% } else if (c.isOverdue()) { %>
                                <span class="status-badge status-overdue">Overdue</span>
                            <% } else { %>
                                <span class="status-badge status-unpaid">Unpaid</span>
                            <% } %>
                            <% if (c.isProofPending()) { %>
                                <span class="status-badge status-proof" style="margin-top:4px;">Proof submitted</span>
                                <span class="cell-sub"><%= c.getProofSubmittedAt() != null ? df.format(c.getProofSubmittedAt()) : "" %><%= c.getProofReference() != null ? " &middot; Ref: " + HtmlUtil.esc(c.getProofReference()) : "" %></span>
                            <% } else if ("REJECTED".equals(c.getProofStatus()) && !c.isPaid()) { %>
                                <span class="cell-sub" style="color:#b91c1c;">Proof rejected: <%= HtmlUtil.esc(c.getProofReviewNote()) %></span>
                            <% } else if ("ACCEPTED".equals(c.getProofStatus()) && c.isPaid()) { %>
                                <span class="cell-sub">via proof<%= c.getProofReference() != null ? " (Ref: " + HtmlUtil.esc(c.getProofReference()) + ")" : "" %></span>
                            <% } %>
                        </td>
                        <td>
                            <div class="row-actions">
                                <a href="challanView?id=<%= c.getChallanId() %>" target="_blank" rel="noopener" class="btn btn-primary btn-sm">View</a>
                                <% if (c.hasProof()) { %><a href="challanFile?id=<%= c.getChallanId() %>&amp;kind=proof" target="_blank" rel="noopener" class="btn btn-secondary btn-sm">Proof</a><% } %>
                                <% if (c.isProofPending()) { %>
                                    <form action="uploadChallan" method="post" data-confirm="Accept the proof and mark <%= c.getNumber() %> as PAID?" onsubmit="return confirm(this.dataset.confirm);">
                                        <input type="hidden" name="action" value="acceptProof">
                                        <input type="hidden" name="challanId" value="<%= c.getChallanId() %>">
                                        <button type="submit" class="btn btn-success btn-sm" title="The proof is valid: mark the challan paid">Accept proof</button>
                                    </form>
                                    <form action="uploadChallan" method="post" class="reject-form" data-number="<%= c.getNumber() %>">
                                        <input type="hidden" name="action" value="rejectProof">
                                        <input type="hidden" name="challanId" value="<%= c.getChallanId() %>">
                                        <input type="hidden" name="note" value="">
                                        <button type="submit" class="btn btn-danger btn-sm" title="Send the proof back to the student with a reason">Reject</button>
                                    </form>
                                <% } %>
                                <% if (c.hasAttachment()) { %><a href="challanFile?id=<%= c.getChallanId() %>" target="_blank" rel="noopener" class="btn btn-secondary btn-sm">File</a><% } %>
                                <% if (!c.isProofPending()) { /* while a proof waits, Accept/Reject is the way to settle it */ %>
                                <form action="uploadChallan" method="post"
                                    data-confirm="<%= c.isPaid() ? "Mark " + c.getNumber() + " as UNPAID?" : "Mark " + c.getNumber() + " as PAID?" %>"
                                    onsubmit="return confirm(this.dataset.confirm);">
                                    <input type="hidden" name="action" value="<%= c.isPaid() ? "markUnpaid" : "markPaid" %>">
                                    <input type="hidden" name="challanId" value="<%= c.getChallanId() %>">
                                    <button type="submit" class="btn btn-sm <%= c.isPaid() ? "" : "btn-success" %>" style="<%= c.isPaid() ? "background:#fef3c7; color:#92400e;" : "" %>"
                                        title="<%= c.isPaid() ? "Undo: mark this challan unpaid again" : "Record a payment received without an uploaded proof (e.g. paid at the counter)" %>"><%= c.isPaid() ? "Mark unpaid" : "Mark paid" %></button>
                                </form>
                                <% } %>
                            </div>
                        </td>
                    </tr>
                <% } %>
                    <tr id="cNoMatch" hidden><td colspan="8" style="text-align:center;">No challans match your filters.</td></tr>
                </tbody>
            </table>
            </div>
        </div>
    </div>
</main>

<script>
(function () {
    function esc(s) {
        return String(s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; });
    }

    /* ---------- Title suggestions (drawn under the field) ---------- */
    (function () {
        var input = document.getElementById("title");
        var box = document.getElementById("titleSuggest");
        var items = Array.prototype.slice.call(box.querySelectorAll("li"));
        var active = -1;
        function visible() { return items.filter(function (li) { return !li.hidden; }); }
        function open() {
            var t = input.value.trim().toLowerCase();
            items.forEach(function (li) {
                var v = li.dataset.value;
                var hit = !t || v.toLowerCase().indexOf(t) !== -1;
                li.hidden = !hit || v.toLowerCase() === t; // hide an exact match: nothing to suggest
                li.classList.remove("active");
                if (hit && t) {
                    var i = v.toLowerCase().indexOf(t);
                    li.innerHTML = esc(v.slice(0, i)) + "<mark>" + esc(v.slice(i, i + t.length)) + "</mark>" + esc(v.slice(i + t.length));
                } else {
                    li.textContent = v;
                }
            });
            active = -1;
            box.hidden = visible().length === 0;
            input.setAttribute("aria-expanded", box.hidden ? "false" : "true");
        }
        function close() { box.hidden = true; input.setAttribute("aria-expanded", "false"); }
        function pick(li) { input.value = li.dataset.value; close(); document.getElementById("amount").focus(); }
        input.addEventListener("focus", open);
        input.addEventListener("input", open);
        input.addEventListener("keydown", function (e) {
            var v = visible();
            if (box.hidden || !v.length) return;
            if (e.key === "ArrowDown" || e.key === "ArrowUp") {
                e.preventDefault();
                active = Math.max(0, Math.min(v.length - 1, active + (e.key === "ArrowDown" ? 1 : -1)));
                v.forEach(function (li, i) { li.classList.toggle("active", i === active); });
                v[active].scrollIntoView({ block: "nearest" });
            } else if (e.key === "Enter" && active >= 0) {
                e.preventDefault();
                pick(v[active]);
            } else if (e.key === "Escape") {
                close();
            }
        });
        items.forEach(function (li) {
            li.addEventListener("mousedown", function (e) { e.preventDefault(); pick(li); });
        });
        input.addEventListener("blur", function () { setTimeout(close, 100); });
    })();

    /* ---------- Reject proof: ask for the reason ---------- */
    Array.prototype.forEach.call(document.querySelectorAll(".reject-form"), function (f) {
        f.addEventListener("submit", function (e) {
            var reason = prompt("Why is the proof for " + f.dataset.number + " rejected? The student will see this.", "");
            if (reason === null || !reason.trim()) { e.preventDefault(); return; }
            f.querySelector("input[name=note]").value = reason.trim();
        });
    });

    /* ---------- Student picker ---------- */
    var select = document.getElementById("studentId");
    var sp = document.getElementById("sp");
    var MAX_RENDER = 300, MAX_PILLS = 12;
    var search = document.getElementById("spSearch"), list = document.getElementById("spList"), cls = document.getElementById("spClass");
    var selectAll = document.getElementById("spSelectAll"), selectAllText = document.getElementById("spSelectAllText");
    var countEl = document.getElementById("spCount"), chosen = document.getElementById("spChosen"), errorEl = document.getElementById("spError");
    var btn = document.getElementById("issueBtn");
    var people = Array.prototype.map.call(select.options, function (o) {
        return { id: o.value, name: o.dataset.name, roll: o.dataset.roll, classId: o.dataset.classId, cls: o.dataset.class, option: o };
    });
    var selected = {}, results = [], showAll = false;
    people.forEach(function (p) { if (p.option.selected) selected[p.id] = true; });
    select.hidden = true;
    sp.hidden = false;

    function setSel(p, on) { if (on) selected[p.id] = true; else delete selected[p.id]; p.option.selected = on; }
    function toggle(p) { setSel(p, !selected[p.id]); sp.classList.remove("sp-invalid"); errorEl.style.display = "none"; refresh(); }

    function renderChosen() {
        var picked = people.filter(function (p) { return selected[p.id]; });
        chosen.innerHTML = "";
        if (!picked.length) { chosen.innerHTML = '<span class="sp-placeholder">No students selected yet — tick students below, or pick a class and use Select all.</span>'; return; }
        var lbl = document.createElement("strong");
        lbl.style.fontSize = "0.85em";
        lbl.textContent = "Selected (" + picked.length + "):";
        chosen.appendChild(lbl);
        (showAll ? picked : picked.slice(0, MAX_PILLS)).forEach(function (p) {
            var pill = document.createElement("span");
            pill.className = "sp-pill";
            pill.innerHTML = "<span>" + esc(p.name) + "</span>";
            var x = document.createElement("button");
            x.type = "button"; x.textContent = "×"; x.setAttribute("aria-label", "Remove " + p.name);
            x.addEventListener("click", function () { toggle(p); });
            pill.appendChild(x);
            chosen.appendChild(pill);
        });
        if (picked.length > MAX_PILLS) {
            var more = document.createElement("button");
            more.type = "button"; more.className = "rp-link";
            more.textContent = showAll ? "Show less" : "+" + (picked.length - MAX_PILLS) + " more";
            more.addEventListener("click", function () { showAll = !showAll; renderChosen(); });
            chosen.appendChild(more);
        }
        var clr = document.createElement("button");
        clr.type = "button"; clr.className = "rp-link"; clr.style.marginLeft = "auto"; clr.textContent = "Clear selection";
        clr.addEventListener("click", function () { people.forEach(function (p) { setSel(p, false); }); refresh(); });
        chosen.appendChild(clr);
    }

    function refresh() {
        Array.prototype.forEach.call(list.querySelectorAll(".sp-item"), function (li) {
            var on = !!selected[li.dataset.id];
            li.setAttribute("aria-selected", on ? "true" : "false");
            li.querySelector(".sp-check").textContent = on ? "✓" : "";
        });
        renderChosen();
        var sel = results.filter(function (p) { return selected[p.id]; }).length;
        selectAll.disabled = !results.length;
        selectAll.checked = results.length > 0 && sel === results.length;
        selectAll.indeterminate = sel > 0 && sel < results.length;
        selectAllText.textContent = (search.value.trim() || cls.value ? "Select all matching" : "Select all") + " (" + results.length + ")";
        var n = Object.keys(selected).length;
        btn.textContent = n > 1 ? "Issue " + n + " Challans" : n === 1 ? "Issue 1 Challan" : "Issue Challans";
    }

    function render() {
        var terms = search.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
        results = people.filter(function (p) {
            if (cls.value && (cls.value === "none" ? p.classId : p.classId !== cls.value)) return false;
            var hay = (p.name + " " + p.roll + " " + p.cls).toLowerCase();
            return terms.every(function (t) { return hay.indexOf(t) !== -1; });
        });
        list.innerHTML = results.length ? "" : '<li class="sp-empty">No students match.</li>';
        results.slice(0, MAX_RENDER).forEach(function (p) {
            var li = document.createElement("li");
            li.className = "sp-item"; li.dataset.id = p.id; li.setAttribute("role", "option");
            li.innerHTML = '<span class="sp-check" aria-hidden="true"></span><div class="sp-info"><div class="sp-name">' + esc(p.name) +
                '</div><div class="sp-sub">' + esc(p.roll + (p.cls ? " · " + p.cls : " · No class")) + '</div></div>';
            li.addEventListener("click", function () { toggle(p); });
            list.appendChild(li);
        });
        countEl.textContent = results.length > MAX_RENDER
            ? "Showing first " + MAX_RENDER + " of " + results.length + " (Select all covers every match)"
            : results.length + " of " + people.length + " students";
        refresh();
    }
    search.addEventListener("input", render);
    cls.addEventListener("change", render);
    selectAll.addEventListener("change", function () {
        results.forEach(function (p) { setSel(p, selectAll.checked); });
        sp.classList.remove("sp-invalid"); errorEl.style.display = "none";
        refresh();
    });
    render();

    document.getElementById("challanForm").addEventListener("submit", function (e) {
        var form = e.target;
        var ids = people.filter(function (p) { return selected[p.id]; }).map(function (p) { return p.id; });
        if (!ids.length) { e.preventDefault(); sp.classList.add("sp-invalid"); errorEl.style.display = "block"; search.focus(); return; }
        var bad = Array.prototype.filter.call(form.querySelectorAll("input[required], select[required]"), function (el) { return !el.checkValidity(); });
        if (bad.length) { e.preventDefault(); bad[0].reportValidity(); return; }
        var amount = document.getElementById("amount").value;
        if (!confirm("Issue \"" + document.getElementById("title").value + "\" (PKR " + amount + ") to " + ids.length + " student(s)?")) { e.preventDefault(); return; }
        var hidden = document.getElementById("studentIds");
        hidden.value = ids.join(",");
        hidden.disabled = false;
        select.disabled = true; // one comma-separated field instead of many
        btn.disabled = true;
        btn.textContent = "Issuing...";
    });
    window.addEventListener("pageshow", function () { select.disabled = false; document.getElementById("studentIds").disabled = true; btn.disabled = false; refresh(); });

    /* ---------- Challan list: filters + bulk selection ---------- */
    var cSearch = document.getElementById("cSearch"), cSem = document.getElementById("cSemester"), cClass = document.getElementById("cClass"), cStatus = document.getElementById("cStatus");
    var rows = Array.prototype.slice.call(document.querySelectorAll("#cTable .c-row"));
    var cAll = document.getElementById("cAll"), bulkLabel = document.getElementById("bulkLabel");
    var bulkForms = Array.prototype.slice.call(document.querySelectorAll(".bulk-form"));

    function checkedIds() {
        return rows.filter(function (r) { return !r.hidden && r.querySelector(".c-check").checked; }).map(function (r) { return r.dataset.id; });
    }
    function syncBulk() {
        var ids = checkedIds();
        bulkLabel.textContent = ids.length + " selected";
        bulkForms.forEach(function (f) { f.querySelector("button").disabled = !ids.length; f.querySelector("input[name=challanIds]").value = ids.join(","); });
        var shown = rows.filter(function (r) { return !r.hidden; });
        var on = shown.filter(function (r) { return r.querySelector(".c-check").checked; }).length;
        cAll.checked = shown.length > 0 && on === shown.length;
        cAll.indeterminate = on > 0 && on < shown.length;
    }
    function applyList() {
        var terms = cSearch.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
        var visible = 0;
        rows.forEach(function (r) {
            var st = r.dataset.state;
            var ok = (!cSem.value || r.dataset.semester === cSem.value)
                && (!cClass.value || r.dataset.class === cClass.value)
                && (!cStatus.value || (cStatus.value === "PROOF" ? r.dataset.proof === "1" : cStatus.value === "UNPAID" ? st !== "PAID" : st === cStatus.value))
                && terms.every(function (t) { return r.dataset.text.indexOf(t) !== -1; });
            r.hidden = !ok;
            if (!ok) r.querySelector(".c-check").checked = false; // never act on hidden rows
            if (ok) visible++;
        });
        document.getElementById("cCount").textContent = "Showing " + visible + " of " + rows.length;
        document.getElementById("cNoMatch").hidden = !(rows.length && visible === 0);
        syncBulk();
    }
    [cSearch, cSem, cClass, cStatus].forEach(function (el) { el.addEventListener(el === cSearch ? "input" : "change", applyList); });
    cAll.addEventListener("change", function () { rows.forEach(function (r) { if (!r.hidden) r.querySelector(".c-check").checked = cAll.checked; }); syncBulk(); });
    rows.forEach(function (r) { r.querySelector(".c-check").addEventListener("change", syncBulk); });
    bulkForms.forEach(function (f) {
        f.addEventListener("submit", function (e) {
            var n = checkedIds().length, act = f.dataset.action;
            var msg = act === "delete" ? "Delete " + n + " challan(s)? Paid challans are kept." : "Mark " + n + " challan(s) as " + (act === "markPaid" ? "PAID" : "UNPAID") + "?";
            if (!n || !confirm(msg)) e.preventDefault();
        });
    });
    applyList();
})();
</script>
</body>
</html>
