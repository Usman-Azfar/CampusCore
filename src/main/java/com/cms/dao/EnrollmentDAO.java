package com.cms.dao;

import com.cms.models.Enrollment;
import com.cms.models.Course;
import com.cms.models.User;
import com.cms.models.CourseAllocation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class EnrollmentDAO {

    /** Academic footprint of one student, for the admin's student list. */
    public static final class StudentSummary {
        public final List<String> currentCourses = new ArrayList<>(); // ENROLLED this (active) semester
        public int enrollmentRecords;  // all enrollment rows, any semester/status (grades and attendance hang off these)
        public int challans;
        public int pendingRequests;

        // Records that deleting the account would erase
        public boolean hasAcademicRecords() {
            return enrollmentRecords > 0 || challans > 0;
        }
    }

    /** Summaries for every student, keyed by student ID (students with nothing are absent). */
    public java.util.Map<Integer, StudentSummary> getStudentSummaries() {
        java.util.Map<Integer, StudentSummary> map = new java.util.HashMap<>();
        String current = "SELECT e.student_id, c.course_code FROM enrollments e " +
                "JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                "JOIN courses c ON ca.course_id = c.course_id " +
                "WHERE e.status = 'ENROLLED' AND ca.semester_id = " + ClassSemesterDAO.studentCurrentTermSql("e.student_id") +
                " ORDER BY c.course_code";
        String counts = "SELECT u.user_id, " +
                "(SELECT COUNT(*) FROM enrollments e WHERE e.student_id = u.user_id) AS records, " +
                "(SELECT COUNT(*) FROM challans ch WHERE ch.student_id = u.user_id) AS challans, " +
                "(SELECT COUNT(*) FROM course_requests r WHERE r.student_id = u.user_id AND r.status = 'PENDING') AS pending " +
                "FROM users u WHERE u.role = 'STUDENT'";
        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(counts);
                    ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    StudentSummary s = new StudentSummary();
                    s.enrollmentRecords = rs.getInt("records");
                    s.challans = rs.getInt("challans");
                    s.pendingRequests = rs.getInt("pending");
                    map.put(rs.getInt("user_id"), s);
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(current);
                    ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    StudentSummary s = map.computeIfAbsent(rs.getInt("student_id"), k -> new StudentSummary());
                    s.currentCourses.add(rs.getString("course_code"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    /** Outcome of a bulk enrollment. */
    public static final class BulkResult {
        public int enrolled;        // new enrollment records
        public int reactivated;     // earlier dropped/withdrawn records in this offering made ENROLLED again
        public int alreadyEnrolled; // skipped: already ENROLLED in this course
        public int invalid;         // skipped: not an active student
        public boolean ok = true;
    }

    /**
     * Enrolls many students into one offering (allocation) in a single transaction.
     * Students already ENROLLED in this course (any semester) are skipped; a student
     * who dropped this same offering is re-activated instead of duplicated. Each new
     * enrollment gets a grade row and the student's semester number for that semester.
     */
    public BulkResult enrollStudents(int allocationId, java.util.Collection<Integer> studentIds) {
        BulkResult result = new BulkResult();
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            int courseId, semesterId;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT course_id, semester_id FROM course_allocations WHERE allocation_id = ?")) {
                stmt.setInt(1, allocationId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        result.ok = false;
                        return result;
                    }
                    courseId = rs.getInt("course_id");
                    semesterId = rs.getInt("semester_id");
                }
            }

            for (Integer studentId : studentIds) {
                // Must be an active student
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT 1 FROM users WHERE user_id = ? AND role = 'STUDENT' AND is_active = TRUE")) {
                    stmt.setInt(1, studentId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            result.invalid++;
                            continue;
                        }
                    }
                }
                // Not already enrolled in this course
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT 1 FROM enrollments e JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                                "WHERE e.student_id = ? AND ca.course_id = ? AND e.status = 'ENROLLED' LIMIT 1")) {
                    stmt.setInt(1, studentId);
                    stmt.setInt(2, courseId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            result.alreadyEnrolled++;
                            continue;
                        }
                    }
                }
                // Re-activate an earlier record in this offering, or create a new one
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
                    result.reactivated++;
                } else {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO enrollments (student_id, allocation_id, status, semester_number) " +
                                    "SELECT ?, ?, 'ENROLLED', COALESCE(" + ClassSemesterDAO.studentSemesterNumberSql("?", "?") + ", " +
                                    "(SELECT MAX(e.semester_number) FROM enrollments e " +
                                    "JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                                    "WHERE e.student_id = ? AND ca.semester_id = ?))",
                            java.sql.Statement.RETURN_GENERATED_KEYS)) {
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
                    result.enrolled++;
                }
                // Every enrollment has a grade row; marks stay NULL (not entered) until the teacher enters them
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT IGNORE INTO grades (enrollment_id) VALUES (?)")) {
                    stmt.setInt(1, enrollmentId);
                    stmt.executeUpdate();
                }
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

    /**
     * Every enrollment record of a course (all semesters and statuses) with the
     * student's class and the offering's semester and teacher.
     */
    public List<Enrollment> getCourseEnrollmentsAllStatuses(int courseId) {
        List<Enrollment> list = new ArrayList<>();
        String sql = "SELECT e.*, u.username AS student_roll, p.full_name AS student_name, " +
                "s.semester_id, s.name AS semester_name, s.is_active, tp.full_name AS teacher_name" +
                AcademicDAO.affiliationColumns("u") +
                "FROM enrollments e " +
                "JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                "JOIN semesters s ON ca.semester_id = s.semester_id " +
                "JOIN users u ON e.student_id = u.user_id " +
                "LEFT JOIN profiles p ON u.user_id = p.user_id " +
                "LEFT JOIN profiles tp ON ca.teacher_id = tp.user_id " +
                AcademicDAO.affiliationJoins("u") +
                "WHERE ca.course_id = ? " +
                "ORDER BY s.is_active DESC, s.start_date DESC, (e.status = 'ENROLLED') DESC, u.username";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, courseId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Enrollment en = new Enrollment();
                    en.setEnrollmentId(rs.getInt("enrollment_id"));
                    en.setStudentId(rs.getInt("student_id"));
                    en.setAllocationId(rs.getInt("allocation_id"));
                    en.setStatus(rs.getString("status"));
                    en.setSemesterNumber(rs.getInt("semester_number"));

                    User student = new User();
                    student.setUserId(en.getStudentId());
                    student.setUsername(rs.getString("student_roll"));
                    student.setRole("STUDENT");
                    com.cms.models.Profile profile = new com.cms.models.Profile();
                    profile.setFullName(rs.getString("student_name"));
                    student.setProfile(profile);
                    AcademicDAO.readAffiliation(rs, student);
                    en.setStudent(student);

                    CourseAllocation ca = new CourseAllocation();
                    ca.setAllocationId(en.getAllocationId());
                    com.cms.models.Semester sem = new com.cms.models.Semester();
                    sem.setSemesterId(rs.getInt("semester_id"));
                    sem.setName(rs.getString("semester_name"));
                    sem.setActive(rs.getBoolean("is_active"));
                    ca.setSemester(sem);
                    User teacher = new User();
                    com.cms.models.Profile tp = new com.cms.models.Profile();
                    tp.setFullName(rs.getString("teacher_name"));
                    teacher.setProfile(tp);
                    ca.setTeacher(teacher);
                    en.setCourseAllocation(ca);

                    list.add(en);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // Drops one ENROLLED record that belongs to the given course (grades/attendance are kept)
    public boolean dropEnrollment(int enrollmentId, int courseId) {
        String sql = "UPDATE enrollments e JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                "SET e.status = 'DROPPED' WHERE e.enrollment_id = ? AND ca.course_id = ? AND e.status = 'ENROLLED'";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, enrollmentId);
            stmt.setInt(2, courseId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Get courses a student is enrolled in
    public List<Enrollment> getEnrollmentsByStudent(int studentId) {
        return getEnrollmentsByStudent(studentId, false);
    }

    // Current courses only: ENROLLED in the active semester (what can be dropped/withdrawn)
    public List<Enrollment> getActiveSemesterEnrollments(int studentId) {
        return getEnrollmentsByStudent(studentId, true);
    }

    private List<Enrollment> getEnrollmentsByStudent(int studentId, boolean activeSemesterOnly) {
        List<Enrollment> enrollments = new ArrayList<>();
        String sql = "SELECT e.*, ca.course_id, ca.teacher_id, c.course_code, c.course_name, c.credit_hours, " +
                "COALESCE(p.full_name, t.username) as teacher_name " +
                "FROM enrollments e " +
                "JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                "JOIN courses c ON ca.course_id = c.course_id " +
                "JOIN users t ON ca.teacher_id = t.user_id " +
                "LEFT JOIN profiles p ON ca.teacher_id = p.user_id " +
                "WHERE e.student_id = ? AND e.status = 'ENROLLED'" +
                (activeSemesterOnly ? " AND ca.semester_id = " + ClassSemesterDAO.studentCurrentTermSql("e.student_id") : "");

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Enrollment enrollment = new Enrollment();
                    enrollment.setEnrollmentId(rs.getInt("enrollment_id"));
                    enrollment.setStudentId(rs.getInt("student_id"));
                    enrollment.setAllocationId(rs.getInt("allocation_id"));
                    enrollment.setStatus(rs.getString("status"));
                    enrollment.setSemesterNumber(rs.getInt("semester_number"));

                    CourseAllocation ca = new CourseAllocation();
                    ca.setAllocationId(enrollment.getAllocationId());
                    ca.setCourseId(rs.getInt("course_id"));
                    ca.setTeacherId(rs.getInt("teacher_id"));
                    Course c = new Course();
                    c.setCourseId(rs.getInt("course_id"));
                    c.setCourseCode(rs.getString("course_code"));
                    c.setCourseName(rs.getString("course_name"));
                    c.setCreditHours(rs.getInt("credit_hours"));

                    User teacher = new User();
                    teacher.setUserId(ca.getTeacherId());
                    teacher.setUsername(rs.getString("teacher_name"));

                    ca.setCourse(c);
                    ca.setTeacher(teacher);
                    enrollment.setCourseAllocation(ca);

                    enrollments.add(enrollment);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return enrollments;
    }

    public List<Enrollment> getEnrollmentsByAllocationId(int allocationId) {
        List<Enrollment> enrollments = new ArrayList<>();
        String sql = "SELECT e.*, p.full_name as student_name, u.username as student_roll" + AcademicDAO.affiliationColumns("u") +
                "FROM enrollments e " +
                "JOIN users u ON e.student_id = u.user_id " +
                "JOIN profiles p ON u.user_id = p.user_id " +
                AcademicDAO.affiliationJoins("u") +
                "WHERE e.allocation_id = ? AND e.status = 'ENROLLED'";

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, allocationId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Enrollment enrollment = new Enrollment();
                    enrollment.setEnrollmentId(rs.getInt("enrollment_id"));
                    enrollment.setStudentId(rs.getInt("student_id"));
                    enrollment.setAllocationId(rs.getInt("allocation_id"));
                    enrollment.setStatus(rs.getString("status"));

                    User student = new User();
                    student.setUserId(rs.getInt("student_id"));
                    student.setUsername(rs.getString("student_roll")); // Set Roll No
                    // We might need profile name too, but Enrollment model mainly links to
                    // User/CourseAllocation
                    // Let's store name in a transient way or just rely on username for now,
                    // or better: Ensure Enrollment has navigation to Student User/Profile.
                    // For now, I'll set the username to Full Name for display or Roll No.
                    // Ideally, we should extend Enrollment model or use a DTO, but I'll shim it
                    // into Student User object.
                    student.setUsername(rs.getString("student_roll") + " - " + rs.getString("student_name"));

                    AcademicDAO.readAffiliation(rs, student);
                    enrollment.setStudent(student);
                    enrollments.add(enrollment);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return enrollments;
    }

    public List<Enrollment> getAllEnrollmentsByCourse(int courseId) {
        List<Enrollment> enrollments = new ArrayList<>();
        String sql = "SELECT e.*, p.full_name as student_name, u.username as student_roll, s.name as semester_name" + AcademicDAO.affiliationColumns("u") +
                "FROM enrollments e " +
                "JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                "JOIN users u ON e.student_id = u.user_id " +
                "JOIN profiles p ON u.user_id = p.user_id " +
                AcademicDAO.affiliationJoins("u") +
                "JOIN semesters s ON ca.semester_id = s.semester_id " +
                "WHERE ca.course_id = ? AND e.status = 'ENROLLED' " +
                "ORDER BY s.start_date DESC";

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, courseId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Enrollment enrollment = new Enrollment();
                    enrollment.setEnrollmentId(rs.getInt("enrollment_id"));
                    enrollment.setStudentId(rs.getInt("student_id"));
                    enrollment.setAllocationId(rs.getInt("allocation_id"));
                    enrollment.setStatus(rs.getString("status"));

                    User student = new User();
                    student.setUserId(rs.getInt("student_id"));
                    student.setUsername(rs.getString("student_roll") + " - " + rs.getString("student_name"));
                    AcademicDAO.readAffiliation(rs, student);
                    enrollment.setStudent(student);

                    CourseAllocation ca = new CourseAllocation();
                    com.cms.models.Semester sem = new com.cms.models.Semester();
                    sem.setName(rs.getString("semester_name"));
                    ca.setSemester(sem);
                    enrollment.setCourseAllocation(ca);

                    enrollments.add(enrollment);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return enrollments;
    }

    public boolean isEnrolledInCourse(int studentId, int courseId) {
        String sql = "SELECT 1 FROM enrollments e " +
                "JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                "WHERE e.student_id = ? AND ca.course_id = ? AND e.status = 'ENROLLED'";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            stmt.setInt(2, courseId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean updateEnrollmentStatus(int studentId, int courseId, String status) {
        // Find enrollment by student and course (via allocation)
        String sql = "UPDATE enrollments e " +
                "JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                "SET e.status = ? " +
                "WHERE e.student_id = ? AND ca.course_id = ?";

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, studentId);
            stmt.setInt(3, courseId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
