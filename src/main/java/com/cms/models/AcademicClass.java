package com.cms.models;

/**
 * A student class (batch), e.g. degree "BS", program "Computer Science", year 2026,
 * displayed as "BS Computer Science-2026".
 */
public class AcademicClass {
    private int classId;
    private String degree;
    private String programName;
    private int batchYear;
    private int studentCount; // Number of students in this class (for listings)

    public AcademicClass() {
    }

    public static String formatName(String degree, String programName, int batchYear) {
        return degree + " " + programName + "-" + batchYear;
    }

    public String getDisplayName() {
        return formatName(degree, programName, batchYear);
    }

    public int getClassId() {
        return classId;
    }

    public void setClassId(int classId) {
        this.classId = classId;
    }

    public String getDegree() {
        return degree;
    }

    public void setDegree(String degree) {
        this.degree = degree;
    }

    public String getProgramName() {
        return programName;
    }

    public void setProgramName(String programName) {
        this.programName = programName;
    }

    public int getBatchYear() {
        return batchYear;
    }

    public void setBatchYear(int batchYear) {
        this.batchYear = batchYear;
    }

    public int getStudentCount() {
        return studentCount;
    }

    public void setStudentCount(int studentCount) {
        this.studentCount = studentCount;
    }
}
