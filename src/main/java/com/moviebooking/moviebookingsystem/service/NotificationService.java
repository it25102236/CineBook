package com.moviebooking.moviebookingsystem.service;

import com.moviebooking.moviebookingsystem.model.Notification;
import com.moviebooking.moviebookingsystem.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void notifySupport(String title, String message, String link) {
        save("SUPPORT", null, title, message, link);
    }

    public void notifyAdmin(String title, String message, String link) {
        save("ADMIN", null, title, message, link);
    }

    public void notifyCustomer(String email, String title, String message, String link) {
        if (email != null && !email.isBlank()) {
            save("CUSTOMER", email.trim(), title, message, link);
        }
    }

    public List<Notification> forRole(String role) {
        return notificationRepository.findByRecipientRoleOrderByCreatedAtDesc(role);
    }

    public List<Notification> forEmail(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        return notificationRepository.findByRecipientEmailIgnoreCaseOrderByCreatedAtDesc(email.trim());
    }

    public Notification findById(Long id) {
        if (id == null) {
            return null;
        }
        return notificationRepository.findById(id).orElse(null);
    }

    public void markAsRead(Long id) {
        notificationRepository.markAsRead(id);
    }

    public void markAllAsReadForRole(String role) {
        notificationRepository.markAllAsRead(role);
    }

    public void markAllAsReadForEmail(String email) {
        notificationRepository.markAllAsReadForEmail(email);
    }

    private void save(
            String role,
            String email,
            String title,
            String message,
            String link) {
        Notification notification = new Notification();
        notification.setRecipientRole(role);
        notification.setRecipientEmail(email);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setLink(link);
        notification.setCreatedAt(System.currentTimeMillis());
        notificationRepository.save(notification);
    }
}