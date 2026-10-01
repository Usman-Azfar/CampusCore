package com.cms.dao;

import com.cms.models.Message;
import com.cms.models.Profile;
import com.cms.models.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {

    public static boolean isHelpDeskMessage(String messageText) {
        if (messageText == null) {
            return false;
        }
        return messageText.startsWith("[HELP DESK TICKET #") || messageText.startsWith("[HELP DESK REPLY #");
    }

    // Messages received by the user, with sender details
    public List<Message> getMessagesForUser(int userId) {
        String sql = "SELECT m.*, u.username, u.role, p.full_name" + AcademicDAO.affiliationColumns("u") +
                "FROM messages m " +
                "JOIN users u ON m.sender_id = u.user_id " +
                "LEFT JOIN profiles p ON u.user_id = p.user_id " +
                AcademicDAO.affiliationJoins("u") +
                "WHERE m.receiver_id = ? AND m.model_message NOT LIKE '[HELP DESK %' " +
                "ORDER BY m.timestamp DESC, m.message_id DESC";
        return queryMessages(sql, userId, true);
    }

    // Messages sent by the user, with receiver details
    public List<Message> getSentMessages(int userId) {
        String sql = "SELECT m.*, u.username, u.role, p.full_name" + AcademicDAO.affiliationColumns("u") +
                "FROM messages m " +
                "JOIN users u ON m.receiver_id = u.user_id " +
                "LEFT JOIN profiles p ON u.user_id = p.user_id " +
                AcademicDAO.affiliationJoins("u") +
                "WHERE m.sender_id = ? AND m.model_message NOT LIKE '[HELP DESK %' " +
                "ORDER BY m.timestamp DESC, m.message_id DESC";
        return queryMessages(sql, userId, false);
    }

    public int countUnread(int userId) {
        String sql = "SELECT COUNT(*) FROM messages WHERE receiver_id = ? AND is_read = FALSE AND model_message NOT LIKE '[HELP DESK %'";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public boolean markInboxRead(int userId) {
        String sql = "UPDATE messages SET is_read = TRUE WHERE receiver_id = ? AND is_read = FALSE AND model_message NOT LIKE '[HELP DESK %'";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean sendMessage(Message message) {
        String sql = "INSERT INTO messages (sender_id, receiver_id, model_message) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, message.getSenderId());
            stmt.setInt(2, message.getReceiverId());
            stmt.setString(3, message.getModelMessage());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sends the same message to several recipients in one transaction: either every
     * recipient gets it or none does. All rows share one timestamp so the Sent view can
     * show them as a single broadcast.
     */
    public boolean sendMessages(int senderId, List<Integer> receiverIds, String content) {
        if (receiverIds.isEmpty())
            return false;
        String sql = "INSERT INTO messages (sender_id, receiver_id, model_message, timestamp) VALUES (?, ?, ?, ?)";
        java.sql.Timestamp now = new java.sql.Timestamp(System.currentTimeMillis() / 1000 * 1000);
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Integer receiverId : receiverIds) {
                    stmt.setInt(1, senderId);
                    stmt.setInt(2, receiverId);
                    stmt.setString(3, content);
                    stmt.setTimestamp(4, now);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public int getAdminId() {
        String sql = "SELECT user_id FROM users WHERE role = 'ADMIN' ORDER BY user_id LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("user_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // The joined user columns describe the sender (inbox) or the receiver (sent)
    private List<Message> queryMessages(String sql, int userId, boolean joinedIsSender) {
        List<Message> messages = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Message msg = new Message();
                    msg.setMessageId(rs.getInt("message_id"));
                    msg.setSenderId(rs.getInt("sender_id"));
                    msg.setReceiverId(rs.getInt("receiver_id"));
                    msg.setModelMessage(rs.getString("model_message"));
                    msg.setTimestamp(rs.getTimestamp("timestamp"));
                    msg.setRead(rs.getBoolean("is_read"));

                    User other = new User();
                    other.setUserId(joinedIsSender ? msg.getSenderId() : msg.getReceiverId());
                    other.setUsername(rs.getString("username"));
                    other.setRole(rs.getString("role"));
                    AcademicDAO.readAffiliation(rs, other);
                    Profile profile = new Profile();
                    profile.setFullName(rs.getString("full_name"));
                    other.setProfile(profile);

                    if (joinedIsSender) {
                        msg.setSender(other);
                    } else {
                        msg.setReceiver(other);
                    }
                    messages.add(msg);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }
}
