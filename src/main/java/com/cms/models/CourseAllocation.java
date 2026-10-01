package com.cms.models;

public class CourseAllocation {
    private int allocationId;
    private int courseId;
    private int teacherId;
    private int semesterId;
    
    // Optional: Include full objects if needed for convenient display
    private Course course;
    private User teacher;
    private Semester semester;

    public CourseAllocation() {}

    public int getAllocationId() { return allocationId; }
    public void setAllocationId(int allocationId) { this.allocationId = allocationId; }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public int getTeacherId() { return teacherId; }
    public void setTeacherId(int teacherId) { this.teacherId = teacherId; }

    public int getSemesterId() { return semesterId; }
    public void setSemesterId(int semesterId) { this.semesterId = semesterId; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public User getTeacher() { return teacher; }
    public void setTeacher(User teacher) { this.teacher = teacher; }

    public Semester getSemester() { return semester; }
    public void setSemester(Semester semester) { this.semester = semester; }

    // Listing statistics (filled by CourseAllocationDAO.getAllocationsDetailed)
    private int enrolledCount;     // students currently ENROLLED in this offering
    private int enrollmentRows;    // all enrollment records (incl. dropped/withdrawn), removed with the offering

    public int getEnrolledCount() { return enrolledCount; }
    public void setEnrolledCount(int enrolledCount) { this.enrolledCount = enrolledCount; }

    public int getEnrollmentRows() { return enrollmentRows; }
    public void setEnrollmentRows(int enrollmentRows) { this.enrollmentRows = enrollmentRows; }

    // Attendance statistics (filled by AttendanceDAO)
    private int lectureCount;           // lectures held so far
    private java.sql.Date lastLectureDate;

    public int getLectureCount() { return lectureCount; }
    public void setLectureCount(int lectureCount) { this.lectureCount = lectureCount; }

    public java.sql.Date getLastLectureDate() { return lastLectureDate; }
    public void setLastLectureDate(java.sql.Date lastLectureDate) { this.lastLectureDate = lastLectureDate; }

    // Grade statistics (filled by GradeDAO), over students currently ENROLLED
    private int completeCount;          // all three marks entered
    private int publishedCount;         // result visible to the student

    public int getCompleteCount() { return completeCount; }
    public void setCompleteCount(int completeCount) { this.completeCount = completeCount; }

    public int getPublishedCount() { return publishedCount; }
    public void setPublishedCount(int publishedCount) { this.publishedCount = publishedCount; }

    // Announcements posted to this offering (filled by CourseAllocationDAO.getTeachingOverview)
    private int announcementCount;

    public int getAnnouncementCount() { return announcementCount; }
    public void setAnnouncementCount(int announcementCount) { this.announcementCount = announcementCount; }
}
