package com.cms.models;

import java.sql.Date;

/**
 * The semester number a class is in during a term, e.g. "BS Computer Science-2022"
 * in its 5th semester in Fall 2024. A class has at most one current term.
 */
public class ClassSemester {
    private int classSemesterId;
    private int classId;
    private String className;
    private Integer departmentId;
    private String departmentName;
    private int semesterId;
    private String termName;
    private Date termStart;
    private Date termEnd;
    private int semesterNumber;
    private boolean current;
    private int studentCount;

    /** 1 -> "1st", 2 -> "2nd", 3 -> "3rd", 4 -> "4th", 11 -> "11th", 22 -> "22nd" */
    public static String ordinal(int n) {
        int lastTwo = n % 100;
        if (lastTwo >= 11 && lastTwo <= 13)
            return n + "th";
        switch (n % 10) {
            case 1: return n + "st";
            case 2: return n + "nd";
            case 3: return n + "rd";
            default: return n + "th";
        }
    }

    /** e.g. "5th semester" */
    public String getNumberLabel() {
        return ordinal(semesterNumber) + " semester";
    }

    /** e.g. "01 Sep 2024 - 15 Jan 2025" (empty when dates are missing) */
    public String getTermDateRange() {
        if (termStart == null || termEnd == null)
            return "";
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("dd MMM yyyy");
        return f.format(termStart) + " - " + f.format(termEnd);
    }

    public int getClassSemesterId() { return classSemesterId; }
    public void setClassSemesterId(int classSemesterId) { this.classSemesterId = classSemesterId; }

    public int getClassId() { return classId; }
    public void setClassId(int classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public Integer getDepartmentId() { return departmentId; }
    public void setDepartmentId(Integer departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public int getSemesterId() { return semesterId; }
    public void setSemesterId(int semesterId) { this.semesterId = semesterId; }

    public String getTermName() { return termName; }
    public void setTermName(String termName) { this.termName = termName; }

    public Date getTermStart() { return termStart; }
    public void setTermStart(Date termStart) { this.termStart = termStart; }

    public Date getTermEnd() { return termEnd; }
    public void setTermEnd(Date termEnd) { this.termEnd = termEnd; }

    public int getSemesterNumber() { return semesterNumber; }
    public void setSemesterNumber(int semesterNumber) { this.semesterNumber = semesterNumber; }

    public boolean isCurrent() { return current; }
    public void setCurrent(boolean current) { this.current = current; }

    public int getStudentCount() { return studentCount; }
    public void setStudentCount(int studentCount) { this.studentCount = studentCount; }
}
