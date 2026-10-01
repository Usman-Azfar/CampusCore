package com.cms.models;

import java.sql.Date;

public class Attendance {
    private int attendanceId;
    private int enrollmentId;
    private int lectureNumber;
    private Date date;
    private String status; // Present, Absent, Leave

    public Attendance() {}

    public int getAttendanceId() { return attendanceId; }
    public void setAttendanceId(int attendanceId) { this.attendanceId = attendanceId; }

    public int getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(int enrollmentId) { this.enrollmentId = enrollmentId; }

    public int getLectureNumber() { return lectureNumber; }
    public void setLectureNumber(int lectureNumber) { this.lectureNumber = lectureNumber; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
