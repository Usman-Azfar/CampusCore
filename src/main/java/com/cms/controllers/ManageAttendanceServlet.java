package com.cms.controllers;

import com.cms.dao.AttendanceDAO;
import com.cms.models.AttendanceSummary;
import com.cms.models.CourseAllocation;
import com.cms.models.Lecture;
import com.cms.models.User;
import com.cms.util.AttendanceRules;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Teacher: mark and correct attendance for their course offerings.
 * Rules (see AttendanceRules): no future dates, dates inside the term, and attendance can be
 * marked, changed or deleted only within 7 days of the lecture. Re-submitting a date that is
 * already marked opens that lecture for editing instead of creating a duplicate; a second
 * lecture on the same day must be asked for explicitly.
 */
public class ManageAttendanceServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String FLASH_SUCCESS = "attendance.flash.success";
    private static final String FLASH_ERROR = "attendance.flash.error";
    private static final int MAX_TOPIC = 200;

    private AttendanceDAO attendanceDAO;

    public void init() {
        attendanceDAO = new AttendanceDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User teacher = requireTeacher(request, response);
        if (teacher == null)
            return;
        HttpSession session = request.getSession();
        SupportTicketServlet.moveFlash(session, request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(session, request, FLASH_ERROR, "errorMessage");

        Integer allocationId = AdminSupport.parseInt(request.getParameter("allocationId"));
        if (allocationId == null) {
            // Step 1: the teacher's courses, split into open (can mark) and closed (view only)
            List<CourseAllocation> open = new ArrayList<>(), closed = new ArrayList<>();
            for (CourseAllocation ca : attendanceDAO.getOfferingsByTeacher(teacher.getUserId()))
                (AttendanceRules.isOpen(ca.getSemester()) ? open : closed).add(ca);
            request.setAttribute("openOfferings", open);
            request.setAttribute("closedOfferings", closed);
            request.getRequestDispatcher("manage_attendance.jsp").forward(request, response);
            return;
        }

        CourseAllocation offering = ownOffering(allocationId, teacher);
        if (offering == null) {
            AdminSupport.flash(request, FLASH_ERROR, "That course is not assigned to you.");
            response.sendRedirect("manageAttendance");
            return;
        }

        List<Lecture> lectures = attendanceDAO.getLectures(allocationId);
        boolean extra = "1".equals(request.getParameter("extra"));

        // Which sheet to show: an existing lecture (edit / view) or a new one for a date
        Lecture sheetLecture = null;
        LocalDate sheetDate = null;
        String sheetNotice = null;
        Integer lectureId = AdminSupport.parseInt(request.getParameter("lectureId"));
        if (lectureId != null) {
            sheetLecture = find(lectures, lectureId);
            if (sheetLecture == null)
                request.setAttribute("errorMessage", "Lecture not found.");
        }
        if (sheetLecture == null) {
            String dateParam = request.getParameter("date");
            if (dateParam != null) {
                sheetDate = AttendanceRules.parse(dateParam);
                if (sheetDate == null)
                    request.setAttribute("errorMessage", "Please choose a valid date.");
            } else if (AttendanceRules.isOpen(offering.getSemester())) {
                sheetDate = AttendanceRules.maxDate(offering.getSemester()); // today, or the term's last day
            }
            if (sheetDate != null && !extra) {
                List<Lecture> sameDay = on(lectures, sheetDate);
                if (!sameDay.isEmpty()) {
                    // Already marked: open it instead of starting a duplicate
                    sheetLecture = sameDay.get(0);
                    sheetNotice = "Attendance for " + AttendanceRules.format(sheetDate) + " is already marked (Lecture "
                            + sheetLecture.getNumber() + "). You are viewing it" + (AttendanceRules.checkDate(sheetDate, offering.getSemester()) == null ? " and can change it." : ".");
                }
            }
        }

        boolean editing = sheetLecture != null;
        LocalDate date = editing ? sheetLecture.getDate().toLocalDate() : sheetDate;
        String lockReason = date == null ? null : AttendanceRules.checkDate(date, offering.getSemester());
        if (!editing && lockReason == null && lectures.size() >= AttendanceRules.MAX_LECTURES)
            lockReason = "This course already has " + AttendanceRules.MAX_LECTURES + " lectures, the most allowed.";

        request.setAttribute("offering", offering);
        request.setAttribute("lectures", lectures);
        request.setAttribute("summaries", attendanceDAO.getSummaries(allocationId));
        request.setAttribute("sheetLecture", sheetLecture);
        request.setAttribute("sheetDate", date);
        request.setAttribute("sheetNotice", sheetNotice);
        request.setAttribute("sheetLocked", lockReason);
        request.setAttribute("sameDayLectures", date == null ? Collections.emptyList() : on(lectures, date));
        request.setAttribute("extra", extra);
        request.setAttribute("statuses", editing ? attendanceDAO.getStatuses(sheetLecture.getLectureId()) : Collections.emptyMap());
        request.getRequestDispatcher("manage_attendance.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User teacher = requireTeacher(request, response);
        if (teacher == null)
            return;
        Integer allocationId = AdminSupport.parseInt(request.getParameter("allocationId"));
        CourseAllocation offering = allocationId == null ? null : ownOffering(allocationId, teacher);
        if (offering == null) {
            AdminSupport.flash(request, FLASH_ERROR, "That course is not assigned to you.");
            response.sendRedirect("manageAttendance");
            return;
        }
        String base = "manageAttendance?allocationId=" + allocationId;
        String action = request.getParameter("action");

        if ("delete".equals(action)) {
            Lecture lecture = lectureOf(request, allocationId);
            String error = lecture == null ? "Lecture not found."
                    : AttendanceRules.checkDate(lecture.getDate(), offering.getSemester());
            if (error == null)
                error = attendanceDAO.deleteLecture(lecture.getLectureId(), allocationId);
            if (error == null) {
                AdminSupport.flash(request, FLASH_SUCCESS, "Lecture " + lecture.getNumber() + " ("
                        + AttendanceRules.format(lecture.getDate().toLocalDate()) + ") and its attendance were deleted. Later lectures were renumbered.");
                response.sendRedirect(base);
            } else {
                AdminSupport.flash(request, FLASH_ERROR, "Not deleted: " + error);
                response.sendRedirect(lecture == null ? base : base + "&lectureId=" + lecture.getLectureId());
            }
            return;
        }
        if (!"save".equals(action)) {
            AdminSupport.flash(request, FLASH_ERROR, "Unknown action.");
            response.sendRedirect(base);
            return;
        }

        // Existing lecture (edit) or a new one
        Lecture lecture = null;
        if (request.getParameter("lectureId") != null && !request.getParameter("lectureId").isEmpty()) {
            lecture = lectureOf(request, allocationId);
            if (lecture == null) {
                AdminSupport.flash(request, FLASH_ERROR, "Lecture not found.");
                response.sendRedirect(base);
                return;
            }
        }
        LocalDate date = lecture != null ? lecture.getDate().toLocalDate() : AttendanceRules.parse(request.getParameter("date"));
        String back = lecture != null ? base + "&lectureId=" + lecture.getLectureId()
                : base + (date != null ? "&date=" + date : "");
        String error = AttendanceRules.checkDate(date, offering.getSemester());

        String topic = request.getParameter("topic") == null ? "" : request.getParameter("topic").trim().replaceAll("\\s+", " ");
        if (error == null && topic.length() > MAX_TOPIC)
            error = "Topic can be at most " + MAX_TOPIC + " characters.";

        // Every listed student needs a status. When editing, a student with no record yet (e.g. enrolled
        // after the lecture) may be left unmarked.
        List<AttendanceSummary> students = attendanceDAO.getSummaries(allocationId);
        Map<Integer, String> existing = lecture != null ? attendanceDAO.getStatuses(lecture.getLectureId()) : Collections.emptyMap();
        Map<Integer, String> statuses = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        if (error == null) {
            if (lecture == null && students.isEmpty())
                error = "No students are enrolled in this course yet.";
            for (AttendanceSummary s : students) {
                String status = request.getParameter("status_" + s.getEnrollmentId());
                if (status == null || status.isEmpty()) {
                    if (lecture == null || existing.containsKey(s.getEnrollmentId()))
                        missing.add(s.getRollNo());
                } else if (!AttendanceDAO.STATUSES.contains(status)) {
                    error = "Invalid attendance status for " + s.getRollNo() + ".";
                    break;
                } else {
                    statuses.put(s.getEnrollmentId(), status);
                }
            }
            if (error == null && !missing.isEmpty())
                error = "Choose Present, Absent or Leave for every student. Missing: " + String.join(", ", missing) + ".";
        }
        if (error != null) {
            AdminSupport.flash(request, FLASH_ERROR, "Attendance not saved: " + error);
            response.sendRedirect(back);
            return;
        }

        int lecturesBefore = lecture == null ? attendanceDAO.getLectures(allocationId).size() : 0;
        AttendanceDAO.SaveResult r = attendanceDAO.save(allocationId, lecture == null ? null : lecture.getLectureId(),
                Date.valueOf(date), topic.isEmpty() ? null : topic, "1".equals(request.getParameter("extra")),
                statuses, teacher.getUserId());
        if (r.error != null) {
            AdminSupport.flash(request, FLASH_ERROR, "Attendance not saved: " + r.error);
            response.sendRedirect(r.existingLectureId != null ? base + "&lectureId=" + r.existingLectureId : back);
            return;
        }

        Lecture saved = attendanceDAO.getLecture(r.lectureId);
        StringBuilder msg = new StringBuilder();
        msg.append("Lecture ").append(saved.getNumber()).append(" (").append(AttendanceRules.format(date)).append(") ")
                .append(lecture == null ? "saved" : "updated").append(": ")
                .append(saved.getPresentCount()).append(" present, ").append(saved.getAbsentCount()).append(" absent, ")
                .append(saved.getLeaveCount()).append(" on leave.");
        if (lecture == null && saved.getNumber() <= lecturesBefore)
            msg.append(" It is dated before later lectures, so those were renumbered (lecture numbers follow date order).");
        AdminSupport.flash(request, FLASH_SUCCESS, msg.toString());
        response.sendRedirect(base + "&lectureId=" + r.lectureId);
    }

    // ---------------- helpers ----------------

    private CourseAllocation ownOffering(int allocationId, User teacher) {
        CourseAllocation offering = attendanceDAO.getOffering(allocationId);
        return offering != null && offering.getTeacherId() == teacher.getUserId() ? offering : null;
    }

    // The lecture named by the lectureId parameter, only if it belongs to this offering
    private Lecture lectureOf(HttpServletRequest request, int allocationId) {
        Integer id = AdminSupport.parseInt(request.getParameter("lectureId"));
        Lecture lecture = id == null ? null : attendanceDAO.getLecture(id);
        return lecture != null && lecture.getAllocationId() == allocationId ? lecture : null;
    }

    private static Lecture find(List<Lecture> lectures, int lectureId) {
        for (Lecture l : lectures)
            if (l.getLectureId() == lectureId)
                return l;
        return null;
    }

    private static List<Lecture> on(List<Lecture> lectures, LocalDate date) {
        List<Lecture> list = new ArrayList<>();
        for (Lecture l : lectures)
            if (l.getDate().toLocalDate().equals(date))
                list.add(l);
        return list;
    }

    private static User requireTeacher(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("user");
        if (user == null) {
            response.sendRedirect("login");
            return null;
        }
        if (!"TEACHER".equals(user.getRole())) {
            response.sendRedirect("dashboard");
            return null;
        }
        return user;
    }
}
