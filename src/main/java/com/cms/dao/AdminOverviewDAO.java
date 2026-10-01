package com.cms.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only figures for the admin dashboard: totals, work waiting for the admin, data that needs
 * fixing, and the current semester of each class. "Current" terms are the active ones (a class is
 * currently placed in them).
 */
public class AdminOverviewDAO {

    /** Totals and counts of waiting work. */
    public static final class Overview {
        public int activeStudents, activeTeachers, inactiveAccounts, classes, departments, courses;
        public int currentOfferings, currentEnrollments;
        public int pendingRequests, proofsToReview, openTickets;
        public int overdueChallans;
        public BigDecimal overdueAmount = BigDecimal.ZERO;
        // Fee challans of the current term(s)
        public int termChallans, termPaid;
        public BigDecimal termCollected = BigDecimal.ZERO, termOutstanding = BigDecimal.ZERO;
        public int studentsWithoutClass, teachersWithoutDepartment;
    }

    /** One class's current placement, e.g. BS Computer Science-2024 in Fall 2026 as its 3rd semester. */
    public static final class ClassRow {
        public int classId;
        public String className, termName;
        public int semesterNumber, students, offerings;
    }

    /** A course a class should take this term that has no teacher yet, or an offering with no students. */
    public static final class CourseGap {
        public int courseId, allocationId;
        public String courseCode, courseName, termName, className;
    }

    private static final String ACTIVE_TERM = "(SELECT semester_id FROM semesters WHERE is_active = TRUE)";
    private static final String CLASS_NAME = "CONCAT(cl.degree, ' ', cl.program_name, '-', cl.batch_year)";

    public Overview getOverview() {
        Overview o = new Overview();
        String sql = "SELECT " +
                "(SELECT COUNT(*) FROM users WHERE role = 'STUDENT' AND is_active = TRUE), " +
                "(SELECT COUNT(*) FROM users WHERE role = 'TEACHER' AND is_active = TRUE), " +
                "(SELECT COUNT(*) FROM users WHERE role <> 'ADMIN' AND is_active = FALSE), " +
                "(SELECT COUNT(*) FROM classes), (SELECT COUNT(*) FROM departments), (SELECT COUNT(*) FROM courses), " +
                "(SELECT COUNT(*) FROM course_allocations WHERE semester_id IN " + ACTIVE_TERM + "), " +
                "(SELECT COUNT(*) FROM enrollments e JOIN course_allocations ca ON ca.allocation_id = e.allocation_id " +
                "   WHERE e.status = 'ENROLLED' AND ca.semester_id IN " + ACTIVE_TERM + "), " +
                "(SELECT COUNT(*) FROM course_requests WHERE status = 'PENDING'), " +
                "(SELECT COUNT(*) FROM challans WHERE status = 'UNPAID' AND proof_status = 'SUBMITTED'), " +
                "(SELECT COUNT(*) FROM support_tickets WHERE status = 'OPEN'), " +
                "(SELECT COUNT(*) FROM challans WHERE status = 'UNPAID' AND due_date < CURDATE()), " +
                "(SELECT COALESCE(SUM(amount), 0) FROM challans WHERE status = 'UNPAID' AND due_date < CURDATE()), " +
                "(SELECT COUNT(*) FROM challans WHERE semester_id IN " + ACTIVE_TERM + "), " +
                "(SELECT COUNT(*) FROM challans WHERE semester_id IN " + ACTIVE_TERM + " AND status = 'PAID'), " +
                "(SELECT COALESCE(SUM(amount), 0) FROM challans WHERE semester_id IN " + ACTIVE_TERM + " AND status = 'PAID'), " +
                "(SELECT COALESCE(SUM(amount), 0) FROM challans WHERE semester_id IN " + ACTIVE_TERM + " AND status = 'UNPAID'), " +
                "(SELECT COUNT(*) FROM users WHERE role = 'STUDENT' AND is_active = TRUE AND class_id IS NULL), " +
                "(SELECT COUNT(*) FROM users WHERE role = 'TEACHER' AND is_active = TRUE AND department_id IS NULL)";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int i = 1;
                o.activeStudents = rs.getInt(i++);
                o.activeTeachers = rs.getInt(i++);
                o.inactiveAccounts = rs.getInt(i++);
                o.classes = rs.getInt(i++);
                o.departments = rs.getInt(i++);
                o.courses = rs.getInt(i++);
                o.currentOfferings = rs.getInt(i++);
                o.currentEnrollments = rs.getInt(i++);
                o.pendingRequests = rs.getInt(i++);
                o.proofsToReview = rs.getInt(i++);
                o.openTickets = rs.getInt(i++);
                o.overdueChallans = rs.getInt(i++);
                o.overdueAmount = rs.getBigDecimal(i++);
                o.termChallans = rs.getInt(i++);
                o.termPaid = rs.getInt(i++);
                o.termCollected = rs.getBigDecimal(i++);
                o.termOutstanding = rs.getBigDecimal(i++);
                o.studentsWithoutClass = rs.getInt(i++);
                o.teachersWithoutDepartment = rs.getInt(i++);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return o;
    }

    /** Every class with its current term and semester number (term null when it has none), by class name. */
    public List<ClassRow> getClasses() {
        List<ClassRow> list = new ArrayList<>();
        String sql = "SELECT cl.class_id, " + CLASS_NAME + " AS class_name, s.name AS term_name, cs.semester_number, " +
                "(SELECT COUNT(*) FROM users u WHERE u.class_id = cl.class_id AND u.role = 'STUDENT' AND u.is_active = TRUE) AS students, " +
                "(SELECT COUNT(*) FROM course_classes cc JOIN course_allocations ca ON ca.course_id = cc.course_id AND ca.semester_id = cs.semester_id " +
                "   WHERE cc.class_id = cl.class_id) AS offerings " +
                "FROM classes cl " +
                "LEFT JOIN class_semesters cs ON cs.class_id = cl.class_id AND cs.is_current = TRUE " +
                "LEFT JOIN semesters s ON s.semester_id = cs.semester_id " +
                "ORDER BY class_name";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                ClassRow r = new ClassRow();
                r.classId = rs.getInt("class_id");
                r.className = rs.getString("class_name");
                r.termName = rs.getString("term_name");
                r.semesterNumber = rs.getInt("semester_number");
                r.students = rs.getInt("students");
                r.offerings = rs.getInt("offerings");
                list.add(r);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** Courses offered to a class in its current term that have no teacher assigned in that term. */
    public List<CourseGap> getUnassignedCourses() {
        String sql = "SELECT c.course_id, 0 AS allocation_id, c.course_code, c.course_name, s.name AS term_name, " + CLASS_NAME + " AS class_name " +
                "FROM course_classes cc " +
                "JOIN courses c ON c.course_id = cc.course_id " +
                "JOIN classes cl ON cl.class_id = cc.class_id " +
                "JOIN class_semesters cs ON cs.class_id = cc.class_id AND cs.is_current = TRUE " +
                "JOIN semesters s ON s.semester_id = cs.semester_id " +
                "WHERE NOT EXISTS (SELECT 1 FROM course_allocations ca WHERE ca.course_id = cc.course_id AND ca.semester_id = cs.semester_id) " +
                "ORDER BY c.course_code, class_name";
        return gaps(sql);
    }

    /** Offerings in a current term with nobody enrolled. */
    public List<CourseGap> getEmptyOfferings() {
        String sql = "SELECT c.course_id, ca.allocation_id, c.course_code, c.course_name, s.name AS term_name, NULL AS class_name " +
                "FROM course_allocations ca " +
                "JOIN courses c ON c.course_id = ca.course_id " +
                "JOIN semesters s ON s.semester_id = ca.semester_id " +
                "WHERE s.is_active = TRUE AND NOT EXISTS (SELECT 1 FROM enrollments e WHERE e.allocation_id = ca.allocation_id AND e.status = 'ENROLLED') " +
                "ORDER BY c.course_code";
        return gaps(sql);
    }

    private List<CourseGap> gaps(String sql) {
        List<CourseGap> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                CourseGap g = new CourseGap();
                g.courseId = rs.getInt("course_id");
                g.allocationId = rs.getInt("allocation_id");
                g.courseCode = rs.getString("course_code");
                g.courseName = rs.getString("course_name");
                g.termName = rs.getString("term_name");
                g.className = rs.getString("class_name");
                list.add(g);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}
