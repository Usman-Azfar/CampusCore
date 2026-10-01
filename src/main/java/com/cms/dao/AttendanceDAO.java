package com.cms.dao;

import com.cms.models.Attendance;
import com.cms.models.AttendanceSummary;
import com.cms.models.Course;
import com.cms.models.CourseAttendance;
import com.cms.models.CourseAllocation;
import com.cms.models.Enrollment;
import com.cms.models.Lecture;
import com.cms.models.Semester;
import com.cms.models.User;
import com.cms.util.AttendanceRules;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lectures and attendance. A lecture belongs to a course offering (allocation); each student's
 * status in it is one attendance row. Lecture numbers follow date order and are not stored.
 */
public class AttendanceDAO {

    public static final Set<String> STATUSES = Set.of("Present", "Absent", "Leave");

    // Position of lecture l in date order within its offering (ties: the one added first comes first)
    private static final String NUMBER_SQL = "(SELECT COUNT(*) FROM lectures l2 WHERE l2.allocation_id = l.allocation_id " +
            "AND (l2.lecture_date < l.lecture_date OR (l2.lecture_date = l.lecture_date AND l2.lecture_id <= l.lecture_id)))";

    private static final String LECTURE_SELECT = "SELECT l.*, " + NUMBER_SQL + " AS lecture_number, " +
            "(SELECT COUNT(*) FROM attendance a WHERE a.lecture_id = l.lecture_id AND a.status = 'Present') AS present_count, " +
            "(SELECT COUNT(*) FROM attendance a WHERE a.lecture_id = l.lecture_id AND a.status = 'Absent') AS absent_count, " +
            "(SELECT COUNT(*) FROM attendance a WHERE a.lecture_id = l.lecture_id AND a.status = 'Leave') AS leave_count, " +
            "COALESCE(cp.full_name, cu.username) AS created_by_name, COALESCE(up.full_name, uu.username) AS updated_by_name " +
            "FROM lectures l " +
            "LEFT JOIN users cu ON cu.user_id = l.created_by LEFT JOIN profiles cp ON cp.user_id = l.created_by " +
            "LEFT JOIN users uu ON uu.user_id = l.updated_by LEFT JOIN profiles up ON up.user_id = l.updated_by ";

    private static final String OFFERING_SELECT = "SELECT ca.allocation_id, ca.course_id, ca.teacher_id, ca.semester_id, " +
            "c.course_code, c.course_name, s.name AS semester_name, s.start_date, s.end_date, s.is_active, " +
            "(SELECT COUNT(*) FROM enrollments e WHERE e.allocation_id = ca.allocation_id AND e.status = 'ENROLLED') AS enrolled_count, " +
            "(SELECT COUNT(*) FROM lectures l WHERE l.allocation_id = ca.allocation_id) AS lecture_count, " +
            "(SELECT MAX(l.lecture_date) FROM lectures l WHERE l.allocation_id = ca.allocation_id) AS last_lecture " +
            "FROM course_allocations ca " +
            "JOIN courses c ON c.course_id = ca.course_id " +
            "JOIN semesters s ON s.semester_id = ca.semester_id ";

    // ---------------- Course offerings ----------------

    /** A teacher's course offerings, newest term first. */
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

    // ---------------- Lectures ----------------

    /** All lectures of an offering in date order, with Present/Absent/Leave counts. */
    public List<Lecture> getLectures(int allocationId) {
        List<Lecture> list = new ArrayList<>();
        String sql = LECTURE_SELECT + "WHERE l.allocation_id = ? ORDER BY l.lecture_date, l.lecture_id";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, allocationId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    list.add(mapLecture(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public Lecture getLecture(int lectureId) {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(LECTURE_SELECT + "WHERE l.lecture_id = ?")) {
            stmt.setInt(1, lectureId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapLecture(rs) : null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Status of each student in a lecture: enrollment id -> Present/Absent/Leave. */
    public Map<Integer, String> getStatuses(int lectureId) {
        Map<Integer, String> map = new HashMap<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement("SELECT enrollment_id, status FROM attendance WHERE lecture_id = ?")) {
            stmt.setInt(1, lectureId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    map.put(rs.getInt(1), rs.getString(2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    /** Outcome of saving a lecture's attendance. */
    public static final class SaveResult {
        public String error;     // user-facing reason when nothing was saved
        public int lectureId;
        public Integer existingLectureId; // set when the date is already marked
    }

    /**
     * Saves attendance. {@code lectureId} null creates a lecture on {@code date}; otherwise the
     * statuses of that lecture are replaced for the given students (others are left as they are).
     * A new lecture on a date that already has one is refused unless {@code extraLecture}.
     * The offering row is locked for the duration, so two submissions cannot both create a lecture
     * for the same date.
     */
    public SaveResult save(int allocationId, Integer lectureId, Date date, String topic, boolean extraLecture,
            Map<Integer, String> statuses, int teacherId) {
        SaveResult result = new SaveResult();
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement lock = conn.prepareStatement(
                        "SELECT allocation_id FROM course_allocations WHERE allocation_id = ? FOR UPDATE")) {
                    lock.setInt(1, allocationId);
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            result.error = "Course offering not found.";
                            return result;
                        }
                    }
                }

                if (lectureId == null) {
                    List<Integer> sameDay = lectureIdsOn(conn, allocationId, date);
                    if (!sameDay.isEmpty() && !extraLecture) {
                        conn.rollback();
                        result.existingLectureId = sameDay.get(0);
                        result.error = "Attendance for " + AttendanceRules.format(date.toLocalDate())
                                + " is already marked. Open it to make changes, or tick \"Another lecture on this date\".";
                        return result;
                    }
                    if (countLectures(conn, allocationId) >= AttendanceRules.MAX_LECTURES) {
                        conn.rollback();
                        result.error = "This course already has " + AttendanceRules.MAX_LECTURES + " lectures, the most allowed.";
                        return result;
                    }
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO lectures (allocation_id, lecture_date, topic, created_by) VALUES (?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        stmt.setInt(1, allocationId);
                        stmt.setDate(2, date);
                        stmt.setString(3, topic);
                        stmt.setInt(4, teacherId);
                        stmt.executeUpdate();
                        try (ResultSet keys = stmt.getGeneratedKeys()) {
                            if (!keys.next())
                                throw new SQLException("No lecture ID returned");
                            result.lectureId = keys.getInt(1);
                        }
                    }
                } else {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE lectures SET topic = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP " +
                                    "WHERE lecture_id = ? AND allocation_id = ?")) {
                        stmt.setString(1, topic);
                        stmt.setInt(2, teacherId);
                        stmt.setInt(3, lectureId);
                        stmt.setInt(4, allocationId);
                        if (stmt.executeUpdate() == 0) {
                            conn.rollback();
                            result.error = "Lecture not found.";
                            return result;
                        }
                    }
                    result.lectureId = lectureId;
                }

                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO attendance (lecture_id, enrollment_id, status) VALUES (?, ?, ?) AS new " +
                                "ON DUPLICATE KEY UPDATE status = new.status")) {
                    for (Map.Entry<Integer, String> e : statuses.entrySet()) {
                        stmt.setInt(1, result.lectureId);
                        stmt.setInt(2, e.getKey());
                        stmt.setString(3, e.getValue());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            result.error = "A database error occurred. Nothing was saved.";
        }
        return result;
    }

    /** Deletes a lecture and its attendance. Returns null on success, otherwise the reason. */
    public String deleteLecture(int lectureId, int allocationId) {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement("DELETE FROM lectures WHERE lecture_id = ? AND allocation_id = ?")) {
            stmt.setInt(1, lectureId);
            stmt.setInt(2, allocationId);
            return stmt.executeUpdate() > 0 ? null : "Lecture not found.";
        } catch (SQLException e) {
            e.printStackTrace();
            return "A database error occurred.";
        }
    }

    // ---------------- Summaries ----------------

    /** Totals for every student currently enrolled in the offering, by roll number. */
    public List<AttendanceSummary> getSummaries(int allocationId) {
        List<AttendanceSummary> list = new ArrayList<>();
        String sql = "SELECT e.enrollment_id, e.student_id, u.username, p.full_name, " +
                "CONCAT(cl.degree, ' ', cl.program_name, '-', cl.batch_year) AS class_name, " +
                "SUM(a.status = 'Present') AS present, SUM(a.status = 'Absent') AS absent, SUM(a.status = 'Leave') AS on_leave " +
                "FROM enrollments e JOIN users u ON u.user_id = e.student_id " +
                "LEFT JOIN profiles p ON p.user_id = u.user_id LEFT JOIN classes cl ON cl.class_id = u.class_id " +
                "LEFT JOIN attendance a ON a.enrollment_id = e.enrollment_id " +
                "WHERE e.allocation_id = ? AND e.status = 'ENROLLED' " +
                "GROUP BY e.enrollment_id, e.student_id, u.username, p.full_name, class_name ORDER BY u.username";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, allocationId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    AttendanceSummary s = new AttendanceSummary();
                    s.setEnrollmentId(rs.getInt("enrollment_id"));
                    s.setStudentId(rs.getInt("student_id"));
                    s.setRollNo(rs.getString("username"));
                    s.setName(rs.getString("full_name"));
                    s.setClassName(rs.getString("class_name"));
                    s.setPresent(rs.getInt("present"));
                    s.setAbsent(rs.getInt("absent"));
                    s.setLeave(rs.getInt("on_leave"));
                    list.add(s);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ---------------- Student view ----------------

    /**
     * A student's courses (currently ENROLLED) with term, the class's semester number, lectures held
     * so far and the student's Present/Absent/Leave counts. Newest term first, then by course code.
     */
    public List<CourseAttendance> getStudentCourses(int studentId) {
        List<CourseAttendance> list = new ArrayList<>();
        String sql = "SELECT e.enrollment_id, e.allocation_id, e.semester_number, ca.teacher_id, c.course_code, c.course_name, " +
                "COALESCE(tp.full_name, tu.username) AS teacher_name, s.semester_id, s.name AS semester_name, s.start_date, s.end_date, " +
                "(SELECT COUNT(*) FROM lectures l WHERE l.allocation_id = e.allocation_id) AS lectures_held, " +
                "(SELECT COUNT(*) FROM attendance a WHERE a.enrollment_id = e.enrollment_id AND a.status = 'Present') AS present, " +
                "(SELECT COUNT(*) FROM attendance a WHERE a.enrollment_id = e.enrollment_id AND a.status = 'Absent') AS absent, " +
                "(SELECT COUNT(*) FROM attendance a WHERE a.enrollment_id = e.enrollment_id AND a.status = 'Leave') AS on_leave " +
                "FROM enrollments e " +
                "JOIN course_allocations ca ON ca.allocation_id = e.allocation_id " +
                "JOIN courses c ON c.course_id = ca.course_id " +
                "JOIN semesters s ON s.semester_id = ca.semester_id " +
                "JOIN users tu ON tu.user_id = ca.teacher_id LEFT JOIN profiles tp ON tp.user_id = ca.teacher_id " +
                "WHERE e.student_id = ? AND e.status = 'ENROLLED' " +
                "ORDER BY s.start_date DESC, s.semester_id DESC, c.course_code";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CourseAttendance ca = new CourseAttendance();
                    ca.setEnrollmentId(rs.getInt("enrollment_id"));
                    ca.setAllocationId(rs.getInt("allocation_id"));
                    ca.setSemesterNumber(rs.getInt("semester_number"));
                    ca.setCourseCode(rs.getString("course_code"));
                    ca.setCourseName(rs.getString("course_name"));
                    ca.setTeacherName(rs.getString("teacher_name"));
                    ca.setTeacherId(rs.getInt("teacher_id"));
                    ca.setTermId(rs.getInt("semester_id"));
                    ca.setTermName(rs.getString("semester_name"));
                    ca.setTermStart(rs.getDate("start_date"));
                    ca.setTermEnd(rs.getDate("end_date"));
                    ca.setLecturesHeld(rs.getInt("lectures_held"));
                    ca.setPresent(rs.getInt("present"));
                    ca.setAbsent(rs.getInt("absent"));
                    ca.setLeave(rs.getInt("on_leave"));
                    list.add(ca);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** Every lecture of the enrollment's course with this student's status (null = not marked). */
    public List<Attendance> getAttendanceByEnrollment(int enrollmentId) {
        List<Attendance> list = new ArrayList<>();
        String sql = "SELECT l.lecture_id, l.lecture_date, l.topic, " + NUMBER_SQL + " AS lecture_number, " +
                "a.attendance_id, a.status FROM enrollments e " +
                "JOIN lectures l ON l.allocation_id = e.allocation_id " +
                "LEFT JOIN attendance a ON a.lecture_id = l.lecture_id AND a.enrollment_id = e.enrollment_id " +
                "WHERE e.enrollment_id = ? ORDER BY l.lecture_date, l.lecture_id";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, enrollmentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Attendance att = new Attendance();
                    att.setAttendanceId(rs.getInt("attendance_id"));
                    att.setLectureId(rs.getInt("lecture_id"));
                    att.setEnrollmentId(enrollmentId);
                    att.setLectureNumber(rs.getInt("lecture_number"));
                    att.setDate(rs.getDate("lecture_date"));
                    att.setTopic(rs.getString("topic"));
                    att.setStatus(rs.getString("status"));
                    list.add(att);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ---------------- helpers ----------------

    /** Lectures of the offering held on the date, in the order they were added. */
    public List<Integer> lectureIdsOn(int allocationId, Date date) {
        try (Connection conn = DBConnection.getConnection()) {
            return lectureIdsOn(conn, allocationId, date);
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private static List<Integer> lectureIdsOn(Connection conn, int allocationId, Date date) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT lecture_id FROM lectures WHERE allocation_id = ? AND lecture_date = ? ORDER BY lecture_id")) {
            stmt.setInt(1, allocationId);
            stmt.setDate(2, date);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    private static int countLectures(Connection conn, int allocationId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM lectures WHERE allocation_id = ?")) {
            stmt.setInt(1, allocationId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
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
        ca.setCourse(c);
        Semester s = new Semester();
        s.setSemesterId(rs.getInt("semester_id"));
        s.setName(rs.getString("semester_name"));
        s.setStartDate(rs.getDate("start_date"));
        s.setEndDate(rs.getDate("end_date"));
        s.setActive(rs.getBoolean("is_active"));
        ca.setSemester(s);
        ca.setEnrolledCount(rs.getInt("enrolled_count"));
        ca.setLectureCount(rs.getInt("lecture_count"));
        ca.setLastLectureDate(rs.getDate("last_lecture"));
        return ca;
    }

    private static Lecture mapLecture(ResultSet rs) throws SQLException {
        Lecture l = new Lecture();
        l.setLectureId(rs.getInt("lecture_id"));
        l.setAllocationId(rs.getInt("allocation_id"));
        l.setNumber(rs.getInt("lecture_number"));
        l.setDate(rs.getDate("lecture_date"));
        l.setTopic(rs.getString("topic"));
        l.setPresentCount(rs.getInt("present_count"));
        l.setAbsentCount(rs.getInt("absent_count"));
        l.setLeaveCount(rs.getInt("leave_count"));
        l.setCreatedAt(rs.getTimestamp("created_at"));
        l.setCreatedByName(rs.getString("created_by_name"));
        l.setUpdatedAt(rs.getTimestamp("updated_at"));
        l.setUpdatedByName(rs.getString("updated_by_name"));
        return l;
    }
}
