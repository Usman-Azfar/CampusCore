package com.cms.dao;

import com.cms.models.Challan;
import com.cms.models.Profile;
import com.cms.models.Semester;
import com.cms.models.User;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ChallanDAO {

    /** Outcome of issuing one challan batch. */
    public static final class IssueResult {
        public int created;
        public int duplicates; // already had a challan with this title for this semester
        public int invalid;    // not an active student
        public boolean ok = true;
    }

    private static final String SELECT = "SELECT ch.*, s.name AS semester_name, s.is_active AS semester_active, " +
            "u.username AS student_roll, p.full_name AS student_name, cp.full_name AS created_by_name" +
            AcademicDAO.affiliationColumns("u") +
            "FROM challans ch " +
            "JOIN semesters s ON ch.semester_id = s.semester_id " +
            "JOIN users u ON ch.student_id = u.user_id " +
            "LEFT JOIN profiles p ON u.user_id = p.user_id " +
            "LEFT JOIN profiles cp ON ch.created_by = cp.user_id " +
            AcademicDAO.affiliationJoins("u");

    /**
     * Issues the same challan to many students in one transaction. Students who
     * already have a challan with the same title in the same semester are skipped.
     */
    public IssueResult issueChallans(Collection<Integer> studentIds, int semesterId, String title, BigDecimal amount,
            Date dueDate, String remarks, String filePath, int adminId) {
        IssueResult result = new IssueResult();
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);
            try (PreparedStatement isStudent = conn.prepareStatement(
                    "SELECT 1 FROM users WHERE user_id = ? AND role = 'STUDENT' AND is_active = TRUE");
                    PreparedStatement dup = conn.prepareStatement(
                            "SELECT 1 FROM challans WHERE student_id = ? AND semester_id = ? AND title = ? LIMIT 1");
                    PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO challans (student_id, semester_id, title, amount, file_path, due_date, status, remarks, created_by) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, 'UNPAID', ?, ?)")) {
                for (Integer studentId : studentIds) {
                    isStudent.setInt(1, studentId);
                    try (ResultSet rs = isStudent.executeQuery()) {
                        if (!rs.next()) {
                            result.invalid++;
                            continue;
                        }
                    }
                    dup.setInt(1, studentId);
                    dup.setInt(2, semesterId);
                    dup.setString(3, title);
                    try (ResultSet rs = dup.executeQuery()) {
                        if (rs.next()) {
                            result.duplicates++;
                            continue;
                        }
                    }
                    ins.setInt(1, studentId);
                    ins.setInt(2, semesterId);
                    ins.setString(3, title);
                    ins.setBigDecimal(4, amount);
                    ins.setString(5, filePath);
                    ins.setDate(6, dueDate);
                    ins.setString(7, remarks);
                    ins.setInt(8, adminId);
                    ins.addBatch();
                    result.created++;
                }
                ins.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            result.ok = false;
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    // A student's challans, newest challan number first
    public List<Challan> getChallansByStudent(int studentId) {
        return query(SELECT + "WHERE ch.student_id = ? " +
                "ORDER BY ch.challan_id DESC", // newest challan number first
                studentId);
    }

    // Every challan, newest issued first
    public List<Challan> getAllChallans() {
        return query(SELECT + "ORDER BY ch.upload_date DESC, ch.challan_id DESC", null);
    }

    public Challan getChallanById(int challanId) {
        List<Challan> list = query(SELECT + "WHERE ch.challan_id = ?", challanId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Records a student's proof of payment on their own UNPAID challan (replacing any
     * earlier proof). Returns the previous proof path (so the caller can delete that
     * file), "" when there was none, or null when not allowed.
     */
    public String submitProof(int challanId, int studentId, String proofPath, String reference) {
        String find = "SELECT proof_path FROM challans WHERE challan_id = ? AND student_id = ? AND status = 'UNPAID'";
        String update = "UPDATE challans SET proof_path = ?, proof_reference = ?, proof_status = 'SUBMITTED', " +
                "proof_submitted_at = CURRENT_TIMESTAMP, proof_review_note = NULL, proof_reviewed_at = NULL " +
                "WHERE challan_id = ? AND student_id = ? AND status = 'UNPAID'";
        try (Connection conn = DBConnection.getConnection()) {
            String previous;
            try (PreparedStatement stmt = conn.prepareStatement(find)) {
                stmt.setInt(1, challanId);
                stmt.setInt(2, studentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next())
                        return null;
                    previous = rs.getString(1);
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(update)) {
                stmt.setString(1, proofPath);
                stmt.setString(2, reference);
                stmt.setInt(3, challanId);
                stmt.setInt(4, studentId);
                if (stmt.executeUpdate() == 0)
                    return null;
            }
            return previous == null ? "" : previous;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Admin decision on a submitted proof. Accepting marks the challan PAID; rejecting
     * keeps it UNPAID with the reason shown to the student.
     */
    public boolean reviewProof(int challanId, boolean accept, String note) {
        String sql = accept
                ? "UPDATE challans SET proof_status = 'ACCEPTED', proof_review_note = ?, proof_reviewed_at = CURRENT_TIMESTAMP, " +
                  "status = 'PAID', paid_at = CURRENT_TIMESTAMP WHERE challan_id = ? AND proof_status = 'SUBMITTED' AND status = 'UNPAID'"
                : "UPDATE challans SET proof_status = 'REJECTED', proof_review_note = ?, proof_reviewed_at = CURRENT_TIMESTAMP " +
                  "WHERE challan_id = ? AND proof_status = 'SUBMITTED' AND status = 'UNPAID'";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, note);
            stmt.setInt(2, challanId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Marks paid (recording when) or back to unpaid
    public boolean setPaid(int challanId, boolean paid) {
        String sql = paid
                ? "UPDATE challans SET status = 'PAID', paid_at = CURRENT_TIMESTAMP, " +
                  "proof_status = IF(proof_status = 'SUBMITTED', 'ACCEPTED', proof_status), " +
                  "proof_reviewed_at = IF(proof_status = 'ACCEPTED', CURRENT_TIMESTAMP, proof_reviewed_at) " +
                  "WHERE challan_id = ? AND status = 'UNPAID'"
                : "UPDATE challans SET status = 'UNPAID', paid_at = NULL, " +
                  "proof_status = IF(proof_status = 'ACCEPTED', 'SUBMITTED', proof_status) " + // back into the review queue
                  "WHERE challan_id = ? AND status = 'PAID'";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, challanId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Deletes an UNPAID challan (paid ones are payment records and are kept)
    public boolean deleteUnpaid(int challanId) {
        String sql = "DELETE FROM challans WHERE challan_id = ? AND status = 'UNPAID'";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, challanId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // How many challans still reference this attachment (a shared file is removed only when unused)
    public int countByFilePath(String filePath) {
        String sql = "SELECT COUNT(*) FROM challans WHERE file_path = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, filePath);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 1; // fail safe: keep the file
        }
    }

    private List<Challan> query(String sql, Integer param) {
        List<Challan> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (param != null)
                stmt.setInt(1, param);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Challan c = new Challan();
                    c.setChallanId(rs.getInt("challan_id"));
                    c.setStudentId(rs.getInt("student_id"));
                    c.setSemesterId(rs.getInt("semester_id"));
                    c.setTitle(rs.getString("title"));
                    c.setAmount(rs.getBigDecimal("amount"));
                    c.setFilePath(rs.getString("file_path"));
                    c.setUploadDate(rs.getTimestamp("upload_date"));
                    c.setDueDate(rs.getDate("due_date"));
                    c.setStatus(rs.getString("status"));
                    c.setRemarks(rs.getString("remarks"));
                    c.setPaidAt(rs.getTimestamp("paid_at"));
                    c.setCreatedByName(rs.getString("created_by_name"));
                    c.setProofPath(rs.getString("proof_path"));
                    c.setProofReference(rs.getString("proof_reference"));
                    c.setProofStatus(rs.getString("proof_status"));
                    c.setProofSubmittedAt(rs.getTimestamp("proof_submitted_at"));
                    c.setProofReviewNote(rs.getString("proof_review_note"));
                    c.setProofReviewedAt(rs.getTimestamp("proof_reviewed_at"));

                    Semester sem = new Semester();
                    sem.setSemesterId(c.getSemesterId());
                    sem.setName(rs.getString("semester_name"));
                    sem.setActive(rs.getBoolean("semester_active"));
                    c.setSemester(sem);

                    User student = new User();
                    student.setUserId(c.getStudentId());
                    student.setUsername(rs.getString("student_roll"));
                    student.setRole("STUDENT");
                    Profile p = new Profile();
                    p.setFullName(rs.getString("student_name"));
                    student.setProfile(p);
                    AcademicDAO.readAffiliation(rs, student);
                    c.setStudent(student);
                    list.add(c);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}
