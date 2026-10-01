package com.cms.dao;

import com.cms.models.Semester;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SemesterDAO {

    public List<Semester> getAllSemesters() {
        List<Semester> semesters = new ArrayList<>();
        String sql = "SELECT s.*, " +
                "(SELECT COUNT(*) FROM course_allocations ca WHERE ca.semester_id = s.semester_id) AS allocation_count, " +
                "(SELECT COUNT(*) FROM challans ch WHERE ch.semester_id = s.semester_id) AS challan_count " +
                "FROM semesters s ORDER BY s.start_date DESC";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Semester s = new Semester();
                s.setSemesterId(rs.getInt("semester_id"));
                s.setName(rs.getString("name"));
                s.setStartDate(rs.getDate("start_date"));
                s.setEndDate(rs.getDate("end_date"));
                s.setActive(rs.getBoolean("is_active"));
                s.setAllocationCount(rs.getInt("allocation_count"));
                s.setChallanCount(rs.getInt("challan_count"));
                semesters.add(s);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return semesters;
    }

    // Changes name and dates only (activation has its own methods)
    public boolean updateSemester(Semester semester) {
        String sql = "UPDATE semesters SET name = ?, start_date = ?, end_date = ? WHERE semester_id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, semester.getName());
            stmt.setDate(2, semester.getStartDate());
            stmt.setDate(3, semester.getEndDate());
            stmt.setInt(4, semester.getSemesterId());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Leaves the system with no active semester
    public boolean deactivateSemester(int semesterId) {
        String sql = "UPDATE semesters SET is_active = FALSE WHERE semester_id = ? AND is_active = TRUE";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, semesterId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Removes a semester that is not active and has no course offerings or fee
     * challans. Returns null on success, otherwise the reason it cannot be removed.
     */
    public String deleteSemester(int semesterId) {
        String usage = "SELECT s.is_active, " +
                "(SELECT COUNT(*) FROM course_allocations WHERE semester_id = s.semester_id) AS allocs, " +
                "(SELECT COUNT(*) FROM challans WHERE semester_id = s.semester_id) AS challans " +
                "FROM semesters s WHERE s.semester_id = ?";
        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(usage)) {
                stmt.setInt(1, semesterId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next())
                        return "Semester not found.";
                    if (rs.getBoolean("is_active"))
                        return "It is the active semester. Activate another semester (or deactivate this one) first.";
                    if (rs.getInt("allocs") > 0)
                        return "It has " + rs.getInt("allocs") + " course offering(s). Remove those teacher assignments first.";
                    if (rs.getInt("challans") > 0)
                        return "It has " + rs.getInt("challans") + " fee challan(s).";
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM semesters WHERE semester_id = ?")) {
                stmt.setInt(1, semesterId);
                return stmt.executeUpdate() > 0 ? null : "Semester not found.";
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "A database error occurred.";
        }
    }

    public boolean addSemester(Semester semester) {
        String sql = "INSERT INTO semesters (name, start_date, end_date, is_active) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, semester.getName());
            stmt.setDate(2, semester.getStartDate());
            stmt.setDate(3, semester.getEndDate());
            stmt.setBoolean(4, semester.isActive());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean activateSemester(int semesterId) {
        // Transaction to ensure only one active semester
        String disableAll = "UPDATE semesters SET is_active = FALSE";
        String enableOne = "UPDATE semesters SET is_active = TRUE WHERE semester_id = ?";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt1 = conn.prepareStatement(disableAll);
                    PreparedStatement stmt2 = conn.prepareStatement(enableOne)) {

                stmt1.executeUpdate();

                stmt2.setInt(1, semesterId);
                stmt2.executeUpdate();

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
