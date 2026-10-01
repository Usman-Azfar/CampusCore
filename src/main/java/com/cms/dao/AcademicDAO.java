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
        String sql = "SELECT cl.*, COUNT(u.user_id) AS student_count FROM classes cl " +
                "LEFT JOIN users u ON u.class_id = cl.class_id AND u.role = 'STUDENT' " +
                "GROUP BY cl.class_id ORDER BY cl.batch_year DESC, cl.degree, cl.program_name";
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

    public Result addClass(String degree, String programName, int batchYear) {
        String sql = "INSERT INTO classes (degree, program_name, batch_year) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, degree);
            stmt.setString(2, programName);
            stmt.setInt(3, batchYear);
            stmt.executeUpdate();
            return Result.ok();
        } catch (SQLException e) {
            if (isDuplicate(e))
                return Result.fail("Class " + AcademicClass.formatName(degree, programName, batchYear) + " already exists.");
            e.printStackTrace();
            return Result.fail("Could not add the class. Please try again.");
        }
    }

    public Result updateClass(int classId, String degree, String programName, int batchYear) {
        String sql = "UPDATE classes SET degree = ?, program_name = ?, batch_year = ? WHERE class_id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, degree);
            stmt.setString(2, programName);
            stmt.setInt(3, batchYear);
            stmt.setInt(4, classId);
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
        return delete("DELETE FROM classes WHERE class_id = ?", classId, "Class");
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

    private static boolean isDuplicate(SQLException e) {
        return e.getErrorCode() == 1062; // MySQL ER_DUP_ENTRY
    }
}
