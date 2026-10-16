package com.moviebooking.moviebookingsystem.controller;

import com.moviebooking.moviebookingsystem.model.Notification;
import com.moviebooking.moviebookingsystem.model.User;
import com.moviebooking.moviebookingsystem.service.NotificationService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;

@Controller
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications/read")
    public void readNotification(
            @RequestParam Long id,
            HttpServletResponse response) throws IOException {

        Notification notification = notificationService.findById(id);

        if (notification == null) {
            response.sendRedirect("/");
            return;
        }

        notificationService.markAsRead(id);

        String link = notification.getLink();
        if (link == null || link.isBlank()) {
            link = "/";
        }

        response.sendRedirect(link);
    }

    @PostMapping("/notifications/read-all")
    public String markAllNotificationsRead(
            HttpSession session,
            HttpServletRequest request) {

        User user = (User) session.getAttribute("loggedInUser");

        if (user == null) {
            return "redirect:/login";
        }

        if ("SUPPORT".equalsIgnoreCase(user.getRole())) {
            notificationService.markAllAsReadForRole("SUPPORT");
        } else if ("CUSTOMER".equalsIgnoreCase(user.getRole())) {
            notificationService.markAllAsReadForEmail(user.getEmail());
        }

        return safeRedirect(request.getHeader("Referer"));
    }

    // Keep the redirect on the current site by dropping the host part
    private String safeRedirect(String referer) {
        if (referer == null || referer.isBlank()) {
            return "redirect:/";
        }
        try {
            URI uri = new URI(referer);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return "redirect:/";
            }
            String query = uri.getQuery();
            return "redirect:" + path
                    + (query != null ? "?" + query : "");
        } catch (Exception e) {
            return "redirect:/";
        }
    }
}
