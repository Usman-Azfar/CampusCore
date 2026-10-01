package com.cms.dao;

import com.cms.models.Course;
import com.cms.models.CourseAllocation;
import com.cms.models.Enrollment;
import com.cms.models.Grade;
import com.cms.models.Profile;
import com.cms.models.Semester;
import com.cms.models.User;
import com.cms.util.GradeRules;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Marks and grades. Each enrollment has one grades row; a mark is NULL until it is entered,
 * and the letter is set only when all three marks are (see GradeRules).
 */
public class GradeDAO {

    private static final String COMPLETE = "g.sessional_marks IS NOT NULL AND g.mid_marks IS NOT NULL AND g.final_marks IS NOT NULL";

    private static final String OFFERING_SELECT = "SELECT ca.allocation_id, ca.course_id, ca.teacher_id, ca.semester_id, " +
            "c.course_code, c.course_name, c.credit_hours, s.name AS semester_name, s.start_date, s.end_date, s.is_active, " +
            "(SELECT COUNT(*) FROM enrollments e WHERE e.allocation_id = ca.allocation_id AND e.status = 'ENROLLED') AS enrolled_count, " +
            "(SELECT COUNT(*) FROM enrollments e JOIN grades g ON g.enrollment_id = e.enrollment_id " +
            "  WHERE e.allocation_id = ca.allocation_id AND e.status = 'ENROLLED' AND " + COMPLETE + ") AS complete_count, " +
            "(SELECT COUNT(*) FROM enrollments e JOIN grades g ON g.enrollment_id = e.enrollment_id " +
            "  WHERE e.allocation_id = ca.allocation_id AND e.status = 'ENROLLED' AND g.is_published = TRUE) AS published_count " +
            "FROM course_allocations ca " +
            "JOIN courses c ON c.course_id = ca.course_id " +
            "JOIN semesters s ON s.semester_id = ca.semester_id ";

    // ---------------- Teacher ----------------

    /** A teacher's course offerings, newest term first, with grading progress. */
    public List<CourseAllocation> getOfferingsByTeacher(int teacherId) {
        List<CourseAllocation> list = new ArrayList<>();
        String sql = OFFERING_SELECT + "WHERE ca.teacher_id = ? ORDER BY s.start_date DESC, c.course_code";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, teacherId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    list.add(mapOffering(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public CourseAllocation getOffering(int allocationId) {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(OFFERING_SELECT + "WHERE ca.allocation_id = ?")) {
            stmt.setInt(1, allocationId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapOffering(rs) : null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** The grade sheet of an offering: one row per student currently ENROLLED, by roll number. */
    public List<Grade> getSheet(int allocationId) {
        List<Grade> grades = new ArrayList<>();
        String sql = "SELECT e.enrollment_id, u.username AS roll_no, p.full_name, " +
                "g.grade_id, g.sessional_marks, g.mid_marks, g.final_marks, g.grade_letter, g.is_published, g.updated_at, " +
                "COALESCE(up.full_name, uu.username) AS updated_by_name" + AcademicDAO.affiliationColumns("u") +
                "FROM enrollments e " +
                "JOIN users u ON u.user_id = e.student_id " +
                "LEFT JOIN profiles p ON p.user_id = u.user_id " +
                AcademicDAO.affiliationJoins("u") +
                "LEFT JOIN grades g ON g.enrollment_id = e.enrollment_id " +
                "LEFT JOIN users uu ON uu.user_id = g.updated_by LEFT JOIN profiles up ON up.user_id = g.updated_by " +
                "WHERE e.allocation_id = ? AND e.status = 'ENROLLED' " +
                "ORDER BY u.username";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, allocationId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Grade g = mapMarks(rs);
                    g.setUpdatedAt(rs.getTimestamp("updated_at"));
                    g.setUpdatedByName(rs.getString("updated_by_name"));

                    User student = new User();
                    student.setUsername(rs.getString("roll_no"));
                    student.setRole("STUDENT");
                    Profile profile = new Profile();
                    profile.setFullName(rs.getString("full_name"));
                    student.setProfile(profile);
                    AcademicDAO.readAffiliation(rs, student);
                    Enrollment enrollment = new Enrollment();
                    enrollment.setEnrollmentId(g.getEnrollmentId());
                    enrollment.setStudent(student);
                    g.setEnrollment(enrollment);
                    grades.add(g);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return grades;
    }

    /** Outcome of saving a grade sheet. */
    public static final class SaveResult {
        public String error;   // user-facing reason when nothing was saved
        public int changed;    // students whose marks or publish setting changed
        public int skipped;    // submitted students no longer enrolled (ignored)
    }

    /**
     * Saves the submitted rows of one offering in one transaction. Rows for students not currently
     * enrolled in it are skipped; unchanged rows are not written, so "last updated" stays accurate.
     * The letter is worked out here from the marks, never taken from the form.
     */
    public SaveResult saveSheet(int allocationId, List<Grade> rows, int teacherId) {
        SaveResult result = new SaveResult();
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // Lock the offering so two saves at once apply one after the other
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT allocation_id FROM course_allocations WHERE allocation_id = ? FOR UPDATE")) {
                stmt.setInt(1, allocationId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        result.error = "This course offering no longer exists.";
                        return result;
                    }
                }
            }

            Map<Integer, Grade> current = new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT e.enrollment_id, g.grade_id, g.sessional_marks, g.mid_marks, g.final_marks, g.grade_letter, g.is_published " +
                            "FROM enrollments e LEFT JOIN grades g ON g.enrollment_id = e.enrollment_id " +
                            "WHERE e.allocation_id = ? AND e.status = 'ENROLLED'")) {
                stmt.setInt(1, allocationId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Grade g = mapMarks(rs);
                        current.put(g.getEnrollmentId(), g);
                    }
                }
            }

            String sql = "INSERT INTO grades (enrollment_id, sessional_marks, mid_marks, final_marks, grade_letter, is_published, updated_by, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                    "ON DUPLICATE KEY UPDATE sessional_marks = ?, mid_marks = ?, final_marks = ?, grade_letter = ?, " +
                    "is_published = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Grade row : rows) {
                    Grade before = current.get(row.getEnrollmentId());
                    if (before == null) {
                        result.skipped++;
                        continue;
                    }
                    String letter = GradeRules.letter(row.getSessionalMarks(), row.getMidMarks(), row.getFinalMarks());
                    boolean same = before.getGradeId() != 0
                            && Objects.equals(before.getSessionalMarks(), row.getSessionalMarks())
                            && Objects.equals(before.getMidMarks(), row.getMidMarks())
                            && Objects.equals(before.getFinalMarks(), row.getFinalMarks())
                            && Objects.equals(before.getGradeLetter(), letter)
                            && before.isPublished() == row.isPublished();
                    if (same)
                        continue;
                    stmt.setInt(1, row.getEnrollmentId());
                    for (int offset : new int[] { 1, 7 }) {
                        setMark(stmt, offset + 1, row.getSessionalMarks());
                        setMark(stmt, offset + 2, row.getMidMarks());
                        setMark(stmt, offset + 3, row.getFinalMarks());
                        stmt.setString(offset + 4, letter);
                        stmt.setBoolean(offset + 5, row.isPublished());
                        stmt.setInt(offset + 6, teacherId);
                    }
                    stmt.executeUpdate();
                    result.changed++;
                }
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            rollback(conn);
            result.error = "A database error occurred. Please try again.";
            result.changed = 0;
        } finally {
            close(conn);
        }
        return result;
    }

    // ---------------- Student ----------------

    /**
     * A student's results, newest term first: courses they are enrolled in, and courses they
     * withdrew from (status WITHDRAWN, shown as "W"; dropped courses are not listed). Marks of
     * unpublished results are left out, so they cannot be shown by mistake.
     */
    public List<Grade> getGradesByStudent(int studentId) {
        return studentGrades(studentId, false);
    }

    /**
     * What the transcript lists, oldest term first: published results with all three marks, and
     * withdrawn courses ("W", no grade points).
     */
    public List<Grade> getTranscriptGrades(int studentId) {
        return studentGrades(studentId, true);
    }

    private List<Grade> studentGrades(int studentId, boolean transcript) {
        List<Grade> grades = new ArrayList<>();
        String sql = "SELECT e.enrollment_id, e.status, e.semester_number, c.course_id, c.course_code, c.course_name, c.credit_hours, " +
                "s.semester_id, s.name AS semester_name, s.start_date, s.end_date, " +
                "COALESCE(tp.full_name, t.username) AS teacher_name, " +
                "g.grade_id, g.sessional_marks, g.mid_marks, g.final_marks, g.grade_letter, COALESCE(g.is_published, FALSE) AS is_published " +
                "FROM enrollments e " +
                "JOIN course_allocations ca ON ca.allocation_id = e.allocation_id " +
                "JOIN courses c ON c.course_id = ca.course_id " +
                "JOIN semesters s ON s.semester_id = ca.semester_id " +
                "JOIN users t ON t.user_id = ca.teacher_id " +
                "LEFT JOIN profiles tp ON tp.user_id = ca.teacher_id " +
                "LEFT JOIN grades g ON g.enrollment_id = e.enrollment_id " +
                "WHERE e.student_id = ? AND " +
                (transcript ? "((e.status = 'ENROLLED' AND g.is_published = TRUE AND " + COMPLETE + ") OR e.status = 'WITHDRAWN') " +
                        "ORDER BY s.start_date, s.semester_id, e.semester_number, c.course_code"
                        : "e.status IN ('ENROLLED', 'WITHDRAWN') ORDER BY s.start_date DESC, s.semester_id DESC, e.semester_number DESC, c.course_code");
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Grade g = mapMarks(rs);
                    boolean withdrawn = "WITHDRAWN".equals(rs.getString("status"));
                    if (withdrawn || !g.isPublished()) {
                        g.setSessionalMarks(null);
                        g.setMidMarks(null);
                        g.setFinalMarks(null);
                        g.setGradeLetter(null);
                        g.setPublished(false);
                    } else {
                        // The letter always follows the marks (a stored letter could be missing or stale)
                        g.setGradeLetter(GradeRules.letter(g.getSessionalMarks(), g.getMidMarks(), g.getFinalMarks()));
                    }

                    Semester sem = new Semester();
                    sem.setSemesterId(rs.getInt("semester_id"));
                    sem.setName(rs.getString("semester_name"));
                    sem.setStartDate(rs.getDate("start_date"));
                    sem.setEndDate(rs.getDate("end_date"));
                    Course c = new Course();
                    c.setCourseId(rs.getInt("course_id"));
                    c.setCourseCode(rs.getString("course_code"));
                    c.setCourseName(rs.getString("course_name"));
                    c.setCreditHours(rs.getInt("credit_hours"));
                    User teacher = new User();
                    teacher.setUsername(rs.getString("teacher_name"));

                    CourseAllocation ca = new CourseAllocation();
                    ca.setSemesterId(sem.getSemesterId());
                    ca.setSemester(sem);
                    ca.setCourse(c);
                    ca.setTeacher(teacher);
                    Enrollment enrollment = new Enrollment();
                    enrollment.setEnrollmentId(g.getEnrollmentId());
                    enrollment.setStatus(rs.getString("status"));
                    enrollment.setSemesterNumber(rs.getInt("semester_number"));
                    enrollment.setCourseAllocation(ca);
                    g.setEnrollment(enrollment);
                    grades.add(g);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return grades;
    }

    // ---------------- helpers ----------------

    // enrollment_id, grade_id and the marks columns of a grades row (grade_id 0 when there is none)
    private static Grade mapMarks(ResultSet rs) throws SQLException {
        Grade g = new Grade();
        g.setEnrollmentId(rs.getInt("enrollment_id"));
        g.setGradeId(rs.getInt("grade_id"));
        g.setSessionalMarks(mark(rs, "sessional_marks"));
        g.setMidMarks(mark(rs, "mid_marks"));
        g.setFinalMarks(mark(rs, "final_marks"));
        g.setGradeLetter(rs.getString("grade_letter"));
        g.setPublished(rs.getBoolean("is_published"));
        return g;
    }

    private static Double mark(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static void setMark(PreparedStatement stmt, int index, Double value) throws SQLException {
        if (value == null)
            stmt.setNull(index, Types.DOUBLE);
        else
            stmt.setDouble(index, value);
    }

    private static CourseAllocation mapOffering(ResultSet rs) throws SQLException {
        CourseAllocation ca = new CourseAllocation();
        ca.setAllocationId(rs.getInt("allocation_id"));
        ca.setCourseId(rs.getInt("course_id"));
        ca.setTeacherId(rs.getInt("teacher_id"));
        ca.setSemesterId(rs.getInt("semester_id"));
        Course c = new Course();
        c.setCourseId(rs.getInt("course_id"));
        c.setCourseCode(rs.getString("course_code"));
        c.setCourseName(rs.getString("course_name"));
        c.setCreditHours(rs.getInt("credit_hours"));
        ca.setCourse(c);
        Semester s = new Semester();
        s.setSemesterId(rs.getInt("semester_id"));
        s.setName(rs.getString("semester_name"));
        s.setStartDate(rs.getDate("start_date"));
        s.setEndDate(rs.getDate("end_date"));
        s.setActive(rs.getBoolean("is_active"));
        ca.setSemester(s);
        ca.setEnrolledCount(rs.getInt("enrolled_count"));
        ca.setCompleteCount(rs.getInt("complete_count"));
        ca.setPublishedCount(rs.getInt("published_count"));
        return ca;
    }

    private static void rollback(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private static void close(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
