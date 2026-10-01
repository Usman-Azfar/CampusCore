package com.cms.dao;

import com.cms.models.CourseRequest;
import com.cms.models.Course;
import com.cms.models.Profile;
import com.cms.models.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class CourseRequestDAO {

    public static final List<String> TYPES = List.of("ADD", "DROP", "WITHDRAW");

    // Columns shared by the admin listings (student, class, course, processing admin)
    private static final String ADMIN_SELECT = "SELECT cr.*, c.course_code, c.course_name, " +
            "u.username AS student_roll, p.full_name AS student_name, pa.full_name AS processed_by_name" +
            AcademicDAO.affiliationColumns("u") +
            "FROM course_requests cr " +
            "JOIN courses c ON cr.course_id = c.course_id " +
            "JOIN users u ON cr.student_id = u.user_id " +
            "LEFT JOIN profiles p ON u.user_id = p.user_id " +
            "LEFT JOIN profiles pa ON cr.processed_by = pa.user_id " +
            AcademicDAO.affiliationJoins("u");

    public boolean createRequest(CourseRequest request) {
        String sql = "INSERT INTO course_requests (student_id, course_id, type) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, request.getStudentId());
            stmt.setInt(2, request.getCourseId());
            stmt.setString(3, request.getType());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<CourseRequest> getRequestsByStudent(int studentId) {
        List<CourseRequest> requests = new ArrayList<>();
        String sql = "SELECT cr.*, c.course_code, c.course_name " +
                "FROM course_requests cr " +
                "JOIN courses c ON cr.course_id = c.course_id " +
                "WHERE cr.student_id = ? " +
                "ORDER BY cr.request_date DESC, cr.request_id DESC";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    requests.add(mapBasic(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return requests;
    }

    // Pending requests, oldest first (first come, first served)
    public List<CourseRequest> getPendingRequests() {
        return queryAdmin(ADMIN_SELECT + "WHERE cr.status = 'PENDING' ORDER BY cr.request_date ASC, cr.request_id ASC");
    }

    // Approved/rejected requests, most recently processed first
    public List<CourseRequest> getProcessedRequests() {
        return queryAdmin(ADMIN_SELECT + "WHERE cr.status <> 'PENDING' " +
                "ORDER BY COALESCE(cr.processed_at, cr.request_date) DESC, cr.request_id DESC");
    }

    // Any pending request (of any type) for this student and course
    public boolean hasPendingRequest(int studentId, int courseId) {
        String sql = "SELECT 1 FROM course_requests WHERE student_id = ? AND course_id = ? AND status = 'PENDING' LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            stmt.setInt(2, courseId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return true; // Fail safe: do not allow a possible duplicate
        }
    }

    public boolean rejectRequest(int requestId, int adminId) {
        String sql = "UPDATE course_requests SET status = 'REJECTED', processed_at = CURRENT_TIMESTAMP, processed_by = ? " +
                "WHERE request_id = ? AND status = 'PENDING'";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, adminId);
            stmt.setInt(2, requestId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Approves a pending request and applies it to the student's enrollment in the
     * active semester, all in one transaction. Returns null on success, otherwise a
     * user-facing reason (and nothing is changed; the request stays pending).
     */
    public String approveRequest(int requestId, int adminId) {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Lock the request so two admins cannot process it at the same time
            int studentId, courseId;
            String type;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT student_id, course_id, type, status FROM course_requests WHERE request_id = ? FOR UPDATE")) {
                stmt.setInt(1, requestId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next())
                        return rollback(conn, "Request not found.");
                    if (!"PENDING".equals(rs.getString("status")))
                        return rollback(conn, "This request has already been " + rs.getString("status").toLowerCase() + ".");
                    studentId = rs.getInt("student_id");
                    courseId = rs.getInt("course_id");
                    type = rs.getString("type");
                }
            }

            // 2. The course offering (allocation) in the current term of the student's class
            int allocationId = -1, semesterId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT ca.allocation_id, ca.semester_id FROM course_allocations ca " +
                            "WHERE ca.course_id = ? AND ca.semester_id = " + ClassSemesterDAO.studentCurrentTermSql("?"))) {
                stmt.setInt(1, courseId);
                stmt.setInt(2, studentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        allocationId = rs.getInt("allocation_id");
                        semesterId = rs.getInt("semester_id");
                    }
                }
            }
            if (allocationId == -1) {
                return rollback(conn, "This course is not offered (allocated to a teacher) in the current semester of the student's class, " +
                        "or the class has no current semester. Allocate it or set the class's semester first, or reject the request.");
            }

            // 3. Apply the change
            String error = "ADD".equals(type)
                    ? applyAdd(conn, studentId, courseId, allocationId, semesterId)
                    : applyDropOrWithdraw(conn, studentId, allocationId, "DROP".equals(type) ? "DROPPED" : "WITHDRAWN");
            if (error != null)
                return rollback(conn, error);

            // 4. Mark the request approved
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE course_requests SET status = 'APPROVED', processed_at = CURRENT_TIMESTAMP, processed_by = ? " +
                            "WHERE request_id = ?")) {
                stmt.setInt(1, adminId);
                stmt.setInt(2, requestId);
                stmt.executeUpdate();
            }

            conn.commit();
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return rollback(conn, "A database error occurred. Nothing was changed.");
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

    private String applyAdd(Connection conn, int studentId, int courseId, int allocationId, int semesterId)
            throws SQLException {
        // Not already enrolled in this course (in any semester)
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM enrollments e JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                        "WHERE e.student_id = ? AND ca.course_id = ? AND e.status = 'ENROLLED' LIMIT 1")) {
            stmt.setInt(1, studentId);
            stmt.setInt(2, courseId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    return "The student is already enrolled in this course.";
            }
        }

        // Re-activate an earlier dropped/withdrawn enrollment in the same offering
        int enrollmentId = -1;
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT enrollment_id FROM enrollments WHERE student_id = ? AND allocation_id = ? LIMIT 1")) {
            stmt.setInt(1, studentId);
            stmt.setInt(2, allocationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    enrollmentId = rs.getInt(1);
            }
        }

        if (enrollmentId != -1) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE enrollments SET status = 'ENROLLED' WHERE enrollment_id = ?")) {
                stmt.setInt(1, enrollmentId);
                stmt.executeUpdate();
            }
        } else {
            // Same semester number as the student's other courses this semester (NULL if none)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO enrollments (student_id, allocation_id, status, semester_number) " +
                            "SELECT ?, ?, 'ENROLLED', COALESCE(" + ClassSemesterDAO.studentSemesterNumberSql("?", "?") + ", " +
                            "(SELECT MAX(e.semester_number) FROM enrollments e " +
                            "JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                            "WHERE e.student_id = ? AND ca.semester_id = ?))",
                    Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, studentId);
                stmt.setInt(2, allocationId);
                stmt.setInt(3, studentId); // the class's semester number for this term
                stmt.setInt(4, semesterId);
                stmt.setInt(5, studentId); // fallback: the student's other courses this term
                stmt.setInt(6, semesterId);
                stmt.executeUpdate();
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (!keys.next())
                        throw new SQLException("No enrollment ID returned");
                    enrollmentId = keys.getInt(1);
                }
            }
        }

        // Every enrollment has a grade row; marks stay NULL (not entered) until the teacher enters them
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT IGNORE INTO grades (enrollment_id) VALUES (?)")) {
            stmt.setInt(1, enrollmentId);
            stmt.executeUpdate();
        }
        return null;
    }

    private String applyDropOrWithdraw(Connection conn, int studentId, int allocationId, String newStatus)
            throws SQLException {
        // Only the active-semester enrollment changes; earlier semesters are left alone
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE enrollments SET status = ? WHERE student_id = ? AND allocation_id = ? AND status = 'ENROLLED'")) {
            stmt.setString(1, newStatus);
            stmt.setInt(2, studentId);
            stmt.setInt(3, allocationId);
            if (stmt.executeUpdate() == 0)
                return "The student is not currently enrolled in this course this semester.";
        }
        return null;
    }

    private static String rollback(Connection conn, String reason) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return reason;
    }

    private List<CourseRequest> queryAdmin(String sql) {
        List<CourseRequest> requests = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                CourseRequest req = mapBasic(rs);
                req.setProcessedByName(rs.getString("processed_by_name"));

                User student = new User();
                student.setUserId(req.getStudentId());
                student.setUsername(rs.getString("student_roll"));
                student.setRole("STUDENT");
                Profile profile = new Profile();
                profile.setFullName(rs.getString("student_name"));
                student.setProfile(profile);
                AcademicDAO.readAffiliation(rs, student);
                req.setStudent(student);

                requests.add(req);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return requests;
    }

    private CourseRequest mapBasic(ResultSet rs) throws SQLException {
        CourseRequest req = new CourseRequest();
        req.setRequestId(rs.getInt("request_id"));
        req.setStudentId(rs.getInt("student_id"));
        req.setCourseId(rs.getInt("course_id"));
        req.setType(rs.getString("type"));
        req.setStatus(rs.getString("status"));
        req.setRequestDate(rs.getTimestamp("request_date"));
        req.setProcessedAt(rs.getTimestamp("processed_at"));

        Course c = new Course();
        c.setCourseId(req.getCourseId());
        c.setCourseCode(rs.getString("course_code"));
        c.setCourseName(rs.getString("course_name"));
        req.setCourse(c);
        return req;
    }
}
