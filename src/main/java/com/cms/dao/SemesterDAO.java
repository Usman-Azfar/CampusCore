package com.cms.dao;

import com.cms.models.Semester;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Terms, e.g. "Fall 2024". A term is active while at least one class is currently in it
 * (see ClassSemesterDAO); several terms can be active at the same time.
 */
public class SemesterDAO {

    public List<Semester> getAllSemesters() {
        List<Semester> semesters = new ArrayList<>();
        String sql = "SELECT s.*, " +
                "(SELECT COUNT(*) FROM course_allocations ca WHERE ca.semester_id = s.semester_id) AS allocation_count, " +
                "(SELECT COUNT(*) FROM challans ch WHERE ch.semester_id = s.semester_id) AS challan_count, " +
                "(SELECT COUNT(*) FROM class_semesters cs WHERE cs.semester_id = s.semester_id) AS class_count, " +
                "(SELECT COUNT(*) FROM class_semesters cs WHERE cs.semester_id = s.semester_id AND cs.is_current = TRUE) AS current_class_count " +
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
                s.setClassCount(rs.getInt("class_count"));
                s.setCurrentClassCount(rs.getInt("current_class_count"));
                semesters.add(s);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return semesters;
    }

    // Changes name and dates only (whether a term is active depends on its classes)
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

    /**
     * Removes a term that no class has been placed in and that has no course offerings or fee
     * challans. Returns null on success, otherwise the reason it cannot be removed.
     */
    public String deleteSemester(int semesterId) {
        String usage = "SELECT " +
                "(SELECT COUNT(*) FROM class_semesters WHERE semester_id = s.semester_id) AS classes, " +
                "(SELECT COUNT(*) FROM course_allocations WHERE semester_id = s.semester_id) AS allocs, " +
                "(SELECT COUNT(*) FROM challans WHERE semester_id = s.semester_id) AS challans " +
                "FROM semesters s WHERE s.semester_id = ?";
        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(usage)) {
                stmt.setInt(1, semesterId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next())
                        return "Semester not found.";
                    if (rs.getInt("classes") > 0)
                        return rs.getInt("classes") + " class(es) are placed in it. Remove them from it in Class Semesters first.";
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

    // New terms are inactive until a class is placed in them as its current term
    public boolean addSemester(Semester semester) {
        String sql = "INSERT INTO semesters (name, start_date, end_date, is_active) VALUES (?, ?, ?, FALSE)";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, semester.getName());
            stmt.setDate(2, semester.getStartDate());
            stmt.setDate(3, semester.getEndDate());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
