package com.cms.models;

import java.sql.Timestamp;

/**
 * An announcement is either general (from the admin, no course offering, sent to everyone, students
 * only or teachers only) or for one course offering (from its teacher, seen by its enrolled students).
 */
public class Announcement {
    public static final String AUDIENCE_ALL = "ALL";
    public static final String AUDIENCE_STUDENTS = "STUDENTS";
    public static final String AUDIENCE_TEACHERS = "TEACHERS";

    private int announcementId;
    private String title;
    private String content;
    private int courseId;          // 0 for a general announcement
    private Integer allocationId;  // the course offering; null for a general announcement
    private String audience = AUDIENCE_ALL; // general announcements only
    private int createdBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;   // null until edited

    // Optional
    private Course course; // Can be null
    private User creator;
    private String termName;       // course announcements: the offering's term

    public Announcement() {}

    public boolean isGeneral() { return allocationId == null; }

    /** "Everyone", "Students" or "Teachers". */
    public String getAudienceLabel() {
        if (AUDIENCE_STUDENTS.equals(audience)) return "Students";
        if (AUDIENCE_TEACHERS.equals(audience)) return "Teachers";
        return "Everyone";
    }

    public int getAnnouncementId() { return announcementId; }
    public void setAnnouncementId(int announcementId) { this.announcementId = announcementId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public Integer getAllocationId() { return allocationId; }
    public void setAllocationId(Integer allocationId) { this.allocationId = allocationId; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public User getCreator() { return creator; }
    public void setCreator(User creator) { this.creator = creator; }

    public String getTermName() { return termName; }
    public void setTermName(String termName) { this.termName = termName; }
}
