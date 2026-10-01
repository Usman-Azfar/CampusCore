package com.cms.models;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * One lecture held in a course offering. Its number is not stored: lectures are numbered
 * in date order (ties by the order they were added).
 */
public class Lecture {
    private int lectureId;
    private int allocationId;
    private int number;
    private Date date;
    private String topic;
    private int presentCount;
    private int absentCount;
    private int leaveCount;
    private Timestamp createdAt;
    private String createdByName;
    private Timestamp updatedAt;
    private String updatedByName;

    public int getMarkedCount() { return presentCount + absentCount + leaveCount; }

    public int getLectureId() { return lectureId; }
    public void setLectureId(int lectureId) { this.lectureId = lectureId; }

    public int getAllocationId() { return allocationId; }
    public void setAllocationId(int allocationId) { this.allocationId = allocationId; }

    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public int getPresentCount() { return presentCount; }
    public void setPresentCount(int presentCount) { this.presentCount = presentCount; }

    public int getAbsentCount() { return absentCount; }
    public void setAbsentCount(int absentCount) { this.absentCount = absentCount; }

    public int getLeaveCount() { return leaveCount; }
    public void setLeaveCount(int leaveCount) { this.leaveCount = leaveCount; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedByName() { return updatedByName; }
    public void setUpdatedByName(String updatedByName) { this.updatedByName = updatedByName; }
}
