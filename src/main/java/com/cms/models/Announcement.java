package com.cms.models;

import java.sql.Timestamp;

public class Announcement {
    private int announcementId;
    private String title;
    private String content;
    private int courseId; // 0 or -1 if NULL (General)
    private int createdBy;
    private Timestamp createdAt;

    // Optional
    private Course course; // Can be null
    private User creator;

    public Announcement() {}

    public int getAnnouncementId() { return announcementId; }
    public void setAnnouncementId(int announcementId) { this.announcementId = announcementId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public User getCreator() { return creator; }
    public void setCreator(User creator) { this.creator = creator; }
}
