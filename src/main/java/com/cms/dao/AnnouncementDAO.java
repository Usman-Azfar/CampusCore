package com.cms.dao;

import com.cms.models.Announcement;
import com.cms.models.Course;
import com.cms.models.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Announcements. General ones (no course offering) come from the admin and go to everyone,
 * students only or teachers only. Course ones belong to one course offering (allocation) and
 * are seen by the students currently ENROLLED in it.
 */
public class AnnouncementDAO {

    public static final int MAX_TITLE = 100;
    public static final int MAX_CONTENT = 5000;
    public static final Set<String> AUDIENCES = Set.of(Announcement.AUDIENCE_ALL, Announcement.AUDIENCE_STUDENTS,
            Announcement.AUDIENCE_TEACHERS);

    private static final String GENERAL = "a.allocation_id IS NULL AND a.course_id IS NULL";

    private static final String SELECT = "SELECT a.announcement_id, a.title, a.content, a.course_id, a.allocation_id, a.audience, " +
            "a.created_by, a.created_at, a.updated_at, c.course_code, c.course_name, s.name AS semester_name, " +
            "u.username AS creator_username, u.role AS creator_role, COALESCE(p.full_name, u.username) AS creator_name " +
            "FROM announcements a " +
            "LEFT JOIN course_allocations ca ON ca.allocation_id = a.allocation_id " +
            "LEFT JOIN courses c ON c.course_id = ca.course_id " +
            "LEFT JOIN semesters s ON s.semester_id = ca.semester_id " +
            "LEFT JOIN users u ON u.user_id = a.created_by " +
            "LEFT JOIN profiles p ON p.user_id = a.created_by ";

    private static final String NEWEST = " ORDER BY a.created_at DESC, a.announcement_id DESC";

    /** Text as typed, trimmed, with Windows line breaks as "\n" (so the length matches what the browser counted). */
    public static String cleanContent(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    /** A title and content as typed: the user-facing problem, or null when they can be saved. */
    public static String validate(String title, String content) {
        if (title == null || title.isEmpty())
            return "Please enter a title.";
        if (title.length() > MAX_TITLE)
            return "The title can be at most " + MAX_TITLE + " characters.";
        if (content == null || content.isEmpty())
            return "Please enter the announcement text.";
        if (content.length() > MAX_CONTENT)
            return "The announcement can be at most " + MAX_CONTENT + " characters.";
        return null;
    }

    // ---------------- Reading ----------------

    /**
     * What a student sees: general announcements for everyone or for students, and those of the
     * course offerings they are enrolled in. With allocationId, only that offering's (if enrolled).
     */
    public List<Announcement> getForStudent(int studentId, Integer allocationId) {
        String enrolled = "a.allocation_id IN (SELECT e.allocation_id FROM enrollments e WHERE e.student_id = ? AND e.status = 'ENROLLED')";
        String where = allocationId != null
                ? "WHERE a.allocation_id = ? AND " + enrolled
                : "WHERE (" + GENERAL + " AND a.audience IN ('ALL', 'STUDENTS')) OR " + enrolled;
        return query(SELECT + where + NEWEST, allocationId != null ? new Object[] { allocationId, studentId } : new Object[] { studentId });
    }

    /**
     * General announcements a role can see: the admin all of them, teachers those for everyone or
     * for teachers, students those for everyone or for students.
     */
    public List<Announcement> getGeneral(String role) {
        String audience = "ADMIN".equals(role) ? ""
                : "TEACHER".equals(role) ? " AND a.audience IN ('ALL', 'TEACHERS')" : " AND a.audience IN ('ALL', 'STUDENTS')";
        return query(SELECT + "WHERE " + GENERAL + audience + NEWEST);
    }

    /** The newest general announcements a role can see (for the dashboard). */
    public List<Announcement> getLatestGeneral(String role, int limit) {
        List<Announcement> all = getGeneral(role);
        return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
    }

    public List<Announcement> getByAllocation(int allocationId) {
        return query(SELECT + "WHERE a.allocation_id = ?" + NEWEST, allocationId);
    }

    public Announcement getById(int announcementId) {
        List<Announcement> list = query(SELECT + "WHERE a.announcement_id = ?", announcementId);
        return list.isEmpty() ? null : list.get(0);
    }

    // ---------------- Writing ----------------

    /** Creates a general announcement (allocationId null) or one for a course offering. */
    public boolean create(Announcement ann) {
        String sql = "INSERT INTO announcements (title, content, course_id, allocation_id, audience, created_by) " +
                "VALUES (?, ?, (SELECT course_id FROM course_allocations WHERE allocation_id = ?), ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ann.getTitle());
            stmt.setString(2, ann.getContent());
            setNullableInt(stmt, 3, ann.getAllocationId());
            setNullableInt(stmt, 4, ann.getAllocationId());
            stmt.setString(5, ann.isGeneral() ? ann.getAudience() : Announcement.AUDIENCE_ALL);
            stmt.setInt(6, ann.getCreatedBy());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Changes the title, text and (general announcements) audience. */
    public boolean update(int announcementId, String title, String content, String audience) {
        String sql = "UPDATE announcements SET title = ?, content = ?, " +
                "audience = IF(allocation_id IS NULL, ?, audience), updated_at = CURRENT_TIMESTAMP WHERE announcement_id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setString(2, content);
            stmt.setString(3, audience == null ? Announcement.AUDIENCE_ALL : audience);
            stmt.setInt(4, announcementId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean delete(int announcementId) {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement("DELETE FROM announcements WHERE announcement_id = ?")) {
            stmt.setInt(1, announcementId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ---------------- helpers ----------------

    private List<Announcement> query(String sql, Object... params) {
        List<Announcement> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++)
                stmt.setInt(i + 1, (Integer) params[i]);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    list.add(map(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private static Announcement map(ResultSet rs) throws SQLException {
        Announcement ann = new Announcement();
        ann.setAnnouncementId(rs.getInt("announcement_id"));
        ann.setTitle(rs.getString("title"));
        ann.setContent(rs.getString("content"));
        ann.setCourseId(rs.getInt("course_id"));
        int allocationId = rs.getInt("allocation_id");
        ann.setAllocationId(rs.wasNull() ? null : allocationId);
        ann.setAudience(rs.getString("audience"));
        ann.setCreatedBy(rs.getInt("created_by"));
        ann.setCreatedAt(rs.getTimestamp("created_at"));
        ann.setUpdatedAt(rs.getTimestamp("updated_at"));
        if (ann.getAllocationId() != null) {
            Course c = new Course();
            c.setCourseId(ann.getCourseId());
            c.setCourseCode(rs.getString("course_code"));
            c.setCourseName(rs.getString("course_name"));
            ann.setCourse(c);
            ann.setTermName(rs.getString("semester_name"));
        }
        User creator = new User();
        creator.setUserId(ann.getCreatedBy());
        creator.setUsername(rs.getString("creator_name"));
        creator.setRole(rs.getString("creator_role"));
        ann.setCreator(creator);
        return ann;
    }

    private static void setNullableInt(PreparedStatement stmt, int index, Integer value) throws SQLException {
        if (value == null)
            stmt.setNull(index, Types.INTEGER);
        else
            stmt.setInt(index, value);
    }
}
