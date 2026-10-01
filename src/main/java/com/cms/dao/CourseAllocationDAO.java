package com.cms.dao;

import com.cms.models.CourseAllocation;
import com.cms.models.User;
import com.cms.models.Course;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CourseAllocationDAO {

    // One offering per row with what the teacher's course list and dashboard show about it
    private static final String OVERVIEW_SELECT = "SELECT ca.allocation_id, ca.course_id, ca.teacher_id, ca.semester_id, " +
            "c.course_code, c.course_name, c.credit_hours, s.name AS semester_name, s.start_date, s.end_date, s.is_active, " +
            "(SELECT COUNT(*) FROM enrollments e WHERE e.allocation_id = ca.allocation_id AND e.status = 'ENROLLED') AS enrolled_count, " +
            "(SELECT COUNT(*) FROM lectures l WHERE l.allocation_id = ca.allocation_id) AS lecture_count, " +
            "(SELECT MAX(l.lecture_date) FROM lectures l WHERE l.allocation_id = ca.allocation_id) AS last_lecture, " +
            "(SELECT COUNT(*) FROM enrollments e JOIN grades g ON g.enrollment_id = e.enrollment_id WHERE e.allocation_id = ca.allocation_id " +
            "  AND e.status = 'ENROLLED' AND g.sessional_marks IS NOT NULL AND g.mid_marks IS NOT NULL AND g.final_marks IS NOT NULL) AS complete_count, " +
            "(SELECT COUNT(*) FROM enrollments e JOIN grades g ON g.enrollment_id = e.enrollment_id WHERE e.allocation_id = ca.allocation_id " +
            "  AND e.status = 'ENROLLED' AND g.is_published = TRUE) AS published_count, " +
            "(SELECT COUNT(*) FROM announcements a WHERE a.allocation_id = ca.allocation_id) AS announcement_count " +
            "FROM course_allocations ca " +
            "JOIN courses c ON c.course_id = ca.course_id " +
            "JOIN semesters s ON s.semester_id = ca.semester_id ";

    /** A teacher's offerings, newest term first, with students, lectures, grading and announcement counts. */
    public List<CourseAllocation> getTeachingOverview(int teacherId) {
        List<CourseAllocation> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(OVERVIEW_SELECT + "WHERE ca.teacher_id = ? ORDER BY s.start_date DESC, c.course_code")) {
            stmt.setInt(1, teacherId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    list.add(mapOverview(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** One offering with its term dates and counts (null if it does not exist). */
    public CourseAllocation getOverview(int allocationId) {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(OVERVIEW_SELECT + "WHERE ca.allocation_id = ?")) {
            stmt.setInt(1, allocationId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapOverview(rs) : null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static CourseAllocation mapOverview(ResultSet rs) throws SQLException {
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
        com.cms.models.Semester s = new com.cms.models.Semester();
        s.setSemesterId(rs.getInt("semester_id"));
        s.setName(rs.getString("semester_name"));
        s.setStartDate(rs.getDate("start_date"));
        s.setEndDate(rs.getDate("end_date"));
        s.setActive(rs.getBoolean("is_active"));
        ca.setSemester(s);
        ca.setEnrolledCount(rs.getInt("enrolled_count"));
        ca.setLectureCount(rs.getInt("lecture_count"));
        ca.setLastLectureDate(rs.getDate("last_lecture"));
        ca.setCompleteCount(rs.getInt("complete_count"));
        ca.setPublishedCount(rs.getInt("published_count"));
        ca.setAnnouncementCount(rs.getInt("announcement_count"));
        return ca;
    }

    public CourseAllocation getAllocationById(int allocationId) {
        CourseAllocation ca = null;
        String sql = "SELECT ca.*, c.course_code, c.course_name " +
                "FROM course_allocations ca " +
                "JOIN courses c ON ca.course_id = c.course_id " +
                "WHERE ca.allocation_id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, allocationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ca = new CourseAllocation();
                    ca.setAllocationId(rs.getInt("allocation_id"));
                    ca.setCourseId(rs.getInt("course_id"));
                    ca.setTeacherId(rs.getInt("teacher_id"));
                    ca.setSemesterId(rs.getInt("semester_id"));

                    Course course = new Course();
                    course.setCourseCode(rs.getString("course_code"));
                    course.setCourseName(rs.getString("course_name"));
                    ca.setCourse(course);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ca;
    }

    public boolean addAllocation(CourseAllocation allocation) {
        String sql = "INSERT INTO course_allocations (course_id, teacher_id, semester_id) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, allocation.getCourseId());
            stmt.setInt(2, allocation.getTeacherId());
            stmt.setInt(3, allocation.getSemesterId());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteAllocation(int allocationId) {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Delete Attendance for all students in this allocation
            String attSql = "DELETE a FROM attendance a JOIN enrollments e ON a.enrollment_id = e.enrollment_id " +
                    "WHERE e.allocation_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(attSql)) {
                stmt.setInt(1, allocationId);
                stmt.executeUpdate();
            }

            // 2. Delete Grades for all students in this allocation
            String gradeSql = "DELETE g FROM grades g JOIN enrollments e ON g.enrollment_id = e.enrollment_id " +
                    "WHERE e.allocation_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(gradeSql)) {
                stmt.setInt(1, allocationId);
                stmt.executeUpdate();
            }

            // 3. Delete Enrollments
            String enrollSql = "DELETE FROM enrollments WHERE allocation_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(enrollSql)) {
                stmt.setInt(1, allocationId);
                stmt.executeUpdate();
            }

            // 4. Delete the Allocation
            String allocSql = "DELETE FROM course_allocations WHERE allocation_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(allocSql)) {
                stmt.setInt(1, allocationId);
                int deleted = stmt.executeUpdate();
                if (deleted == 0)
                    throw new SQLException("Allocation not found");
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
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

    /**
     * Offerings with full details: course (+ department), semester (+ dates/active),
     * teacher (name, username, department) and enrollment counts. Pass a courseId to
     * limit to one course, or null for all. Active semester first, then newest.
     */
    public List<CourseAllocation> getAllocationsDetailed(Integer courseId) {
        List<CourseAllocation> allocations = new ArrayList<>();
        String sql = "SELECT ca.*, c.course_code, c.course_name, c.department_id AS course_dept_id, cd.name AS course_dept_name, " +
                "s.name AS semester_name, s.start_date, s.end_date, s.is_active, " +
                "u.username AS teacher_username, u.role AS teacher_role, p.full_name AS teacher_name, " +
                "(SELECT COUNT(*) FROM enrollments e WHERE e.allocation_id = ca.allocation_id AND e.status = 'ENROLLED') AS enrolled_count, " +
                "(SELECT COUNT(*) FROM enrollments e WHERE e.allocation_id = ca.allocation_id) AS enrollment_rows" +
                AcademicDAO.affiliationColumns("u") +
                "FROM course_allocations ca " +
                "JOIN courses c ON ca.course_id = c.course_id " +
                "LEFT JOIN departments cd ON c.department_id = cd.department_id " +
                "JOIN semesters s ON ca.semester_id = s.semester_id " +
                "JOIN users u ON ca.teacher_id = u.user_id " +
                "LEFT JOIN profiles p ON u.user_id = p.user_id " +
                AcademicDAO.affiliationJoins("u") +
                (courseId != null ? "WHERE ca.course_id = ? " : "") +
                "ORDER BY s.is_active DESC, s.start_date DESC, c.course_code";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (courseId != null)
                stmt.setInt(1, courseId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CourseAllocation ca = new CourseAllocation();
                    ca.setAllocationId(rs.getInt("allocation_id"));
                    ca.setCourseId(rs.getInt("course_id"));
                    ca.setTeacherId(rs.getInt("teacher_id"));
                    ca.setSemesterId(rs.getInt("semester_id"));
                    ca.setEnrolledCount(rs.getInt("enrolled_count"));
                    ca.setEnrollmentRows(rs.getInt("enrollment_rows"));

                    Course course = new Course();
                    course.setCourseId(ca.getCourseId());
                    course.setCourseCode(rs.getString("course_code"));
                    course.setCourseName(rs.getString("course_name"));
                    int cd = rs.getInt("course_dept_id");
                    course.setDepartmentId(rs.wasNull() ? null : cd);
                    course.setDepartmentName(rs.getString("course_dept_name"));
                    ca.setCourse(course);

                    com.cms.models.Semester sem = new com.cms.models.Semester();
                    sem.setSemesterId(ca.getSemesterId());
                    sem.setName(rs.getString("semester_name"));
                    sem.setStartDate(rs.getDate("start_date"));
                    sem.setEndDate(rs.getDate("end_date"));
                    sem.setActive(rs.getBoolean("is_active"));
                    ca.setSemester(sem);

                    User teacher = new User();
                    teacher.setUserId(ca.getTeacherId());
                    teacher.setUsername(rs.getString("teacher_username"));
                    teacher.setRole(rs.getString("teacher_role"));
                    com.cms.models.Profile profile = new com.cms.models.Profile();
                    profile.setFullName(rs.getString("teacher_name"));
                    teacher.setProfile(profile);
                    AcademicDAO.readAffiliation(rs, teacher);
                    ca.setTeacher(teacher);

                    allocations.add(ca);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return allocations;
    }

    public List<CourseAllocation> getAllAllocations() {
        List<CourseAllocation> allocations = new ArrayList<>();
        String sql = "SELECT ca.*, c.course_code, c.course_name, s.name as semester_name, p.full_name as teacher_name "
                +
                "FROM course_allocations ca " +
                "JOIN courses c ON ca.course_id = c.course_id " +
                "JOIN semesters s ON ca.semester_id = s.semester_id " +
                "JOIN profiles p ON ca.teacher_id = p.user_id " +
                "ORDER BY s.is_active DESC, s.start_date DESC";

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                CourseAllocation ca = new CourseAllocation();
                ca.setAllocationId(rs.getInt("allocation_id"));
                ca.setCourseId(rs.getInt("course_id"));
                ca.setTeacherId(rs.getInt("teacher_id"));
                ca.setSemesterId(rs.getInt("semester_id"));

                Course course = new Course();
                course.setCourseCode(rs.getString("course_code"));
                course.setCourseName(rs.getString("course_name"));
                ca.setCourse(course);

                com.cms.models.Semester sem = new com.cms.models.Semester();
                sem.setName(rs.getString("semester_name"));
                ca.setSemester(sem);

                User teacher = new User();
                teacher.setUsername(rs.getString("teacher_name"));
                ca.setTeacher(teacher);

                allocations.add(ca);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return allocations;
    }

    public List<CourseAllocation> getAllocationsByCourse(int courseId) {
        List<CourseAllocation> allocations = new ArrayList<>();
        String sql = "SELECT ca.*, c.course_code, c.course_name, s.name as semester_name, p.full_name as teacher_name "
                +
                "FROM course_allocations ca " +
                "JOIN courses c ON ca.course_id = c.course_id " +
                "JOIN semesters s ON ca.semester_id = s.semester_id " +
                "JOIN profiles p ON ca.teacher_id = p.user_id " +
                "WHERE ca.course_id = ? " +
                "ORDER BY s.is_active DESC, s.start_date DESC";

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, courseId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CourseAllocation ca = new CourseAllocation();
                    ca.setAllocationId(rs.getInt("allocation_id"));
                    ca.setCourseId(rs.getInt("course_id"));
                    ca.setTeacherId(rs.getInt("teacher_id"));
                    ca.setSemesterId(rs.getInt("semester_id"));

                    Course course = new Course();
                    course.setCourseCode(rs.getString("course_code"));
                    course.setCourseName(rs.getString("course_name"));
                    ca.setCourse(course);

                    com.cms.models.Semester sem = new com.cms.models.Semester();
                    sem.setName(rs.getString("semester_name"));
                    ca.setSemester(sem);

                    User teacher = new User();
                    teacher.setUsername(rs.getString("teacher_name"));
                    ca.setTeacher(teacher);

                    allocations.add(ca);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return allocations;
    }

    public CourseAllocation getAllocationByCourseAndSemester(int courseId, int semesterId) {
        CourseAllocation ca = null;
        String sql = "SELECT * FROM course_allocations WHERE course_id = ? AND semester_id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, courseId);
            stmt.setInt(2, semesterId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ca = new CourseAllocation();
                    ca.setAllocationId(rs.getInt("allocation_id"));
                    ca.setCourseId(rs.getInt("course_id"));
                    ca.setTeacherId(rs.getInt("teacher_id"));
                    ca.setSemesterId(rs.getInt("semester_id"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ca;
    }

    public boolean updateAllocation(CourseAllocation allocation) {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Update the first record found
            String updateSql = "UPDATE course_allocations SET teacher_id = ? WHERE course_id = ? AND semester_id = ? LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setInt(1, allocation.getTeacherId());
                stmt.setInt(2, allocation.getCourseId());
                stmt.setInt(3, allocation.getSemesterId());
                stmt.executeUpdate();
            }

            // 2. Delete any duplicates (anything beyond the one we just updated/kept)
            // Note: We use a subquery to find all but one ID to keep.
            String cleanupSql = "DELETE FROM course_allocations WHERE course_id = ? AND semester_id = ? AND allocation_id NOT IN ( "
                    +
                    "SELECT id FROM (SELECT MIN(allocation_id) as id FROM course_allocations WHERE course_id = ? AND semester_id = ?) as tmp)";
            try (PreparedStatement stmt = conn.prepareStatement(cleanupSql)) {
                stmt.setInt(1, allocation.getCourseId());
                stmt.setInt(2, allocation.getSemesterId());
                stmt.setInt(3, allocation.getCourseId());
                stmt.setInt(4, allocation.getSemesterId());
                stmt.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
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
}
