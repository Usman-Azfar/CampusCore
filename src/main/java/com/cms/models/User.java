package com.cms.models;

import java.sql.Timestamp;

public class User {
    private int userId;
    private String username;
    private String password;
    private String role; // ADMIN, TEACHER, STUDENT
    private boolean isActive;
    private Timestamp createdAt;
    private Profile profile;

    // Students belong to a class, teachers to a department (both optional)
    private Integer classId;
    private String className; // e.g. "BS Computer Science-2026"
    private Integer departmentId;
    private String departmentName; // e.g. "Computer Science"

    public User() {
    }

    public User(int userId, String username, String password, String role, boolean isActive, Timestamp createdAt) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.role = role;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public Integer getClassId() {
        return classId;
    }

    public void setClassId(Integer classId) {
        this.classId = classId;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    // Class for students, department for teachers; null when not assigned
    public String getAffiliation() {
        if ("STUDENT".equals(role))
            return className;
        if ("TEACHER".equals(role))
            return departmentName;
        return null;
    }

    // Human-readable label, e.g. "Dr. Sarah Ahmed (TEACHER1)"; falls back to username
    public String getDisplayName() {
        String fullName = (profile != null) ? profile.getFullName() : null;
        if (fullName == null || fullName.trim().isEmpty()) {
            return username;
        }
        return fullName + " (" + username + ")";
    }
}
