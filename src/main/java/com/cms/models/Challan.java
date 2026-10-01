package com.cms.models;

import java.sql.Timestamp;
import java.sql.Date;

public class Challan {
    private int challanId;
    private int studentId;
    private int semesterId;
    private String filePath;
    private Timestamp uploadDate;
    private Date dueDate;
    private String status; // UNPAID, PAID

    // Optional
    private User student;
    private Semester semester;

    public Challan() {}

    public int getChallanId() { return challanId; }
    public void setChallanId(int challanId) { this.challanId = challanId; }

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public int getSemesterId() { return semesterId; }
    public void setSemesterId(int semesterId) { this.semesterId = semesterId; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Timestamp getUploadDate() { return uploadDate; }
    public void setUploadDate(Timestamp uploadDate) { this.uploadDate = uploadDate; }

    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }

    public Semester getSemester() { return semester; }
    public void setSemester(Semester semester) { this.semester = semester; }

    private String title;               // e.g. "Semester Fee - Fall 2024"
    private java.math.BigDecimal amount; // PKR, may be null
    private String remarks;
    private Timestamp paidAt;
    private String createdByName;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public java.math.BigDecimal getAmount() { return amount; }
    public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public Timestamp getPaidAt() { return paidAt; }
    public void setPaidAt(Timestamp paidAt) { this.paidAt = paidAt; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public boolean isPaid() { return "PAID".equals(status); }

    // Unpaid and past its due date
    public boolean isOverdue() {
        return !isPaid() && dueDate != null && dueDate.toLocalDate().isBefore(java.time.LocalDate.now());
    }

    // Printed challan number, e.g. "CH-000042"
    public String getNumber() { return String.format("CH-%06d", challanId); }

    // e.g. "PKR 45,000.00" or "-" when no amount
    public String getAmountLabel() {
        return amount == null ? "-" : "PKR " + new java.text.DecimalFormat("#,##0.00").format(amount);
    }

    public boolean hasAttachment() { return filePath != null && !filePath.isEmpty(); }

    // Proof of payment uploaded by the student (NONE, SUBMITTED, ACCEPTED, REJECTED)
    private String proofPath;
    private String proofReference;
    private String proofStatus = "NONE";
    private Timestamp proofSubmittedAt;
    private String proofReviewNote;
    private Timestamp proofReviewedAt;

    public String getProofPath() { return proofPath; }
    public void setProofPath(String proofPath) { this.proofPath = proofPath; }

    public String getProofReference() { return proofReference; }
    public void setProofReference(String proofReference) { this.proofReference = proofReference; }

    public String getProofStatus() { return proofStatus; }
    public void setProofStatus(String proofStatus) { this.proofStatus = proofStatus == null ? "NONE" : proofStatus; }

    public Timestamp getProofSubmittedAt() { return proofSubmittedAt; }
    public void setProofSubmittedAt(Timestamp proofSubmittedAt) { this.proofSubmittedAt = proofSubmittedAt; }

    public String getProofReviewNote() { return proofReviewNote; }
    public void setProofReviewNote(String proofReviewNote) { this.proofReviewNote = proofReviewNote; }

    public Timestamp getProofReviewedAt() { return proofReviewedAt; }
    public void setProofReviewedAt(Timestamp proofReviewedAt) { this.proofReviewedAt = proofReviewedAt; }

    public boolean hasProof() { return proofPath != null && !proofPath.isEmpty(); }

    // Proof is waiting for the accounts office
    public boolean isProofPending() { return "SUBMITTED".equals(proofStatus) && !isPaid(); }

    // The student may upload (or replace) proof while the challan is unpaid
    public boolean canSubmitProof() { return !isPaid(); }
}
