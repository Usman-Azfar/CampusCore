package com.cms.controllers;

import com.cms.dao.ChallanDAO;
import com.cms.models.Challan;
import com.cms.models.User;
import com.cms.util.UploadStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/** Student: My Challans, and uploading proof of payment for an unpaid challan. */
public class ChallanServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = "myChallans.flash.success";
    private static final String FLASH_ERROR = "myChallans.flash.error";

    private ChallanDAO challanDAO;

    public void init() {
        challanDAO = new ChallanDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = currentStudent(request, response);
        if (user == null)
            return;
        HttpSession session = request.getSession();
        SupportTicketServlet.moveFlash(session, request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(session, request, FLASH_ERROR, "errorMessage");

        request.setAttribute("challans", challanDAO.getChallansByStudent(user.getUserId()));
        request.getRequestDispatcher("challans.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = currentStudent(request, response);
        if (user == null)
            return;
        HttpSession session = request.getSession();

        // Oversized uploads only surface from getParts()
        try {
            if (request.getContentType() != null && request.getContentType().toLowerCase().startsWith("multipart/"))
                request.getParts();
        } catch (IllegalStateException tooBig) {
            session.setAttribute(FLASH_ERROR, "The file is larger than 10 MB. Please upload a smaller photo or PDF.");
            response.sendRedirect("challans");
            return;
        }

        if (!"uploadProof".equals(request.getParameter("action"))) {
            session.setAttribute(FLASH_ERROR, "Unknown action.");
            response.sendRedirect("challans");
            return;
        }

        Integer challanId = AdminSupport.parseInt(request.getParameter("challanId"));
        String reference = UserFormSupport.clean(request.getParameter("reference"));
        Challan c = challanId != null ? challanDAO.getChallanById(challanId) : null;

        String error = null;
        if (c == null || c.getStudentId() != user.getUserId()) {
            error = "Challan not found.";
        } else if (c.isPaid()) {
            error = c.getNumber() + " is already paid.";
        } else if (reference.length() > 100) {
            error = "The reference number can be at most 100 characters.";
        }

        String stored = null;
        if (error == null) {
            try {
                stored = UploadStore.save(request.getPart("proofFile"), "proofs");
                if (stored == null)
                    error = "Please choose a photo or PDF of your paid challan / bank receipt.";
            } catch (UploadStore.RejectedFileException e) {
                error = e.getMessage();
            }
        }

        if (error == null) {
            String previous = challanDAO.submitProof(challanId, user.getUserId(), stored, reference.isEmpty() ? null : reference);
            if (previous == null) {
                UploadStore.delete(stored);
                error = "Could not save your proof. Please try again.";
            } else {
                if (!previous.isEmpty())
                    UploadStore.delete(previous); // replaced an earlier upload
                session.setAttribute(FLASH_SUCCESS, "Proof of payment for " + c.getNumber()
                        + " submitted. The accounts office will review it and mark the challan paid.");
            }
        }
        if (error != null)
            session.setAttribute(FLASH_ERROR, error);
        response.sendRedirect("challans");
    }

    private User currentStudent(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        User user = session != null ? (User) session.getAttribute("user") : null;
        if (user == null) {
            response.sendRedirect("login");
            return null;
        }
        if (!"STUDENT".equals(user.getRole())) {
            response.sendRedirect("dashboard");
            return null;
        }
        return user;
    }
}
