package com.cms.controllers;

import com.cms.dao.AdminOverviewDAO;
import com.cms.dao.AnnouncementDAO;
import com.cms.dao.AttendanceDAO;
import com.cms.dao.ChallanDAO;
import com.cms.dao.ClassSemesterDAO;
import com.cms.dao.CourseRequestDAO;
import com.cms.dao.GradeDAO;
import com.cms.dao.MessageDAO;
import com.cms.dao.ProfileDAO;
import com.cms.models.Announcement;
import com.cms.models.Challan;
import com.cms.models.ClassSemester;
import com.cms.models.CourseAllocation;
import com.cms.models.CourseAttendance;
import com.cms.models.CourseRequest;
import com.cms.models.Grade;
import com.cms.models.Profile;
import com.cms.models.User;
import com.cms.util.AttendanceRules;
import com.cms.util.GradeRules;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Home page. Students: an overview (this semester's courses with attendance and results, CGPA, fees,
 * things that need attention, latest announcements).
 * Teachers: an overview (counts, things that need attention, latest announcements); the courses
 * themselves and their actions are on the Course List.
 * Admin: totals, work waiting (proofs, requests, tickets), set-up gaps (classes without a semester,
 * courses without a teacher), classes this semester, fee collection, latest announcements.
 */
public class DashboardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final int LATEST_ANNOUNCEMENTS = 3;

    private ProfileDAO profileDAO;
    private com.cms.dao.CourseAllocationDAO allocationDAO;
    private AnnouncementDAO announcementDAO;

    public void init() {
        profileDAO = new ProfileDAO();
        allocationDAO = new com.cms.dao.CourseAllocationDAO();
        announcementDAO = new AnnouncementDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.sendRedirect("login.jsp");
            return;
        }

        User user = (User) session.getAttribute("user");
        Profile profile = profileDAO.getProfileByUserId(user.getUserId());
        request.setAttribute("profile", profile);
        // Account details incl. class (students) / department (teachers)
        request.setAttribute("account", new com.cms.dao.UserDAO().getUserById(user.getUserId()));

        if ("STUDENT".equals(user.getRole())) {
            int studentId = user.getUserId();
            // The current term and semester number of the student's class (null if not set)
            ClassSemester currentTerm = new ClassSemesterDAO().getCurrentForStudent(studentId);
            request.setAttribute("currentTerm", currentTerm);

            // This semester's courses: the class's current term, else the newest term the student has courses in
            List<CourseAttendance> allCourses = new AttendanceDAO().getStudentCourses(studentId);
            Integer termId = currentTerm != null ? Integer.valueOf(currentTerm.getSemesterId())
                    : allCourses.isEmpty() ? null : Integer.valueOf(allCourses.get(0).getTermId());
            List<CourseAttendance> thisTerm = new ArrayList<>();
            for (CourseAttendance c : allCourses)
                if (termId != null && c.getTermId() == termId)
                    thisTerm.add(c);
            int present = 0, marked = 0;
            for (CourseAttendance c : thisTerm) {
                present += c.getPresent();
                marked += c.getMarked();
            }
            request.setAttribute("termCourses", thisTerm);
            request.setAttribute("termId", termId);
            request.setAttribute("termAttendance", AttendanceRules.percent(present, marked));
            request.setAttribute("otherCourseCount", allCourses.size() - thisTerm.size());

            // Results per enrollment (unpublished ones come without marks) and CGPA as on the transcript
            GradeDAO gradeDAO = new GradeDAO();
            Map<Integer, Grade> results = new HashMap<>();
            for (Grade g : gradeDAO.getGradesByStudent(studentId))
                results.put(g.getEnrollmentId(), g);
            request.setAttribute("results", results);
            request.setAttribute("cgpa", GradeRules.cgpa(gradeDAO.getTranscriptGrades(studentId)));

            // Fees
            List<Challan> unpaid = new ArrayList<>();
            List<Challan> proofRejected = new ArrayList<>();
            java.math.BigDecimal due = java.math.BigDecimal.ZERO;
            for (Challan c : new ChallanDAO().getChallansByStudent(studentId)) {
                if (c.isPaid())
                    continue;
                unpaid.add(c);
                if (c.getAmount() != null)
                    due = due.add(c.getAmount());
                if ("REJECTED".equals(c.getProofStatus()))
                    proofRejected.add(c);
            }
            request.setAttribute("unpaidCount", unpaid.size());
            request.setAttribute("feesDue", due);

            int pendingRequests = 0;
            for (CourseRequest r : new CourseRequestDAO().getRequestsByStudent(studentId))
                if ("PENDING".equals(r.getStatus()))
                    pendingRequests++;

            request.setAttribute("attention", studentAttention(currentTerm, thisTerm, unpaid, proofRejected, pendingRequests));
            request.setAttribute("unreadMessages", new MessageDAO().countUnread(studentId));
            List<Announcement> latest = announcementDAO.getForStudent(studentId, null);
            request.setAttribute("latestAnnouncements", latest.size() > LATEST_ANNOUNCEMENTS ? latest.subList(0, LATEST_ANNOUNCEMENTS) : latest);
        } else if ("ADMIN".equals(user.getRole())) {
            AdminOverviewDAO overviewDAO = new AdminOverviewDAO();
            AdminOverviewDAO.Overview overview = overviewDAO.getOverview();
            List<AdminOverviewDAO.ClassRow> classes = overviewDAO.getClasses();
            request.setAttribute("overview", overview);
            request.setAttribute("classRows", classes);
            request.setAttribute("attention", adminAttention(overview, classes,
                    overviewDAO.getUnassignedCourses(), overviewDAO.getEmptyOfferings()));
            request.setAttribute("unreadMessages", new MessageDAO().countUnread(user.getUserId()));
            request.setAttribute("latestAnnouncements", announcementDAO.getLatestGeneral("ADMIN", LATEST_ANNOUNCEMENTS));
        } else if ("TEACHER".equals(user.getRole())) {
            LocalDate today = LocalDate.now();
            List<CourseAllocation> current = new ArrayList<>();
            List<String[]> attention = new ArrayList<>(); // {text, link, link label}
            int students = 0;
            for (CourseAllocation ca : allocationDAO.getTeachingOverview(user.getUserId())) {
                String code = ca.getCourse().getCourseCode();
                if (TeacherCoursesServlet.isCurrentTerm(ca.getSemester(), today)) {
                    current.add(ca);
                    students += ca.getEnrolledCount();
                    if (AttendanceRules.isOpen(ca.getSemester()) && ca.getEnrolledCount() > 0) {
                        if (ca.getLastLectureDate() == null)
                            attention.add(new String[] { "No attendance has been marked for " + code + " yet.",
                                    "manageAttendance?allocationId=" + ca.getAllocationId(), "Mark attendance" });
                        else if (ca.getLastLectureDate().toLocalDate().isBefore(AttendanceRules.earliestEditable()))
                            attention.add(new String[] { "No attendance marked for " + code + " in the last "
                                    + AttendanceRules.EDIT_WINDOW_DAYS + " days (last lecture "
                                    + AttendanceRules.format(ca.getLastLectureDate().toLocalDate()) + ").",
                                    "manageAttendance?allocationId=" + ca.getAllocationId(), "Mark attendance" });
                    }
                }
                // Term over but grades still open: remind before they lock
                boolean termOver = ca.getSemester().getEndDate() != null && ca.getSemester().getEndDate().toLocalDate().isBefore(today);
                if (termOver && GradeRules.isEditable(ca.getSemester()) && ca.getPublishedCount() < ca.getEnrolledCount())
                    attention.add(new String[] { code + " (" + ca.getSemester().getName() + "): " + ca.getPublishedCount() + " of "
                            + ca.getEnrolledCount() + " results published. Grades lock after "
                            + GradeRules.formatDate(GradeRules.lockDate(ca.getSemester())) + ".",
                            "uploadGrades?allocationId=" + ca.getAllocationId(), "Upload grades" });
            }
            request.setAttribute("currentCourses", current);
            request.setAttribute("studentCount", students);
            request.setAttribute("attention", attention);
            request.setAttribute("unreadMessages", new MessageDAO().countUnread(user.getUserId()));
            request.setAttribute("latestAnnouncements", announcementDAO.getLatestGeneral("TEACHER", LATEST_ANNOUNCEMENTS));
        }

        request.getRequestDispatcher("dashboard.jsp").forward(request, response);
    }

    private static final int MAX_LISTED = 5;

    /**
     * What the admin should act on: {text, link, link label, "error" for urgent}. Urgent: classes
     * with students but no current semester, and courses a class should take this term with no
     * teacher. Then waiting work (payment proofs, add/drop requests, tickets) and data to tidy up.
     */
    static List<String[]> adminAttention(AdminOverviewDAO.Overview o, List<AdminOverviewDAO.ClassRow> classes,
            List<AdminOverviewDAO.CourseGap> unassigned, List<AdminOverviewDAO.CourseGap> empty) {
        List<String[]> items = new ArrayList<>();
        List<String> unplaced = new ArrayList<>();
        int unplacedStudents = 0;
        for (AdminOverviewDAO.ClassRow c : classes)
            if (c.termName == null && c.students > 0) {
                unplaced.add(c.className + " (" + c.students + " student" + (c.students == 1 ? "" : "s") + ")");
                unplacedStudents += c.students;
            }
        if (!unplaced.isEmpty())
            items.add(new String[] { (unplaced.size() == 1 ? "This class has" : unplaced.size() + " classes have")
                    + " students but no current semester, so " + (unplacedStudents == 1 ? "its student cannot" : "they cannot")
                    + " request add/drop: " + String.join(", ", unplaced) + ".", "manageSemesters", "Manage semesters", "error" });
        for (int i = 0; i < unassigned.size() && i < MAX_LISTED; i++) {
            AdminOverviewDAO.CourseGap g = unassigned.get(i);
            items.add(new String[] { g.courseCode + " " + g.courseName + " has no teacher for " + g.termName
                    + " (offered to " + g.className + ").", "manageAllocations?courseId=" + g.courseId, "Assign teacher", "error" });
        }
        if (unassigned.size() > MAX_LISTED)
            items.add(new String[] { (unassigned.size() - MAX_LISTED) + " more course" + (unassigned.size() - MAX_LISTED == 1 ? "" : "s")
                    + " for this term have no teacher.", "manageCourses", "Manage courses", "error" });
        if (o.proofsToReview > 0)
            items.add(new String[] { o.proofsToReview + " payment proof" + (o.proofsToReview == 1 ? " is" : "s are") + " waiting for review.",
                    "uploadChallan", "Review proofs", "" });
        if (o.pendingRequests > 0)
            items.add(new String[] { o.pendingRequests + " add/drop request" + (o.pendingRequests == 1 ? " is" : "s are") + " waiting for approval.",
                    "manageCourseRequests", "Review requests", "" });
        if (o.openTickets > 0)
            items.add(new String[] { o.openTickets + " help desk ticket" + (o.openTickets == 1 ? " is" : "s are") + " open.",
                    "helpdesk", "Open Help Desk", "" });
        if (o.overdueChallans > 0)
            items.add(new String[] { o.overdueChallans + " challan" + (o.overdueChallans == 1 ? " is" : "s are") + " overdue ("
                    + money(o.overdueAmount) + " unpaid past the due date).", "uploadChallan", "View challans", "" });
        if (!empty.isEmpty()) {
            List<String> codes = new ArrayList<>();
            for (int i = 0; i < empty.size() && i < MAX_LISTED; i++)
                codes.add(empty.get(i).courseCode + " (" + empty.get(i).termName + ")");
            AdminOverviewDAO.CourseGap first = empty.get(0);
            items.add(new String[] { empty.size() + " course offering" + (empty.size() == 1 ? " has" : "s have") + " no students enrolled: "
                    + String.join(", ", codes) + (empty.size() > MAX_LISTED ? " and " + (empty.size() - MAX_LISTED) + " more" : "") + ".",
                    "manageEnrollment?courseId=" + first.courseId + "&allocationId=" + first.allocationId, "Enroll students", "" });
        }
        if (o.studentsWithoutClass > 0)
            items.add(new String[] { o.studentsWithoutClass + " active student" + (o.studentsWithoutClass == 1 ? " has" : "s have") + " no class assigned.",
                    "manageStudents", "Manage students", "" });
        if (o.teachersWithoutDepartment > 0)
            items.add(new String[] { o.teachersWithoutDepartment + " active teacher" + (o.teachersWithoutDepartment == 1 ? " has" : "s have") + " no department assigned.",
                    "manageTeachers", "Manage teachers", "" });
        return items;
    }

    static String money(java.math.BigDecimal amount) {
        return "PKR " + new java.text.DecimalFormat("#,##0").format(amount == null ? java.math.BigDecimal.ZERO : amount);
    }

    /**
     * What a student should act on: {text, link (or null), link label, "error" for urgent}.
     * Overdue fees and low attendance come first.
     */
    static List<String[]> studentAttention(ClassSemester currentTerm, List<CourseAttendance> termCourses,
            List<Challan> unpaid, List<Challan> proofRejected, int pendingRequests) {
        List<String[]> items = new ArrayList<>();
        java.text.SimpleDateFormat day = new java.text.SimpleDateFormat("dd MMM yyyy");
        for (Challan c : unpaid) {
            String what = "Challan " + c.getNumber() + (c.getTitle() != null ? " (" + c.getTitle() + ")" : "") + ", " + c.getAmountLabel();
            if (c.isOverdue())
                items.add(new String[] { what + ", is overdue: it was due on " + day.format(c.getDueDate()) + ".", "challans", "View challans", "error" });
            else if (!c.isProofPending())
                items.add(new String[] { what + (c.getDueDate() != null ? ", is due on " + day.format(c.getDueDate()) : ", is unpaid") + ".",
                        "challans", "View challans", "" });
        }
        for (Challan c : proofRejected)
            items.add(new String[] { "Your payment proof for challan " + c.getNumber() + " was not accepted"
                    + (c.getProofReviewNote() != null && !c.getProofReviewNote().isBlank() ? ": " + c.getProofReviewNote() : "")
                    + ". Please upload it again.", "challans", "Upload proof", "error" });
        for (CourseAttendance c : termCourses)
            if (c.isLow())
                items.add(new String[] { "Your attendance in " + c.getCourseCode() + " is " + c.getPercent() + "%, below "
                        + AttendanceRules.LOW_ATTENDANCE_PERCENT + "%.", "attendance?enrollmentId=" + c.getEnrollmentId(), "View attendance", "error" });
        if (pendingRequests > 0)
            items.add(new String[] { pendingRequests + " add/drop request" + (pendingRequests == 1 ? " is" : "s are") + " waiting for approval.",
                    "courseRequests", "View requests", "" });
        if (currentTerm == null)
            items.add(new String[] { "Your class is not placed in a semester right now, so add/drop is closed. Contact the administration if this is wrong.",
                    "helpdesk", "Help Desk", "" });
        // Urgent first, keeping the order within each kind
        List<String[]> sorted = new ArrayList<>();
        for (String[] i : items) if ("error".equals(i[3])) sorted.add(i);
        for (String[] i : items) if (!"error".equals(i[3])) sorted.add(i);
        return sorted;
    }
}
