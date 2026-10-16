package com.moviebooking.moviebookingsystem.config;

import com.moviebooking.moviebookingsystem.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SupportAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {

        HttpSession session = request.getSession(false);
        User user =
                session == null
                        ? null
                        : (User) session.getAttribute("loggedInUser");

        String role =
                user == null || user.getRole() == null
                        ? null
                        : user.getRole().trim().toUpperCase();

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();

        // Support staff and administrators only
        if (path.startsWith("/support")) {
            if (!"SUPPORT".equals(role) && !"ADMIN".equals(role)) {
                if (user == null) {
                    response.sendRedirect(contextPath + "/login");
                } else {
                    response.sendRedirect(contextPath + "/error/403");
                }
                return false;
            }
        }

        // Customers only (own ticket management)
        if (path.equals("/my-support-tickets")
                || path.startsWith("/my-support-tickets/")) {
            if (!"CUSTOMER".equals(role)) {
                if (user == null) {
                    response.sendRedirect(contextPath + "/login");
                } else {
                    response.sendRedirect(contextPath + "/error/403");
                }
                return false;
            }
        }

        return true;
    }
}
