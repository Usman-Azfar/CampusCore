package com.cms.models;

public class Grade {
    private int gradeId;
    private int enrollmentId;
    private double sessionalMarks;
    private double midMarks;
    private double finalMarks;
    private double totalMarks;
    private String gradeLetter;
    private boolean isPublished;
    
    // Optional
    private Enrollment enrollment;

    public Grade() {}

    public int getGradeId() { return gradeId; }
    public void setGradeId(int gradeId) { this.gradeId = gradeId; }

    public int getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(int enrollmentId) { this.enrollmentId = enrollmentId; }

    public double getSessionalMarks() { return sessionalMarks; }
    public void setSessionalMarks(double sessionalMarks) { this.sessionalMarks = sessionalMarks; }

    public double getMidMarks() { return midMarks; }
    public void setMidMarks(double midMarks) { this.midMarks = midMarks; }

    public double getFinalMarks() { return finalMarks; }
    public void setFinalMarks(double finalMarks) { this.finalMarks = finalMarks; }

    public double getTotalMarks() { return totalMarks; }
    public void setTotalMarks(double totalMarks) { this.totalMarks = totalMarks; }

    public String getGradeLetter() { return gradeLetter; }
    public void setGradeLetter(String gradeLetter) { this.gradeLetter = gradeLetter; }

    public boolean isPublished() { return isPublished; }
    public void setPublished(boolean published) { isPublished = published; }

    public Enrollment getEnrollment() { return enrollment; }
    public void setEnrollment(Enrollment enrollment) { this.enrollment = enrollment; }
}
