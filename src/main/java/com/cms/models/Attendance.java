package com.cms.models;

import java.sql.Date;

/** One student's status in one lecture (status is null when the student was not marked). */
public class Attendance {
    private int attendanceId;
    private int lectureId;
    private int enrollmentId;
    private int lectureNumber; // from the lecture's position in date order
    private Date date;         // the lecture date
    private String topic;
    private String status;     // Present, Absent, Leave, or null = not marked

    public Attendance() {}

    public int getAttendanceId() { return attendanceId; }
    public void setAttendanceId(int attendanceId) { this.attendanceId = attendanceId; }

    public int getLectureId() { return lectureId; }
    public void setLectureId(int lectureId) { this.lectureId = lectureId; }

    public int getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(int enrollmentId) { this.enrollmentId = enrollmentId; }

    public int getLectureNumber() { return lectureNumber; }
    public void setLectureNumber(int lectureNumber) { this.lectureNumber = lectureNumber; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
