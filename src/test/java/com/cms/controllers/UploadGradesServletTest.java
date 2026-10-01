package com.cms.controllers;

import com.cms.models.CourseAllocation;
import com.cms.models.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UploadGradesServletTest {

    @Test
    void onlyAssignedTeacherCanManageAllocation() {
        User teacher = new User();
        teacher.setUserId(12);
        teacher.setRole("TEACHER");

        CourseAllocation allocation = new CourseAllocation();
        allocation.setTeacherId(12);

        assertTrue(UploadGradesServlet.canManageAllocation(teacher, allocation));
        allocation.setTeacherId(13);
        assertFalse(UploadGradesServlet.canManageAllocation(teacher, allocation));
        teacher.setRole("ADMIN");
        allocation.setTeacherId(12);
        assertFalse(UploadGradesServlet.canManageAllocation(teacher, allocation));
        assertFalse(UploadGradesServlet.canManageAllocation(null, allocation));
        assertFalse(UploadGradesServlet.canManageAllocation(teacher, null));
    }

    // Mark validation and the editing window are in GradeRules (see GradeRulesTest)
}
