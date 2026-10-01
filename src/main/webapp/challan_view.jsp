<%@ page contentType="text/html;charset=UTF-8"
    import="com.cms.models.Challan, com.cms.models.User, com.cms.models.Profile, com.cms.util.HtmlUtil" %>
<%!
    private static final String[] ONES = { "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen" };
    private static final String[] TENS = { "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety" };

    private String below1000(int n) {
        StringBuilder sb = new StringBuilder();
        if (n >= 100) { sb.append(ONES[n / 100]).append(" Hundred"); n %= 100; if (n > 0) sb.append(' '); }
        if (n >= 20) { sb.append(TENS[n / 10]); if (n % 10 > 0) sb.append(' ').append(ONES[n % 10]); }
        else if (n > 0) sb.append(ONES[n]);
        return sb.toString();
    }

    // Whole rupees in words, e.g. 45000 -> "Forty Five Thousand"
    private String words(long n) {
        if (n == 0) return "Zero";
        String[] units = { "", " Thousand", " Million", " Billion" };
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (n > 0) {
            int chunk = (int) (n % 1000);
            if (chunk > 0) sb.insert(0, below1000(chunk) + units[i] + (sb.length() > 0 ? " " : ""));
            n /= 1000;
            i++;
        }
        return sb.toString();
    }
%>
<%
    Challan c = (Challan) request.getAttribute("challan");
    User st = (User) request.getAttribute("student");
    Profile p = st != null && st.getProfile() != null ? st.getProfile() : new Profile();
    boolean isAdmin = "ADMIN".equals(session.getAttribute("role"));
    java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("dd MMM yyyy");
    String amountWords = c.getAmount() == null ? "-"
            : words(c.getAmount().longValue()) + " Rupees"
              + (c.getAmount().remainder(java.math.BigDecimal.ONE).signum() != 0
                 ? " and " + c.getAmount().remainder(java.math.BigDecimal.ONE).movePointRight(2).intValue() + " Paisa" : "")
              + " Only";
    String[] copies = { "Bank Copy", "Accounts Copy", "Student Copy" };
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%= HtmlUtil.esc(c.getNumber()) %> - Fee Challan | CampusCore</title>
    <link rel="icon" type="image/png" href="images/campuscore-icon-64.png">
    <link rel="apple-touch-icon" href="images/campuscore-icon-192.png">
    <style>
        :root { --ink: #111827; --muted: #4b5563; --line: #9ca3af; --brand: #0c4a6e; }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: 'Segoe UI', Arial, sans-serif; color: var(--ink); background: #f3f4f6; padding: 20px 16px; }
        .toolbar { max-width: 1180px; margin: 0 auto 16px; display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
        .toolbar a, .toolbar button { padding: 8px 16px; border-radius: 6px; border: 1px solid var(--brand); background: #fff; color: var(--brand); font-weight: 600; cursor: pointer; text-decoration: none; font-size: 0.9rem; }
        .toolbar button.primary { background: var(--brand); color: #fff; }
        .toolbar .hint { color: var(--muted); font-size: 0.85rem; margin-left: auto; }
        .sheet { max-width: 1180px; margin: 0 auto; display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
        .copy { background: #fff; border: 1.5px solid var(--ink); padding: 14px; position: relative; font-size: 12.5px; overflow: hidden; }
        .copy + .copy { border-left-style: dashed; }
        .copy-name { position: absolute; top: 8px; right: 10px; font-size: 10.5px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); }
        .head { text-align: center; border-bottom: 1.5px solid var(--ink); padding-bottom: 8px; margin-bottom: 8px; }
        .head img { height: 42px; border-radius: 50%; }
        .head h1 { font-size: 13.5px; margin-top: 4px; }
        .head p { font-size: 11px; color: var(--muted); }
        .title { text-align: center; font-weight: 700; letter-spacing: 0.08em; font-size: 12px; margin: 6px 0 8px; }
        .grid { display: grid; grid-template-columns: 96px 1fr; gap: 3px 8px; margin-bottom: 8px; }
        .grid dt { color: var(--muted); }
        .grid dd { font-weight: 600; word-break: break-word; }
        table { width: 100%; border-collapse: collapse; margin: 8px 0; }
        th, td { border: 1px solid var(--line); padding: 5px 6px; text-align: left; }
        th { background: #f3f4f6; font-size: 11px; }
        td.amt, th.amt { text-align: right; white-space: nowrap; }
        tr.total td { font-weight: 700; }
        .words { font-size: 11px; margin-bottom: 10px; }
        .sign { display: flex; justify-content: space-between; margin-top: 26px; font-size: 11px; color: var(--muted); }
        .sign span { border-top: 1px solid var(--line); padding-top: 3px; min-width: 42%; text-align: center; }
        .note { font-size: 10.5px; color: var(--muted); margin-top: 8px; }
        .stamp { position: absolute; top: 44%; left: 50%; transform: translate(-50%, -50%) rotate(-18deg); font-size: 34px; font-weight: 800; letter-spacing: 0.1em; padding: 4px 14px; border: 4px solid; border-radius: 8px; opacity: 0.22; pointer-events: none; white-space: nowrap; }
        .stamp.paid { color: #047857; }
        .stamp.overdue { color: #b91c1c; }
        @media (max-width: 900px) { .sheet { grid-template-columns: 1fr; } .copy + .copy { border-left-style: solid; } }
        @media print {
            @page { size: A4 landscape; margin: 8mm; }
            body { background: #fff; padding: 0; }
            .toolbar { display: none; }
            .sheet { max-width: none; gap: 6mm; grid-template-columns: repeat(3, 1fr); }
            .copy { break-inside: avoid; }
        }
    </style>
</head>
<body>
    <div class="toolbar">
        <a href="<%= isAdmin ? "uploadChallan#challanList" : "challans" %>">&larr; Back</a>
        <button type="button" class="primary" onclick="window.print()">Print challan</button>
        <% if (c.hasAttachment()) { %><a href="challanFile?id=<%= c.getChallanId() %>" target="_blank" rel="noopener">Open attachment</a><% } %>
        <span class="hint">Print on A4 (landscape). Pay at the bank before the due date.</span>
    </div>

    <div class="sheet">
    <% for (String copy : copies) { %>
        <div class="copy">
            <span class="copy-name"><%= copy %></span>
            <% if (c.isPaid()) { %><div class="stamp paid">PAID</div><% } else if (c.isOverdue()) { %><div class="stamp overdue">OVERDUE</div><% } %>
            <div class="head">
                <img src="images/campuscore-icon-192.png" alt="CampusCore logo">
                <h1>CampusCore</h1>
                <p>A Smart Campus Management System</p>
            </div>
            <div class="title">FEE CHALLAN</div>
            <dl class="grid">
                <dt>Challan No.</dt><dd><%= HtmlUtil.esc(c.getNumber()) %></dd>
                <dt>Issued</dt><dd><%= c.getUploadDate() != null ? df.format(c.getUploadDate()) : "-" %></dd>
                <dt>Due Date</dt><dd><%= c.getDueDate() != null ? df.format(c.getDueDate()) : "-" %></dd>
                <dt>Student</dt><dd><%= HtmlUtil.esc(p.getFullName() != null ? p.getFullName() : "-") %></dd>
                <dt>Father Name</dt><dd><%= HtmlUtil.esc(p.getFatherName() != null ? p.getFatherName() : "-") %></dd>
                <dt>Roll No.</dt><dd><%= HtmlUtil.esc(st != null ? st.getUsername() : "-") %></dd>
                <dt>Class</dt><dd><%= HtmlUtil.esc(st != null && st.getClassName() != null ? st.getClassName() : "-") %></dd>
                <dt>Semester</dt><dd><%= HtmlUtil.esc(c.getSemester().getName()) %></dd>
            </dl>
            <table>
                <thead><tr><th>Particulars</th><th class="amt">Amount (PKR)</th></tr></thead>
                <tbody>
                    <tr><td><%= HtmlUtil.esc(c.getTitle()) %></td><td class="amt"><%= c.getAmount() != null ? new java.text.DecimalFormat("#,##0.00").format(c.getAmount()) : "-" %></td></tr>
                    <tr class="total"><td>Total Payable</td><td class="amt"><%= c.getAmount() != null ? new java.text.DecimalFormat("#,##0.00").format(c.getAmount()) : "-" %></td></tr>
                </tbody>
            </table>
            <p class="words"><strong>In words:</strong> <%= HtmlUtil.esc(amountWords) %></p>
            <% if (c.getRemarks() != null) { %><p class="words"><strong>Remarks:</strong> <%= HtmlUtil.esc(c.getRemarks()) %></p><% } %>
            <% if (c.isPaid() && c.getPaidAt() != null) { %><p class="words"><strong>Paid on:</strong> <%= df.format(c.getPaidAt()) %></p><% } %>
            <div class="sign"><span>Depositor's signature</span><span>Bank stamp &amp; signature</span></div>
            <p class="note">Payment after the due date may be subject to a fine. Keep the student copy as proof of payment.</p>
        </div>
    <% } %>
    </div>
</body>
</html>
