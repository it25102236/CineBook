package com.moviebooking.moviebookingsystem.controller;

import com.moviebooking.moviebookingsystem.model.Notification;
import com.moviebooking.moviebookingsystem.model.User;
import com.moviebooking.moviebookingsystem.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
public class NotificationViewAdvice {

    private final NotificationService notificationService;

    public NotificationViewAdvice(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @ModelAttribute
    public void addNotifications(Model model, HttpSession session) {
        User user = (User) session.getAttribute("loggedInUser");
        List<Notification> notifications = List.of();
        if (user != null) {
            if ("SUPPORT".equalsIgnoreCase(user.getRole())) {
                notifications = notificationService.forRole("SUPPORT");
            } else if ("ADMIN".equalsIgnoreCase(user.getRole())) {
                notifications = notificationService.forRole("ADMIN");
            } else if ("CUSTOMER".equalsIgnoreCase(user.getRole())) {
                notifications = notificationService.forEmail(user.getEmail());
            }
        }
        model.addAttribute("userNotifications", notifications);
        model.addAttribute(
                "unreadNotificationCount",
                notifications.stream().filter(notification -> !notification.isRead()).count()
        );
    }
}