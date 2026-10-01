package com.cms.dao;

import com.cms.models.ClassSemester;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Which semester number each class is in during each term, and each class's current term.
 * Every change also refreshes semesters.is_active ("at least one class is currently in it").
 */
public class ClassSemesterDAO {

    /** Highest semester number accepted, e.g. 8 for a four-year BS degree. */
    public static final int MAX_SEMESTER = 8;

    /**
     * SQL for the current term (semester_id) of a student's class, for use inside a query.
     * {@code studentIdSql} is a column or "?" giving the student's user_id.
     */
    public static String studentCurrentTermSql(String studentIdSql) {
        return "(SELECT cs_cur.semester_id FROM class_semesters cs_cur " +
                "JOIN users u_cur ON u_cur.class_id = cs_cur.class_id " +
                "WHERE u_cur.user_id = " + studentIdSql + " AND cs_cur.is_current = TRUE)";
    }

    /**
     * SQL for a student's semester number in a term (NULL when their class has no row for it).
     */
    public static String studentSemesterNumberSql(String studentIdSql, String semesterIdSql) {
        return "(SELECT cs_num.semester_number FROM class_semesters cs_num " +
                "JOIN users u_num ON u_num.class_id = cs_num.class_id " +
                "WHERE u_num.user_id = " + studentIdSql + " AND cs_num.semester_id = " + semesterIdSql + ")";
    }

    private static final String SELECT = "SELECT cs.*, " +
            "CONCAT(cl.degree, ' ', cl.program_name, '-', cl.batch_year) AS class_name, " +
            "cl.department_id, d.name AS department_name, " +
            "s.name AS term_name, s.start_date, s.end_date, " +
            "(SELECT COUNT(*) FROM users u WHERE u.class_id = cs.class_id AND u.role = 'STUDENT') AS student_count " +
            "FROM class_semesters cs " +
            "JOIN classes cl ON cl.class_id = cs.class_id " +
            "LEFT JOIN departments d ON d.department_id = cl.department_id " +
            "JOIN semesters s ON s.semester_id = cs.semester_id ";

    /** Current rows first, then newest term, then class name. */
    public List<ClassSemester> getAll() {
        List<ClassSemester> list = new ArrayList<>();
        String sql = SELECT + "ORDER BY cs.is_current DESC, s.start_date DESC, class_name";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next())
                list.add(map(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public ClassSemester getById(int classSemesterId) {
        return one(SELECT + "WHERE cs.class_semester_id = ?", classSemesterId);
    }

    /** The current term and semester number of a student's class, or null if there is none. */
    public ClassSemester getCurrentForStudent(int studentId) {
        return one(SELECT + "JOIN users su ON su.class_id = cs.class_id " +
                "WHERE su.user_id = ? AND cs.is_current = TRUE", studentId);
    }

    /** Outcome of placing several classes in a term. */
    public static final class AssignResult {
        public final List<String> saved = new ArrayList<>();
        public final List<String> skipped = new ArrayList<>();
    }

    /**
     * Places classes in a term. {@code number} null means "the class's next semester": one more
     * than its semester in the latest earlier term, or 1 if it has none. A class already placed in
     * this term keeps its row (its number is changed only when {@code number} is given).
     * {@code makeCurrent} makes this term the class's current one.
     */
    public AssignResult assign(List<Integer> classIds, int semesterId, Integer number, boolean makeCurrent) {
        AssignResult result = new AssignResult();
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (int classId : classIds) {
                    String name = className(conn, classId);
                    if (name == null) {
                        result.skipped.add("Class #" + classId + ": not found.");
                        continue;
                    }
                    Integer existingId = null, existingNumber = null;
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "SELECT class_semester_id, semester_number FROM class_semesters WHERE class_id = ? AND semester_id = ?")) {
                        stmt.setInt(1, classId);
                        stmt.setInt(2, semesterId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                existingId = rs.getInt(1);
                                existingNumber = rs.getInt(2);
                            }
                        }
                    }
                    int n = number != null ? number
                            : existingNumber != null ? existingNumber
                            : nextNumber(conn, classId, semesterId);
                    if (n > MAX_SEMESTER) {
                        result.skipped.add(name + ": already finished its " + ClassSemester.ordinal(MAX_SEMESTER) + " semester.");
                        continue;
                    }
                    String clash = numberUsedElsewhere(conn, classId, n, semesterId);
                    if (clash != null) {
                        result.skipped.add(name + ": its " + ClassSemester.ordinal(n) + " semester is already " + clash + ".");
                        continue;
                    }
                    if (makeCurrent)
                        clearCurrent(conn, classId);
                    if (existingId == null) {
                        try (PreparedStatement stmt = conn.prepareStatement(
                                "INSERT INTO class_semesters (class_id, semester_id, semester_number, is_current) VALUES (?, ?, ?, ?)")) {
                            stmt.setInt(1, classId);
                            stmt.setInt(2, semesterId);
                            stmt.setInt(3, n);
                            stmt.setBoolean(4, makeCurrent);
                            stmt.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement stmt = conn.prepareStatement(
                                "UPDATE class_semesters SET semester_number = ?" + (makeCurrent ? ", is_current = TRUE" : "") +
                                        " WHERE class_semester_id = ?")) {
                            stmt.setInt(1, n);
                            stmt.setInt(2, existingId);
                            stmt.executeUpdate();
                        }
                    }
                    syncEnrollments(conn, classId, semesterId, n);
                    result.saved.add(name + " (" + ClassSemester.ordinal(n) + (makeCurrent ? ", current" : "") + ")");
                }
                refreshActiveTerms(conn);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            result.saved.clear();
            result.skipped.add("A database error occurred. Nothing was saved.");
        }
        return result;
    }

    /** Makes this row its class's current term. Returns null on success, otherwise the reason. */
    public String makeCurrent(int classSemesterId) {
        return change(classSemesterId, (conn, row) -> {
            clearCurrent(conn, row.getClassId());
            update(conn, "UPDATE class_semesters SET is_current = TRUE WHERE class_semester_id = ?", classSemesterId);
            return null;
        });
    }

    /** The class is no longer in this term (e.g. the term is over and the next has not started). */
    public String endCurrent(int classSemesterId) {
        return change(classSemesterId, (conn, row) -> {
            if (!row.isCurrent())
                return "That is not the class's current semester.";
            update(conn, "UPDATE class_semesters SET is_current = FALSE WHERE class_semester_id = ?", classSemesterId);
            return null;
        });
    }

    public String changeNumber(int classSemesterId, int number) {
        if (number < 1 || number > MAX_SEMESTER)
            return "Semester number must be between 1 and " + MAX_SEMESTER + ".";
        return change(classSemesterId, (conn, row) -> {
            String clash = numberUsedElsewhere(conn, row.getClassId(), number, row.getSemesterId());
            if (clash != null)
                return row.getClassName() + "'s " + ClassSemester.ordinal(number) + " semester is already " + clash + ".";
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE class_semesters SET semester_number = ? WHERE class_semester_id = ?")) {
                stmt.setInt(1, number);
                stmt.setInt(2, classSemesterId);
                stmt.executeUpdate();
            }
            syncEnrollments(conn, row.getClassId(), row.getSemesterId(), number);
            return null;
        });
    }

    /**
     * The class's students' enrollments in that term take the class's semester number, so the
     * gradebook, transcript and attendance agree with Manage Semesters after a change.
     */
    static int syncEnrollments(Connection conn, int classId, int semesterId, int number) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE enrollments e JOIN users u ON u.user_id = e.student_id " +
                        "JOIN course_allocations ca ON ca.allocation_id = e.allocation_id " +
                        "SET e.semester_number = ? WHERE u.class_id = ? AND ca.semester_id = ?")) {
            stmt.setInt(1, number);
            stmt.setInt(2, classId);
            stmt.setInt(3, semesterId);
            return stmt.executeUpdate();
        }
    }

    /** Removes the row. Enrollments keep the semester number they were given. */
    public String delete(int classSemesterId) {
        return change(classSemesterId, (conn, row) -> {
            update(conn, "DELETE FROM class_semesters WHERE class_semester_id = ?", classSemesterId);
            return null;
        });
    }

    /** semesters.is_active = some class is currently in the term. */
    static void refreshActiveTerms(Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE semesters s SET s.is_active = EXISTS (SELECT 1 FROM class_semesters cs " +
                        "WHERE cs.semester_id = s.semester_id AND cs.is_current = TRUE)")) {
            stmt.executeUpdate();
        }
    }

    // ---------------- helpers ----------------

    private interface Change {
        String apply(Connection conn, ClassSemester row) throws SQLException;
    }

    // Runs a change to one row in a transaction and refreshes the terms' active flag
    private String change(int classSemesterId, Change change) {
        ClassSemester row = getById(classSemesterId);
        if (row == null)
            return "Class semester not found.";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String error = change.apply(conn, row);
                if (error != null) {
                    conn.rollback();
                    return error;
                }
                refreshActiveTerms(conn);
                conn.commit();
                return null;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "A database error occurred.";
        }
    }

    private static void clearCurrent(Connection conn, int classId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE class_semesters SET is_current = FALSE WHERE class_id = ? AND is_current = TRUE")) {
            stmt.setInt(1, classId);
            stmt.executeUpdate();
        }
    }

    private static void update(Connection conn, String sql, int id) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    // One more than the class's number in its latest term that starts before this one (1 if none)
    private static int nextNumber(Connection conn, int classId, int semesterId) throws SQLException {
        String sql = "SELECT cs.semester_number FROM class_semesters cs " +
                "JOIN semesters s ON s.semester_id = cs.semester_id " +
                "JOIN semesters target ON target.semester_id = ? " +
                "WHERE cs.class_id = ? AND s.start_date < target.start_date " +
                "ORDER BY s.start_date DESC LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, semesterId);
            stmt.setInt(2, classId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) + 1 : 1;
            }
        }
    }

    // The term (e.g. "Spring 2025") where the class already has this number, or null
    private static String numberUsedElsewhere(Connection conn, int classId, int number, int exceptSemesterId)
            throws SQLException {
        String sql = "SELECT s.name FROM class_semesters cs JOIN semesters s ON s.semester_id = cs.semester_id " +
                "WHERE cs.class_id = ? AND cs.semester_number = ? AND cs.semester_id <> ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, classId);
            stmt.setInt(2, number);
            stmt.setInt(3, exceptSemesterId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? "set to " + rs.getString(1) : null;
            }
        }
    }

    private static String className(Connection conn, int classId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT CONCAT(degree, ' ', program_name, '-', batch_year) FROM classes WHERE class_id = ?")) {
            stmt.setInt(1, classId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private ClassSemester one(String sql, int id) {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static ClassSemester map(ResultSet rs) throws SQLException {
        ClassSemester cs = new ClassSemester();
        cs.setClassSemesterId(rs.getInt("class_semester_id"));
        cs.setClassId(rs.getInt("class_id"));
        cs.setClassName(rs.getString("class_name"));
        int deptId = rs.getInt("department_id");
        cs.setDepartmentId(rs.wasNull() ? null : deptId);
        cs.setDepartmentName(rs.getString("department_name"));
        cs.setSemesterId(rs.getInt("semester_id"));
        cs.setTermName(rs.getString("term_name"));
        cs.setTermStart(rs.getDate("start_date"));
        cs.setTermEnd(rs.getDate("end_date"));
        cs.setSemesterNumber(rs.getInt("semester_number"));
        cs.setCurrent(rs.getBoolean("is_current"));
        cs.setStudentCount(rs.getInt("student_count"));
        return cs;
    }
}
