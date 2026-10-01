package com.cms.controllers;

import com.cms.dao.MessageDAO;
import com.cms.dao.UserDAO;
import com.cms.models.Message;
import com.cms.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class MessageServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final int MAX_RECIPIENTS = 5000;
    private static final String FLASH_SUCCESS = "messages.flash.success";
    private static final String FLASH_ERROR = "messages.flash.error";
    private static final String FLASH_DRAFT = "messages.flash.draft";
    private static final String FLASH_DRAFT_TO = "messages.flash.draftTo";

    private MessageDAO messageDAO;
    private UserDAO userDAO;

    public void init() {
        messageDAO = new MessageDAO();
        userDAO = new UserDAO();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.sendRedirect("login");
            return;
        }
        User user = (User) session.getAttribute("user");

        SupportTicketServlet.moveFlash(session, request, FLASH_SUCCESS, "successMessage");
        SupportTicketServlet.moveFlash(session, request, FLASH_ERROR, "errorMessage");

        List<Message> inbox = messageDAO.getMessagesForUser(user.getUserId());
        List<Message> sent = messageDAO.getSentMessages(user.getUserId());

        request.setAttribute("messages", inbox);
        request.setAttribute("sentMessages", sent);
        request.setAttribute("view", "sent".equals(request.getParameter("view")) ? "sent" : "inbox");
        Map<String, List<User>> contactGroups = buildContactGroups(user, inbox);
        request.setAttribute("contactGroups", contactGroups);
        addCourseFilterData(request, user, contactGroups);

        // A failed send restores the recipients and the typed text
        SupportTicketServlet.moveFlash(session, request, FLASH_DRAFT, "draftContent");
        SupportTicketServlet.moveFlash(session, request, FLASH_DRAFT_TO, "selectedReceiverIds");

        // Reply / "Message again" links: ?to=5 or ?to=5,6,7 (or repeated to=)
        Set<Integer> toIds = parseIds(request.getParameterValues("to"));
        if (!toIds.isEmpty()) {
            request.setAttribute("selectedReceiverIds", toIds);
        }

        // Inbox has been viewed; the page still highlights what was unread before this visit
        if (!"sent".equals(request.getAttribute("view"))) {
            messageDAO.markInboxRead(user.getUserId());
        }

        request.getRequestDispatcher("messages.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.sendRedirect("login");
            return;
        }
        User user = (User) session.getAttribute("user");

        String content = request.getParameter("content");
        content = (content == null) ? "" : content.trim();

        // Recipients: the picker sends one comma-separated "receiverIds" field (so a large
        // selection cannot hit the container's form-parameter limit); without JavaScript
        // the native multi-select sends repeated "receiverId" fields.
        Set<Integer> ids = parseIds(request.getParameterValues("receiverIds"));
        ids.addAll(parseIds(request.getParameterValues("receiverId")));
        ids.remove(user.getUserId());

        boolean tooMany = ids.size() > MAX_RECIPIENTS;
        Map<Integer, User> found = (ids.isEmpty() || tooMany) ? new HashMap<>() : userDAO.getUsersByIds(ids);
        // Teachers may only message their own students and students who have messaged them
        // (the same people their recipient picker offers)
        Set<Integer> teacherStudents = null;
        if ("TEACHER".equals(user.getRole()) && !found.isEmpty()) {
            teacherStudents = new HashSet<>();
            for (User s : userDAO.getStudentsByTeacher(user.getUserId()))
                teacherStudents.add(s.getUserId());
            for (Message m : messageDAO.getMessagesForUser(user.getUserId()))
                teacherStudents.add(m.getSenderId());
        }

        List<User> receivers = new ArrayList<>();
        User notAllowed = null;
        boolean unknown = false;
        for (Integer id : ids) {
            User r = found.get(id);
            if (r == null) {
                unknown = true;
            } else if (!canMessage(user, r)
                    || (teacherStudents != null && "STUDENT".equals(r.getRole()) && !teacherStudents.contains(id))) {
                notAllowed = r;
            } else {
                receivers.add(r);
            }
        }

        if (ids.isEmpty()) {
            session.setAttribute(FLASH_ERROR, "Please select at least one recipient.");
        } else if (tooMany) {
            session.setAttribute(FLASH_ERROR, "You can message at most " + MAX_RECIPIENTS + " recipients at once.");
        } else if (unknown) {
            session.setAttribute(FLASH_ERROR, "One or more selected recipients no longer exist. Please check your selection.");
        } else if (notAllowed != null) {
            session.setAttribute(FLASH_ERROR, "You are not allowed to message " + notAllowed.getDisplayName() + ".");
        } else if (content.isEmpty()) {
            session.setAttribute(FLASH_ERROR, "Message cannot be empty.");
        } else if (content.length() > MAX_TEXT_LENGTH) {
            session.setAttribute(FLASH_ERROR, "Message is too long (maximum " + MAX_TEXT_LENGTH + " characters).");
        } else if (MessageDAO.isHelpDeskMessage(content)) {
            // Such messages are hidden from Messages, so it would silently disappear
            session.setAttribute(FLASH_ERROR, "Messages cannot start with \"[HELP DESK\". Use the Help Desk page for tickets.");
        } else {
            List<Integer> receiverIds = new ArrayList<>();
            receivers.forEach(r -> receiverIds.add(r.getUserId()));

            // All-or-nothing: one transaction for the whole broadcast
            if (messageDAO.sendMessages(user.getUserId(), receiverIds, content)) {
                session.setAttribute(FLASH_SUCCESS, receivers.size() == 1
                        ? "Message sent to " + receivers.get(0).getDisplayName() + "."
                        : "Message sent to " + receivers.size() + " recipients.");
                response.sendRedirect("messages?view=sent");
                return;
            }
            session.setAttribute(FLASH_ERROR, "Failed to send message. Please try again.");
        }

        // Keep what the user typed and who they picked, so nothing is lost on an error
        if (!content.isEmpty() && content.length() <= MAX_TEXT_LENGTH) {
            session.setAttribute(FLASH_DRAFT, content);
        }
        Set<Integer> keep = new LinkedHashSet<>();
        receivers.forEach(r -> keep.add(r.getUserId()));
        if (!keep.isEmpty()) {
            session.setAttribute(FLASH_DRAFT_TO, keep);
        }
        response.sendRedirect("messages#compose");
    }

    // Parses values like "5", "5,6,7" (possibly repeated) into distinct positive IDs, in order
    static Set<Integer> parseIds(String[] values) {
        Set<Integer> ids = new LinkedHashSet<>();
        if (values == null)
            return ids;
        for (String v : values) {
            if (v == null)
                continue;
            for (String part : v.split(",")) {
                try {
                    int id = Integer.parseInt(part.trim());
                    if (id > 0)
                        ids.add(id);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return ids;
    }

    /**
     * Course data for the recipient picker's course filter:
     * "contactCourses" (userId -> code -> name) and "filterCourses" (code -> name).
     * Teachers only see course links for their own courses.
     */
    private void addCourseFilterData(HttpServletRequest request, User user, Map<String, List<User>> contactGroups) {
        Map<Integer, Map<String, String>> allCourses = userDAO.getCoursesByUser();
        Map<String, String> ownCourses = allCourses.getOrDefault(user.getUserId(), new TreeMap<>());
        boolean isAdmin = "ADMIN".equals(user.getRole());
        boolean isTeacher = "TEACHER".equals(user.getRole());

        Map<Integer, Map<String, String>> contactCourses = new HashMap<>();
        // Admin filters by any course a contact has; others by their own courses
        Map<String, String> filterCourses = new TreeMap<>();
        if (!isAdmin) {
            filterCourses.putAll(ownCourses);
        }
        for (List<User> group : contactGroups.values()) {
            for (User contact : group) {
                Map<String, String> courses = new TreeMap<>(allCourses.getOrDefault(contact.getUserId(), new TreeMap<>()));
                if (isTeacher) {
                    courses.keySet().retainAll(ownCourses.keySet());
                }
                if (isAdmin) {
                    filterCourses.putAll(courses);
                }
                contactCourses.put(contact.getUserId(), courses);
            }
        }
        request.setAttribute("contactCourses", contactCourses);
        request.setAttribute("filterCourses", filterCourses);
    }

    /**
     * Messaging rules: Admin can message anyone; Teachers can message Students and
     * Admin; Students can message Teachers and Admin. Nobody messages themselves or
     * a deactivated account.
     */
    static boolean canMessage(User sender, User receiver) {
        if (receiver == null || receiver.getUserId() == sender.getUserId() || !receiver.isActive()) {
            return false;
        }
        String to = receiver.getRole();
        switch (sender.getRole()) {
            case "ADMIN":
                return true;
            case "TEACHER":
                return "STUDENT".equals(to) || "ADMIN".equals(to);
            case "STUDENT":
                return "TEACHER".equals(to) || "ADMIN".equals(to);
            default:
                return false;
        }
    }

    // Recipient dropdown options, grouped by label, for the logged-in user's role
    private Map<String, List<User>> buildContactGroups(User user, List<Message> inbox) {
        Map<String, List<User>> groups = new LinkedHashMap<>();
        List<User> admins = new ArrayList<>();
        List<User> teachers = new ArrayList<>();
        List<User> students = new ArrayList<>();

        switch (user.getRole()) {
            case "ADMIN":
                teachers.addAll(userDAO.getUsersByRole("TEACHER"));
                students.addAll(userDAO.getUsersByRole("STUDENT"));
                break;
            case "TEACHER":
                admins.addAll(userDAO.getUsersByRole("ADMIN"));
                students.addAll(userDAO.getStudentsByTeacher(user.getUserId()));
                break;
            case "STUDENT":
                admins.addAll(userDAO.getUsersByRole("ADMIN"));
                teachers.addAll(userDAO.getUsersByRole("TEACHER"));
                break;
            default:
                break;
        }

        // Anyone who messaged this user can be replied to, even if not in the default lists
        Set<Integer> listed = new HashSet<>();
        for (List<User> list : List.of(admins, teachers, students)) {
            list.removeIf(u -> !canMessage(user, u));
            list.forEach(u -> listed.add(u.getUserId()));
        }
        for (Message m : inbox) {
            if (listed.contains(m.getSenderId()))
                continue;
            User sender = userDAO.getUserById(m.getSenderId());
            if (canMessage(user, sender)) {
                listed.add(sender.getUserId());
                if ("ADMIN".equals(sender.getRole()))
                    admins.add(sender);
                else if ("TEACHER".equals(sender.getRole()))
                    teachers.add(sender);
                else
                    students.add(sender);
            }
        }

        if (!admins.isEmpty())
            groups.put("Administration", admins);
        if (!teachers.isEmpty())
            groups.put("Teachers", teachers);
        if (!students.isEmpty())
            groups.put("TEACHER".equals(user.getRole()) ? "My Students" : "Students", students);
        return groups;
    }
}
