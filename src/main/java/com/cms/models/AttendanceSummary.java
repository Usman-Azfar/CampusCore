package com.cms.models;

import com.cms.util.AttendanceRules;

/** A student's attendance totals in one course offering. */
public class AttendanceSummary {
    private int enrollmentId;
    private int studentId;
    private String rollNo;
    private String name;
    private String className;
    private int present;
    private int absent;
    private int leave;

    /** Lectures this student was marked in. */
    public int getMarked() { return present + absent + leave; }

    /** Present / marked lectures, rounded; -1 when nothing is marked yet. Leave does not count as present. */
    public int getPercent() { return AttendanceRules.percent(present, getMarked()); }

    public boolean isLow() {
        int p = getPercent();
        return p >= 0 && p < AttendanceRules.LOW_ATTENDANCE_PERCENT;
    }

    public int getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(int enrollmentId) { this.enrollmentId = enrollmentId; }

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public int getPresent() { return present; }
    public void setPresent(int present) { this.present = present; }

    public int getAbsent() { return absent; }
    public void setAbsent(int absent) { this.absent = absent; }

    public int getLeave() { return leave; }
    public void setLeave(int leave) { this.leave = leave; }
}
