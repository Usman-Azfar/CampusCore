<%@ page contentType="text/html;charset=UTF-8"
    import="java.util.List, java.util.Collections, java.math.BigDecimal, com.cms.models.Challan, com.cms.util.HtmlUtil" %>
<%
    List<Challan> challans = (List<Challan>) request.getAttribute("challans");
    if (challans == null) challans = Collections.emptyList();
    int unpaid = 0, overdue = 0;
    BigDecimal outstanding = BigDecimal.ZERO;
    for (Challan c : challans) {
        if (!c.isPaid()) {
            unpaid++;
            if (c.isOverdue()) overdue++;
            if (c.getAmount() != null) outstanding = outstanding.add(c.getAmount());
        }
    }
    java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("dd MMM yyyy");
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>My Challans | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <link rel="stylesheet" href="css/styles.css">
    <style>
        .summary { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 8px; }
        .summary div { background: #f8fafc; border: 1px solid #e5e7eb; border-radius: var(--border-radius); padding: 10px 14px; }
        .summary strong { display: block; font-size: 1.15rem; }
        .summary span { font-size: 0.8rem; color: #6b7280; }
        .summary .bad strong { color: #b91c1c; }
        .status-overdue { background: #fee2e2; color: #b91c1c; }
        .cell-sub { display: block; font-size: 0.8em; color: #6b7280; margin-top: 2px; }
        .row-actions { display: flex; gap: 6px; flex-wrap: wrap; }
        #myTable td strong { white-space: nowrap; }
        .status-proof { background: #dbeafe; color: #1e40af; }
        .proof-box { margin-top: 8px; font-size: 0.9em; }
        .proof-box summary { cursor: pointer; color: var(--primary-color); font-weight: 600; }
        .proof-form { margin-top: 8px; padding: 10px; background: #f8fafc; border: 1px solid #e5e7eb; border-radius: 6px; max-width: 340px; }
        .proof-form .form-label { font-size: 0.85em; }
        #myTable td.num, #myTable th.num { text-align: right; white-space: nowrap; }
    </style>
</head>

<body>
    <jsp:include page="sidebar.jsp" />

    <main class="main-content">
        <jsp:include page="header.jsp" />

        <div class="page-container">
            <% String okMsg = (String) request.getAttribute("successMessage"); if (okMsg != null) { %><div class="alert alert-success"><%= HtmlUtil.esc(okMsg) %></div><% } %>
            <% String errMsg = (String) request.getAttribute("errorMessage"); if (errMsg != null) { %><div class="alert alert-error"><%= HtmlUtil.esc(errMsg) %></div><% } %>
            <div class="card">
                <h2>My Fee Challans</h2>
                <% if (challans.isEmpty()) { %>
                    <p style="margin-top:8px;">You have no challans yet.</p>
                <% } else { %>
                    <div class="summary">
                        <div><strong><%= unpaid %></strong><span>Unpaid</span></div>
                        <div class="<%= overdue > 0 ? "bad" : "" %>"><strong><%= overdue %></strong><span>Overdue</span></div>
                        <div><strong>PKR <%= new java.text.DecimalFormat("#,##0.00").format(outstanding) %></strong><span>Outstanding</span></div>
                    </div>
                    <% if (overdue > 0) { %>
                        <div class="alert alert-error" style="margin:14px 0 0;">You have <%= overdue %> overdue challan<%= overdue == 1 ? "" : "s" %>. Please pay as soon as possible.</div>
                    <% } %>
                <% } %>
            </div>

            <% if (!challans.isEmpty()) { %>
            <div class="card">
                <div class="table-scroll">
                <table class="styled-table" id="myTable">
                    <thead>
                        <tr>
                            <th>Challan</th>
                            <th>Title</th>
                            <th class="num">Amount</th>
                            <th>Due Date</th>
                            <th>Status</th>
                            <th>Action</th>
                        </tr>
                    </thead>
                    <tbody>
                    <% for (Challan c : challans) { %>
                        <tr>
                            <td>
                                <strong><%= c.getNumber() %></strong>
                                <span class="cell-sub">Issued <%= c.getUploadDate() != null ? df.format(c.getUploadDate()) : "-" %></span>
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
                                    <% if (c.getPaidAt() != null) { %><span class="cell-sub">on <%= df.format(c.getPaidAt()) %></span><% } %>
                                <% } else if (c.isOverdue()) { %>
                                    <span class="status-badge status-overdue">Overdue</span>
                                <% } else { %>
                                    <span class="status-badge status-unpaid">Unpaid</span>
                                <% } %>
                                <% if (c.isProofPending()) { %>
                                    <span class="status-badge status-proof" style="margin-top:4px;">Proof under review</span>
                                    <span class="cell-sub">Sent <%= c.getProofSubmittedAt() != null ? df.format(c.getProofSubmittedAt()) : "" %></span>
                                <% } else if ("REJECTED".equals(c.getProofStatus()) && !c.isPaid()) { %>
                                    <span class="status-badge status-overdue" style="margin-top:4px;">Proof rejected</span>
                                    <span class="cell-sub" style="color:#b91c1c;"><%= HtmlUtil.esc(c.getProofReviewNote()) %></span>
                                <% } else if ("ACCEPTED".equals(c.getProofStatus()) && c.isPaid()) { %>
                                    <span class="cell-sub">Proof accepted</span>
                                <% } %>
                            </td>
                            <td>
                                <div class="row-actions">
                                    <a href="challanView?id=<%= c.getChallanId() %>" target="_blank" rel="noopener" class="btn btn-primary btn-sm">View / Print</a>
                                    <% if (c.hasAttachment()) { %>
                                        <a href="challanFile?id=<%= c.getChallanId() %>" target="_blank" rel="noopener" class="btn btn-secondary btn-sm">Attachment</a>
                                    <% } %>
                                    <% if (c.hasProof()) { %>
                                        <a href="challanFile?id=<%= c.getChallanId() %>&amp;kind=proof" target="_blank" rel="noopener" class="btn btn-secondary btn-sm">My proof</a>
                                    <% } %>
                                </div>
                                <% if (c.canSubmitProof()) { %>
                                    <details class="proof-box" <%= "REJECTED".equals(c.getProofStatus()) ? "open" : "" %>>
                                        <summary><%= c.isProofPending() ? "Replace proof" : "REJECTED".equals(c.getProofStatus()) ? "Upload a new proof" : "Upload proof of payment" %></summary>
                                        <form action="challans" method="post" enctype="multipart/form-data" class="proof-form">
                                            <input type="hidden" name="action" value="uploadProof">
                                            <input type="hidden" name="challanId" value="<%= c.getChallanId() %>">
                                            <label class="form-label" for="proof<%= c.getChallanId() %>">Paid challan / bank receipt (PDF, PNG or JPG, max 10 MB)</label>
                                            <input type="file" name="proofFile" id="proof<%= c.getChallanId() %>" class="form-control" required
                                                accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" style="padding:6px;">
                                            <label class="form-label" for="ref<%= c.getChallanId() %>" style="margin-top:8px;">Bank transaction / reference no. (optional)</label>
                                            <input type="text" name="reference" id="ref<%= c.getChallanId() %>" class="form-control" maxlength="100"
                                                placeholder="e.g. TXN-458812" value="<%= c.getProofReference() != null ? HtmlUtil.esc(c.getProofReference()) : "" %>">
                                            <button type="submit" class="btn btn-success btn-sm" style="margin-top:8px;">Submit proof</button>
                                        </form>
                                    </details>
                                <% } %>
                            </td>
                        </tr>
                    <% } %>
                    </tbody>
                </table>
                </div>
                <p class="muted" style="margin-top:10px;">Print the challan and pay at the bank, then upload a photo or scan of the paid challan (or bank receipt). Your status changes to Paid once the accounts office checks it.</p>
            </div>
            <% } %>
        </div>
    </main>
</body>

</html>
