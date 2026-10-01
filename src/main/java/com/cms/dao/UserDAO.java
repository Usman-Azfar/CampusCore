package com.cms.dao;

import com.cms.models.User;
import com.cms.util.PasswordHasher;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {

    // Checked for unknown usernames too, so a wrong username takes as long as a wrong password
    private static final String DUMMY_HASH = PasswordHasher.hash("not-a-real-password");

    public User authenticateUser(String username, String password) {
        User user = null;
        String sql = "SELECT * FROM users WHERE username = ? AND is_active = TRUE";

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    PasswordHasher.verify(password, DUMMY_HASH);
                    return null;
                }
                String stored = rs.getString("password");
                if (!PasswordHasher.verify(password, stored))
                    return null;
                user = new User();
                user.setUserId(rs.getInt("user_id"));
                user.setUsername(rs.getString("username"));
                user.setRole(rs.getString("role"));
                user.setActive(rs.getBoolean("is_active"));
                user.setCreatedAt(rs.getTimestamp("created_at"));
                if (PasswordHasher.needsRehash(stored))
                    updatePassword(user.getUserId(), password); // upgrade to the current iteration count
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return user;
    }

    /** Stores the password as a PBKDF2 hash. */
    public boolean updatePassword(int userId, String newPassword) {
        String sql = "UPDATE users SET password = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, PasswordHasher.hash(newPassword));
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean verifyPassword(int userId, String password) {
        String sql = "SELECT password FROM users WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && PasswordHasher.verify(password, rs.getString("password"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Replaces plain-text passwords left from before hashing was added. Runs at start-up
     * (PasswordMigrationListener); returns the number of accounts converted.
     */
    public int hashPlainTextPasswords() {
        String select = "SELECT user_id, password FROM users WHERE password NOT LIKE ?";
        String update = "UPDATE users SET password = ? WHERE user_id = ? AND password = ?";
        int converted = 0;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement sel = conn.prepareStatement(select);
                PreparedStatement upd = conn.prepareStatement(update)) {
            sel.setString(1, PasswordHasher.PREFIX + "$%");
            try (ResultSet rs = sel.executeQuery()) {
                while (rs.next()) {
                    String plain = rs.getString("password");
                    upd.setString(1, PasswordHasher.hash(plain));
                    upd.setInt(2, rs.getInt("user_id"));
                    upd.setString(3, plain); // skip the row if it changed in the meantime
                    converted += upd.executeUpdate();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return converted;
    }

    public User getUserByUsername(String username) {
        User user = null;
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    user = new User();
                    user.setUserId(rs.getInt("user_id"));
                    user.setUsername(rs.getString("username"));
                    user.setRole(rs.getString("role"));
                    user.setActive(rs.getBoolean("is_active"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return user;
    }

    public java.util.List<User> getUsersByRole(String role) {
        java.util.List<User> users = new java.util.ArrayList<>();
        String sql = "SELECT u.*, p.full_name, p.father_name, p.email, p.gender, p.phone, p.address, p.city, p.country " +
                AcademicDAO.affiliationColumns("u") +
                "FROM users u " +
                "LEFT JOIN profiles p ON u.user_id = p.user_id " +
                AcademicDAO.affiliationJoins("u") +
                "WHERE u.role = ? ORDER BY u.username";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, role);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    User user = new User();
                    user.setUserId(rs.getInt("user_id"));
                    user.setUsername(rs.getString("username"));
                    user.setRole(rs.getString("role"));
                    user.setActive(rs.getBoolean("is_active"));
                    AcademicDAO.readAffiliation(rs, user);

                    com.cms.models.Profile profile = new com.cms.models.Profile();
                    profile.setUserId(user.getUserId());
                    profile.setFullName(rs.getString("full_name"));
                    profile.setEmail(rs.getString("email"));
                    profile.setGender(rs.getString("gender"));
                    profile.setPhone(rs.getString("phone"));
                    profile.setAddress(rs.getString("address"));
                    profile.setCity(rs.getString("city"));
                    profile.setCountry(rs.getString("country"));
                    profile.setFatherName(rs.getString("father_name"));
                    user.setProfile(profile);

                    users.add(user);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    // Distinct active students currently enrolled in any course allocated to this teacher
    public java.util.List<User> getStudentsByTeacher(int teacherId) {
        java.util.List<User> users = new java.util.ArrayList<>();
        String sql = "SELECT DISTINCT u.user_id, u.username, u.role, u.is_active, p.full_name " +
                AcademicDAO.affiliationColumns("u") +
                "FROM enrollments e " +
                "JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                "JOIN users u ON e.student_id = u.user_id " +
                "LEFT JOIN profiles p ON u.user_id = p.user_id " +
                AcademicDAO.affiliationJoins("u") +
                "WHERE ca.teacher_id = ? AND e.status = 'ENROLLED' AND u.is_active = TRUE " +
                "ORDER BY u.username";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, teacherId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    User user = new User();
                    user.setUserId(rs.getInt("user_id"));
                    user.setUsername(rs.getString("username"));
                    user.setRole(rs.getString("role"));
                    user.setActive(rs.getBoolean("is_active"));
                    AcademicDAO.readAffiliation(rs, user);
                    com.cms.models.Profile profile = new com.cms.models.Profile();
                    profile.setUserId(user.getUserId());
                    profile.setFullName(rs.getString("full_name"));
                    user.setProfile(profile);
                    users.add(user);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    /**
     * Courses linked to each user: courses a teacher is allocated to, and courses a
     * student is currently enrolled in. Returns userId -> (course code -> course name),
     * with codes sorted.
     */
    public java.util.Map<Integer, java.util.Map<String, String>> getCoursesByUser() {
        java.util.Map<Integer, java.util.Map<String, String>> result = new java.util.HashMap<>();
        String sql = "SELECT ca.teacher_id AS user_id, c.course_code, c.course_name " +
                "FROM course_allocations ca JOIN courses c ON ca.course_id = c.course_id " +
                "UNION " +
                "SELECT e.student_id, c.course_code, c.course_name " +
                "FROM enrollments e " +
                "JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                "JOIN courses c ON ca.course_id = c.course_id " +
                "WHERE e.status = 'ENROLLED'";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.computeIfAbsent(rs.getInt("user_id"), k -> new java.util.TreeMap<>())
                        .put(rs.getString("course_code"), rs.getString("course_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Loads many users (role, active flag, name, class/department) with a few IN (...)
     * queries instead of one query per user. Missing IDs are simply absent.
     */
    public java.util.Map<Integer, User> getUsersByIds(java.util.Collection<Integer> ids) {
        java.util.Map<Integer, User> result = new java.util.HashMap<>();
        java.util.List<Integer> list = new java.util.ArrayList<>(ids);
        final int chunk = 500;
        for (int from = 0; from < list.size(); from += chunk) {
            java.util.List<Integer> part = list.subList(from, Math.min(from + chunk, list.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(part.size(), "?"));
            String sql = "SELECT u.user_id, u.username, u.role, u.is_active, p.full_name" +
                    AcademicDAO.affiliationColumns("u") +
                    "FROM users u LEFT JOIN profiles p ON u.user_id = p.user_id " +
                    AcademicDAO.affiliationJoins("u") +
                    "WHERE u.user_id IN (" + placeholders + ")";
            try (Connection conn = DBConnection.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < part.size(); i++) {
                    stmt.setInt(i + 1, part.get(i));
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        User user = new User();
                        user.setUserId(rs.getInt("user_id"));
                        user.setUsername(rs.getString("username"));
                        user.setRole(rs.getString("role"));
                        user.setActive(rs.getBoolean("is_active"));
                        AcademicDAO.readAffiliation(rs, user);
                        com.cms.models.Profile profile = new com.cms.models.Profile();
                        profile.setUserId(user.getUserId());
                        profile.setFullName(rs.getString("full_name"));
                        user.setProfile(profile);
                        result.put(user.getUserId(), user);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    // True when another user (not exceptUserId) already has this email (profiles.email is UNIQUE)
    public boolean emailInUse(String email, Integer exceptUserId) {
        String sql = "SELECT 1 FROM profiles WHERE email = ?" + (exceptUserId != null ? " AND user_id <> ?" : "") + " LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            if (exceptUserId != null)
                stmt.setInt(2, exceptUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return true; // fail safe
        }
    }

    // Activates or deactivates an account of the given role (inactive users cannot log in)
    public boolean setActive(int userId, String role, boolean active) {
        String sql = "UPDATE users SET is_active = ? WHERE user_id = ? AND role = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, active);
            stmt.setInt(2, userId);
            stmt.setString(3, role);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void setNullableInt(PreparedStatement stmt, int index, Integer value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, java.sql.Types.INTEGER);
        } else {
            stmt.setInt(index, value);
        }
    }

    public boolean addUserWithProfile(User user, com.cms.models.Profile profile) {
        String userSql = "INSERT INTO users (username, password, role, is_active, class_id, department_id) VALUES (?, ?, ?, ?, ?, ?)";
        String profileSql = "INSERT INTO profiles (user_id, full_name, email, gender, phone, address, city, country, father_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(userSql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, user.getUsername());
                stmt.setString(2, PasswordHasher.hash(user.getPassword()));
                stmt.setString(3, user.getRole());
                stmt.setBoolean(4, true);
                setNullableInt(stmt, 5, user.getClassId());
                setNullableInt(stmt, 6, user.getDepartmentId());

                int affected = stmt.executeUpdate();
                if (affected == 0)
                    throw new SQLException("Creating user failed.");

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        user.setUserId(rs.getInt(1));
                    } else {
                        throw new SQLException("Creating user failed, no ID obtained.");
                    }
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(profileSql)) {
                stmt.setInt(1, user.getUserId());
                stmt.setString(2, profile.getFullName());
                stmt.setString(3, profile.getEmail());
                stmt.setString(4, profile.getGender());
                stmt.setString(5, profile.getPhone());
                stmt.setString(6, profile.getAddress());
                stmt.setString(7, profile.getCity());
                stmt.setString(8, profile.getCountry());
                stmt.setString(9, profile.getFatherName());
                stmt.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
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

    public User getUserById(int userId) {
        User user = null;
        String sql = "SELECT u.*, p.*" + AcademicDAO.affiliationColumns("u") +
                "FROM users u LEFT JOIN profiles p ON u.user_id = p.user_id " +
                AcademicDAO.affiliationJoins("u") + "WHERE u.user_id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    user = new User();
                    user.setUserId(rs.getInt("user_id"));
                    user.setUsername(rs.getString("username"));
                    user.setRole(rs.getString("role"));
                    user.setActive(rs.getBoolean("is_active"));
                    AcademicDAO.readAffiliation(rs, user);

                    com.cms.models.Profile profile = new com.cms.models.Profile();
                    profile.setUserId(userId);
                    profile.setFullName(rs.getString("full_name"));
                    profile.setEmail(rs.getString("email"));
                    profile.setGender(rs.getString("gender"));
                    profile.setPhone(rs.getString("phone"));
                    profile.setAddress(rs.getString("address"));
                    profile.setCity(rs.getString("city"));
                    profile.setCountry(rs.getString("country"));
                    profile.setFatherName(rs.getString("father_name"));
                    user.setProfile(profile);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return user;
    }

    public boolean updateUserWithProfile(User user, com.cms.models.Profile profile) {
        // Password is only changed when a new one was provided (getUserById never loads it,
        // so writing it unconditionally would try to store NULL and fail)
        boolean changePassword = user.getPassword() != null && !user.getPassword().isEmpty();
        String userSql = "UPDATE users SET username = ?, is_active = ?, class_id = ?, department_id = ?" +
                (changePassword ? ", password = ?" : "") + " WHERE user_id = ?";
        String profileSql = "UPDATE profiles SET full_name = ?, email = ?, gender = ?, phone = ?, address = ?, city = ?, country = ?, father_name = ? WHERE user_id = ?";

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(userSql)) {
                int i = 1;
                stmt.setString(i++, user.getUsername());
                stmt.setBoolean(i++, user.isActive());
                setNullableInt(stmt, i++, user.getClassId());
                setNullableInt(stmt, i++, user.getDepartmentId());
                if (changePassword) {
                    stmt.setString(i++, PasswordHasher.hash(user.getPassword()));
                }
                stmt.setInt(i, user.getUserId());
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement(profileSql)) {
                stmt.setString(1, profile.getFullName());
                stmt.setString(2, profile.getEmail());
                stmt.setString(3, profile.getGender());
                stmt.setString(4, profile.getPhone());
                stmt.setString(5, profile.getAddress());
                stmt.setString(6, profile.getCity());
                stmt.setString(7, profile.getCountry());
                stmt.setString(8, profile.getFatherName());
                stmt.setInt(9, user.getUserId());
                stmt.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
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

    public boolean deleteUser(int userId) {
        User user = getUserById(userId);
        if (user == null)
            return false;

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Common: Delete Messages
            String msgSql = "DELETE FROM messages WHERE sender_id = ? OR receiver_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(msgSql)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, userId);
                stmt.executeUpdate();
            }

            // 2. Common: Delete Support Tickets
            String ticketSql = "DELETE FROM support_tickets WHERE user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(ticketSql)) {
                stmt.setInt(1, userId);
                stmt.executeUpdate();
            }

            if ("TEACHER".equals(user.getRole())) {
                // Delete Attendance for teacher's courses
                String attSql = "DELETE a FROM attendance a JOIN enrollments e ON a.enrollment_id = e.enrollment_id " +
                        "JOIN course_allocations ca ON e.allocation_id = ca.allocation_id WHERE ca.teacher_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(attSql)) {
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                }

                // Delete Grades for teacher's courses
                String gradeSql = "DELETE g FROM grades g JOIN enrollments e ON g.enrollment_id = e.enrollment_id " +
                        "JOIN course_allocations ca ON e.allocation_id = ca.allocation_id WHERE ca.teacher_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(gradeSql)) {
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                }

                // Delete Enrollments for teacher's courses
                String enrollSql = "DELETE e FROM enrollments e JOIN course_allocations ca ON e.allocation_id = ca.allocation_id "
                        +
                        "WHERE ca.teacher_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(enrollSql)) {
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                }

                // Delete Allocations
                String allocSql = "DELETE FROM course_allocations WHERE teacher_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(allocSql)) {
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                }

                // Delete Announcements
                String annSql = "DELETE FROM announcements WHERE created_by = ?";
                try (PreparedStatement stmt = conn.prepareStatement(annSql)) {
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                }
            } else if ("STUDENT".equals(user.getRole())) {
                // Delete Attendance for student
                String attSql = "DELETE a FROM attendance a JOIN enrollments e ON a.enrollment_id = e.enrollment_id WHERE e.student_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(attSql)) {
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                }

                // Delete Grades for student
                String gradeSql = "DELETE g FROM grades g JOIN enrollments e ON g.enrollment_id = e.enrollment_id WHERE e.student_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(gradeSql)) {
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                }

                // Delete Enrollments
                String enrollSql = "DELETE FROM enrollments WHERE student_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(enrollSql)) {
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                }

                // Delete Challans
                String chalSql = "DELETE FROM challans WHERE student_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(chalSql)) {
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                }

                // Delete Course Requests
                String reqSql = "DELETE FROM course_requests WHERE student_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(reqSql)) {
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                }
            }

            // 6. Delete User (Cascades to Profile if set in DB)
            String userSql = "DELETE FROM users WHERE user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(userSql)) {
                stmt.setInt(1, userId);
                stmt.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
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
}
