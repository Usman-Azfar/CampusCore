package com.cms.controllers;

import com.cms.dao.MessageDAO;
import com.cms.dao.SupportTicketDAO;
import com.cms.models.Message;
import com.cms.models.SupportTicket;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;

public class SupportTicketServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final String FLASH_SUCCESS = "helpdesk.flash.success";
    private static final String FLASH_ERROR = "helpdesk.flash.error";

    // Prefixes of the Messages copies of help desk traffic (messages.jsp links these back here)
    public static final String TICKET_MESSAGE_PREFIX = "[HELP DESK TICKET #";
    public static final String REPLY_MESSAGE_PREFIX = "[HELP DESK REPLY #";

    private SupportTicketDAO ticketDAO;
    private MessageDAO messageDAO;

    public void init() {
        ticketDAO = new SupportTicketDAO();
        messageDAO = new MessageDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.sendRedirect("login.jsp");
            return;
        }
        User user = (User) session.getAttribute("user");

        // Move one-time flash messages from the session to the request
        moveFlash(session, request, FLASH_SUCCESS, "successMessage");
        moveFlash(session, request, FLASH_ERROR, "errorMessage");

        List<SupportTicket> tickets;
        if ("ADMIN".equals(user.getRole())) {
            tickets = ticketDAO.getAllTickets();

            // Pre-select a ticket in the reply dropdown (from a row's Reply button or an inbox link)
            String replyParam = request.getParameter("reply");
            if (replyParam != null) {
                try {
                    request.setAttribute("selectedTicketId", Integer.parseInt(replyParam));
                } catch (NumberFormatException ignored) {
                }
            }
        } else {
            tickets = ticketDAO.getTicketsByUserId(user.getUserId());
        }
        request.setAttribute("tickets", tickets);
        request.getRequestDispatcher("helpdesk.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.sendRedirect("login.jsp");
            return;
        }
        User user = (User) session.getAttribute("user");

        if ("ADMIN".equals(user.getRole())) {
            handleAdminReply(request, session, user);
        } else {
            handleNewTicket(request, session, user);
        }

        // Post/Redirect/Get so refreshing the page does not resubmit the form
        response.sendRedirect("helpdesk");
    }

    private void handleAdminReply(HttpServletRequest request, HttpSession session, User admin) {
        String reply = trim(request.getParameter("replyText"));
        int ticketId;
        try {
            ticketId = Integer.parseInt(request.getParameter("ticketId"));
        } catch (NumberFormatException e) {
            session.setAttribute(FLASH_ERROR, "Please select a ticket to reply to.");
            return;
        }

        if (reply.isEmpty()) {
            session.setAttribute(FLASH_ERROR, "Reply message cannot be empty.");
            return;
        }
        if (reply.length() > MAX_TEXT_LENGTH) {
            session.setAttribute(FLASH_ERROR, "Reply is too long (maximum " + MAX_TEXT_LENGTH + " characters).");
            return;
        }

        int ticketOwnerId = ticketDAO.replyToTicket(ticketId, reply);
        if (ticketOwnerId == -1) {
            session.setAttribute(FLASH_ERROR, "Ticket #" + ticketId + " was not found or is already closed.");
            return;
        }

        // Keep Help Desk traffic separate from the general Messages screen.
        session.setAttribute(FLASH_SUCCESS, "Reply sent and ticket #" + ticketId + " closed.");
    }

    private void handleNewTicket(HttpServletRequest request, HttpSession session, User user) {
        String query = trim(request.getParameter("queryText"));
        if (query.isEmpty()) {
            session.setAttribute(FLASH_ERROR, "Query description cannot be empty.");
            return;
        }
        if (query.length() > MAX_TEXT_LENGTH) {
            session.setAttribute(FLASH_ERROR, "Query is too long (maximum " + MAX_TEXT_LENGTH + " characters).");
            return;
        }

        SupportTicket ticket = new SupportTicket();
        ticket.setUserId(user.getUserId());
        ticket.setQueryText(query);

        int ticketId = ticketDAO.createTicket(ticket);
        if (ticketId == -1) {
            session.setAttribute(FLASH_ERROR, "Failed to submit query. Please try again.");
            return;
        }

        // Keep Help Desk tickets separate from the normal message inbox/sent list.
        session.setAttribute(FLASH_SUCCESS, "Query submitted successfully as ticket #" + ticketId + ".");
    }

    static void moveFlash(HttpSession session, HttpServletRequest request, String sessionKey, String requestKey) {
        Object value = session.getAttribute(sessionKey);
        if (value != null) {
            request.setAttribute(requestKey, value);
            session.removeAttribute(sessionKey);
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
