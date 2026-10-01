package com.cms.dao;

import com.cms.models.AcademicClass;
import com.cms.models.Course;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CourseDAO {

    private static final String BASE_SELECT = "SELECT c.*, d.name AS department_name FROM courses c " +
            "LEFT JOIN departments d ON c.department_id = d.department_id ";

    /**
     * All courses with department, classes and listing statistics (teacher in the
     * active semester, number of offerings, currently enrolled students).
     */
    public List<Course> getAllCourses() {
        List<Course> courses = new ArrayList<>();
        String sql = "SELECT c.*, d.name AS department_name, " +
                "(SELECT COUNT(*) FROM course_allocations ca WHERE ca.course_id = c.course_id) AS allocation_count, " +
                "(SELECT COUNT(*) FROM enrollments e JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                "   WHERE ca.course_id = c.course_id AND e.status = 'ENROLLED') AS enrolled_count, " +
                "(SELECT GROUP_CONCAT(COALESCE(p.full_name, u.username) SEPARATOR ', ') FROM course_allocations ca " +
                "   JOIN semesters s ON ca.semester_id = s.semester_id AND s.is_active = TRUE " +
                "   JOIN users u ON ca.teacher_id = u.user_id LEFT JOIN profiles p ON u.user_id = p.user_id " +
                "   WHERE ca.course_id = c.course_id) AS active_teachers " +
                "FROM courses c LEFT JOIN departments d ON c.department_id = d.department_id " +
                "ORDER BY c.course_code";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Course course = map(rs);
                course.setAllocationCount(rs.getInt("allocation_count"));
                course.setEnrolledCount(rs.getInt("enrolled_count"));
                course.setActiveTeachers(rs.getString("active_teachers"));
                courses.add(course);
            }
            attachClasses(conn, courses);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return courses;
    }

    /** Courses offered (allocated to a teacher) in the current term of the student's class. */
    public List<Course> getCoursesOfferedToStudent(int studentId) {
        List<Course> courses = new ArrayList<>();
        String sql = "SELECT DISTINCT c.*, d.name AS department_name FROM courses c " +
                "LEFT JOIN departments d ON c.department_id = d.department_id " +
                "JOIN course_allocations ca ON c.course_id = ca.course_id " +
                "WHERE ca.semester_id = " + ClassSemesterDAO.studentCurrentTermSql("?") + " ORDER BY c.course_code";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    courses.add(map(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return courses;
    }

    public Course getCourseById(int courseId) {
        String sql = BASE_SELECT + "WHERE c.course_id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, courseId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Course course = map(rs);
                    List<Course> one = new ArrayList<>();
                    one.add(course);
                    attachClasses(conn, one);
                    return course;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean codeExists(String code, Integer exceptCourseId) {
        String sql = "SELECT 1 FROM courses WHERE course_code = ?" + (exceptCourseId != null ? " AND course_id <> ?" : "");
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, code);
            if (exceptCourseId != null)
                stmt.setInt(2, exceptCourseId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return true; // fail safe: treat as taken
        }
    }

    // Adds a course and its classes in one transaction
    public boolean addCourse(Course course, Collection<Integer> classIds) {
        String sql = "INSERT INTO courses (course_code, course_name, credit_hours, department_id, description) VALUES (?, ?, ?, ?, ?)";
        return inTransaction(conn -> {
            int id;
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                fill(stmt, course);
                stmt.executeUpdate();
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (!keys.next())
                        throw new SQLException("No course ID returned");
                    id = keys.getInt(1);
                }
            }
            replaceClasses(conn, id, classIds);
        });
    }

    // Updates a course and replaces its classes in one transaction
    public boolean updateCourse(Course course, Collection<Integer> classIds) {
        String sql = "UPDATE courses SET course_code = ?, course_name = ?, credit_hours = ?, department_id = ?, description = ? " +
                "WHERE course_id = ?";
        return inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                fill(stmt, course);
                stmt.setInt(6, course.getCourseId());
                if (stmt.executeUpdate() == 0)
                    throw new SQLException("Course not found");
            }
            replaceClasses(conn, course.getCourseId(), classIds);
        });
    }

    /**
     * Deletes a course that has never been offered or requested. Returns null on
     * success, otherwise the reason it cannot be deleted.
     */
    public String deleteCourse(int courseId) {
        String usage = "SELECT (SELECT COUNT(*) FROM course_allocations WHERE course_id = ?) AS allocs, " +
                "(SELECT COUNT(*) FROM course_requests WHERE course_id = ?) AS reqs, " +
                "(SELECT COUNT(*) FROM announcements WHERE course_id = ?) AS anns";
        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(usage)) {
                stmt.setInt(1, courseId);
                stmt.setInt(2, courseId);
                stmt.setInt(3, courseId);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    if (rs.getInt("allocs") > 0)
                        return "It has teacher assignments. Remove them first (this also removes its enrollments).";
                    if (rs.getInt("reqs") > 0)
                        return "Students have add/drop requests for it.";
                    if (rs.getInt("anns") > 0)
                        return "It has announcements.";
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM courses WHERE course_id = ?")) {
                stmt.setInt(1, courseId);
                return stmt.executeUpdate() > 0 ? null : "Course not found.";
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "A database error occurred.";
        }
    }

    // ---------------- Helpers ----------------

    private interface TxWork {
        void run(Connection conn) throws SQLException;
    }

    private boolean inTransaction(TxWork work) {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);
            work.run(conn);
            conn.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            return false;
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

    private void fill(PreparedStatement stmt, Course course) throws SQLException {
        stmt.setString(1, course.getCourseCode());
        stmt.setString(2, course.getCourseName());
        stmt.setInt(3, course.getCreditHours());
        if (course.getDepartmentId() == null)
            stmt.setNull(4, java.sql.Types.INTEGER);
        else
            stmt.setInt(4, course.getDepartmentId());
        stmt.setString(5, course.getDescription());
    }

    private void replaceClasses(Connection conn, int courseId, Collection<Integer> classIds) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM course_classes WHERE course_id = ?")) {
            del.setInt(1, courseId);
            del.executeUpdate();
        }
        if (classIds == null || classIds.isEmpty())
            return;
        try (PreparedStatement ins = conn.prepareStatement("INSERT INTO course_classes (course_id, class_id) VALUES (?, ?)")) {
            for (Integer classId : classIds) {
                ins.setInt(1, courseId);
                ins.setInt(2, classId);
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    // Loads the classes of the given courses with one query
    private void attachClasses(Connection conn, List<Course> courses) throws SQLException {
        if (courses.isEmpty())
            return;
        Map<Integer, Course> byId = new HashMap<>();
        for (Course c : courses)
            byId.put(c.getCourseId(), c);
        String sql = "SELECT cc.course_id, cl.* FROM course_classes cc JOIN classes cl ON cc.class_id = cl.class_id " +
                "ORDER BY cl.batch_year DESC, cl.degree, cl.program_name";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Course c = byId.get(rs.getInt("course_id"));
                if (c == null)
                    continue;
                AcademicClass cl = new AcademicClass();
                cl.setClassId(rs.getInt("class_id"));
                cl.setDegree(rs.getString("degree"));
                cl.setProgramName(rs.getString("program_name"));
                cl.setBatchYear(rs.getInt("batch_year"));
                c.getClasses().add(cl);
            }
        }
    }

    private Course map(ResultSet rs) throws SQLException {
        Course course = new Course();
        course.setCourseId(rs.getInt("course_id"));
        course.setCourseCode(rs.getString("course_code"));
        course.setCourseName(rs.getString("course_name"));
        course.setCreditHours(rs.getInt("credit_hours"));
        course.setDescription(rs.getString("description"));
        int deptId = rs.getInt("department_id");
        course.setDepartmentId(rs.wasNull() ? null : deptId);
        course.setDepartmentName(rs.getString("department_name"));
        return course;
    }
}
