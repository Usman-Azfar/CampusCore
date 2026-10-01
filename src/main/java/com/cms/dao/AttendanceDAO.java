package com.cms.dao;

import com.cms.models.Attendance;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AttendanceDAO {

    public List<Attendance> getAttendanceByEnrollment(int enrollmentId) {
        List<Attendance> attendanceList = new ArrayList<>();
        String sql = "SELECT * FROM attendance WHERE enrollment_id = ?";

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, enrollmentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Attendance att = new Attendance();
                    att.setAttendanceId(rs.getInt("attendance_id"));
                    att.setEnrollmentId(rs.getInt("enrollment_id"));
                    att.setLectureNumber(rs.getInt("lecture_number"));
                    att.setDate(rs.getDate("date"));
                    att.setStatus(rs.getString("status"));
                    attendanceList.add(att);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return attendanceList;
    }

    public boolean addAttendance(Attendance attendance) {
        String sql = "INSERT INTO attendance (enrollment_id, lecture_number, date, status) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, attendance.getEnrollmentId());
            stmt.setInt(2, attendance.getLectureNumber());
            stmt.setDate(3, attendance.getDate());
            stmt.setString(4, attendance.getStatus());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
