package com.cms.filters;

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

/**
 * Authentication Filter
 * Intercepts requests to protected resources and ensures the user is logged in.
 */

public class AuthenticationFilter extends HttpFilter implements Filter {
    private static final long serialVersionUID = 1L;

    // List of public resources that don't satisfy the filter
    private static final String[] PUBLIC_URLS = {
            "/login.jsp",
            "/login",
            "/css/styles.css",
            "/images/"
    };

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String requestURI = httpRequest.getRequestURI();

        // 1. Check if resource is public
        boolean isPublic = false;
        String pathInsideContext = requestURI.substring(httpRequest.getContextPath().length());

        for (String publicUrl : PUBLIC_URLS) {
            if (pathInsideContext.startsWith(publicUrl) || pathInsideContext.equals(publicUrl)) {
                isPublic = true;
                break;
            }
        }

        // Also allow root path to go to login.jsp (via welcome file)
        if (requestURI.equals(httpRequest.getContextPath() + "/")) {
            isPublic = true;
        }

        if (isPublic) {
            chain.doFilter(request, response);
            return;
        }

        // 2. Check Session for Protected Resources
        HttpSession session = httpRequest.getSession(false);
        boolean isLoggedIn = (session != null && session.getAttribute("user") != null);

        if (isLoggedIn) {
            // User is logged in, proceed
            chain.doFilter(request, response);
        } else {
            // User is NOT logged in, redirect to login page
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/login.jsp");
        }
    }

    public void init(FilterConfig fConfig) throws ServletException {
    }

    public void destroy() {
    }
}
