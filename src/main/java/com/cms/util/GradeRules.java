package com.cms.util;

import com.cms.models.Semester;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rules for course marks and grades:
 * - marks are Sessional ({@value #SESSIONAL_MAX}), Mid ({@value #MID_MAX}) and Final ({@value #FINAL_MAX}),
 *   out of 100, in steps of 0.5;
 * - a mark left blank is "not entered yet" (e.g. the final exam has not been held), not zero;
 * - a letter grade is given only once all three marks are entered;
 * - students see a result only when it is published; only complete, published results count
 *   towards the transcript and CGPA;
 * - marks and publishing can be changed until {@value #EDIT_DAYS_AFTER_TERM} days after the term's
 *   end date; after that the grade sheet is locked (view only).
 * The letter scale below is the single source for the server and the teacher's live preview.
 */
public final class GradeRules {

    public static final int EDIT_DAYS_AFTER_TERM = 10;

    public static final double SESSIONAL_MAX = 25;
    public static final double MID_MAX = 35;
    public static final double FINAL_MAX = 40;
    public static final double TOTAL_MAX = SESSIONAL_MAX + MID_MAX + FINAL_MAX;

    // Lowest total for each letter, best first, and the letter's grade points
    private static final double[] MIN_TOTAL = { 85, 80, 75, 70, 65, 61, 58, 55, 50, 0 };
    private static final String[] LETTERS = { "A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D", "F" };
    private static final double[] POINTS = { 4.0, 3.7, 3.3, 3.0, 2.7, 2.3, 2.0, 1.7, 1.0, 0.0 };

    // Plain decimal numbers only (Double.parseDouble would also accept "NaN", "1e1" or "0x1p3")
    private static final Pattern NUMBER = Pattern.compile("\\d{1,3}(\\.\\d{1,2})?|\\.\\d{1,2}");

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private GradeRules() {
    }

    // ---------------- Editing window ----------------

    /** The last day grades of a term can be changed (end date + 10 days); null when the term has no end date. */
    public static LocalDate lockDate(Semester term) {
        return term == null || term.getEndDate() == null ? null
                : term.getEndDate().toLocalDate().plusDays(EDIT_DAYS_AFTER_TERM);
    }

    /** True while marks and publishing can still be changed (the lock date itself is still allowed). */
    public static boolean isEditable(Semester term, LocalDate today) {
        LocalDate lock = lockDate(term);
        return lock == null || !today.isAfter(lock);
    }

    public static boolean isEditable(Semester term) {
        return isEditable(term, LocalDate.now());
    }

    /** Why the grade sheet is locked, or null while it can be changed. */
    public static String lockReason(Semester term) {
        if (isEditable(term))
            return null;
        return "Grades for " + term.getName() + " are locked: the term ended on "
                + formatDate(term.getEndDate().toLocalDate()) + ", and grades can be changed only until "
                + formatDate(lockDate(term)) + " (" + EDIT_DAYS_AFTER_TERM + " days after the term ends).";
    }

    /** e.g. "25 Jan 2025". */
    public static String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DISPLAY);
    }

    /** True for 0..maximum in steps of 0.5. */
    public static boolean isValidMark(double mark, double maximum) {
        return Double.isFinite(mark) && mark >= 0 && mark <= maximum
                && Math.abs(mark * 2 - Math.rint(mark * 2)) < 1e-9;
    }

    /**
     * A typed mark: null when blank (not entered yet). Throws IllegalArgumentException with a
     * user-facing message, e.g. "Mid must be between 0 and 35 in steps of 0.5".
     */
    public static Double parseMark(String value, double maximum, String label) {
        if (value == null || value.trim().isEmpty())
            return null;
        String v = value.trim();
        double mark = NUMBER.matcher(v).matches() ? Double.parseDouble(v) : Double.NaN;
        if (!isValidMark(mark, maximum))
            throw new IllegalArgumentException(label + " must be between 0 and " + format(maximum) + " in steps of 0.5");
        return mark;
    }

    /** All three marks entered. */
    public static boolean isComplete(Double sessional, Double mid, Double fin) {
        return sessional != null && mid != null && fin != null;
    }

    /** Sum of the marks entered so far; null when none is entered. */
    public static Double total(Double sessional, Double mid, Double fin) {
        if (sessional == null && mid == null && fin == null)
            return null;
        return (sessional == null ? 0 : sessional) + (mid == null ? 0 : mid) + (fin == null ? 0 : fin);
    }

    /** Letter for a total out of 100. */
    public static String letter(double total) {
        for (int i = 0; i < MIN_TOTAL.length; i++)
            if (total >= MIN_TOTAL[i])
                return LETTERS[i];
        return "F";
    }

    /** Letter once all marks are entered, otherwise null. */
    public static String letter(Double sessional, Double mid, Double fin) {
        return isComplete(sessional, mid, fin) ? letter(total(sessional, mid, fin)) : null;
    }

    /** Grade points of a letter (unknown or null: 0). */
    public static double points(String letter) {
        for (int i = 0; i < LETTERS.length; i++)
            if (LETTERS[i].equals(letter))
                return POINTS[i];
        return 0.0;
    }

    public static boolean isPass(String letter) {
        return letter != null && !"F".equals(letter);
    }

    /** GPA from quality points and credit hours, e.g. "3.45"; "-" without credits. */
    public static String gpa(double qualityPoints, int credits) {
        return credits > 0 ? String.format(Locale.ROOT, "%.2f", qualityPoints / credits) : "-";
    }

    /**
     * CGPA over results that count on the transcript (published, all marks entered), weighted by
     * credit hours; for a course taken more than once only its latest graded attempt counts.
     * "-" when there are none. Same rule as the transcript and gradebook.
     */
    public static String cgpa(java.util.List<com.cms.models.Grade> grades) {
        return summarizeCumulative(grades).getGpa();
    }

    /** Totals over a set of results; only graded results (see Grade.isGraded) count. */
    public static final class Summary {
        public int graded;          // results counted
        public int withdrawn;       // "W" courses (no credits, no points)
        public int replaced;        // graded attempts left out because the course was graded again later
        public int creditsAttempted;
        public int creditsEarned;   // graded with a pass (not F)
        public double qualityPoints;

        public String getGpa() { return gpa(qualityPoints, creditsAttempted); }
    }

    /** Every graded result in the list counts (e.g. one semester's GPA, as it was in that term). */
    public static Summary summarize(java.util.List<com.cms.models.Grade> grades) {
        return summarize(grades, java.util.Collections.emptySet());
    }

    /**
     * Cumulative totals (CGPA, overall credit hours): for a course taken more than once, only the
     * latest graded attempt counts; earlier graded attempts are left out (see {@link #replaced}).
     */
    public static Summary summarizeCumulative(java.util.List<com.cms.models.Grade> grades) {
        return summarize(grades, replaced(grades));
    }

    private static Summary summarize(java.util.List<com.cms.models.Grade> grades, java.util.Set<Integer> leaveOut) {
        Summary s = new Summary();
        for (com.cms.models.Grade g : grades) {
            if (g.isWithdrawn()) {
                s.withdrawn++;
                continue;
            }
            if (!g.isGraded())
                continue;
            if (leaveOut.contains(g.getEnrollmentId())) {
                s.replaced++;
                continue;
            }
            int ch = g.getEnrollment().getCourseAllocation().getCourse().getCreditHours();
            s.graded++;
            s.qualityPoints += points(g.getGradeLetter()) * ch;
            s.creditsAttempted += ch;
            if (isPass(g.getGradeLetter()))
                s.creditsEarned += ch;
        }
        return s;
    }

    /**
     * Enrollment IDs of graded attempts replaced by a later graded attempt of the same course
     * (later = later term start, then term id). An attempt that is not graded yet, or withdrawn,
     * does not replace anything: the earlier grade keeps counting until the new one is published.
     */
    public static java.util.Set<Integer> replaced(java.util.List<com.cms.models.Grade> grades) {
        java.util.Map<Integer, com.cms.models.Grade> latest = new java.util.HashMap<>();
        for (com.cms.models.Grade g : grades) {
            if (!g.isGraded())
                continue;
            int courseId = g.getEnrollment().getCourseAllocation().getCourse().getCourseId();
            com.cms.models.Grade current = latest.get(courseId);
            if (current == null || isLater(g, current))
                latest.put(courseId, g);
        }
        java.util.Set<Integer> replaced = new java.util.HashSet<>();
        for (com.cms.models.Grade g : grades)
            if (g.isGraded() && latest.get(g.getEnrollment().getCourseAllocation().getCourse().getCourseId()) != g)
                replaced.add(g.getEnrollmentId());
        return replaced;
    }

    // a taken in a later term than b (start date, then term id, then enrollment id)
    private static boolean isLater(com.cms.models.Grade a, com.cms.models.Grade b) {
        Semester ta = a.getEnrollment().getCourseAllocation().getSemester();
        Semester tb = b.getEnrollment().getCourseAllocation().getSemester();
        java.sql.Date da = ta == null ? null : ta.getStartDate(), db = tb == null ? null : tb.getStartDate();
        if (da != null && db != null && !da.equals(db))
            return da.after(db);
        int sa = a.getEnrollment().getCourseAllocation().getSemesterId(), sb = b.getEnrollment().getCourseAllocation().getSemesterId();
        if (sa != sb)
            return sa > sb;
        return a.getEnrollmentId() > b.getEnrollmentId();
    }

    /** "20", "12.5"; "-" when not entered. */
    public static String format(Double mark) {
        if (mark == null)
            return "-";
        return mark == Math.rint(mark) ? String.valueOf(mark.longValue()) : String.valueOf(mark);
    }

    /** Value for a number input: "" when not entered. */
    public static String inputValue(Double mark) {
        return mark == null ? "" : format(mark);
    }

    /** Rows of the scale for display: {letter, "85 - 100", "4.0"}. */
    public static String[][] scale() {
        String[][] rows = new String[LETTERS.length][];
        for (int i = 0; i < LETTERS.length; i++) {
            String upper = i == 0 ? format(TOTAL_MAX) : format(MIN_TOTAL[i - 1] - 0.5);
            rows[i] = new String[] { LETTERS[i], format(MIN_TOTAL[i]) + " - " + upper,
                    String.format(Locale.ROOT, "%.1f", POINTS[i]) };
        }
        return rows;
    }

    /** The scale as a JavaScript array literal, e.g. [[85,"A"],[80,"A-"],...]. */
    public static String scaleJs() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < LETTERS.length; i++)
            sb.append(i == 0 ? "" : ",").append('[').append(format(MIN_TOTAL[i])).append(",\"").append(LETTERS[i]).append("\"]");
        return sb.append(']').toString();
    }
}
