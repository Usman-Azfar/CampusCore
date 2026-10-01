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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * /challanView?id=N  printable fee voucher
 * /challanFile?id=N  the challan's attachment
 * Only the admin and the student the challan belongs to may open them.
 */
public class ChallanDocumentServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private ChallanDAO challanDAO;

    public void init() {
        challanDAO = new ChallanDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User user = session != null ? (User) session.getAttribute("user") : null;
        if (user == null) {
            response.sendRedirect("login");
            return;
        }

        Integer id = AdminSupport.parseInt(request.getParameter("id"));
        Challan c = id != null ? challanDAO.getChallanById(id) : null;
        boolean allowed = c != null && ("ADMIN".equals(user.getRole())
                || ("STUDENT".equals(user.getRole()) && c.getStudentId() == user.getUserId()));
        if (!allowed) {
            // Same answer for "missing" and "not yours", so challan numbers cannot be probed
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Challan not found.");
            return;
        }

        if (request.getServletPath().endsWith("challanFile")) {
            // kind=proof -> the student's proof of payment; otherwise the admin's attachment
            boolean proof = "proof".equals(request.getParameter("kind"));
            String path = proof ? c.getProofPath() : c.getFilePath();
            File f = (path != null && !path.isEmpty()) ? UploadStore.resolve(path) : null;
            if (f == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "This challan has no such file, or the file is missing.");
                return;
            }
            String type = UploadStore.contentType(path);
            String ext = type.equals("application/pdf") ? "pdf" : type.equals("image/png") ? "png" : "jpg";
            response.setContentType(type);
            response.setContentLengthLong(f.length());
            response.setHeader("Content-Disposition", "inline; filename=\"" + c.getNumber() + (proof ? "-payment-proof." : "-attachment.") + ext + "\"");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "private, no-store");
            Files.copy(f.toPath(), response.getOutputStream());
            return;
        }

        request.setAttribute("challan", c);
        request.setAttribute("student", new com.cms.dao.UserDAO().getUserById(c.getStudentId()));
        request.getRequestDispatcher("challan_view.jsp").forward(request, response);
    }
}
