package com.cms.controllers;

import com.cms.dao.MessageDAO;
import com.cms.models.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageFlowTest {

    @Test
    void adminCanMessageTeacherAndStudent() {
        User admin = new User();
        admin.setUserId(1);
        admin.setRole("ADMIN");
        admin.setActive(true);

        User teacher = new User();
        teacher.setUserId(2);
        teacher.setRole("TEACHER");
        teacher.setActive(true);

        User student = new User();
        student.setUserId(3);
        student.setRole("STUDENT");
        student.setActive(true);

        assertTrue(MessageServlet.canMessage(admin, teacher));
        assertTrue(MessageServlet.canMessage(admin, student));
    }

    @Test
    void teacherAndStudentCanMessageAdmin() {
        User teacher = new User();
        teacher.setUserId(2);
        teacher.setRole("TEACHER");
        teacher.setActive(true);

        User student = new User();
        student.setUserId(3);
        student.setRole("STUDENT");
        student.setActive(true);

        User admin = new User();
        admin.setUserId(1);
        admin.setRole("ADMIN");
        admin.setActive(true);

        assertTrue(MessageServlet.canMessage(teacher, admin));
        assertTrue(MessageServlet.canMessage(student, admin));
    }

    @Test
    void helpDeskMessagesAreKeptSeparateFromUserMessages() {
        String ticketText = "[HELP DESK TICKET #5]: I need help";
        String replyText = "[HELP DESK REPLY #5]: This is fixed";

        assertTrue(MessageDAO.isHelpDeskMessage(ticketText));
        assertTrue(MessageDAO.isHelpDeskMessage(replyText));
        assertFalse(MessageDAO.isHelpDeskMessage("Hello teacher, can you review my work?"));
    }
}
