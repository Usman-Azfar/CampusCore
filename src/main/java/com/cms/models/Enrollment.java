package com.cms.models;

public class Enrollment {
    private int enrollmentId;
    private int studentId;
    private int allocationId;
    private String status; // ENROLLED, DROPPED, WITHDRAWN
    private int semesterNumber;

    // Optional Navigation Properties
    private User student;
    private CourseAllocation courseAllocation;

    public Enrollment() {}

    public int getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(int enrollmentId) { this.enrollmentId = enrollmentId; }

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public int getAllocationId() { return allocationId; }
    public void setAllocationId(int allocationId) { this.allocationId = allocationId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getSemesterNumber() { return semesterNumber; }
    public void setSemesterNumber(int semesterNumber) { this.semesterNumber = semesterNumber; }

    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }

    public CourseAllocation getCourseAllocation() { return courseAllocation; }
    public void setCourseAllocation(CourseAllocation courseAllocation) { this.courseAllocation = courseAllocation; }
}
