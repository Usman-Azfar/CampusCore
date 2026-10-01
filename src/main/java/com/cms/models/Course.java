package com.cms.models;

public class Course {
    private int courseId;
    private String courseCode;
    private String courseName;
    private int creditHours;
    private String description;

    public Course() {}

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public int getCreditHours() { return creditHours; }
    public void setCreditHours(int creditHours) { this.creditHours = creditHours; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    // Owning department (optional) and the classes the course is offered to
    private Integer departmentId;
    private String departmentName;
    private java.util.List<AcademicClass> classes = new java.util.ArrayList<>();

    // Listing statistics (filled by CourseDAO.getAllCourses)
    private String activeTeachers;  // teacher(s) allocated in the active semester, or null
    private int allocationCount;    // offerings across all semesters
    private int enrolledCount;      // students currently ENROLLED across all offerings

    public Integer getDepartmentId() { return departmentId; }
    public void setDepartmentId(Integer departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public java.util.List<AcademicClass> getClasses() { return classes; }
    public void setClasses(java.util.List<AcademicClass> classes) { this.classes = classes; }

    public boolean hasClass(int classId) {
        for (AcademicClass c : classes) {
            if (c.getClassId() == classId) return true;
        }
        return false;
    }

    public String getActiveTeachers() { return activeTeachers; }
    public void setActiveTeachers(String activeTeachers) { this.activeTeachers = activeTeachers; }

    public int getAllocationCount() { return allocationCount; }
    public void setAllocationCount(int allocationCount) { this.allocationCount = allocationCount; }

    public int getEnrolledCount() { return enrolledCount; }
    public void setEnrolledCount(int enrolledCount) { this.enrolledCount = enrolledCount; }
}
