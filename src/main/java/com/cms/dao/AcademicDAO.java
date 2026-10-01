package com.cms.dao;

import com.cms.models.AcademicClass;
import com.cms.models.Department;
import com.cms.models.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Classes (student batches, e.g. "BS Computer Science-2026") and Departments
 * (for teachers, e.g. "Computer Science").
 */
public class AcademicDAO {

    // Result of an add/delete, carrying a user-facing error when it fails
    public static final class Result {
        public final boolean ok;
        public final String error;

        private Result(boolean ok, String error) {
            this.ok = ok;
            this.error = error;
        }

        static Result ok() {
            return new Result(true, null);
        }

        static Result fail(String error) {
            return new Result(false, error);
        }
    }

    /*
     * Shared SQL for attaching a user's class/department to any query that selects
     * from users. Append affiliationColumns(alias) to the SELECT list and
     * affiliationJoins(alias) after the FROM/JOIN of that users table, then call
     * readAffiliation(rs, user).
     */
    public static String affiliationColumns(String userAlias) {
        return ", " + userAlias + ".class_id AS aff_class_id, " +
                "CONCAT(aff_cl.degree, ' ', aff_cl.program_name, '-', aff_cl.batch_year) AS aff_class_name, " +
                userAlias + ".department_id AS aff_department_id, aff_d.name AS aff_department_name ";
    }

    public static String affiliationJoins(String userAlias) {
        return " LEFT JOIN classes aff_cl ON " + userAlias + ".class_id = aff_cl.class_id " +
                "LEFT JOIN departments aff_d ON " + userAlias + ".department_id = aff_d.department_id ";
    }

    public static void readAffiliation(ResultSet rs, User user) throws SQLException {
        int classId = rs.getInt("aff_class_id");
        user.setClassId(rs.wasNull() ? null : classId);
        user.setClassName(rs.getString("aff_class_name"));
        int deptId = rs.getInt("aff_department_id");
        user.setDepartmentId(rs.wasNull() ? null : deptId);
        user.setDepartmentName(rs.getString("aff_department_name"));
    }

    // ---------------- Classes ----------------

    public List<AcademicClass> getAllClasses() {
        List<AcademicClass> classes = new ArrayList<>();
        String sql = "SELECT cl.*, d.name AS department_name, COUNT(u.user_id) AS student_count FROM classes cl " +
                "LEFT JOIN departments d ON d.department_id = cl.department_id " +
                "LEFT JOIN users u ON u.class_id = cl.class_id AND u.role = 'STUDENT' " +
                "GROUP BY cl.class_id, d.name ORDER BY cl.batch_year DESC, cl.degree, cl.program_name";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                AcademicClass c = new AcademicClass();
                c.setClassId(rs.getInt("class_id"));
                c.setDegree(rs.getString("degree"));
                c.setProgramName(rs.getString("program_name"));
                c.setBatchYear(rs.getInt("batch_year"));
                c.setStudentCount(rs.getInt("student_count"));
                int deptId = rs.getInt("department_id");
                c.setDepartmentId(rs.wasNull() ? null : deptId);
                c.setDepartmentName(rs.getString("department_name"));
                classes.add(c);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return classes;
    }

    public boolean classExists(int classId) {
        return exists("SELECT 1 FROM classes WHERE class_id = ?", classId);
    }

    public Result addClass(String degree, String programName, int batchYear, Integer departmentId) {
        String sql = "INSERT INTO classes (degree, program_name, batch_year, department_id) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, degree);
            stmt.setString(2, programName);
            stmt.setInt(3, batchYear);
            setNullableInt(stmt, 4, departmentId);
            stmt.executeUpdate();
            return Result.ok();
        } catch (SQLException e) {
            if (isDuplicate(e))
                return Result.fail("Class " + AcademicClass.formatName(degree, programName, batchYear) + " already exists.");
            e.printStackTrace();
            return Result.fail("Could not add the class. Please try again.");
        }
    }

    public Result updateClass(int classId, String degree, String programName, int batchYear, Integer departmentId) {
        String sql = "UPDATE classes SET degree = ?, program_name = ?, batch_year = ?, department_id = ? WHERE class_id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, degree);
            stmt.setString(2, programName);
            stmt.setInt(3, batchYear);
            setNullableInt(stmt, 4, departmentId);
            stmt.setInt(5, classId);
            return stmt.executeUpdate() > 0 ? Result.ok() : Result.fail("Class not found.");
        } catch (SQLException e) {
            if (isDuplicate(e))
                return Result.fail("Class " + AcademicClass.formatName(degree, programName, batchYear) + " already exists.");
            e.printStackTrace();
            return Result.fail("Could not update the class. Please try again.");
        }
    }

    public Result deleteClass(int classId) {
        if (exists("SELECT 1 FROM users WHERE class_id = ? LIMIT 1", classId))
            return Result.fail("This class still has students. Move them to another class first.");
        // Its semester history goes with it (class_semesters cannot cascade, see the schema)
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM class_semesters WHERE class_id = ?")) {
                    stmt.setInt(1, classId);
                    stmt.executeUpdate();
                }
                int deleted;
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM classes WHERE class_id = ?")) {
                    stmt.setInt(1, classId);
                    deleted = stmt.executeUpdate();
                }
                if (deleted == 0) {
                    conn.rollback();
                    return Result.fail("Class not found.");
                }
                ClassSemesterDAO.refreshActiveTerms(conn);
                conn.commit();
                return Result.ok();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Result.fail("Could not delete the class. It may still be in use.");
        }
    }

    // ---------------- Departments ----------------

    public List<Department> getAllDepartments() {
        List<Department> departments = new ArrayList<>();
        String sql = "SELECT d.*, COUNT(u.user_id) AS teacher_count FROM departments d " +
                "LEFT JOIN users u ON u.department_id = d.department_id AND u.role = 'TEACHER' " +
                "GROUP BY d.department_id ORDER BY d.name";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Department d = new Department();
                d.setDepartmentId(rs.getInt("department_id"));
                d.setName(rs.getString("name"));
                d.setTeacherCount(rs.getInt("teacher_count"));
                departments.add(d);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return departments;
    }

    public boolean departmentExists(int departmentId) {
        return exists("SELECT 1 FROM departments WHERE department_id = ?", departmentId);
    }

    public Result addDepartment(String name) {
        String sql = "INSERT INTO departments (name) VALUES (?)";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
            return Result.ok();
        } catch (SQLException e) {
            if (isDuplicate(e))
                return Result.fail("Department " + name + " already exists.");
            e.printStackTrace();
            return Result.fail("Could not add the department. Please try again.");
        }
    }

    public Result updateDepartment(int departmentId, String name) {
        String sql = "UPDATE departments SET name = ? WHERE department_id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setInt(2, departmentId);
            return stmt.executeUpdate() > 0 ? Result.ok() : Result.fail("Department not found.");
        } catch (SQLException e) {
            if (isDuplicate(e))
                return Result.fail("Department " + name + " already exists.");
            e.printStackTrace();
            return Result.fail("Could not update the department. Please try again.");
        }
    }

    public Result deleteDepartment(int departmentId) {
        if (exists("SELECT 1 FROM users WHERE department_id = ? LIMIT 1", departmentId))
            return Result.fail("This department still has teachers. Move them to another department first.");
        if (exists("SELECT 1 FROM courses WHERE department_id = ? LIMIT 1", departmentId))
            return Result.fail("This department still owns courses. Change their department first.");
        if (exists("SELECT 1 FROM classes WHERE department_id = ? LIMIT 1", departmentId))
            return Result.fail("Classes still belong to this department. Change their department first.");
        return delete("DELETE FROM departments WHERE department_id = ?", departmentId, "Department");
    }

    // ---------------- Helpers ----------------

    private boolean exists(String sql, int id) {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private Result delete(String sql, int id, String what) {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0 ? Result.ok() : Result.fail(what + " not found.");
        } catch (SQLException e) {
            e.printStackTrace();
            return Result.fail("Could not delete the " + what.toLowerCase() + ". It may still be in use.");
        }
    }

    private static void setNullableInt(PreparedStatement stmt, int index, Integer value) throws SQLException {
        if (value == null)
            stmt.setNull(index, java.sql.Types.INTEGER);
        else
            stmt.setInt(index, value);
    }

    private static boolean isDuplicate(SQLException e) {
        return e.getErrorCode() == 1062; // MySQL ER_DUP_ENTRY
    }
}
