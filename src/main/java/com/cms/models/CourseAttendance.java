package com.cms.models;

import com.cms.util.AttendanceRules;
import java.sql.Date;

/** A student's attendance in one course offering, with the course, its term and the class's semester number. */
public class CourseAttendance {
    private int enrollmentId;
    private int allocationId;
    private String courseCode;
    private String courseName;
    private String teacherName;
    private int teacherId;
    private int termId;
    private String termName;
    private Date termStart;
    private Date termEnd;
    private int semesterNumber;   // the class's semester in that term (0 when unknown)
    private int lecturesHeld;     // lectures of the offering so far
    private int present;
    private int absent;
    private int leave;

    /** e.g. "Fall 2024-3rd Semester" (just the term when the semester number is unknown). */
    public String getTermLabel() {
        return label(termName, semesterNumber);
    }

    public static String label(String termName, int semesterNumber) {
        String term = termName == null ? "" : termName;
        return semesterNumber > 0 ? term + "-" + ClassSemester.ordinal(semesterNumber) + " Semester" : term;
    }

    /** Lectures this student was marked in. */
    public int getMarked() { return present + absent + leave; }

    /** Lectures held that this student has no record for (e.g. enrolled later). */
    public int getNotMarked() { return Math.max(0, lecturesHeld - getMarked()); }

    /** Present / marked, rounded; -1 when not marked in any lecture yet. */
    public int getPercent() { return AttendanceRules.percent(present, getMarked()); }

    public boolean isLow() {
        int p = getPercent();
        return p >= 0 && p < AttendanceRules.LOW_ATTENDANCE_PERCENT;
    }

    public int getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(int enrollmentId) { this.enrollmentId = enrollmentId; }

    public int getAllocationId() { return allocationId; }
    public void setAllocationId(int allocationId) { this.allocationId = allocationId; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public int getTeacherId() { return teacherId; }
    public void setTeacherId(int teacherId) { this.teacherId = teacherId; }

    public int getTermId() { return termId; }
    public void setTermId(int termId) { this.termId = termId; }

    public String getTermName() { return termName; }
    public void setTermName(String termName) { this.termName = termName; }

    public Date getTermStart() { return termStart; }
    public void setTermStart(Date termStart) { this.termStart = termStart; }

    public Date getTermEnd() { return termEnd; }
    public void setTermEnd(Date termEnd) { this.termEnd = termEnd; }

    public int getSemesterNumber() { return semesterNumber; }
    public void setSemesterNumber(int semesterNumber) { this.semesterNumber = semesterNumber; }

    public int getLecturesHeld() { return lecturesHeld; }
    public void setLecturesHeld(int lecturesHeld) { this.lecturesHeld = lecturesHeld; }

    public int getPresent() { return present; }
    public void setPresent(int present) { this.present = present; }

    public int getAbsent() { return absent; }
    public void setAbsent(int absent) { this.absent = absent; }

    public int getLeave() { return leave; }
    public void setLeave(int leave) { this.leave = leave; }
}
