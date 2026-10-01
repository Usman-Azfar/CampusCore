package com.cms.models;

import com.cms.util.GradeRules;
import java.sql.Timestamp;

/**
 * One student's marks in one course offering. A mark is null until it is entered
 * (see GradeRules); the letter is set only once all three marks are entered.
 */
public class Grade {
    private int gradeId;
    private int enrollmentId;
    private Double sessionalMarks;
    private Double midMarks;
    private Double finalMarks;
    private String gradeLetter;
    private boolean isPublished;
    private Timestamp updatedAt;
    private String updatedByName;

    // Optional
    private Enrollment enrollment;

    public Grade() {}

    public int getGradeId() { return gradeId; }
    public void setGradeId(int gradeId) { this.gradeId = gradeId; }

    public int getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(int enrollmentId) { this.enrollmentId = enrollmentId; }

    public Double getSessionalMarks() { return sessionalMarks; }
    public void setSessionalMarks(Double sessionalMarks) { this.sessionalMarks = sessionalMarks; }

    public Double getMidMarks() { return midMarks; }
    public void setMidMarks(Double midMarks) { this.midMarks = midMarks; }

    public Double getFinalMarks() { return finalMarks; }
    public void setFinalMarks(Double finalMarks) { this.finalMarks = finalMarks; }

    /** Sum of the marks entered so far; null when none is entered. */
    public Double getTotalMarks() { return GradeRules.total(sessionalMarks, midMarks, finalMarks); }

    /** All three marks entered. */
    public boolean isComplete() { return GradeRules.isComplete(sessionalMarks, midMarks, finalMarks); }

    public static final String RESULT_WITHDRAWN = "WITHDRAWN";
    public static final String RESULT_AWAITED = "AWAITED";         // not published yet
    public static final String RESULT_IN_PROGRESS = "IN_PROGRESS"; // published, marks still missing
    public static final String RESULT_GRADED = "GRADED";           // published with all marks: counts for GPA

    /** The student withdrew from the course (shown as "W", no grade points). */
    public boolean isWithdrawn() {
        return enrollment != null && "WITHDRAWN".equals(enrollment.getStatus());
    }

    /** Counts on the transcript and towards GPA / CGPA. */
    public boolean isGraded() {
        return !isWithdrawn() && isPublished && isComplete() && gradeLetter != null;
    }

    /** One of RESULT_WITHDRAWN, RESULT_AWAITED, RESULT_IN_PROGRESS, RESULT_GRADED. */
    public String getResultStatus() {
        if (isWithdrawn()) return RESULT_WITHDRAWN;
        if (!isPublished) return RESULT_AWAITED;
        return isGraded() ? RESULT_GRADED : RESULT_IN_PROGRESS;
    }

    public String getGradeLetter() { return gradeLetter; }
    public void setGradeLetter(String gradeLetter) { this.gradeLetter = gradeLetter; }

    public boolean isPublished() { return isPublished; }
    public void setPublished(boolean published) { isPublished = published; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedByName() { return updatedByName; }
    public void setUpdatedByName(String updatedByName) { this.updatedByName = updatedByName; }

    public Enrollment getEnrollment() { return enrollment; }
    public void setEnrollment(Enrollment enrollment) { this.enrollment = enrollment; }
}
