package com.cms.controllers;

import com.cms.dao.AcademicDAO;
import com.cms.dao.ChallanDAO;
import com.cms.dao.SemesterDAO;
import com.cms.dao.UserDAO;
import com.cms.models.Challan;
import com.cms.models.Semester;
import com.cms.models.User;
import com.cms.util.UploadStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Admin Accounts / Challan: issue fee challans to one, many or all matching students
 * in one go, and track them (mark paid/unpaid, delete unpaid).
 */
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 2, maxFileSize = 1024 * 1024 * 10, maxRequestSize = 1024 * 1024 * 50)
public class UploadChallanServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = "challans.flash.success";
    private static final String FLASH_ERROR = "challans.flash.error";
    private static final String DRAFT = "challans.flash.draft";
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");

    private ChallanDAO challanDAO;
    private UserDAO userDAO;
    private SemesterDAO semesterDAO;
    private AcademicDAO academicDAO;

    public void init() {
        challanDAO = new ChallanDAO();
        userDAO = new UserDAO();
        semesterDAO = new SemesterDAO();
        academicDAO = new AcademicDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AdminSupport.requireAdmin(request, response) == null)
            return;
        SupportTicketServlet.moveFlash(request.getSession(), request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(request.getSession(), request, FLASH_ERROR, "errorMessage");
        SupportTicketServlet.moveFlash(request.getSession(), request, DRAFT, "draft");

        List<User> students = new ArrayList<>();
        for (User s : userDAO.getUsersByRole("STUDENT"))
            if (s.isActive())
                students.add(s);

        request.setAttribute("students", students);
        request.setAttribute("semesters", semesterDAO.getAllSemesters());
        request.setAttribute("classes", academicDAO.getAllClasses());
        request.setAttribute("challans", challanDAO.getAllChallans());
        request.getRequestDispatcher("upload_challan.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User admin = AdminSupport.requireAdmin(request, response);
        if (admin == null)
            return;

        // Parse multipart bodies first: when a file exceeds the limit, Tomcat does not fail
        // getParameter() (it just returns null), only getParts() reports it
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            try {
                request.getParts();
            } catch (IllegalStateException tooBig) {
                AdminSupport.flash(request, FLASH_ERROR, "The attachment is larger than 10 MB. Nothing was issued.");
                response.sendRedirect("uploadChallan#issueForm");
                return;
            }
        }
        String action = request.getParameter("action");

        if ("issue".equals(action)) {
            issue(request, admin);
            response.sendRedirect("uploadChallan" + (request.getSession().getAttribute(DRAFT) != null ? "?draft=1#issueForm" : ""));
            return;
        }

        // Status / delete: one challan (challanId) or a selection (challanIds="1,2,3")
        Set<Integer> ids = AdminSupport.parseIds(request.getParameterValues("challanIds"));
        ids.addAll(AdminSupport.parseIds(request.getParameterValues("challanId")));
        if (ids.isEmpty()) {
            AdminSupport.flash(request, FLASH_ERROR, "Select at least one challan.");
        } else if ("acceptProof".equals(action) || "rejectProof".equals(action)) {
            boolean accept = "acceptProof".equals(action);
            String note = UserFormSupport.clean(request.getParameter("note"));
            if (!accept && note.isEmpty()) {
                AdminSupport.flash(request, FLASH_ERROR, "Please give a reason when rejecting a proof, so the student knows what to fix.");
            } else if (note.length() > 255) {
                AdminSupport.flash(request, FLASH_ERROR, "The note can be at most 255 characters.");
            } else {
                int done = 0;
                for (Integer id : ids)
                    if (challanDAO.reviewProof(id, accept, note.isEmpty() ? null : note))
                        done++;
                AdminSupport.flash(request, done > 0 ? FLASH_SUCCESS : FLASH_ERROR, done > 0
                        ? done + " proof(s) " + (accept ? "accepted: the challan(s) are now marked paid." : "rejected. The student can upload a new proof.")
                        : "No submitted proof was found for that challan (it may already have been reviewed).");
            }
        } else if ("markPaid".equals(action) || "markUnpaid".equals(action)) {
            boolean paid = "markPaid".equals(action);
            int changed = 0;
            for (Integer id : ids)
                if (challanDAO.setPaid(id, paid))
                    changed++;
            int skipped = ids.size() - changed;
            AdminSupport.flash(request, changed > 0 ? FLASH_SUCCESS : FLASH_ERROR,
                    changed + " challan(s) marked " + (paid ? "paid" : "unpaid") + "."
                            + (skipped > 0 ? " " + skipped + " skipped (already " + (paid ? "paid" : "unpaid") + " or not found)." : ""));
        } else if ("delete".equals(action)) {
            int deleted = 0;
            for (Integer id : ids) {
                Challan c = challanDAO.getChallanById(id);
                if (c != null && !c.isPaid() && challanDAO.deleteUnpaid(id)) {
                    deleted++;
                    // A batch shares one attachment: remove the file only when no challan uses it any more
                    if (c.hasAttachment() && challanDAO.countByFilePath(c.getFilePath()) == 0)
                        UploadStore.delete(c.getFilePath());
                    if (c.hasProof())
                        UploadStore.delete(c.getProofPath()); // each proof belongs to one challan
                }
            }
            int skipped = ids.size() - deleted;
            AdminSupport.flash(request, deleted > 0 ? FLASH_SUCCESS : FLASH_ERROR,
                    deleted + " challan(s) deleted." + (skipped > 0 ? " " + skipped + " skipped: paid challans are kept as payment records." : ""));
        } else {
            AdminSupport.flash(request, FLASH_ERROR, "Unknown action.");
        }
        response.sendRedirect("uploadChallan#challanList");
    }

    private void issue(HttpServletRequest request, User admin) throws IOException, ServletException {
        String title = UserFormSupport.clean(request.getParameter("title"));
        String remarks = UserFormSupport.clean(request.getParameter("remarks"));
        Integer semesterId = AdminSupport.parseInt(request.getParameter("semesterId"));
        Set<Integer> studentIds = AdminSupport.parseIds(request.getParameterValues("studentIds"));
        studentIds.addAll(AdminSupport.parseIds(request.getParameterValues("studentId")));

        BigDecimal amount = null;
        String amountStr = request.getParameter("amount") == null ? "" : request.getParameter("amount").trim().replace(",", "");
        Date due = null;
        String error = null;
        try {
            if (!amountStr.isEmpty())
                amount = new BigDecimal(amountStr).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            error = "Amount must be a number, e.g. 45000.";
        }
        try {
            due = Date.valueOf(request.getParameter("dueDate"));
        } catch (IllegalArgumentException | NullPointerException ignored) {
        }

        Semester semester = null;
        if (semesterId != null)
            for (Semester s : semesterDAO.getAllSemesters())
                if (s.getSemesterId() == semesterId)
                    semester = s;

        if (error == null) {
            if (title.isEmpty() || title.length() > 100)
                error = "Challan title is required (up to 100 characters), e.g. Semester Fee - Fall 2024.";
            else if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0)
                error = "Amount must be greater than 0 and at most 10,000,000.";
            else if (semester == null)
                error = "Please choose a semester.";
            else if (due == null)
                error = "Please choose a due date.";
            else if (due.toLocalDate().isBefore(LocalDate.now()))
                error = "The due date cannot be in the past.";
            else if (remarks.length() > 255)
                error = "Remarks can be at most 255 characters.";
            else if (studentIds.isEmpty())
                error = "Select at least one student.";
            else if (studentIds.size() > 5000)
                error = "You can issue at most 5000 challans at once.";
        }

        String filePath = null;
        if (error == null) {
            try {
                filePath = UploadStore.save(request.getPart("attachment"), "challans");
            } catch (UploadStore.RejectedFileException e) {
                error = e.getMessage();
            } catch (IllegalStateException tooBig) {
                error = "The attachment is larger than 10 MB.";
            }
        }

        if (error != null) {
            AdminSupport.flash(request, FLASH_ERROR, error);
            java.util.Map<String, String> d = new java.util.HashMap<>();
            for (String f : new String[] { "title", "amount", "semesterId", "dueDate", "remarks" })
                if (request.getParameter(f) != null)
                    d.put(f, request.getParameter(f));
            d.put("studentIds", String.join(",", studentIds.stream().map(String::valueOf).toArray(String[]::new)));
            request.getSession().setAttribute(DRAFT, d);
            return;
        }

        ChallanDAO.IssueResult r = challanDAO.issueChallans(studentIds, semesterId, title, amount, due,
                remarks.isEmpty() ? null : remarks, filePath, admin.getUserId());
        if (!r.ok) {
            UploadStore.delete(filePath);
            AdminSupport.flash(request, FLASH_ERROR, "Could not issue the challans; nothing was saved. Please try again.");
            return;
        }
        if (r.created == 0)
            UploadStore.delete(filePath);
        StringBuilder msg = new StringBuilder();
        msg.append(r.created).append(r.created == 1 ? " challan" : " challans").append(" issued: ").append(title)
                .append(" (").append(semester.getName()).append("), ")
                .append(new java.text.DecimalFormat("#,##0.00").format(amount)).append(" PKR each, due ")
                .append(new java.text.SimpleDateFormat("dd MMM yyyy").format(due)).append('.');
        if (r.duplicates > 0)
            msg.append(" Skipped ").append(r.duplicates).append(" student(s) who already have this challan for this semester.");
        if (r.invalid > 0)
            msg.append(" Skipped ").append(r.invalid).append(" inactive or non-student account(s).");
        AdminSupport.flash(request, r.created > 0 ? FLASH_SUCCESS : FLASH_ERROR, msg.toString());
    }
}
