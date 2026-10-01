package com.cms.models;

import java.sql.Date;

public class Semester {
    private int semesterId;
    private String name;
    private Date startDate;
    private Date endDate;
    private boolean isActive;

    public Semester() {}

    public int getSemesterId() { return semesterId; }
    public void setSemesterId(int semesterId) { this.semesterId = semesterId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }

    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    // Usage (filled by SemesterDAO.getAllSemesters): a used semester cannot be removed
    private int allocationCount; // course offerings (teacher assignments) in this semester
    private int challanCount;    // fee challans issued for this semester

    public int getAllocationCount() { return allocationCount; }
    public void setAllocationCount(int allocationCount) { this.allocationCount = allocationCount; }

    public int getChallanCount() { return challanCount; }
    public void setChallanCount(int challanCount) { this.challanCount = challanCount; }

    // e.g. "01 Sep 2024 - 15 Jan 2025" (empty when dates are missing)
    public String getDateRange() {
        if (startDate == null || endDate == null) return "";
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("dd MMM yyyy");
        return f.format(startDate) + " - " + f.format(endDate);
    }

    // e.g. "Fall 2024 (01 Sep 2024 - 15 Jan 2025) - Active"
    public String getLabel() {
        String range = getDateRange();
        return name + (range.isEmpty() ? "" : " (" + range + ")") + (isActive ? " - Active" : "");
    }
}
