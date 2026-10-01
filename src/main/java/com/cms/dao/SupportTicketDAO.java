package com.cms.dao;

import com.cms.models.Profile;
import com.cms.models.SupportTicket;
import com.cms.models.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SupportTicketDAO {

    // Returns the new ticket's ID, or -1 on failure
    public int createTicket(SupportTicket ticket) {
        String sql = "INSERT INTO support_tickets (user_id, query_text) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, ticket.getUserId());
            stmt.setString(2, ticket.getQueryText());
            if (stmt.executeUpdate() > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public List<SupportTicket> getTicketsByUserId(int userId) {
        List<SupportTicket> tickets = new ArrayList<>();
        String sql = "SELECT * FROM support_tickets WHERE user_id = ? ORDER BY created_at DESC, ticket_id DESC";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tickets.add(mapTicket(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tickets;
    }

    // All tickets with submitter details; open tickets first, newest first
    public List<SupportTicket> getAllTickets() {
        List<SupportTicket> tickets = new ArrayList<>();
        String sql = "SELECT t.*, u.username, u.role, p.full_name" + AcademicDAO.affiliationColumns("u") + "FROM support_tickets t " +
                "JOIN users u ON t.user_id = u.user_id " +
                "LEFT JOIN profiles p ON t.user_id = p.user_id " +
                AcademicDAO.affiliationJoins("u") +
                "ORDER BY (t.status = 'OPEN') DESC, t.created_at DESC, t.ticket_id DESC";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                SupportTicket ticket = mapTicket(rs);

                User submitter = new User();
                submitter.setUserId(ticket.getUserId());
                submitter.setUsername(rs.getString("username"));
                submitter.setRole(rs.getString("role"));
                AcademicDAO.readAffiliation(rs, submitter);
                Profile profile = new Profile();
                profile.setFullName(rs.getString("full_name"));
                submitter.setProfile(profile);
                ticket.setUser(submitter);

                tickets.add(ticket);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tickets;
    }

    /**
     * Saves the admin reply on one specific open ticket and closes it.
     * Returns the ticket owner's user ID, or -1 if the ticket was not found or
     * already closed.
     */
    public int replyToTicket(int ticketId, String replyContent) {
        String findSql = "SELECT user_id FROM support_tickets WHERE ticket_id = ? AND status = 'OPEN'";
        String updateSql = "UPDATE support_tickets SET admin_reply = ?, status = 'CLOSED' " +
                "WHERE ticket_id = ? AND status = 'OPEN'";
        try (Connection conn = DBConnection.getConnection()) {
            int userId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(findSql)) {
                stmt.setInt(1, ticketId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getInt("user_id");
                    }
                }
            }
            if (userId == -1)
                return -1;

            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setString(1, replyContent);
                stmt.setInt(2, ticketId);
                // Another admin may have closed it in the meantime
                return stmt.executeUpdate() > 0 ? userId : -1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    private SupportTicket mapTicket(ResultSet rs) throws SQLException {
        SupportTicket ticket = new SupportTicket();
        ticket.setTicketId(rs.getInt("ticket_id"));
        ticket.setUserId(rs.getInt("user_id"));
        ticket.setQueryText(rs.getString("query_text"));
        ticket.setAdminReply(rs.getString("admin_reply"));
        ticket.setStatus(rs.getString("status"));
        ticket.setCreatedAt(rs.getTimestamp("created_at"));
        return ticket;
    }
}
