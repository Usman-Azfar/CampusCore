package com.cms.dao;

import com.cms.models.Announcement;
import com.cms.models.Course;
import com.cms.models.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AnnouncementDAO {

    // Get General + Course specific announcements for a student
    public List<Announcement> getAnnouncementsForStudent(int studentId) {
        List<Announcement> announcements = new ArrayList<>();
        // Logic: Get announcements where course_id is NULL (General) OR course_id is in
        // student's enrolled list
        String sql = "SELECT a.*, c.course_name, u.username as creator_name " +
                "FROM announcements a " +
                "LEFT JOIN courses c ON a.course_id = c.course_id " +
                "JOIN users u ON a.created_by = u.user_id " +
                "WHERE a.course_id IS NULL " +
                "OR a.course_id IN (" +
                "SELECT ca.course_id FROM enrollments e " +
                "JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                "WHERE e.student_id = ? AND e.status = 'ENROLLED'" +
                ") ORDER BY a.created_at DESC";

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Announcement ann = new Announcement();
                    ann.setAnnouncementId(rs.getInt("announcement_id"));
                    ann.setTitle(rs.getString("title"));
                    ann.setContent(rs.getString("content"));
                    ann.setCourseId(rs.getInt("course_id"));
                    ann.setCreatedAt(rs.getTimestamp("created_at"));

                    if (rs.getInt("course_id") != 0) {
                        Course c = new Course();
                        c.setCourseName(rs.getString("course_name"));
                        ann.setCourse(c);
                    }

                    User creator = new User();
                    creator.setUsername(rs.getString("creator_name"));
                    ann.setCreator(creator);

                    announcements.add(ann);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return announcements;
    }

    // Create a new announcement
    public boolean createAnnouncement(Announcement ann) {
        String sql = "INSERT INTO announcements (title, content, course_id, created_by) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, ann.getTitle());
            stmt.setString(2, ann.getContent());
            if (ann.getCourseId() > 0) {
                stmt.setInt(3, ann.getCourseId());
            } else {
                stmt.setNull(3, java.sql.Types.INTEGER);
            }
            stmt.setInt(4, ann.getCreatedBy());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Get announcements for a specific course
    public List<Announcement> getAnnouncementsByCourseId(int courseId) {
        List<Announcement> announcements = new ArrayList<>();
        String sql = "SELECT a.*, u.username as creator_name " +
                "FROM announcements a " +
                "JOIN users u ON a.created_by = u.user_id " +
                "WHERE a.course_id = ? " +
                "ORDER BY a.created_at DESC";

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, courseId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Announcement ann = new Announcement();
                    ann.setAnnouncementId(rs.getInt("announcement_id"));
                    ann.setTitle(rs.getString("title"));
                    ann.setContent(rs.getString("content"));
                    ann.setCourseId(rs.getInt("course_id"));
                    ann.setCreatedAt(rs.getTimestamp("created_at"));

                    User creator = new User();
                    creator.setUsername(rs.getString("creator_name"));
                    ann.setCreator(creator);

                    announcements.add(ann);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return announcements;
    }
}
