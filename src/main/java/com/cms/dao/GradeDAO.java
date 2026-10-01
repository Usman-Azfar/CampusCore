package com.cms.dao;

import com.cms.models.Grade;
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

public class GradeDAO {

    public List<Grade> getGradesByStudent(int studentId) {
        List<Grade> grades = new ArrayList<>();
        String sql = "SELECT g.*, c.course_code, c.course_name, c.credit_hours, p.full_name as teacher_name, s.name as semester_name, s.semester_id "
                +
                "FROM grades g " +
                "JOIN enrollments e ON g.enrollment_id = e.enrollment_id " +
                "JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                "JOIN courses c ON ca.course_id = c.course_id " +
                "JOIN semesters s ON ca.semester_id = s.semester_id " +
                "JOIN profiles p ON ca.teacher_id = p.user_id " +
                "WHERE e.student_id = ? AND e.status = 'ENROLLED' " +
                "ORDER BY s.start_date ASC, c.course_code ASC";

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Grade grade = new Grade();
                    grade.setGradeId(rs.getInt("grade_id"));
                    grade.setEnrollmentId(rs.getInt("enrollment_id"));
                    grade.setSessionalMarks(rs.getDouble("sessional_marks"));
                    grade.setMidMarks(rs.getDouble("mid_marks"));
                    grade.setFinalMarks(rs.getDouble("final_marks"));
                    grade.setTotalMarks(rs.getDouble("total_marks"));
                    grade.setGradeLetter(rs.getString("grade_letter"));
                    grade.setPublished(rs.getBoolean("is_published"));

                    Enrollment enrollment = new Enrollment();
                    CourseAllocation ca = new CourseAllocation();

                    com.cms.models.Semester sem = new com.cms.models.Semester();
                    sem.setSemesterId(rs.getInt("semester_id"));
                    sem.setName(rs.getString("semester_name"));
                    ca.setSemester(sem);

                    Course c = new Course();
                    c.setCourseCode(rs.getString("course_code"));
                    c.setCourseName(rs.getString("course_name"));
                    c.setCreditHours(rs.getInt("credit_hours")); // Important for GPA

                    User teacher = new User();
                    teacher.setUsername(rs.getString("teacher_name"));

                    ca.setCourse(c);
                    ca.setTeacher(teacher);
                    enrollment.setCourseAllocation(ca);
                    grade.setEnrollment(enrollment);

                    grades.add(grade);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return grades;
    }

    public List<Grade> getGradesByAllocation(int allocationId) {
        List<Grade> grades = new ArrayList<>();
        String sql = "SELECT e.enrollment_id, u.username as student_roll, p.full_name as student_name, " +
                "g.grade_id, g.sessional_marks, g.mid_marks, g.final_marks, g.total_marks, g.grade_letter, g.is_published" + AcademicDAO.affiliationColumns("u")
                +
                "FROM enrollments e " +
                "JOIN users u ON e.student_id = u.user_id " +
                "JOIN profiles p ON u.user_id = p.user_id " +
                AcademicDAO.affiliationJoins("u") +
                "LEFT JOIN grades g ON e.enrollment_id = g.enrollment_id " +
                "WHERE e.allocation_id = ? AND e.status = 'ENROLLED'";

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, allocationId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Grade grade = new Grade();
                    grade.setEnrollmentId(rs.getInt("enrollment_id"));
                    grade.setGradeId(rs.getInt("grade_id"));
                    grade.setSessionalMarks(rs.getDouble("sessional_marks"));
                    grade.setMidMarks(rs.getDouble("mid_marks"));
                    grade.setFinalMarks(rs.getDouble("final_marks"));
                    grade.setTotalMarks(rs.getDouble("total_marks"));
                    grade.setGradeLetter(rs.getString("grade_letter"));
                    grade.setPublished(rs.getBoolean("is_published"));

                    Enrollment enrollment = new Enrollment();
                    enrollment.setEnrollmentId(rs.getInt("enrollment_id"));
                    User student = new User();
                    student.setUsername(rs.getString("student_roll") + " - " + rs.getString("student_name"));
                    AcademicDAO.readAffiliation(rs, student);
                    enrollment.setStudent(student);
                    grade.setEnrollment(enrollment);

                    grades.add(grade);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return grades;
    }

    public static String calculateLetterGrade(double total) {
        if (total >= 85)
            return "A";
        if (total >= 80)
            return "A-";
        if (total >= 75)
            return "B+";
        if (total >= 70)
            return "B";
        if (total >= 65)
            return "B-";
        if (total >= 61)
            return "C+";
        if (total >= 58)
            return "C";
        if (total >= 55)
            return "C-";
        if (total >= 50)
            return "D";
        return "F";
    }

    public static double getGradePoints(String letter) {
        if (letter == null)
            return 0.0;
        switch (letter) {
            case "A":
                return 4.0;
            case "A-":
                return 3.7;
            case "B+":
                return 3.3;
            case "B":
                return 3.0;
            case "B-":
                return 2.7;
            case "C+":
                return 2.3;
            case "C":
                return 2.0;
            case "C-":
                return 1.7;
            case "D":
                return 1.0;
            case "F":
                return 0.0;
            default:
                return 0.0;
        }
    }

    public List<Grade> getPublishedGradesByStudent(int studentId) {
        List<Grade> grades = new ArrayList<>();
        String sql = "SELECT g.*, c.course_code, c.course_name, c.credit_hours, s.name as semester_name, s.semester_id, e.semester_number "
                +
                "FROM grades g " +
                "JOIN enrollments e ON g.enrollment_id = e.enrollment_id " +
                "JOIN course_allocations ca ON e.allocation_id = ca.allocation_id " +
                "JOIN courses c ON ca.course_id = c.course_id " +
                "JOIN semesters s ON ca.semester_id = s.semester_id " +
                "WHERE e.student_id = ? AND e.status = 'ENROLLED' AND g.is_published = TRUE " +
                "ORDER BY e.semester_number ASC, c.course_code ASC";

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Grade grade = new Grade();
                    grade.setGradeId(rs.getInt("grade_id"));
                    grade.setEnrollmentId(rs.getInt("enrollment_id"));
                    grade.setTotalMarks(rs.getDouble("total_marks"));
                    grade.setGradeLetter(rs.getString("grade_letter"));

                    Enrollment enrollment = new Enrollment();
                    enrollment.setSemesterNumber(rs.getInt("semester_number"));

                    CourseAllocation ca = new CourseAllocation();
                    com.cms.models.Semester sem = new com.cms.models.Semester();
                    sem.setName(rs.getString("semester_name"));
                    ca.setSemester(sem);

                    Course c = new Course();
                    c.setCourseCode(rs.getString("course_code"));
                    c.setCourseName(rs.getString("course_name"));
                    c.setCreditHours(rs.getInt("credit_hours"));

                    ca.setCourse(c);
                    enrollment.setCourseAllocation(ca);
                    grade.setEnrollment(enrollment);

                    grades.add(grade);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return grades;
    }

    public boolean upsertGrade(Grade grade) {
        // Calculate letter grade automatically
        double total = grade.getSessionalMarks() + grade.getMidMarks() + grade.getFinalMarks();
        String gradeLetter = calculateLetterGrade(total);

        String sql = "INSERT INTO grades (enrollment_id, sessional_marks, mid_marks, final_marks, grade_letter, is_published) "
                +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "sessional_marks = ?, " +
                "mid_marks = ?, " +
                "final_marks = ?, " +
                "grade_letter = ?, " +
                "is_published = ?";

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, grade.getEnrollmentId());
            stmt.setDouble(2, grade.getSessionalMarks());
            stmt.setDouble(3, grade.getMidMarks());
            stmt.setDouble(4, grade.getFinalMarks());
            stmt.setString(5, gradeLetter);
            stmt.setBoolean(6, grade.isPublished());

            // For UPDATE part
            stmt.setDouble(7, grade.getSessionalMarks());
            stmt.setDouble(8, grade.getMidMarks());
            stmt.setDouble(9, grade.getFinalMarks());
            stmt.setString(10, gradeLetter);
            stmt.setBoolean(11, grade.isPublished());

            int rows = stmt.executeUpdate();
            return rows >= 0;
        } catch (SQLException e) {
            System.err
                    .println("Error in upsertGrade for enrollment " + grade.getEnrollmentId() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
