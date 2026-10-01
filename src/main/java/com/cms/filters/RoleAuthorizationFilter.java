package com.cms.filters;

import com.cms.models.User;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Role Authorization Filter
 * Restricts access to specific URLs based on User Role.
 */

public class RoleAuthorizationFilter extends HttpFilter implements Filter {
    private static final long serialVersionUID = 1L;

    // Map of URL patterns to Allowed Roles
    private static final Map<String, List<String>> ACCESS_CONTROLS = new HashMap<>();

    static {
        // Student Only Pages
        List<String> studentRoles = Arrays.asList("STUDENT");
        ACCESS_CONTROLS.put("/courseRequests", studentRoles);
        ACCESS_CONTROLS.put("/course_requests.jsp", studentRoles);
        ACCESS_CONTROLS.put("/challans", studentRoles);
        ACCESS_CONTROLS.put("/challans.jsp", studentRoles);
        ACCESS_CONTROLS.put("/attendance", studentRoles);
        ACCESS_CONTROLS.put("/attendance.jsp", studentRoles);
        ACCESS_CONTROLS.put("/gradebook", studentRoles);
        ACCESS_CONTROLS.put("/gradebook.jsp", studentRoles);
        ACCESS_CONTROLS.put("/transcript", studentRoles);
        ACCESS_CONTROLS.put("/transcript.jsp", studentRoles);

        // Teacher Only Pages
        List<String> teacherRoles = Arrays.asList("TEACHER");
        ACCESS_CONTROLS.put("/manageAttendance", teacherRoles);
        ACCESS_CONTROLS.put("/manage_attendance.jsp", teacherRoles);
        ACCESS_CONTROLS.put("/uploadGrades", teacherRoles);
        ACCESS_CONTROLS.put("/upload_grades.jsp", teacherRoles);
        ACCESS_CONTROLS.put("/myCourses", teacherRoles);
        ACCESS_CONTROLS.put("/my_courses.jsp", teacherRoles);
        ACCESS_CONTROLS.put("/manageAnnouncements", teacherRoles);
        ACCESS_CONTROLS.put("/course_announcements.jsp", teacherRoles);

        // Example: Admin Only (Future)
        // ACCESS_CONTROLS.put("/manageCourses", Arrays.asList("ADMIN"));
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String requestURI = httpRequest.getRequestURI();

        // Get path relative to context (e.g., "/courseRequests")
        String path = requestURI.substring(httpRequest.getContextPath().length());

        // Check if strict rule exists for this path
        if (ACCESS_CONTROLS.containsKey(path)) {
            HttpSession session = httpRequest.getSession(false);
            User user = (session != null) ? (User) session.getAttribute("user") : null;

            if (user != null) {
                String userRole = user.getRole();
                List<String> allowedRoles = ACCESS_CONTROLS.get(path);

                if (!allowedRoles.contains(userRole)) {
                    // Role NOT authorized - Redirect to Dashboard with error
                    // Or send 403 Forbidden
                    httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN,
                            "Access Denied: You do not have permission to view this resource.");
                    return;
                }
            } else {
                // Not logged in (AuthenticationFilter should handle this, but double check)
                httpResponse.sendRedirect(httpRequest.getContextPath() + "/login.jsp");
                return;
            }
        }

        // Proceed if no rule exists or authorized
        chain.doFilter(request, response);
    }

    public void init(FilterConfig fConfig) throws ServletException {
    }

    public void destroy() {
    }
}
